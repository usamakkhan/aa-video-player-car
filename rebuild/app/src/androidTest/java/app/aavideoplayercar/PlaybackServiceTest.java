package app.aavideoplayercar;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.MediaEntry;
import app.aavideoplayercar.data.MediaRepository;
import app.aavideoplayercar.playback.PlaybackService;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public class PlaybackServiceTest {
    private Context context;
    private MediaRepository repository;
    private MediaBrowser browser;
    private ActivityScenario<MainActivity> activity;
    private static final String FIRST = "test-audio-first", SECOND = "test-audio-second", VIDEO = "test-hidden-video";

    private <T> T main(Callable<T> action) {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { value.set(action.call()); } catch (Throwable e) { error.set(e); }
        });
        if (error.get() != null) throw new AssertionError(error.get());
        return value.get();
    }
    private void await(Callable<Boolean> condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (main(condition)) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for playback state");
    }
    private void insert(String id, String title, String mime, File file, long time) {
        repository.dao.remove(id);
        MediaEntry entry = new MediaEntry(); entry.id = id; entry.title = title; entry.mime = mime;
        entry.uri = Uri.fromFile(file).toString(); entry.addedAt = time;
        repository.dao.insert(entry);
    }
    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("dashboard", 0).edit().clear().commit();
        repository = MediaRepository.get(context);
        File fixture = new File(context.getFilesDir(), "playback-fixture.wav");
        int sampleRate = 8000, samples = sampleRate * 30, bytes = samples * 2;
        ByteBuffer wav = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36 + bytes).put(new byte[]{'W','A','V','E','f','m','t',' '})
            .putInt(16).putShort((short)1).putShort((short)1).putInt(sampleRate).putInt(sampleRate * 2)
            .putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(bytes);
        for (int i = 0; i < samples; i++) wav.putShort((short)(Math.sin(i * 2 * Math.PI * 440 / sampleRate) * 1000));
        try (FileOutputStream out = new FileOutputStream(fixture)) { out.write(wav.array()); }
        long time = System.currentTimeMillis();
        insert(FIRST, "Fixture alpha", "audio/wav", fixture, time + 2);
        insert(SECOND, "Fixture beta", "audio/wav", fixture, time + 1);
        insert(VIDEO, "Private video", "video/mp4", fixture, time);
        activity = ActivityScenario.launch(MainActivity.class);
        ListenableFuture<MediaBrowser> future = main(() -> new MediaBrowser.Builder(context,
            new SessionToken(context, new ComponentName(context, PlaybackService.class))).buildAsync());
        browser = future.get(10, TimeUnit.SECONDS);
    }
    @After public void cleanup() throws Exception {
        if (browser != null) main(() -> { browser.pause(); browser.clearMediaItems(); browser.release(); return null; });
        if (activity != null) activity.close();
        if (repository != null) repository.worker.submit(() -> {
            repository.dao.remove(FIRST); repository.dao.remove(SECOND); repository.dao.remove(VIDEO);
        }).get(10, TimeUnit.SECONDS);
    }
    @Test public void newsSpeechTakesFocusAndMediaResumesAfterStop() throws Exception {
        var state = new AtomicReference<app.aavideoplayercar.news.NewsSpeech.State>();
        CountDownLatch ready = new CountDownLatch(1);
        var speech = main(() -> app.aavideoplayercar.news.NewsSpeech.create(context, next -> {
            state.set(next);
            if (next == app.aavideoplayercar.news.NewsSpeech.State.READY || next == app.aavideoplayercar.news.NewsSpeech.State.ERROR) ready.countDown();
        }));
        try {
            assertTrue(ready.await(20, TimeUnit.SECONDS));
            assertEquals("Dedicated VM speech engine must be available for focus verification", app.aavideoplayercar.news.NewsSpeech.State.READY, state.get());
            main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId(FIRST).build()); browser.setRepeatMode(Player.REPEAT_MODE_ONE); browser.prepare(); browser.play(); return null; });
            await(() -> browser.isPlaying());
            main(() -> { speech.speak("A long news article is being read aloud while media playback waits. ".repeat(100)); return null; });
            await(() -> state.get() == app.aavideoplayercar.news.NewsSpeech.State.SPEAKING && !browser.isPlaying());
            assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS, (int)main(() -> browser.getPlaybackSuppressionReason()));
            main(() -> { speech.stop(); return null; }); await(() -> browser.isPlaying());
            assertEquals(app.aavideoplayercar.news.NewsSpeech.State.STOPPED, state.get());
            android.util.Log.i("NewsSpeechProbe", "media_focus=suppressed_during_speech,resumed_after_stop");
        } finally { main(() -> { speech.close(); browser.setRepeatMode(Player.REPEAT_MODE_OFF); return null; }); }
    }
    @Test public void movieStreamPlaysResumesAndRetainsIdentityAcrossSourceRefresh() throws Exception {
        String addonId = null;
        AtomicReference<androidx.media3.ui.PlayerView> videoView = new AtomicReference<>();
        activity.onActivity(a -> { var view = new androidx.media3.ui.PlayerView(a); view.setPlayer(browser); a.setContentView(view); videoView.set(view); });
        try (var server = new LocalHttpFixture()) {
            String manifest = "{\"id\":\"org.movie.play\",\"name\":\"Movie fixture\",\"version\":\"1\",\"types\":[\"movie\"],\"resources\":[\"stream\"],\"catalogs\":[]}";
            server.add("/manifest.json", manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
            try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("iptv/clip.mp4")) { server.add("/clip.mp4", input.readAllBytes(), "video/mp4"); }
            var addon = app.aavideoplayercar.movies.AddonClient.manifest(new app.aavideoplayercar.network.HttpDocument(), server.url("/manifest.json"));
            addonId = app.aavideoplayercar.movies.AddonRegistration.save(repository.database.movieAddons(), addon, null);
            server.add("/stream/movie/tt1.json", ("{\"streams\":[{\"url\":\""+server.url("/clip.mp4")+"\"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
            var stream = app.aavideoplayercar.movies.AddonClient.streams(new app.aavideoplayercar.network.HttpDocument(), addon, "movie", "tt1").get(0);
            var video = app.aavideoplayercar.movies.MoviePlayback.prepare(repository.database, addonId, "movie", "tt1", "Movie fixture", stream);
            assertFalse(repository.database.movieVideos().get(video.id).source.contains("clip.mp4"));
            String mediaId = app.aavideoplayercar.data.MovieVideo.PREFIX + video.id;
            main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId(mediaId).setUri("https://invalid.example/override").build()); browser.prepare(); browser.play(); return null; });
            await(() -> browser.isPlaying() && browser.getVideoSize().width > 0);
            main(() -> { browser.seekTo(4000); return null; }); await(() -> browser.getCurrentPosition() >= 4000);
            main(() -> { browser.pause(); return null; }); repository.worker.submit(() -> {}).get(10,TimeUnit.SECONDS);
            assertTrue(repository.database.movieVideos().get(video.id).positionMs >= 4000);
            try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("iptv/clip.mp4")) { server.add("/rotated.mp4", input.readAllBytes(), "video/mp4"); }
            server.add("/stream/movie/tt1.json", ("{\"streams\":[{\"url\":\""+server.url("/rotated.mp4")+"\"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
            var rotated = app.aavideoplayercar.movies.AddonClient.streams(new app.aavideoplayercar.network.HttpDocument(), addon, "movie", "tt1").get(0);
            var refreshed = app.aavideoplayercar.movies.MoviePlayback.prepare(repository.database, addonId, "movie", "tt1", "Renamed movie", rotated);
            assertEquals(server.url("/rotated.mp4"), app.aavideoplayercar.movies.MoviePlayback.resolve(repository.database.movieVideos().get(video.id)));
            assertEquals(video.id, refreshed.id); assertTrue(refreshed.positionMs >= 4000);
            main(() -> { browser.clearMediaItems(); browser.setMediaItem(new MediaItem.Builder().setMediaId(mediaId).build()); browser.prepare(); browser.play(); return null; });
            await(() -> browser.isPlaying() && browser.getCurrentPosition() >= 4000);
            var item = main(() -> browser.getItem(mediaId)).get(10,TimeUnit.SECONDS); assertEquals(0,item.resultCode); assertNull(item.value.localConfiguration);
            var audio = main(() -> browser.getSearchResult("Renamed movie",0,50,null)).get(10,TimeUnit.SECONDS); assertTrue(audio.value.isEmpty());
        } finally { main(() -> { browser.pause(); browser.clearMediaItems(); videoView.get().setPlayer(null); return null; }); if(addonId!=null) repository.database.movieAddons().delete(addonId); }
    }
    @Test public void expiredMovieUrlRenewsSameSourceAndResumes() throws Exception {
        String addonId = null;
        AtomicReference<androidx.media3.ui.PlayerView> videoView = new AtomicReference<>();
        activity.onActivity(a -> { var view = new androidx.media3.ui.PlayerView(a); view.setPlayer(browser); a.setContentView(view); videoView.set(view); });
        try (var server = new LocalHttpFixture()) {
            String manifest = "{\"id\":\"org.movie.renew\",\"name\":\"Renew fixture\",\"version\":\"1\",\"types\":[\"movie\"],\"resources\":[\"stream\"],\"catalogs\":[]}";
            server.add("/manifest.json", manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
            server.error("/expired.mp4", 403);
            try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("iptv/clip.mp4")) { server.add("/renewed.mp4", input.readAllBytes(), "video/mp4"); }
            var addon = app.aavideoplayercar.movies.AddonClient.manifest(new app.aavideoplayercar.network.HttpDocument(), server.url("/manifest.json"));
            addonId = app.aavideoplayercar.movies.AddonRegistration.save(repository.database.movieAddons(), addon, null);
            server.add("/stream/movie/tt-renew.json", ("{\"streams\":[{\"name\":\"HD\",\"url\":\""+server.url("/expired.mp4")+"\"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
            var stream = app.aavideoplayercar.movies.AddonClient.streams(new app.aavideoplayercar.network.HttpDocument(), addon, "movie", "tt-renew").get(0);
            var video = app.aavideoplayercar.movies.MoviePlayback.prepare(repository.database, addonId, "movie", "tt-renew", "Renew film", stream, 0);
            repository.database.movieVideos().progress(video.id, 4000, 30000, System.currentTimeMillis());
            server.add("/stream/movie/tt-renew.json", ("{\"streams\":[{\"name\":\"HD\",\"url\":\""+server.url("/renewed.mp4")+"\"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
            String mediaId = app.aavideoplayercar.data.MovieVideo.PREFIX + video.id;
            main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId(mediaId).build()); browser.prepare(); browser.play(); return null; });
            await(() -> browser.isPlaying() && browser.getVideoSize().width > 0 && browser.getCurrentPosition() >= 4000);
            assertEquals(server.url("/renewed.mp4"), app.aavideoplayercar.movies.MoviePlayback.resolve(repository.database.movieVideos().get(video.id)));
            assertEquals(video.id, repository.database.movieVideos().get(video.id).id);
        } finally { main(() -> { browser.pause(); browser.clearMediaItems(); videoView.get().setPlayer(null); return null; }); if (addonId != null) repository.database.movieAddons().delete(addonId); }
    }
    @Test public void restoredMovieSurfaceDoesNotReplaceAnotherPlaybackItem() throws Exception {
        main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId(FIRST).build()); browser.prepare(); browser.play(); return null; });
        await(() -> browser.isPlaying() && FIRST.equals(browser.getCurrentMediaItem().mediaId));
        app.aavideoplayercar.movies.MoviePlayerDialog[] dialog = new app.aavideoplayercar.movies.MoviePlayerDialog[1];
        android.os.Bundle restore = new android.os.Bundle(); restore.putBoolean("playing", true);
        try {
            activity.onActivity(a -> dialog[0] = new app.aavideoplayercar.movies.MoviePlayerDialog(a, app.aavideoplayercar.data.MovieVideo.PREFIX + "other-video", restore, () -> {}));
            long deadline = android.os.SystemClock.elapsedRealtime() + 10000; boolean guarded = false;
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                var ready = new java.util.concurrent.atomic.AtomicBoolean();
                try { androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withTagValue(org.hamcrest.Matchers.is("movies.player.status")))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e) -> { if(e!=null) throw e; ready.set(context.getString(R.string.movies_other_playback).contentEquals(((android.widget.TextView)v).getText())); }); }
                catch (androidx.test.espresso.NoMatchingViewException | androidx.test.espresso.NoMatchingRootException missing) { }
                if (ready.get()) { guarded = true; break; } Thread.sleep(50);
            }
            assertTrue("Restored surface did not notice the other item", guarded);
            assertEquals(FIRST, main(() -> browser.getCurrentMediaItem().mediaId));
        } finally { if (dialog[0] != null) main(() -> { dialog[0].close(); browser.pause(); return null; }); }
    }
    @Test public void browseSearchAndPaginationExposeAudioOnly() throws Exception {
        var root = main(() -> browser.getLibraryRoot(null)).get(10, TimeUnit.SECONDS);
        assertEquals(0, root.resultCode); assertEquals(PlaybackService.ROOT, root.value.mediaId);
        var first = main(() -> browser.getChildren(PlaybackService.MUSIC, 0, 1, null)).get(10, TimeUnit.SECONDS);
        var second = main(() -> browser.getChildren(PlaybackService.MUSIC, 1, 1, null)).get(10, TimeUnit.SECONDS);
        assertEquals(FIRST, first.value.get(0).mediaId); assertEquals(SECOND, second.value.get(0).mediaId);
        var all = main(() -> browser.getChildren(PlaybackService.MUSIC, 0, Integer.MAX_VALUE, null)).get(10, TimeUnit.SECONDS);
        for (MediaItem item : all.value) assertNotEquals(VIDEO, item.mediaId);
        var search = main(() -> browser.getSearchResult("beta", 0, 10, null)).get(10, TimeUnit.SECONDS);
        assertEquals(1, search.value.size()); assertEquals(SECOND, search.value.get(0).mediaId);
        var missing = main(() -> browser.getItem("unknown-id")).get(10, TimeUnit.SECONDS);
        assertNotEquals(0, missing.resultCode);
    }
    @Test public void playbackQueueSeekAndProgressUseSharedService() throws Exception {
        main(() -> {
            browser.setMediaItems(Arrays.asList(new MediaItem.Builder().setMediaId(FIRST).build(), new MediaItem.Builder().setMediaId(SECOND).build()));
            browser.prepare(); browser.play(); return null;
        });
        await(() -> browser.isPlaying() && browser.getCurrentPosition() > 250);
        main(() -> { browser.seekToNextMediaItem(); return null; });
        await(() -> browser.getCurrentMediaItem() != null && SECOND.equals(browser.getCurrentMediaItem().mediaId) && browser.isPlaying());
        main(() -> { browser.seekTo(7000); return null; });
        await(() -> browser.getCurrentPosition() >= 7000);
        main(() -> { browser.pause(); return null; });
        await(() -> !browser.isPlaying());
        repository.worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
        assertTrue(repository.dao.get(SECOND).positionMs >= 7000);
        assertTrue(repository.dao.get(SECOND).durationMs > 29000);
        assertNull(main(() -> browser.getPlayerError()));
    }
    @Test public void recreationAndSecondControllerDoNotRestartPlayback() throws Exception {
        main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId(FIRST).build()); browser.prepare(); browser.play(); return null; });
        await(() -> browser.isPlaying());
        main(() -> { browser.seekTo(5000); return null; });
        await(() -> browser.getCurrentPosition() >= 5000);
        activity.recreate();
        MediaController other = main(() -> new MediaController.Builder(context,
            new SessionToken(context, new ComponentName(context, PlaybackService.class))).buildAsync()).get(10, TimeUnit.SECONDS);
        try {
            assertEquals(FIRST, main(() -> other.getCurrentMediaItem().mediaId));
            assertTrue(main(() -> other.getCurrentPosition()) >= 5000);
            main(() -> { other.pause(); return null; });
            await(() -> !browser.isPlaying());
        } finally { main(() -> { other.release(); return null; }); }
    }
    @Test public void suppliedUriCannotOverrideLibraryIdentity() throws Exception {
        main(() -> {
            browser.setMediaItem(new MediaItem.Builder().setMediaId(FIRST).setUri("https://invalid.example/untrusted.wav").build());
            browser.prepare(); browser.play(); return null;
        });
        await(() -> browser.isPlaying() && browser.getCurrentPosition() > 250);
        assertNull(main(() -> browser.getPlayerError()));
    }
    @Test public void mediaIdResumesSavedPositionAfterQueueCleared() throws Exception {
        main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId(FIRST).build()); browser.prepare(); browser.play(); return null; });
        await(() -> browser.isPlaying());
        main(() -> { browser.seekTo(9000); browser.pause(); return null; });
        await(() -> browser.getCurrentPosition() >= 9000 && !browser.isPlaying());
        main(() -> { browser.clearMediaItems(); return null; });
        await(() -> browser.getMediaItemCount() == 0);
        repository.worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
        assertTrue(repository.dao.get(FIRST).positionMs >= 9000);
        main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId(FIRST).build()); browser.prepare(); return null; });
        await(() -> browser.getPlaybackState() == Player.STATE_READY && browser.getCurrentPosition() >= 9000);
        assertFalse(main(() -> browser.isPlaying()));
    }
    @Test public void legacyAndroidMediaBrowserCanDiscoverLibrary() throws Exception {
        CountDownLatch connected = new CountDownLatch(1), loaded = new CountDownLatch(1);
        AtomicReference<java.util.List<android.media.browse.MediaBrowser.MediaItem>> children = new AtomicReference<>();
        android.media.browse.MediaBrowser legacy = main(() -> new android.media.browse.MediaBrowser(context,
            new ComponentName(context, PlaybackService.class), new android.media.browse.MediaBrowser.ConnectionCallback() {
                @Override public void onConnected() { connected.countDown(); }
            }, null));
        try {
            main(() -> { legacy.connect(); return null; });
            assertTrue("Legacy browser connection failed", connected.await(10, TimeUnit.SECONDS));
            main(() -> {
                assertEquals(PlaybackService.ROOT, legacy.getRoot());
                legacy.subscribe(PlaybackService.MUSIC, new android.media.browse.MediaBrowser.SubscriptionCallback() {
                    @Override public void onChildrenLoaded(String parent, java.util.List<android.media.browse.MediaBrowser.MediaItem> items) {
                        children.set(items); loaded.countDown();
                    }
                });
                return null;
            });
            assertTrue("Legacy browse did not load", loaded.await(10, TimeUnit.SECONDS));
            assertTrue(children.get().size() >= 2);
            for (android.media.browse.MediaBrowser.MediaItem item : children.get()) {
                assertTrue(item.isPlayable()); assertNotEquals(VIDEO, item.getMediaId());
            }
        } finally { main(() -> { legacy.disconnect(); return null; }); }
    }
    @Test public void completedTrackStartsFromBeginningNextTime() throws Exception {
        main(() -> {
            browser.setMediaItems(Arrays.asList(new MediaItem.Builder().setMediaId(FIRST).build(), new MediaItem.Builder().setMediaId(SECOND).build()));
            browser.prepare(); browser.play(); return null;
        });
        await(() -> browser.isPlaying() && browser.getDuration() == 30000);
        main(() -> { browser.seekTo(29950); return null; });
        await(() -> browser.getCurrentMediaItem() != null && SECOND.equals(browser.getCurrentMediaItem().mediaId));
        repository.worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
        assertEquals(0, repository.dao.get(FIRST).positionMs);
    }

    @Test public void largeLibraryBrowseSearchAndPhonePagesRemainReachable() throws Exception {
        File fixture = new File(context.getFilesDir(), "playback-fixture.wav");
        app.aavideoplayercar.playback.PlaybackScreen[] screen = new app.aavideoplayercar.playback.PlaybackScreen[1];
        try {
            repository.database.runInTransaction(() -> {
                for (int i = 0; i < 520; i++) insert(String.format(java.util.Locale.ROOT, "paging-%03d", i),
                    String.format(java.util.Locale.ROOT, "Paging fixture %03d", i), "audio/wav", fixture, Long.MAX_VALUE - i);
            });
            var first = main(() -> browser.getChildren(PlaybackService.MUSIC, 0, 501, null)).get(10, TimeUnit.SECONDS);
            var second = main(() -> browser.getChildren(PlaybackService.MUSIC, 1, 501, null)).get(10, TimeUnit.SECONDS);
            assertEquals(500, first.value.size());
            assertEquals("paging-499", first.value.get(499).mediaId);
            assertEquals("paging-500", second.value.get(0).mediaId);
            var search = main(() -> browser.getSearchResult("Paging fixture", 1, 501, null)).get(10, TimeUnit.SECONDS);
            assertEquals(20, search.value.size());
            assertEquals("paging-500", search.value.get(0).mediaId);
            activity.onActivity(a -> {
                screen[0] = new app.aavideoplayercar.playback.PlaybackScreen(a, "audio/", null);
                a.setContentView(screen[0].getView());
            });
            await(() -> ((android.widget.ListView) screen[0].getView().getChildAt(3)).getCount() == 50);
            for (int page = 1; page <= 10; page++) {
                final int expected = page;
                main(() -> {
                    android.view.ViewGroup navigation = (android.view.ViewGroup) screen[0].getView().getChildAt(4);
                    assertTrue(navigation.getChildAt(2).isEnabled());
                    navigation.getChildAt(2).performClick();
                    return null;
                });
                await(() -> ((android.widget.TextView) ((android.view.ViewGroup) screen[0].getView().getChildAt(4)).getChildAt(1))
                    .getText().toString().startsWith("Page " + (expected + 1) + " -"));
            }
            main(() -> {
                android.widget.ListView list = (android.widget.ListView) screen[0].getView().getChildAt(3);
                assertEquals("Paging fixture 500", list.getAdapter().getItem(0));
                list.performItemClick(null, 0, 0);
                return null;
            });
            await(() -> browser.isPlaying() && browser.getCurrentMediaItem() != null
                && "paging-500".equals(browser.getCurrentMediaItem().mediaId));
        } finally {
            main(() -> { if (screen[0] != null) screen[0].close(); browser.pause(); browser.clearMediaItems(); return null; });
            repository.worker.submit(() -> repository.database.runInTransaction(() -> {
                for (int i = 0; i < 520; i++) repository.dao.remove(String.format(java.util.Locale.ROOT, "paging-%03d", i));
            })).get(10, TimeUnit.SECONDS);
        }
    }

    @Test public void radioFavoritesBrowseAndPlayStoredHttpStreamWithoutResume() throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        byte[] wav = java.nio.file.Files.readAllBytes(new File(context.getFilesDir(), "playback-fixture.wav").toPath());
        java.util.concurrent.Future<?> served = worker.submit(() -> {
            try (java.net.Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
                String line; while ((line = in.readLine()) != null && !line.isEmpty()) { }
                java.io.OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: " + wav.length + "\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                out.write(wav); out.flush();
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        try {
            app.aavideoplayercar.data.RadioStation station = new app.aavideoplayercar.data.RadioStation();
            station.id = id; station.title = "Network fixture radio"; station.url = "http://127.0.0.1:" + server.getLocalPort() + "/radio.wav";
            repository.database.radio().save(station, true);
            var root = main(() -> browser.getChildren(PlaybackService.ROOT, 0, 50, null)).get(10, TimeUnit.SECONDS);
            assertEquals(3, root.value.size());
            assertEquals(PlaybackService.MUSIC, root.value.get(0).mediaId);
            assertEquals(PlaybackService.RADIO, root.value.get(1).mediaId);
            var favorites = main(() -> browser.getChildren(PlaybackService.RADIO, 0, 50, null)).get(10, TimeUnit.SECONDS);
            assertTrue(favorites.value.stream().anyMatch(item -> item.mediaId.equals("radio:" + id)));
            var search = main(() -> browser.getSearchResult("Network fixture radio", 0, 10, null)).get(10, TimeUnit.SECONDS);
            assertEquals("radio:" + id, search.value.get(0).mediaId);
            main(() -> {
                browser.setMediaItem(new MediaItem.Builder().setMediaId("radio:" + id).setUri("https://invalid.example/forged").build(), 9000);
                browser.prepare(); browser.play(); return null;
            });
            await(() -> browser.isPlaying() && browser.getCurrentPosition() > 100);
            assertTrue(main(() -> browser.getCurrentPosition()) < 5000);
            assertNull(main(() -> browser.getPlayerError()));
            assertNull(repository.dao.get("radio:" + id));
            served.get(10, TimeUnit.SECONDS);
        } finally {
            main(() -> { browser.pause(); browser.clearMediaItems(); return null; });
            server.close(); worker.shutdownNow(); repository.database.radio().delete(id);
        }
    }
    @Test public void downloadedPodcastPlaysAfterServerClosesAndPublisherUrlChanges() throws Exception {
        var downloads = app.aavideoplayercar.podcast.PodcastDownloads.get(context);
        var feed = new app.aavideoplayercar.data.PodcastFeed(); feed.id = java.util.UUID.randomUUID().toString(); feed.title = "Offline show"; feed.url = "https://example.com/offline";
        var episode = new app.aavideoplayercar.data.PodcastEpisode(); episode.id = java.util.UUID.randomUUID().toString(); episode.feedId = feed.id; episode.title = "Offline episode"; episode.mime = "audio/wav";
        String mediaId = "podcast:" + episode.id;
        try {
            try (LocalHttpFixture server = new LocalHttpFixture()) {
                server.add("/offline.wav", java.nio.file.Files.readAllBytes(new File(context.getFilesDir(), "playback-fixture.wav").toPath()), "audio/wav");
                episode.url = server.url("/offline.wav"); repository.database.podcasts().importFeed(feed, java.util.Collections.singletonList(episode), true);
                var request = downloads.request(episode);
                main(() -> { downloads.manager().setRequirements(new androidx.media3.exoplayer.scheduler.Requirements(0));
                    androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(context, app.aavideoplayercar.podcast.PodcastDownloadService.class, request, false); return null; });
                long deadline = android.os.SystemClock.elapsedRealtime() + 15000;
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    var saved = downloads.index.getDownload(mediaId);
                    if (saved != null && saved.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) break;
                    Thread.sleep(50);
                }
                assertEquals(androidx.media3.exoplayer.offline.Download.STATE_COMPLETED, downloads.index.getDownload(mediaId).state);
            }
            episode.url = "http://127.0.0.1:1/changed.wav";
            repository.database.podcasts().importFeed(feed, java.util.Collections.singletonList(episode), false);
            repository.database.podcasts().progress(episode.id, 9000, 30000, 1);
            main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId(mediaId).setUri("http://127.0.0.1:1/forged").build()); browser.prepare(); browser.play(); return null; });
            await(() -> browser.isPlaying() && browser.getCurrentPosition() >= 9000);
            assertNull(main(() -> browser.getPlayerError()));
            main(() -> { browser.pause(); browser.clearMediaItems(); return null; });
            repository.database.podcasts().unsubscribe(feed.id); downloads.pruneOrphans();
            long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
            while (downloads.index.getDownload(mediaId) != null && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50);
            assertNull(downloads.index.getDownload(mediaId));
        } finally {
            main(() -> { browser.pause(); browser.clearMediaItems(); downloads.manager().removeDownload(mediaId); downloads.manager().setRequirements(app.aavideoplayercar.podcast.PodcastDownloads.requirements(true)); return null; });
            repository.database.podcasts().unsubscribe(feed.id);
        }
    }
    @Test public void iptvPlaysMp4AndHlsWithSeparateVodResumeAndLiveStart() throws Exception {
        String playlistId = null;
        AtomicReference<androidx.media3.ui.PlayerView> videoView = new AtomicReference<>();
        activity.onActivity(a -> {
            var view = new androidx.media3.ui.PlayerView(a); view.setPlayer(browser); a.setContentView(view); videoView.set(view);
        });
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            var assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
            for (String file : assets.list("iptv")) {
                if (!(file.endsWith(".mp4") || file.endsWith(".ts") || file.endsWith(".m3u8"))) continue;
                try (var input = assets.open("iptv/" + file)) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
                    while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
                    server.add("/" + file, bytes.toByteArray(), file.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : file.endsWith(".ts") ? "video/mp2t" : "video/mp4");
                }
            }
            String list = "#EXTM3U\n#EXTINF:12,VOD fixture\n" + server.url("/clip.mp4") + "\n#EXTINF:-1,Live fixture\n" + server.url("/stream.m3u8") + "\n";
            var parsed = app.aavideoplayercar.iptv.M3uParser.parse(list.getBytes(java.nio.charset.StandardCharsets.UTF_8), server.url("/list"), server.url("/list"), "IPTV fixture");
            playlistId = parsed.playlist.id; repository.database.iptv().replace(parsed.playlist, parsed.channels, true);
            var vod = parsed.channels.get(0); var live = parsed.channels.get(1);
            repository.database.iptv().progress(vod.id, 3000, 12000, 1);
            repository.database.iptv().progress(live.id, 9000, 12000, 1);
            main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId("iptv:" + vod.id).setUri("http://127.0.0.1:1/forged").build()); browser.prepare(); browser.play(); return null; });
            await(() -> browser.isPlaying() && browser.getCurrentPosition() >= 3000 && browser.getVideoSize().width == 160);
            main(() -> { browser.pause(); browser.clearMediaItems(); return null; });
            repository.worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
            assertTrue(repository.database.iptv().channel(vod.id).positionMs >= 3000);
            main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId("iptv:" + live.id).build(), 9000); browser.prepare(); browser.play(); return null; });
            await(() -> browser.isPlaying() && browser.getVideoSize().width == 160);
            assertTrue(main(() -> browser.getCurrentPosition()) < 3000);
            assertNull(main(() -> browser.getPlayerError()));
            // Change policy while the old live item is still playing: its final checkpoint must be ignored.
            repository.database.iptv().playbackMode(live.id, 2);
            main(() -> { browser.pause(); browser.clearMediaItems(); return null; });
            repository.worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
            assertEquals(9000, repository.database.iptv().channel(live.id).positionMs);
            main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId("iptv:" + live.id).build()); browser.prepare(); browser.play(); return null; });
            await(() -> browser.isPlaying() && browser.getCurrentPosition() >= 9000);
            assertEquals(Integer.valueOf(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MOVIE), main(() -> browser.getCurrentMediaItem().mediaMetadata.mediaType));
            main(() -> { browser.pause(); browser.clearMediaItems(); return null; });
            repository.worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
            repository.database.iptv().playbackMode(vod.id, 1);
            main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId("iptv:" + vod.id).build(), 9000); browser.prepare(); browser.play(); return null; });
            await(() -> browser.isPlaying()); assertTrue(main(() -> browser.getCurrentPosition()) < 3000);
            var search = main(() -> browser.getSearchResult("VOD fixture", 0, 50, null)).get(10, TimeUnit.SECONDS);
            assertTrue(search.value.isEmpty()); // Video channels do not leak into the audio/car catalog.
        } finally {
            main(() -> { browser.pause(); browser.clearMediaItems(); return null; });
            main(() -> { videoView.get().setPlayer(null); return null; });
            if (playlistId != null) repository.database.iptv().delete(playlistId);
        }
    }
    @Test public void podcastFeedBrowsesAndStreamsWithSavedProgress() throws Exception {
        String feedId = null;
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            server.add("/episode.wav", java.nio.file.Files.readAllBytes(new File(context.getFilesDir(), "playback-fixture.wav").toPath()), "audio/wav");
            String xml = "<rss version='2.0'><channel><title>Podcast network show</title><item><guid>one</guid><title>Podcast network episode</title><enclosure type='audio/wav' url='/episode.wav'/></item></channel></rss>";
            server.add("/feed.xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/rss+xml");
            var document = new app.aavideoplayercar.network.HttpDocument().get(server.url("/feed.xml"));
            var parsed = app.aavideoplayercar.podcast.FeedParser.parse(document.bytes, server.url("/feed.xml"), document.url);
            feedId = parsed.feed.id; repository.database.podcasts().importFeed(parsed.feed, parsed.episodes, true);
            String id = parsed.episodes.get(0).id, mediaId = "podcast:" + id, parentId = PlaybackService.FEED + feedId;
            repository.database.podcasts().progress(id, 9000, 30000, 1);
            var feeds = main(() -> browser.getChildren(PlaybackService.PODCASTS, 0, 50, null)).get(10, TimeUnit.SECONDS);
            assertTrue(feeds.value.stream().anyMatch(item -> item.mediaId.equals(parentId)));
            var episodes = main(() -> browser.getChildren(parentId, 0, 50, null)).get(10, TimeUnit.SECONDS);
            assertEquals(mediaId, episodes.value.get(0).mediaId);
            var search = main(() -> browser.getSearchResult("Podcast network episode", 0, 10, null)).get(10, TimeUnit.SECONDS);
            assertEquals(mediaId, search.value.get(0).mediaId);
            main(() -> {
                browser.setMediaItem(new MediaItem.Builder().setMediaId(mediaId).setUri("https://invalid.example/forged").build());
                browser.prepare(); return null;
            });
            await(() -> browser.getPlaybackState() == Player.STATE_READY && browser.getCurrentPosition() >= 9000);
            main(() -> { browser.play(); return null; }); await(() -> browser.isPlaying());
            main(() -> { browser.pause(); browser.clearMediaItems(); return null; });
            await(() -> browser.getMediaItemCount() == 0);
            repository.worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
            assertTrue(repository.database.podcasts().episode(id).positionMs >= 9000);
            assertNull(repository.dao.get(mediaId));
            main(() -> { browser.setMediaItem(new MediaItem.Builder().setMediaId(mediaId).build()); browser.prepare(); return null; });
            await(() -> browser.getPlaybackState() == Player.STATE_READY && browser.getCurrentPosition() >= 9000);
        } finally {
            main(() -> { browser.pause(); browser.clearMediaItems(); return null; });
            if (feedId != null) repository.database.podcasts().unsubscribe(feedId);
        }
    }
}
