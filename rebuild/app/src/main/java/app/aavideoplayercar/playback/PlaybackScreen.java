package app.aavideoplayercar.playback;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import app.aavideoplayercar.data.MediaEntry;
import app.aavideoplayercar.data.MediaRepository;

/** Owns only views and a controller connection; leaving the screen does not destroy playback. */
@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public final class PlaybackScreen {
    private final LinearLayout view;
    private final PlayerView playerView;
    private final TextView status;
    private final ListView list;
    private final Button previous, next;
    private final TextView pageLabel;
    private int page;
    private int generation;
    private static final int PAGE_SIZE = 50;
    private final MediaRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ListenableFuture<MediaController> connection;
    private MediaController controller;
    private MediaEntry pending;
    private boolean closed;
    private final String mimePrefix;
    private List<MediaEntry> entries = new ArrayList<>();
    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlayerError(PlaybackException error) {
            status.setText("Playback failed. The file may be missing, unsupported or no longer permitted. Choose it again.");
        }
        @Override public void onMediaItemTransition(MediaItem item, int reason) { updateStatus(); }
        @Override public void onIsPlayingChanged(boolean playing) { updateStatus(); }
    };

    public PlaybackScreen(Context context, String mimePrefix, Uri selected) {
        this.mimePrefix = mimePrefix;
        repository = MediaRepository.get(context);
        view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(context); status.setTextColor(Color.WHITE);
        status.setText("Connecting to player…"); view.addView(status);
        playerView = new PlayerView(context);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setControllerAutoShow(true);
        playerView.setControllerShowTimeoutMs(0);
        view.addView(playerView, new LinearLayout.LayoutParams(-1, Math.round(220 * context.getResources().getDisplayMetrics().density)));
        Button stop = new Button(context); stop.setText("Stop playback");
        stop.setOnClickListener(v -> { if (controller != null) { controller.pause(); controller.stop(); updateStatus(); } });
        view.addView(stop);
        list = new ListView(context); view.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, row, position, id) -> play(entries.get(position)));
        LinearLayout navigation = new LinearLayout(context);
        previous = new Button(context); previous.setText("Previous");
        next = new Button(context); next.setText("Next");
        pageLabel = new TextView(context); pageLabel.setTextColor(Color.WHITE);
        navigation.addView(previous); navigation.addView(pageLabel, new LinearLayout.LayoutParams(0, -2, 1)); navigation.addView(next);
        view.addView(navigation);
        previous.setOnClickListener(v -> { if (page > 0) { page--; refresh(); } });
        next.setOnClickListener(v -> { page++; refresh(); });
        connection = new MediaController.Builder(context,
            new SessionToken(context, new ComponentName(context, PlaybackService.class))).buildAsync();
        connection.addListener(() -> {
            if (closed) return;
            try {
                controller = connection.get();
                controller.addListener(listener); playerView.setPlayer(controller);
                updateStatus();
                if (pending != null) { MediaEntry item = pending; pending = null; play(item); }
            } catch (Exception e) { status.setText("Player connection failed. Return home and try again."); }
        }, main::post);
        refresh();
        if (selected != null) repository.worker.execute(() -> {
            try {
                MediaEntry entry = repository.importDocument(selected, mimePrefix + "*");
                main.post(() -> { if (!closed) { play(entry); refresh(); } });
            } catch (Exception e) {
                main.post(() -> { if (!closed) status.setText("Cannot open this file. Choose it again to restore access."); });
            }
        });
    }
    private void refresh() {
        int request = ++generation;
        int requestedPage = page;
        previous.setEnabled(false); next.setEnabled(false);
        repository.worker.execute(() -> {
            try {
                int total = repository.dao.count(mimePrefix);
                int effectivePage = Math.min(requestedPage, Math.max(0, (total - 1) / PAGE_SIZE));
                List<MediaEntry> result = repository.dao.list(mimePrefix, PAGE_SIZE, effectivePage * PAGE_SIZE);
                main.post(() -> {
                    if (closed || request != generation) return;
                    page = effectivePage;
                    previous.setEnabled(page > 0);
                    next.setEnabled((long) (page + 1) * PAGE_SIZE < total);
                    pageLabel.setText("Page " + (page + 1) + " - " + total + " items");
                    entries = result;
                    List<String> titles = new ArrayList<>();
                    for (MediaEntry entry : result) titles.add(entry.title);
                    list.setAdapter(new ArrayAdapter<>(view.getContext(), android.R.layout.simple_list_item_1, titles));
                    if (controller != null) updateStatus();
                });
            } catch (RuntimeException error) {
                main.post(() -> {
                    if (closed || request != generation) return;
                    status.setText("Could not load the library. Return home and try again.");
                });
            }
        });
    }
    private void play(MediaEntry entry) {
        if (controller == null) { pending = entry; return; }
        MediaItem current = controller.getCurrentMediaItem();
        if (current == null || !current.mediaId.equals(entry.id)) {
            List<String> ids = new ArrayList<>();
            for (MediaEntry candidate : entries) ids.add(candidate.id);
            List<String> bounded = PlaybackLimits.queue(ids, entry.id);
            List<MediaItem> queue = new ArrayList<>();
            for (String id : bounded) queue.add(new MediaItem.Builder().setMediaId(id).build());
            controller.setMediaItems(queue, bounded.indexOf(entry.id), entry.positionMs);
        }
        controller.prepare(); controller.play();
    }
    private void updateStatus() {
        if (controller == null || controller.getPlayerError() != null) return;
        MediaItem item = controller.getCurrentMediaItem();
        status.setText(item == null ? (entries.isEmpty() ? "Choose a file to build your library." : "Select a library item to play.")
            : (controller.isPlaying() ? "Playing: " : "Paused: ") + item.mediaMetadata.title);
    }
    public LinearLayout getView() { return view; }
    public void playSearch(String query) {
        repository.worker.execute(() -> {
            List<MediaEntry> matches = repository.dao.searchAudio(query, 1, 0);
            main.post(() -> {
                if (closed) return;
                if (matches.isEmpty()) status.setText("No matching audio. Add music using Choose a file.");
                else play(matches.get(0));
            });
        });
    }
    public void close() {
        closed = true;
        if (controller != null) controller.removeListener(listener);
        playerView.setPlayer(null);
        MediaController.releaseFuture(connection);
        main.removeCallbacksAndMessages(null);
    }
}
