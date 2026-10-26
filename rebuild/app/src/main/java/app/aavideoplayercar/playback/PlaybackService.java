package app.aavideoplayercar.playback;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionError;
import androidx.room.InvalidationTracker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import app.aavideoplayercar.MainActivity;
import app.aavideoplayercar.data.MediaEntry;
import app.aavideoplayercar.data.MovieVideo;
import app.aavideoplayercar.data.RadioStation;
import app.aavideoplayercar.data.PodcastEpisode;
import app.aavideoplayercar.data.PodcastFeed;
import app.aavideoplayercar.data.IptvChannel;
import app.aavideoplayercar.data.MediaRepository;

/** One player for phone, system notification and Android Auto media browsers. */
@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public final class PlaybackService extends MediaLibraryService {
    public static final String ROOT = "audio-library";
    public static final String MUSIC = "local-music", RADIO = "radio-favorites", PODCASTS = "podcasts", FEED = "podcast-feed:";
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaLibrarySession session;
    private ExoPlayer player;
    private MediaRepository repository;
    private int audioCount = -1;
    private final ThreadPoolExecutor movieNetwork = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
    private app.aavideoplayercar.network.HttpDocument renewalRequest;
    private Future<?> renewalTask;
    private long movieItemGeneration;
    private boolean applyingRenewal, renewalAttempted;
    private final InvalidationTracker.Observer observer = new InvalidationTracker.Observer("media", "radio_stations", "podcast_feeds") {
        @Override public void onInvalidated(Set<String> tables) {
            repository.worker.execute(() -> {
                int count = repository.dao.count("audio/");
                int radioCount = repository.database.radio().count();
                int feedCount = repository.database.podcasts().feedCount();
                main.post(() -> {
                    if (session != null && count != audioCount) {
                        audioCount = count; session.notifyChildrenChanged(MUSIC, count, null);
                    }
                    if (session != null && tables.contains("radio_stations")) session.notifyChildrenChanged(RADIO, radioCount, null);
                    if (session != null && tables.contains("podcast_feeds")) session.notifyChildrenChanged(PODCASTS, feedCount, null);
                });
            });
        }
    };
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            saveProgress();
            if (player != null && player.isPlaying()) main.postDelayed(this, 5000);
        }
    };

    private static MediaItem rootItem() {
        return folder(ROOT, "Audio library");
    }
    private static MediaItem folder(String id, String title) {
        return new MediaItem.Builder().setMediaId(id).setMediaMetadata(new MediaMetadata.Builder()
            .setTitle(title).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build();
    }
    @Override public void onCreate() {
        super.onCreate();
        repository = MediaRepository.get(this);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(() -> app.aavideoplayercar.podcast.PodcastDownloads.get(this).playbackSource().createDataSource())).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean playing) {
                main.removeCallbacks(checkpoint);
                checkpoint.run();
            }
            @Override public void onPositionDiscontinuity(Player.PositionInfo old, Player.PositionInfo next, int reason) {
                if (old.mediaItem != null) persist(old.mediaItem, old.positionMs, 0);
                saveProgress();
            }
            @Override public void onPlaybackStateChanged(int state) { if (state == Player.STATE_ENDED) saveProgress(); }
            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                if (!applyingRenewal) { movieItemGeneration++; renewalAttempted = false; cancelMovieRenewal(); }
            }
            @Override public void onPlayerError(PlaybackException error) { renewExpiredMovie(error); }
        });
        PendingIntent launch = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        session = new MediaLibrarySession.Builder(this, player, new LibraryCallback()).setSessionActivity(launch).build();
        repository.database.getInvalidationTracker().addObserver(observer);
    }
    @Override public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controller) {
        // Legacy binding supplies a placeholder identity here. Authorize the real client in onConnect.
        return session;
    }
    private boolean local(MediaSession.ControllerInfo controller) { return controller.getUid() == android.os.Process.myUid(); }
    private void persist(MediaItem item, long position, long duration) {
        String id = item.mediaId;
        if (id.startsWith(RadioStation.PREFIX)) return;
        // Use the resolved playing item's policy as well as the current database policy.
        // An old live checkpoint must not become VOD progress after the user changes its type.
        if (id.startsWith(IptvChannel.PREFIX) && java.util.Objects.equals(item.mediaMetadata.mediaType, MediaMetadata.MEDIA_TYPE_TV_CHANNEL)) return;
        repository.worker.execute(() -> {
            if (id.startsWith(MovieVideo.PREFIX)) {
                MovieVideo video = repository.database.movieVideos().get(id.substring(MovieVideo.PREFIX.length()));
                if (video != null) { long known = duration > 0 ? duration : video.durationMs; long normalized = known > 0 && position >= known - 1000 ? 0 : Math.max(0, position); repository.database.movieVideos().progress(video.id, normalized, known, System.currentTimeMillis()); }
                return;
            }
            if (id.startsWith(IptvChannel.PREFIX)) {
                IptvChannel channel = repository.database.iptv().channel(id.substring(IptvChannel.PREFIX.length()));
                if (channel != null && !channel.isLive()) {
                    long known = duration > 0 ? duration : channel.durationMs;
                    long normalized = known > 0 && position >= known - 1000 ? 0 : Math.max(0, position);
                    repository.database.iptv().progress(channel.id, normalized, known, System.currentTimeMillis());
                }
                return;
            }
            if (id.startsWith(PodcastEpisode.PREFIX)) {
                PodcastEpisode episode = repository.database.podcasts().episode(id.substring(PodcastEpisode.PREFIX.length()));
                if (episode != null) {
                    long knownDuration = duration > 0 ? duration : episode.durationMs;
                    long normalized = knownDuration > 0 && position >= knownDuration - 1000 ? 0 : Math.max(0, position);
                    repository.database.podcasts().progress(episode.id, normalized, knownDuration, System.currentTimeMillis());
                }
                return;
            }
            MediaEntry entry = repository.dao.get(id);
            if (entry != null) {
                long knownDuration = duration > 0 ? duration : entry.durationMs;
                long normalized = knownDuration > 0 && position >= knownDuration - 1000 ? 0 : Math.max(0, position);
                repository.dao.progress(id, normalized, knownDuration, System.currentTimeMillis());
            }
        });
    }
    private void saveProgress() {
        if (player == null || player.getCurrentMediaItem() == null) return;
        persist(player.getCurrentMediaItem(), player.getCurrentPosition(), player.getDuration());
    }
    private void cancelMovieRenewal() {
        if (renewalRequest != null) renewalRequest.cancel();
        if (renewalTask != null) renewalTask.cancel(true);
        renewalRequest = null; renewalTask = null;
    }
    private static boolean expiredHttp(PlaybackException error) {
        if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return false;
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
                int code = ((HttpDataSource.InvalidResponseCodeException) cause).responseCode;
                return code == 401 || code == 403 || code == 404 || code == 410;
            }
        return false;
    }
    private void renewExpiredMovie(PlaybackException error) {
        if (player == null || renewalAttempted || !expiredHttp(error)) return;
        MediaItem failed = player.getCurrentMediaItem();
        if (failed == null || !failed.mediaId.startsWith(MovieVideo.PREFIX) || failed.localConfiguration == null) return;
        renewalAttempted = true;
        String mediaId = failed.mediaId, failedUrl = failed.localConfiguration.uri.toString();
        long generation = movieItemGeneration, position = Math.max(0, player.getCurrentPosition()); boolean play = player.getPlayWhenReady();
        saveProgress();
        repository.worker.execute(() -> {
            MovieVideo video = repository.database.movieVideos().get(mediaId.substring(MovieVideo.PREFIX.length()));
            if (video == null) return;
            app.aavideoplayercar.data.MovieAddon addon = repository.database.movieAddons().get(video.addonId);
            if (addon == null) return;
            var client = new app.aavideoplayercar.network.HttpDocument();
            main.post(() -> {
                if (player == null || generation != movieItemGeneration) return;
                renewalRequest = client;
                try { renewalTask = movieNetwork.submit(() -> {
                    try {
                        var stream = app.aavideoplayercar.movies.MoviePlayback.renew(video, addon, client);
                        repository.worker.execute(() -> {
                            try {
                                MovieVideo latest = repository.database.movieVideos().get(video.id);
                                if (latest == null || !latest.source.equals(video.source)) return;
                                MovieVideo refreshed = app.aavideoplayercar.movies.MoviePlayback.prepare(repository.database, video.addonId, video.providerType, video.providerVideoId, video.title, stream, video.streamIndex);
                                String url = app.aavideoplayercar.movies.MoviePlayback.resolve(refreshed);
                                main.post(() -> {
                                    if (player == null || generation != movieItemGeneration) return;
                                    MediaItem current = player.getCurrentMediaItem();
                                    if (current == null || !mediaId.equals(current.mediaId) || current.localConfiguration == null || !failedUrl.equals(current.localConfiguration.uri.toString())) return;
                                    applyingRenewal = true;
                                    try { player.setMediaItem(refreshed.item().buildUpon().setUri(url).build(), position); player.prepare(); if (play) player.play(); }
                                    finally { applyingRenewal = false; }
                                });
                            } catch (Exception ignored) { /* A stale or unavailable source stays in the error state for manual selection. */ }
                        });
                    } catch (Exception ignored) { /* No matching replacement: manual selection remains available. */ }
                }); } catch (java.util.concurrent.RejectedExecutionException ignored) { }
            });
        });
    }
    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        cancelMovieRenewal(); movieNetwork.shutdownNow();
        repository.database.getInvalidationTracker().removeObserver(observer);
        saveProgress();
        if (session != null) { session.release(); session = null; }
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }

    private final class LibraryCallback implements MediaLibrarySession.Callback {
        @Override public MediaSession.ConnectionResult onConnect(MediaSession session, MediaSession.ControllerInfo controller) {
            if (!local(controller) && !controller.isTrusted()) return MediaSession.ConnectionResult.reject();
            return new MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build();
        }
        @Override public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, LibraryParams params) {
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params));
        }
        @Override public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String id) {
            if (ROOT.equals(id)) return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), null));
            if (MUSIC.equals(id) || RADIO.equals(id) || PODCASTS.equals(id)) return Futures.immediateFuture(LibraryResult.ofItem(folder(id, MUSIC.equals(id) ? "Music" : RADIO.equals(id) ? "Radio favorites" : "Podcasts"), null));
            return repository.worker.submit(() -> {
                if (id.startsWith(FEED)) {
                    PodcastFeed feed = repository.database.podcasts().feed(id.substring(FEED.length()));
                    return feed == null ? LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) : LibraryResult.ofItem(folder(id, feed.title), null);
                }
                if (id.startsWith(MovieVideo.PREFIX)) {
                    MovieVideo video = repository.database.movieVideos().get(id.substring(MovieVideo.PREFIX.length()));
                    return !local(browser) || video == null ? LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) : LibraryResult.ofItem(video.item(), null);
                }
                if (id.startsWith(PodcastEpisode.PREFIX)) {
                    PodcastEpisode episode = repository.database.podcasts().episode(id.substring(PodcastEpisode.PREFIX.length()));
                    return episode == null ? LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) : LibraryResult.ofItem(app.aavideoplayercar.podcast.PodcastDownloads.get(PlaybackService.this).resolve(episode), null);
                }
                if (id.startsWith(IptvChannel.PREFIX)) {
                    IptvChannel channel = repository.database.iptv().channel(id.substring(IptvChannel.PREFIX.length()));
                    return !local(browser) || channel == null ? LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) : LibraryResult.ofItem(channel.toMediaItem(), null);
                }
                if (id.startsWith(RadioStation.PREFIX)) {
                    RadioStation station = repository.database.radio().get(id.substring(RadioStation.PREFIX.length()));
                    return station == null || (!local(browser) && !station.favorite) ? LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                        : LibraryResult.ofItem(station.toMediaItem(), null);
                }
                MediaEntry entry = repository.dao.get(id);
                return entry == null || (!local(browser) && !entry.isAudio())
                    ? LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                    : LibraryResult.ofItem(entry.toMediaItem(), null);
            });
        }
        @Override public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String parentId,
                int page, int pageSize, LibraryParams params) {
            if ((!ROOT.equals(parentId) && !MUSIC.equals(parentId) && !RADIO.equals(parentId) && !PODCASTS.equals(parentId) && !parentId.startsWith(FEED)) || page < 0 || pageSize < 1 || (long) page * Math.min(pageSize, PlaybackLimits.MAX_ITEMS) > Integer.MAX_VALUE)
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
            return repository.worker.submit(() -> {
                List<MediaItem> items = new ArrayList<>();
                int size = PlaybackLimits.pageSize(pageSize), offset = PlaybackLimits.offset(page, pageSize);
                if (ROOT.equals(parentId)) {
                    List<MediaItem> folders = java.util.Arrays.asList(folder(MUSIC, "Music"), folder(RADIO, "Radio favorites"), folder(PODCASTS, "Podcasts"));
                    for (int i = offset; i < Math.min(3L, (long) offset + size); i++) items.add(folders.get(i));
                } else if (PODCASTS.equals(parentId)) {
                    for (PodcastFeed feed : repository.database.podcasts().feeds(size, offset)) items.add(folder(FEED + feed.id, feed.title));
                } else if (parentId.startsWith(FEED)) {
                    String feedId = parentId.substring(FEED.length());
                    if (repository.database.podcasts().feed(feedId) == null) return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE);
                    for (PodcastEpisode episode : repository.database.podcasts().episodes(feedId, size, offset)) items.add(app.aavideoplayercar.podcast.PodcastDownloads.get(PlaybackService.this).resolve(episode));
                } else if (RADIO.equals(parentId)) {
                    for (RadioStation station : repository.database.radio().favorites(size, offset)) items.add(station.toMediaItem());
                } else {
                    for (MediaEntry entry : repository.dao.list("audio/", size, offset)) items.add(entry.toMediaItem());
                }
                return LibraryResult.ofItemList(items, params);
            });
        }
        @Override public ListenableFuture<LibraryResult<Void>> onSearch(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String query, LibraryParams params) {
            return repository.worker.submit(() -> {
                int count = repository.dao.searchCount(query) + repository.database.radio().searchCount(query) + repository.database.podcasts().searchCount(query);
                main.post(() -> { if (PlaybackService.this.session != null) session.notifySearchResultChanged(browser, query, count, params); });
                return LibraryResult.ofVoid();
            });
        }
        @Override public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String query, int page, int pageSize, LibraryParams params) {
            if (page < 0 || pageSize < 1 || (long) page * Math.min(pageSize, PlaybackLimits.MAX_ITEMS) > Integer.MAX_VALUE)
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
            return repository.worker.submit(() -> {
                List<MediaItem> items = new ArrayList<>();
                int size = PlaybackLimits.pageSize(pageSize), offset = PlaybackLimits.offset(page, pageSize);
                int musicCount = repository.dao.searchCount(query);
                if (offset < musicCount) for (MediaEntry entry : repository.dao.searchAudio(query, size, offset)) items.add(entry.toMediaItem());
                if (items.size() < size) for (RadioStation station : repository.database.radio().search(query, size - items.size(), Math.max(0, offset - musicCount))) items.add(station.toMediaItem());
                int radioCount = repository.database.radio().searchCount(query);
                if (items.size() < size) for (PodcastEpisode episode : repository.database.podcasts().search(query, size - items.size(), Math.max(0, offset - musicCount - radioCount))) items.add(app.aavideoplayercar.podcast.PodcastDownloads.get(PlaybackService.this).resolve(episode));
                return LibraryResult.ofItemList(items, params);
            });
        }
        @Override public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession session, MediaSession.ControllerInfo controller, List<MediaItem> requested) {
            return repository.worker.submit(() -> resolve(controller, requested));
        }
        @Override public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
                MediaSession session, MediaSession.ControllerInfo controller, List<MediaItem> requested,
                int startIndex, long startPositionMs) {
            return repository.worker.submit(() -> {
                List<MediaItem> items = resolve(controller, requested);
                if (items.isEmpty()) return new MediaSession.MediaItemsWithStartPosition(items, C.INDEX_UNSET, C.TIME_UNSET);
                int index = startIndex == C.INDEX_UNSET ? 0 : startIndex;
                if (index < 0 || index >= items.size()) throw new IllegalArgumentException("Invalid queue position");
                String id = items.get(index).mediaId;
                if (id.startsWith(RadioStation.PREFIX))
                    return new MediaSession.MediaItemsWithStartPosition(items, index, C.TIME_UNSET);
                if (id.startsWith(IptvChannel.PREFIX)) {
                    IptvChannel channel = repository.database.iptv().channel(id.substring(IptvChannel.PREFIX.length()));
                    if (channel == null) throw new IllegalArgumentException("Channel removed");
                    return new MediaSession.MediaItemsWithStartPosition(items, index, channel.isLive() ? C.TIME_UNSET : Math.max(0, startPositionMs == C.TIME_UNSET ? channel.positionMs : startPositionMs));
                }
                long savedPosition = id.startsWith(MovieVideo.PREFIX) ? repository.database.movieVideos().get(id.substring(MovieVideo.PREFIX.length())).positionMs : id.startsWith(PodcastEpisode.PREFIX) ? repository.database.podcasts().episode(id.substring(PodcastEpisode.PREFIX.length())).positionMs : repository.dao.get(id).positionMs;
                long position = startPositionMs == C.TIME_UNSET ? savedPosition : startPositionMs;
                return new MediaSession.MediaItemsWithStartPosition(items, index, Math.max(0, position));
            });
        }
        private List<MediaItem> resolve(MediaSession.ControllerInfo controller, List<MediaItem> requested) {
            if (requested.size() > PlaybackLimits.MAX_ITEMS) throw new IllegalArgumentException("Invalid queue size");
            List<MediaItem> result = new ArrayList<>();
            java.util.Map<String, app.aavideoplayercar.iptv.XtreamClient.StreamResolver> accounts = new java.util.HashMap<>();
            for (MediaItem item : requested) {
                if (item.mediaId.startsWith(MovieVideo.PREFIX)) {
                    MovieVideo video = repository.database.movieVideos().get(item.mediaId.substring(MovieVideo.PREFIX.length()));
                    if (!local(controller) || video == null) throw new IllegalArgumentException("Movie unavailable");
                    try { result.add(video.item().buildUpon().setUri(app.aavideoplayercar.movies.MoviePlayback.resolve(video)).build()); }
                    catch (java.io.IOException error) { throw new IllegalArgumentException("Movie source unavailable; select it again"); }
                    continue;
                }
                if (item.mediaId.startsWith(IptvChannel.PREFIX)) {
                    IptvChannel channel = repository.database.iptv().channel(item.mediaId.substring(IptvChannel.PREFIX.length()));
                    if (!local(controller) || channel == null)
                        throw new IllegalArgumentException("Channel unavailable");
                    String url = channel.url;
                    if (url.startsWith(app.aavideoplayercar.iptv.XtreamClient.PREFIX)) {
                        try {
                            var account = accounts.get(channel.playlistId);
                            if (account == null) { account = new app.aavideoplayercar.iptv.XtreamClient.StreamResolver(repository.database.xtream().account(channel.playlistId)); accounts.put(channel.playlistId, account); }
                            url = account.resolve(url);
                        } catch (java.io.IOException error) { throw new IllegalArgumentException("Account unavailable; sign in again"); }
                    }
                    if (!app.aavideoplayercar.radio.RadioClient.validStream(url)) throw new IllegalArgumentException("Channel unavailable");
                    result.add(channel.toMediaItem().buildUpon().setUri(url).build()); continue;
                }
                if (item.mediaId.startsWith(PodcastEpisode.PREFIX)) {
                    PodcastEpisode episode = repository.database.podcasts().episode(item.mediaId.substring(PodcastEpisode.PREFIX.length()));
                    if (episode == null || !app.aavideoplayercar.radio.RadioClient.validStream(episode.url))
                        throw new IllegalArgumentException("Episode unavailable. Refresh its feed on your phone.");
                    result.add(app.aavideoplayercar.podcast.PodcastDownloads.get(PlaybackService.this).resolve(episode)); continue;
                }
                if (item.mediaId.startsWith(RadioStation.PREFIX)) {
                    RadioStation station = repository.database.radio().get(item.mediaId.substring(RadioStation.PREFIX.length()));
                    if (station == null || (!local(controller) && !station.favorite)
                            || !app.aavideoplayercar.radio.RadioClient.validStream(station.url))
                        throw new IllegalArgumentException("Station is unavailable. Select it again on your phone.");
                    result.add(station.toMediaItem());
                    continue;
                }
                MediaEntry entry = repository.dao.get(item.mediaId);
                if (entry == null && item.requestMetadata.searchQuery != null) {
                    List<MediaEntry> matches = repository.dao.searchAudio(item.requestMetadata.searchQuery, 1, 0);
                    if (!matches.isEmpty()) entry = matches.get(0);
                    else {
                        List<RadioStation> stations = repository.database.radio().search(item.requestMetadata.searchQuery, 1, 0);
                        if (!stations.isEmpty() && app.aavideoplayercar.radio.RadioClient.validStream(stations.get(0).url)) {
                            result.add(stations.get(0).toMediaItem()); continue;
                        }
                        List<PodcastEpisode> episodes = repository.database.podcasts().search(item.requestMetadata.searchQuery, 1, 0);
                        if (!episodes.isEmpty() && app.aavideoplayercar.radio.RadioClient.validStream(episodes.get(0).url)) {
                            result.add(app.aavideoplayercar.podcast.PodcastDownloads.get(PlaybackService.this).resolve(episodes.get(0))); continue;
                        }
                    }
                }
                if (entry == null || (!local(controller) && !entry.isAudio()))
                    throw new IllegalArgumentException("Media is unavailable. Select it again on your phone.");
                // Resolve IDs from our library. Never open arbitrary URIs supplied by remote controllers.
                result.add(entry.toMediaItem());
            }
            return result;
        }
    }
}
