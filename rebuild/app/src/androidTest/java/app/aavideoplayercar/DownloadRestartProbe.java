package app.aavideoplayercar;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import androidx.media3.common.*;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.media3.session.*;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.podcast.*;
import app.aavideoplayercar.playback.PlaybackService;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Separate instrumentation entry point: the host kills the entire target process between phases. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class DownloadRestartProbe extends androidx.test.runner.AndroidJUnitRunner {
    private static final String FEED = "restart-probe-feed", SAVED = "restart-probe-saved", PAUSED = "restart-probe-paused";
    private Bundle arguments;
    private Context context;
    private MediaRepository repository;
    private PodcastDownloads downloads;
    @Override public void onCreate(Bundle arguments) { this.arguments = arguments; super.onCreate(arguments); }
    @Override public void onStart() {
        if (!arguments.containsKey("phase")) { super.onStart(); return; }
        Bundle output = new Bundle(); Activity activity = null;
        int result = Activity.RESULT_OK;
        String phase = arguments.getString("phase", "");
        try {
            context = getTargetContext(); repository = MediaRepository.get(context); downloads = PodcastDownloads.get(context);
            activity = startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if ("seed".equals(phase)) seed();
            else if ("verify".equals(phase)) verify();
            else if ("cleanup".equals(phase)) cleanup();
            else throw new AssertionError("Unknown phase");
            output.putString("probe", "PASS " + phase);
            output.putInt("pid", android.os.Process.myPid());
        } catch (Throwable failure) {
            output.putString("probe", "FAIL " + phase); output.putString("failure", android.util.Log.getStackTraceString(failure));
            result = Activity.RESULT_CANCELED;
        } finally {
            if (activity != null) { Activity owner = activity; runOnMainSync(owner::finish); }
        }
        finish(result, output);
    }
    private <T> T main(Callable<T> action) {
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMainSync(() -> { try { value.set(action.call()); } catch (Throwable failure) { error.set(failure); } });
        if (error.get() != null) throw new AssertionError(error.get()); return value.get();
    }
    private void await(Callable<Boolean> ready) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) { if (ready.call()) return; Thread.sleep(50); }
        throw new AssertionError("Probe timed out");
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private Download state(String id) throws Exception { return downloads.index.getDownload(PodcastEpisode.PREFIX + id); }
    private PodcastEpisode episode(String id, String url) {
        PodcastEpisode result = new PodcastEpisode(); result.id = id; result.feedId = FEED; result.title = id; result.url = url; result.mime = "audio/wav"; return result;
    }
    private byte[] wav() {
        int samples = 30 * 8000, length = samples * 2;
        ByteBuffer out = ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(new byte[]{'R','I','F','F'}).putInt(36 + length).put(new byte[]{'W','A','V','E','f','m','t',' '})
            .putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16)
            .put(new byte[]{'d','a','t','a'}).putInt(length);
        for (int i = 0; i < samples; i++) out.putShort((short)(Math.sin(i * Math.PI * 440 / 4000) * 1000));
        return out.array();
    }
    private void seed() throws Exception {
        cleanup();
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            server.add("/saved.wav", wav(), "audio/wav"); server.slow("/paused.wav", new byte[4 * 1024 * 1024]);
            PodcastFeed feed = new PodcastFeed(); feed.id = FEED; feed.title = "Restart probe"; feed.url = server.url("/feed.xml");
            PodcastEpisode saved = episode(SAVED, server.url("/saved.wav")), paused = episode(PAUSED, server.url("/paused.wav"));
            repository.database.podcasts().importFeed(feed, Arrays.asList(saved, paused), true);
            DownloadRequest savedRequest = downloads.request(saved), pausedRequest = downloads.request(paused);
            main(() -> { downloads.manager().setRequirements(new Requirements(0));
                DownloadService.sendAddDownload(context, PodcastDownloadService.class, savedRequest, false); return null; });
            await(() -> state(SAVED) != null && state(SAVED).state == Download.STATE_COMPLETED);
            main(() -> { DownloadService.sendAddDownload(context, PodcastDownloadService.class, pausedRequest, false); return null; });
            await(() -> main(() -> downloads.manager().getCurrentDownloads().stream().anyMatch(d -> d.request.id.equals(pausedRequest.id) && d.getBytesDownloaded() > 128 * 1024)));
            main(() -> { DownloadService.sendSetStopReason(context, PodcastDownloadService.class, pausedRequest.id, PodcastDownloads.USER_PAUSED, false); return null; });
            await(() -> state(PAUSED).state == Download.STATE_STOPPED && main(() -> downloads.manager().isIdle()));
            long partial = downloads.cache.getCachedBytes(pausedRequest.customCacheKey, 0, 4 * 1024 * 1024);
            check(partial > 0 && partial < 4 * 1024 * 1024, "Partial bytes were not saved");
            repository.database.podcasts().progress(SAVED, 9000, 30000, 1);
            check(context.getSharedPreferences("restart-probe", 0).edit().putInt("pid", android.os.Process.myPid())
                .putLong("partial", partial).putString("savedKey", savedRequest.customCacheKey).putString("pausedKey", pausedRequest.customCacheKey).commit(), "Probe state not committed");
        }
        // Source server is now closed; only the cache can provide the saved episode in the next process.
    }
    private void verify() throws Exception {
        var preferences = context.getSharedPreferences("restart-probe", 0);
        check(preferences.getInt("pid", -1) > 0 && preferences.getInt("pid", -1) != android.os.Process.myPid(), "Target process did not change");
        check(state(SAVED) != null && state(SAVED).state == Download.STATE_COMPLETED, "Completed state was lost");
        check(state(PAUSED) != null && state(PAUSED).stopReason == PodcastDownloads.USER_PAUSED, "Paused state was lost");
        check(downloads.cache.getCachedBytes(preferences.getString("pausedKey", ""), 0, 4 * 1024 * 1024) == preferences.getLong("partial", -1), "Partial bytes did not survive");
        main(() -> { DownloadService.start(context, PodcastDownloadService.class); return null; });
        await(() -> main(() -> downloads.manager().isInitialized() && downloads.manager().isIdle()));
        check(state(PAUSED).state == Download.STATE_STOPPED, "Explicit pause resumed unexpectedly");
        MediaController controller = main(() -> new MediaController.Builder(context,
            new SessionToken(context, new ComponentName(context, PlaybackService.class))).buildAsync()).get(10, TimeUnit.SECONDS);
        try {
            main(() -> { controller.setMediaItem(new MediaItem.Builder().setMediaId(PodcastEpisode.PREFIX + SAVED).build()); controller.prepare(); controller.play(); return null; });
            await(() -> main(() -> controller.isPlaying() && controller.getCurrentPosition() >= 9000));
            check(main(() -> controller.getPlayerError()) == null, "Offline playback failed");
        } finally { main(() -> { controller.pause(); controller.clearMediaItems(); controller.release(); return null; }); }
        repository.worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
        cleanup();
        check(downloads.cache.getCachedBytes(preferences.getString("savedKey", ""), 0, wav().length) == 0, "Saved cache was not removed");
        check(downloads.cache.getCachedBytes(preferences.getString("pausedKey", ""), 0, 4 * 1024 * 1024) == 0, "Partial cache was not removed");
        preferences.edit().clear().commit();
    }
    private void cleanup() throws Exception {
        main(() -> { downloads.manager().removeDownload(PodcastEpisode.PREFIX + SAVED); downloads.manager().removeDownload(PodcastEpisode.PREFIX + PAUSED); return null; });
        await(() -> state(SAVED) == null && state(PAUSED) == null && main(() -> downloads.manager().isIdle()));
        repository.database.podcasts().unsubscribe(FEED);
        main(() -> { downloads.manager().setRequirements(PodcastDownloads.requirements(context.getSharedPreferences("downloads", 0).getBoolean("wifiOnly", true))); return null; });
    }
}
