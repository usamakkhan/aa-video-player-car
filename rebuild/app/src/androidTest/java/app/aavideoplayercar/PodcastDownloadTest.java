package app.aavideoplayercar;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.Requirements;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.podcast.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.*;

@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PodcastDownloadTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final MediaRepository repository = MediaRepository.get(context);
    private final PodcastDownloads downloads = PodcastDownloads.get(context);
    private <T> T main(Callable<T> action) {
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { value.set(action.call()); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get()); return value.get();
    }
    private void await(Callable<Boolean> condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) { if (condition.call()) return; Thread.sleep(50); }
        fail("Download state timeout");
    }
    private PodcastEpisode episode(String url) {
        PodcastFeed feed = new PodcastFeed(); feed.id = UUID.randomUUID().toString(); feed.url = url + "/feed"; feed.title = "Download fixture";
        PodcastEpisode episode = new PodcastEpisode(); episode.id = UUID.randomUUID().toString(); episode.feedId = feed.id;
        episode.title = "Download test"; episode.url = url; episode.mime = "audio/mpeg";
        repository.database.podcasts().importFeed(feed, Collections.singletonList(episode), true); return episode;
    }
    private void remove(PodcastEpisode episode) throws Exception {
        String id = PodcastEpisode.PREFIX + episode.id;
        main(() -> { downloads.manager().removeDownload(id); return null; });
        await(() -> downloads.index.getDownload(id) == null);
        repository.database.podcasts().unsubscribe(episode.feedId);
        main(() -> { downloads.manager().setRequirements(PodcastDownloads.requirements(true)); return null; });
    }
    @Test public void servicePauseResumeUsesRangesPersistsStateAndRemovesBytes() throws Exception {
        try (LocalHttpFixture server = new LocalHttpFixture(); var activity = ActivityScenario.launch(MainActivity.class)) {
            byte[] bytes = new byte[4 * 1024 * 1024]; new Random(42).nextBytes(bytes); server.slow("/episode.mp3", bytes);
            PodcastEpisode episode = episode(server.url("/episode.mp3")); String id = PodcastEpisode.PREFIX + episode.id;
            try {
                DownloadRequest request = downloads.request(episode);
                main(() -> {
                    downloads.manager().setRequirements(new Requirements(0));
                    DownloadService.sendAddDownload(context, PodcastDownloadService.class, request, PodcastDownloads.USER_PAUSED, false); return null;
                });
                await(() -> downloads.index.getDownload(id) != null && downloads.index.getDownload(id).state == Download.STATE_STOPPED);
                assertEquals(PodcastDownloads.USER_PAUSED, new DefaultDownloadIndex(new androidx.media3.database.StandaloneDatabaseProvider(context), "podcasts").getDownload(id).stopReason);
                main(() -> { DownloadService.sendSetStopReason(context, PodcastDownloadService.class, id, 0, false); return null; });
                await(() -> main(() -> downloads.manager().getCurrentDownloads().stream().anyMatch(d -> d.request.id.equals(id) && d.getBytesDownloaded() > 128 * 1024)));
                main(() -> { DownloadService.sendSetStopReason(context, PodcastDownloadService.class, id, PodcastDownloads.USER_PAUSED, false); return null; });
                await(() -> downloads.index.getDownload(id).state == Download.STATE_STOPPED);
                // STOPPED is published before the cancelled writer finishes flushing its cache span.
                await(() -> main(() -> downloads.manager().isIdle()));
                assertTrue(downloads.index.getDownload(id).getBytesDownloaded() < bytes.length);
                assertTrue(downloads.cache.getCachedBytes(request.customCacheKey, 0, bytes.length) > 0);
                main(() -> { DownloadService.sendSetStopReason(context, PodcastDownloadService.class, id, 0, false); return null; });
                await(() -> downloads.index.getDownload(id).state == Download.STATE_COMPLETED);
                assertTrue(server.ranges.stream().anyMatch(offset -> offset > 0));
                var cached = new androidx.media3.datasource.cache.CacheDataSource.Factory().setCache(downloads.cache).setUpstreamDataSourceFactory(null).createDataSource();
                java.io.ByteArrayOutputStream saved = new java.io.ByteArrayOutputStream();
                try {
                    cached.open(new androidx.media3.datasource.DataSpec.Builder().setUri(request.uri).setKey(request.customCacheKey).build());
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = cached.read(buffer, 0, buffer.length)) != -1) saved.write(buffer, 0, count);
                } finally { cached.close(); }
                assertArrayEquals(bytes, saved.toByteArray());
                main(() -> { DownloadService.sendRemoveDownload(context, PodcastDownloadService.class, id, false); return null; });
                await(() -> downloads.index.getDownload(id) == null);
                assertEquals(0, downloads.cache.getCachedBytes(request.customCacheKey, 0, bytes.length));
            } finally { remove(episode); }
        }
    }
    @Test public void oversizedTransferFailsAndAdaptiveOrUnsafeRequestsAreRejected() throws Exception {
        try (LocalHttpFixture server = new LocalHttpFixture(); var activity = ActivityScenario.launch(MainActivity.class)) {
            server.oversized("/large.mp3", PodcastDownloads.MAX_EPISODE_BYTES + 1);
            PodcastEpisode episode = episode(server.url("/large.mp3")); String id = PodcastEpisode.PREFIX + episode.id;
            try {
                DownloadRequest request = downloads.request(episode);
                main(() -> { downloads.manager().setRequirements(new Requirements(0)); downloads.manager().setMinRetryCount(0);
                    DownloadService.sendAddDownload(context, PodcastDownloadService.class, request, false); return null; });
                await(() -> downloads.index.getDownload(id) != null && downloads.index.getDownload(id).state == Download.STATE_FAILED);
                assertEquals(0, downloads.index.getDownload(id).getBytesDownloaded());
                episode.url = "file:///private/file.mp3";
                assertThrows(java.io.IOException.class, () -> downloads.request(episode));
                episode.url = "https://example.com/live.m3u8"; episode.mime = "application/x-mpegURL";
                assertThrows(java.io.IOException.class, () -> downloads.request(episode));
            } finally { main(() -> { downloads.manager().setMinRetryCount(2); return null; }); remove(episode); }
        }
    }
    @Test public void phoneOffersDownloadAndShowsSavedFileAndRemoval() throws Exception {
        try (LocalHttpFixture server = new LocalHttpFixture(); var activity = ActivityScenario.launch(MainActivity.class)) {
            server.add("/small.mp3", new byte[8192], "audio/mpeg");
            PodcastEpisode episode = episode(server.url("/small.mp3")); String id = PodcastEpisode.PREFIX + episode.id;
            AtomicReference<PodcastScreen> screen = new AtomicReference<>();
            try {
                main(() -> { downloads.manager().setRequirements(new Requirements(0)); return null; });
                activity.onActivity(a -> {
                    android.os.Bundle state = new android.os.Bundle(); state.putString("feedId", episode.feedId);
                    screen.set(new PodcastScreen(a, state)); a.setContentView(screen.get().getView());
                });
                await(() -> main(() -> {
                    android.widget.ListView list = screen.get().getView().findViewWithTag("podcast.list");
                    return list.getAdapter() != null && list.getCount() == 1;
                }));
                onData(instanceOf(PodcastEpisode.class)).inAdapterView(withTagValue(is("podcast.list"))).atPosition(0).perform(longClick());
                // The options are loaded from the download index on the repository worker.
                await(() -> main(() -> screen.get().getView().hasWindowFocus() == false));
                onView(withText(R.string.podcast_download)).inRoot(isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
                CountDownLatch workerBlocked = new CountDownLatch(1), releaseWorker = new CountDownLatch(1);
                repository.worker.execute(() -> {
                    workerBlocked.countDown();
                    try { releaseWorker.await(20, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                });
                assertTrue(workerBlocked.await(5, TimeUnit.SECONDS));
                try {
                    onView(withText(R.string.podcast_download)).inRoot(isDialog()).perform(click());
                    activity.onActivity(a -> {
                        var state = screen.get().saveState(); screen.get().close();
                        screen.set(new PodcastScreen(a, state)); a.setContentView(screen.get().getView());
                    });
                    // Confirmation is already dispatched: screen closure and a busy repository cannot lose the request.
                    await(() -> downloads.index.getDownload(id) != null && downloads.index.getDownload(id).state == Download.STATE_COMPLETED);
                } finally { releaseWorker.countDown(); }
                onView(withText(R.string.podcast_downloads)).perform(click());
                onView(withTagValue(is("podcast.download.list"))).inRoot(isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
                onData(containsString("Download test")).inAdapterView(withTagValue(is("podcast.download.list"))).inRoot(isDialog()).perform(click());
                onView(withText(R.string.podcast_download_remove)).inRoot(isDialog()).perform(click());
                await(() -> downloads.index.getDownload(id) == null);
            } finally {
                activity.onActivity(a -> { if (screen.get() != null) screen.get().close(); }); remove(episode);
            }
        }
    }
    @Test public void downloadPagesClampAfterRemovalAndRestoreAcrossActivityRecreation() throws Exception {
        List<PodcastEpisode> fixtures = new ArrayList<>();
        context.getSharedPreferences("dashboard", 0).edit().clear().commit();
        try {
            assertEquals(0, downloads.page(0, 50).count);
            for (int i = 0; i < 51; i++) {
                PodcastEpisode episode = episode("https://example.com/paging-" + i + ".mp3"); fixtures.add(episode);
                DownloadRequest request = downloads.request(episode);
                downloads.index.putDownload(new Download(request, Download.STATE_FAILED, i, i, -1, 0, Download.FAILURE_REASON_UNKNOWN));
                if (i == 49) { assertEquals(50, downloads.page(0, 50).rows.size()); assertFalse(downloads.page(0, 50).hasNext); }
            }
            assertTrue(downloads.page(0, 50).hasNext);
            assertEquals(1, downloads.page(Integer.MAX_VALUE, 50).number);
            assertEquals(1, downloads.page(1, 50).rows.size());
            assertThrows(IllegalArgumentException.class, () -> downloads.page(-1, 50));
            assertThrows(IllegalArgumentException.class, () -> downloads.page(0, 51));
            assertThrows(UnsupportedOperationException.class, () -> downloads.page(0, 50).rows.clear());
            try (var activity = ActivityScenario.launch(MainActivity.class)) {
                onView(withText("Podcast")).perform(click());
                onView(withText(R.string.podcast_downloads)).perform(click());
                awaitDialogCount(50);
                onView(withTagValue(is("podcast.download.next"))).inRoot(isDialog()).perform(click());
                awaitDialogCount(1);
                activity.recreate(); awaitDialogCount(1);
                onView(withTagValue(is("podcast.download.previous"))).inRoot(isDialog())
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(isEnabled()));
                downloads.index.removeDownload(PodcastEpisode.PREFIX + fixtures.get(50).id);
                awaitDialogCount(50);
                onView(withTagValue(is("podcast.download.next"))).inRoot(isDialog())
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(not(isEnabled())));
                onView(withTagValue(is("podcast.download.previous"))).inRoot(isDialog())
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(not(isEnabled())));
                onData(anything()).inAdapterView(withTagValue(is("podcast.download.list"))).inRoot(isDialog()).atPosition(20)
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
                AtomicReference<Integer> before = new AtomicReference<>();
                onView(withTagValue(is("podcast.download.list"))).inRoot(isDialog()).check((view, failure) -> before.set(((android.widget.ListView)view).getFirstVisiblePosition()));
                assertTrue(before.get() > 0);
                activity.recreate(); awaitDialogCount(50);
                onView(withTagValue(is("podcast.download.list"))).inRoot(isDialog()).check((view, failure) -> assertEquals((int)before.get(), ((android.widget.ListView)view).getFirstVisiblePosition()));
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click());
                activity.recreate();
                onView(withText(R.string.podcast_downloads)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            }
        } finally {
            for (PodcastEpisode episode : fixtures) {
                downloads.index.removeDownload(PodcastEpisode.PREFIX + episode.id);
                repository.database.podcasts().unsubscribe(episode.feedId);
            }
        }
        assertEquals(0, downloads.page(Integer.MAX_VALUE, 50).number);
        assertFalse(downloads.page(0, 50).hasNext);
    }
    private void awaitDialogCount(int expected) throws Exception {
        await(() -> {
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            onView(withTagValue(is("podcast.download.list"))).inRoot(isDialog()).check((view, failure) -> {
                if (view instanceof android.widget.ListView) ready.set(((android.widget.ListView)view).getCount() == expected);
            });
            return ready.get();
        });
    }
}
