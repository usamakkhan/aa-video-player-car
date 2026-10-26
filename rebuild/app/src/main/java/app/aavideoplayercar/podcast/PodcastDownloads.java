package app.aavideoplayercar.podcast;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.common.C;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.Requirements;
import app.aavideoplayercar.data.PodcastEpisode;
import app.aavideoplayercar.network.HttpDocument;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** App-private durable downloads. All index reads and file checks run off the UI thread. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PodcastDownloads {
    public static final long MAX_EPISODE_BYTES = 1024L * 1024 * 1024;
    public static final long MAX_STORAGE_BYTES = 2 * MAX_EPISODE_BYTES;
    public static final int MAX_DOWNLOADS = 200, USER_PAUSED = 1;
    @android.annotation.SuppressLint("StaticFieldLeak") // Constructor stores only the application context.
    private static PodcastDownloads instance;
    public final SimpleCache cache;
    public final DefaultDownloadIndex index;
    private final Context context;
    private final File directory;
    private final DataSource.Factory http;
    private DownloadManager manager;
    public static synchronized PodcastDownloads get(Context context) {
        if (instance == null) instance = new PodcastDownloads(context.getApplicationContext());
        return instance;
    }
    private PodcastDownloads(Context context) {
        this.context = context;
        directory = new File(context.getNoBackupFilesDir(), "podcast-downloads");
        StandaloneDatabaseProvider database = new StandaloneDatabaseProvider(context);
        cache = new SimpleCache(directory, new NoOpCacheEvictor(), database);
        index = new DefaultDownloadIndex(database, "podcasts");
        http = new DefaultHttpDataSource.Factory().setUserAgent("AA-Video-Player-Car/0.1")
            .setConnectTimeoutMs(10000).setReadTimeoutMs(15000);
    }
    public DownloadManager manager() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Download manager requires main thread");
        if (manager == null) {
            CacheDataSource.Factory source = new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(() -> new LimitedSource(http.createDataSource()));
            manager = new DownloadManager(context, index, new DefaultDownloaderFactory(source, Runnable::run));
            manager.setMaxParallelDownloads(1); manager.setMinRetryCount(2);
            manager.setRequirements(requirements(context.getSharedPreferences("downloads", 0).getBoolean("wifiOnly", true)));
            manager.addListener(new DownloadManager.Listener() {
                @Override public void onInitialized(DownloadManager manager) { pruneOrphans(); }
            });
        }
        return manager;
    }
    public static Requirements requirements(boolean wifiOnly) {
        return new Requirements(Requirements.NETWORK | Requirements.DEVICE_STORAGE_NOT_LOW | (wifiOnly ? Requirements.NETWORK_UNMETERED : 0));
    }
    public DataSource.Factory playbackSource() {
        return new CacheDataSource.Factory().setCache(cache).setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(new DefaultDataSource.Factory(context, http));
    }
    public DownloadRequest request(PodcastEpisode episode) throws IOException {
        String url = HttpDocument.url(episode.url);
        String mime = episode.mime.isEmpty() ? null : androidx.media3.common.MimeTypes.normalizeMimeType(episode.mime.toLowerCase(Locale.ROOT));
        if (Util.inferContentType(Uri.parse(url), mime) != C.CONTENT_TYPE_OTHER || Util.inferContentType(Uri.parse(url)) != C.CONTENT_TYPE_OTHER)
            throw new IOException("Offline adaptive streams are not supported yet.");
        String id = PodcastEpisode.PREFIX + episode.id;
        Download existing = index.getDownload(id);
        if (existing == null) {
            try (DownloadCursor cursor = index.getDownloads()) {
                if (cursor.getCount() >= MAX_DOWNLOADS) throw new IOException("Remove an existing download first.");
            }
        }
        checkSpace();
        // A retry must retain its original cache key/URL. Removing first allows a fresh enclosure.
        if (existing != null) return existing.request;
        return new DownloadRequest.Builder(id, Uri.parse(url)).setMimeType(mime)
            .setCustomCacheKey(id + ":" + UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)))
            .setData(episode.title.getBytes(StandardCharsets.UTF_8)).build();
    }
    /** Use the original downloaded URL even if the publisher later changes its enclosure URL. */
    public MediaItem resolve(PodcastEpisode episode) {
        try {
            Download saved = index.getDownload(PodcastEpisode.PREFIX + episode.id);
            if (saved != null && saved.state == Download.STATE_COMPLETED)
                return saved.request.toMediaItem().buildUpon().setMediaMetadata(episode.toMediaItem().mediaMetadata).build();
        } catch (IOException ignored) { /* Streaming remains available if the download index cannot be read. */ }
        return episode.toMediaItem();
    }
    public static final class Page {
        public final List<Download> rows;
        public final int number, count;
        public final boolean hasNext;
        private Page(List<Download> rows, int number, int count, int size) {
            this.rows = Collections.unmodifiableList(rows); this.number = number; this.count = count;
            hasNext = (long)(number + 1) * size < count;
        }
    }
    public Page page(int page, int size) throws IOException {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("Invalid download page");
        List<Download> results = new ArrayList<>();
        try (DownloadCursor cursor = index.getDownloads()) {
            int count = cursor.getCount(), effective = Math.min(page, Math.max(0, (count - 1) / size));
            if (cursor.moveToPosition(effective * size)) do { results.add(cursor.getDownload()); } while (results.size() < size && cursor.moveToNext());
            return new Page(results, effective, count, size);
        }
    }
    public void pruneOrphans() {
        var repository = app.aavideoplayercar.data.MediaRepository.get(context);
        repository.worker.execute(() -> {
            try (DownloadCursor cursor = index.getDownloads()) {
                List<String> missing = new ArrayList<>();
                while (cursor.moveToNext()) {
                    String id = cursor.getDownload().request.id;
                    if (id.startsWith(PodcastEpisode.PREFIX) && repository.database.podcasts().episode(id.substring(PodcastEpisode.PREFIX.length())) == null) missing.add(id);
                }
                new android.os.Handler(Looper.getMainLooper()).post(() -> {
                    if (!missing.isEmpty()) for (String id : missing) manager().removeDownload(id);
                });
            } catch (IOException ignored) { /* Retain records for a later retry; Downloads still permits manual removal. */ }
        });
    }
    @android.annotation.SuppressLint("UsableSpace") // Keep a real free-space reserve; do not reclaim other apps' cache for optional downloads.
    private void checkSpace() throws IOException {
        if (cache.getCacheSpace() >= MAX_STORAGE_BYTES || directory.getUsableSpace() < 100L * 1024 * 1024)
            throw new IOException("Download storage is full. Remove downloads and retry.");
    }
    private final class LimitedSource implements DataSource {
        private final DataSource delegate;
        private long position;
        private long nextSpaceCheck;
        private long spaceAtOpen, loaded;
        LimitedSource(DataSource delegate) { this.delegate = delegate; }
        @Override public long open(DataSpec spec) throws IOException {
            checkSpace(); position = spec.position; nextSpaceCheck = position + 1024 * 1024;
            spaceAtOpen = cache.getCacheSpace(); loaded = 0;
            long remaining = delegate.open(spec);
            if (position > MAX_EPISODE_BYTES || remaining > MAX_EPISODE_BYTES - position) throw new IOException("Episode exceeds 1 GiB download limit.");
            return remaining;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (position >= nextSpaceCheck) { checkSpace(); nextSpaceCheck = position + 1024 * 1024; }
            long remaining = Math.min(MAX_EPISODE_BYTES - position, MAX_STORAGE_BYTES - Math.max(cache.getCacheSpace(), spaceAtOpen + loaded));
            if (remaining <= 0) throw new IOException("Download storage limit reached.");
            int count = delegate.read(buffer, offset, (int)Math.min(length, remaining));
            if (count > 0) { position += count; loaded += count; }
            return count;
        }
        @Override public Uri getUri() { return delegate.getUri(); }
        @Override public Map<String, List<String>> getResponseHeaders() { return delegate.getResponseHeaders(); }
        @Override public void addTransferListener(TransferListener listener) { delegate.addTransferListener(listener); }
        @Override public void close() throws IOException { delegate.close(); }
    }
}
