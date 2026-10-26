package app.aavideoplayercar;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.news.NewsSpeech;
import java.util.*;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class NewsSpeechTest {
    @Test public void platformEngineReportsAvailabilityAndCompletesOrExplainsFailure() throws Exception {
        var state = new java.util.concurrent.atomic.AtomicReference<NewsSpeech.State>();
        var initialized = new java.util.concurrent.CountDownLatch(1); var finished = new java.util.concurrent.CountDownLatch(1);
        var reading = new java.util.concurrent.atomic.AtomicBoolean(); NewsSpeech[] speech = new NewsSpeech[1];
        try (var activity = androidx.test.core.app.ActivityScenario.launch(MainActivity.class)) {
            activity.onActivity(a -> speech[0] = NewsSpeech.create(a, next -> {
                state.set(next);
                if (next == NewsSpeech.State.READY || next == NewsSpeech.State.ERROR) { initialized.countDown(); if (reading.get()) finished.countDown(); }
            }));
            try {
                assertTrue("Platform engine initialization must resolve", initialized.await(20, java.util.concurrent.TimeUnit.SECONDS));
                if (state.get() == NewsSpeech.State.ERROR) {
                    android.util.Log.i("NewsSpeechProbe", "platform_engine=unavailable; speech error state verified");
                    return;
                }
                // Arm completion on the main thread after the initialization callback has returned.
                activity.onActivity(a -> { reading.set(true); speech[0].speak("News reader speech verification."); });
                assertTrue("Platform speech must complete or report failure", finished.await(30, java.util.concurrent.TimeUnit.SECONDS));
                android.util.Log.i("NewsSpeechProbe", "platform_speech_result=" + state.get());
                assertTrue(state.get() == NewsSpeech.State.READY || state.get() == NewsSpeech.State.ERROR || state.get() == NewsSpeech.State.STOPPED);
                activity.onActivity(a -> speech[0].stop()); assertEquals(NewsSpeech.State.STOPPED, state.get());
            } finally { activity.onActivity(a -> speech[0].close()); }
        }
        assertEquals(NewsSpeech.State.CLOSED, state.get());
    }
    static class Engine implements NewsSpeech.Engine {
        Consumer<Boolean> initialized, completed; int calls, stops, closes; boolean accepted = true;
        public void initialize(Consumer<Boolean> ready) { initialized = ready; }
        public int limit() { return 10; }
        public boolean speak(String text, String id, Consumer<Boolean> done) { assertTrue(text.length() <= 10); calls++; completed = done; return accepted; }
        public void stop() { stops++; }
        public void close() { closes++; }
    }
    static class Focus implements NewsSpeech.Focus {
        Runnable lost; boolean granted = true; int releases;
        public boolean acquire(Runnable loss) { lost = loss; return granted; }
        public void release() { releases++; }
    }
    private void ui(Runnable r) { InstrumentationRegistry.getInstrumentation().runOnMainSync(r); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    @Test public void sequencesChunksIgnoresStaleCallbacksAndReleasesFocus() {
        Engine engine = new Engine(); Focus focus = new Focus(); List<NewsSpeech.State> states = new ArrayList<>(); NewsSpeech[] speech = new NewsSpeech[1];
        ui(() -> speech[0] = new NewsSpeech(engine, focus, states::add)); ui(() -> engine.initialized.accept(true));
        ui(() -> speech[0].speak("123456789012345678901")); assertEquals(1, engine.calls);
        ui(() -> engine.completed.accept(true)); assertEquals(2, engine.calls);
        Consumer<Boolean> stale = engine.completed; ui(() -> speech[0].stop()); ui(() -> stale.accept(true)); assertEquals(2, engine.calls);
        ui(() -> speech[0].speak("hello")); ui(() -> engine.completed.accept(true)); assertEquals(NewsSpeech.State.READY, states.get(states.size() - 1));
        ui(() -> speech[0].speak("again")); ui(() -> focus.lost.run()); assertEquals(NewsSpeech.State.STOPPED, states.get(states.size() - 1));
        assertTrue(focus.releases > 0); ui(() -> speech[0].close()); assertEquals(1, engine.closes);
        ui(() -> { stale.accept(false); speech[0].speak("ignored"); speech[0].close(); }); assertEquals(NewsSpeech.State.CLOSED, states.get(states.size() - 1)); assertEquals(1, engine.closes);
    }
    @Test public void engineFailureFocusDenialAndCloseBeforeInitializationAreSafe() {
        Engine engine = new Engine(); Focus focus = new Focus(); List<NewsSpeech.State> states = new ArrayList<>(); NewsSpeech[] speech = new NewsSpeech[1];
        ui(() -> speech[0] = new NewsSpeech(engine, focus, states::add)); ui(() -> engine.initialized.accept(false));
        ui(() -> speech[0].speak("hello")); assertEquals(0, engine.calls); assertEquals(NewsSpeech.State.ERROR, states.get(states.size() - 1));
        ui(() -> engine.initialized.accept(true)); focus.granted = false; ui(() -> speech[0].speak("hello")); assertEquals(0, engine.calls);
        focus.granted = true; engine.accepted = false; ui(() -> speech[0].speak("hello")); assertEquals(NewsSpeech.State.ERROR, states.get(states.size() - 1));
        ui(() -> speech[0].close());
        Engine late = new Engine(); ui(() -> { speech[0] = new NewsSpeech(late, focus, states::add); speech[0].close(); }); ui(() -> late.initialized.accept(true));
        assertEquals(NewsSpeech.State.CLOSED, states.get(states.size() - 1)); assertEquals(1, late.closes);
    }
}
