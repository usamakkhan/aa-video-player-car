package app.aavideoplayercar;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.podcast.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PodcastDiscoveryScreenTest {
    private final AtomicReference<PodcastScreen> screen = new AtomicReference<>();
    private ListView list() { return screen.get().getView().findViewWithTag("podcast.list"); }
    private void install(ActivityScenario<MainActivity> scenario, Bundle state, PodcastScreen.DirectorySource source) {
        scenario.onActivity(a -> {
            if (screen.get() != null) screen.get().close();
            PodcastScreen next = new PodcastScreen(a, state, source);
            screen.set(next); a.setContentView(next.getView());
        });
    }
    private void await(ActivityScenario<MainActivity> scenario, java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(a -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(50);
        }
        fail("Podcast discovery screen did not become ready");
    }
    private void search(String query) {
        onView(withText(R.string.podcast_discover)).perform(click());
        onView(withTagValue(is("podcast.search.name"))).perform(replaceText(query));
        onView(withTagValue(is("podcast.search.country"))).perform(replaceText("us"), closeSoftKeyboard());
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
    }
    private void ready(ActivityScenario<MainActivity> scenario) throws Exception {
        await(scenario, () -> list().getAdapter() != null && list().isEnabled());
    }
    private byte[] results(String feed) throws Exception {
        return new org.json.JSONObject().put("results", new org.json.JSONArray().put(
            new org.json.JSONObject().put("kind", "podcast").put("collectionName", "Fixture show")
                .put("artistName", "Fixture publisher").put("feedUrl", feed))).toString().getBytes(StandardCharsets.UTF_8);
    }
    @Test public void discoverySubscribesFromHttpFeedRestoresResultsAndUnsubscribes() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var dao = MediaRepository.get(context).database.podcasts();
        String feedId = null;
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            String url = server.url("/feed.xml");
            byte[] xml = ("<rss><channel><title>Fixture show</title><item><guid>discovered</guid><title>Discovered episode</title>"
                + "<enclosure type='audio/mpeg' url='" + server.url("/episode.mp3") + "'/></item></channel></rss>").getBytes(StandardCharsets.UTF_8);
            feedId = FeedParser.parse(xml, url, url).feed.id;
            server.add("/feed.xml", xml, "application/rss+xml");
            server.add("/search", results(url), "application/json");
            PodcastScreen.DirectorySource source = (name, country, request) -> {
                assertEquals("Fixture", name); assertEquals("US", country);
                return PodcastDirectory.parse(request.get(server.url("/search"), "application/json").bytes);
            };
            try (var scenario = ActivityScenario.launch(MainActivity.class)) {
                try {
                    install(scenario, null, source); ready(scenario); search("Fixture"); ready(scenario);
                    scenario.onActivity(a -> assertTrue(list().getAdapter().getItem(0) instanceof PodcastDirectory.Show));
                    AtomicReference<Bundle> saved = new AtomicReference<>();
                    scenario.onActivity(a -> saved.set(screen.get().saveState()));
                    install(scenario, saved.get(), source); ready(scenario);
                    scenario.onActivity(a -> {
                        assertEquals(1, list().getCount());
                        list().performItemClick(null, 0, 0);
                    });
                    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
                    await(scenario, () -> list().getAdapter() != null && list().getCount() == 1
                        && list().getAdapter().getItem(0) instanceof PodcastEpisode);
                    assertEquals(1, dao.episodeCount(feedId));
                    onView(withText(R.string.podcast_remove)).perform(click());
                    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
                    ready(scenario);
                    assertNull(dao.feed(feedId)); assertEquals(0, dao.episodeCount(feedId));
                } finally { scenario.onActivity(a -> screen.get().close()); }
            }
        } finally { if (feedId != null) dao.unsubscribe(feedId); }
    }
    @Test public void failedSearchCanRetryAndLateResponseCannotReplaceSubscriptions() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        List<PodcastDirectory.Show> results = PodcastDirectory.parse(results("https://example.com/feed.xml"));
        PodcastScreen.DirectorySource source = (name, country, request) -> {
            if (first.getAndSet(false)) throw new java.io.IOException("Fixture offline");
            started.countDown();
            // Deliberately emulate a provider that finishes despite cancellation.
            boolean waiting = true;
            while (waiting) {
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new java.io.IOException("Fixture timeout"); waiting = false; }
                catch (InterruptedException ignored) { }
            }
            finished.countDown(); return results;
        };
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            try {
                install(scenario, null, source); ready(scenario); search("Fixture");
                await(scenario, () -> ((TextView)screen.get().getView().findViewWithTag("podcast.status")).getText()
                    .toString().equals(InstrumentationRegistry.getInstrumentation().getTargetContext().getString(R.string.podcast_directory_error)));
                search("Fixture"); assertTrue(started.await(5, TimeUnit.SECONDS));
                onView(withText(R.string.podcast_subscriptions)).perform(click()); ready(scenario);
                release.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a -> {
                    assertFalse(screen.get().saveState().getBoolean("discovery"));
                    for (int i = 0; i < list().getCount(); i++) assertTrue(list().getAdapter().getItem(i) instanceof PodcastFeed);
                });
            } finally { release.countDown(); scenario.onActivity(a -> screen.get().close()); }
        }
    }
    @Test public void invalidSearchKeepsEditableDraftAndRestoresItWithoutSubmitting() throws Exception {
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        PodcastScreen.DirectorySource source = (name, country, request) -> { requests.incrementAndGet(); return java.util.Collections.emptyList(); };
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            try {
                install(scenario, null, source); ready(scenario);
                onView(withText(R.string.podcast_discover)).perform(click());
                onView(withTagValue(is("podcast.search.name"))).perform(replaceText("Draft name"));
                onView(withTagValue(is("podcast.search.country"))).perform(replaceText("USA"), closeSoftKeyboard());
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
                onView(withTagValue(is("podcast.search.country"))).inRoot(isDialog()).check(matches(withText("USA")));
                assertEquals(0, requests.get());
                AtomicReference<Bundle> saved = new AtomicReference<>();
                scenario.onActivity(a -> saved.set(screen.get().saveState()));
                install(scenario, saved.get(), source);
                onView(withTagValue(is("podcast.search.name"))).inRoot(isDialog()).check(matches(withText("Draft name")));
                onView(withTagValue(is("podcast.search.country"))).inRoot(isDialog()).check(matches(withText("USA")));
                assertEquals(0, requests.get());
                onView(withTagValue(is("podcast.search.country"))).perform(replaceText("US"), closeSoftKeyboard());
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()); ready(scenario);
                assertEquals(1, requests.get());
                scenario.onActivity(a -> assertEquals(0, list().getCount()));
            } finally { scenario.onActivity(a -> screen.get().close()); }
        }
    }
}
