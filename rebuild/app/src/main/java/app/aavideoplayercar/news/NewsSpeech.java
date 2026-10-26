package app.aavideoplayercar.news;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;
import java.util.function.Consumer;

/** Foreground reader speech. All state changes run on the main thread; stale callbacks are ignored. */
public final class NewsSpeech {
    public enum State { LOADING, READY, SPEAKING, STOPPED, ERROR, CLOSED }
    public interface Engine {
        void initialize(Consumer<Boolean> ready);
        int limit();
        boolean speak(String text, String id, Consumer<Boolean> completed);
        void stop();
        void close();
    }
    public interface Focus { boolean acquire(Runnable lost); void release(); }
    private final Engine engine;
    private final Focus focus;
    private final Consumer<State> listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean ready, closed;
    private int generation, index;
    private List<String> chunks = List.of();
    private Runnable cleanup = () -> {};
    public NewsSpeech(Engine engine, Focus focus, Consumer<State> listener) {
        this.engine = engine; this.focus = focus; this.listener = listener;
        listener.accept(State.LOADING);
        main.postDelayed(() -> { if (!closed && !ready) listener.accept(State.ERROR); }, 15000);
        try { engine.initialize(ok -> main.post(() -> { if (!closed) { ready = ok; listener.accept(ok ? State.READY : State.ERROR); } })); }
        catch (Exception error) { listener.accept(State.ERROR); }
    }
    public void speak(String text) {
        if (closed) return;
        stop(); if (!ready) { listener.accept(State.ERROR); return; }
        try {
            chunks = SpeechChunks.split(text, Math.min(3500, engine.limit())); index = 0;
            if (chunks.isEmpty()) { listener.accept(State.READY); return; }
            int version = generation;
            if (!focus.acquire(() -> main.post(() -> { if (!closed && version == generation) stop(); }))) { fail(); return; }
            listener.accept(State.SPEAKING); next(version);
        } catch (Exception error) { fail(); }
    }
    private void next(int version) {
        if (closed || version != generation) return;
        if (index == chunks.size()) { generation++; chunks = List.of(); focus.release(); listener.accept(State.READY); return; }
        int position = index++;
        try {
            boolean accepted = engine.speak(chunks.get(position), version + ":" + position, ok -> main.post(() -> {
                if (closed || version != generation || index != position + 1) return;
                if (ok) next(version); else fail();
            }));
            if (!accepted) fail();
        } catch (Exception error) { fail(); }
    }
    private void fail() { stop(); if (!closed) listener.accept(State.ERROR); }
    public void stop() {
        if (closed) return; generation++; chunks = List.of();
        try { engine.stop(); } catch (Exception ignored) { }
        try { focus.release(); } catch (Exception ignored) { }
        listener.accept(State.STOPPED);
    }
    public void close() {
        if (closed) return; stop(); closed = true; main.removeCallbacksAndMessages(null);
        try { engine.close(); } catch (Exception ignored) { }
        try { cleanup.run(); } catch (Exception ignored) { }
        listener.accept(State.CLOSED);
    }
    public static NewsSpeech create(Context context, Consumer<State> listener) {
        Context app = context.getApplicationContext(); AudioManager audio = (AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        Engine engine = new Engine() {
            TextToSpeech tts; Consumer<Boolean> done; String current;
            public void initialize(Consumer<Boolean> ready) {
                tts = new TextToSpeech(app, status -> new Handler(Looper.getMainLooper()).post(() -> {
                    if (tts == null) return;
                    boolean ok;
                    try { ok = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale.getDefault()) >= TextToSpeech.LANG_AVAILABLE; }
                    catch (Exception error) { ready.accept(false); return; }
                    if (ok) {
                        tts.setAudioAttributes(attributes);
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            public void onStart(String id) {}
                            public void onDone(String id) { finish(id, true); }
                            @Override public void onError(String id) { finish(id, false); }
                            @Override public void onError(String id, int code) { finish(id, false); }
                            @Override public void onStop(String id, boolean interrupted) { finish(id, false); }
                            private void finish(String id, boolean ok) { new Handler(Looper.getMainLooper()).post(() -> { if (id.equals(current) && done != null) { var callback = done; done = null; callback.accept(ok); } }); }
                        });
                    } ready.accept(ok);
                }));
            }
            public int limit() { return TextToSpeech.getMaxSpeechInputLength(); }
            public boolean speak(String text, String id, Consumer<Boolean> completed) { current = id; done = completed; return tts != null && tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS; }
            public void stop() { current = null; done = null; if (tts != null) tts.stop(); }
            public void close() { if (tts != null) { tts.shutdown(); tts = null; } }
        };
        Focus focus = new Focus() {
            AudioFocusRequest request;
            public boolean acquire(Runnable lost) {
                request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(change -> { if (change < 0) lost.run(); }).build();
                return audio != null && audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
            public void release() { if (request != null && audio != null) audio.abandonAudioFocusRequest(request); request = null; }
        };
        NewsSpeech speech = new NewsSpeech(engine, focus, listener);
        BroadcastReceiver noisy = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { speech.stop(); } };
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED); else app.registerReceiver(noisy, filter);
        speech.cleanup = () -> app.unregisterReceiver(noisy); return speech;
    }
}
