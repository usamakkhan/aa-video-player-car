package app.aavideoplayercar.radio;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import app.aavideoplayercar.R;
import app.aavideoplayercar.data.MediaRepository;
import app.aavideoplayercar.data.RadioStation;
import app.aavideoplayercar.playback.PlaybackService;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

public final class RadioScreen {
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4));
    private final Context context;
    private final MediaRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout view;
    private final EditText query, country;
    private final TextView status, playback;
    private final Button search, previous, next;
    private final ListView list;
    private final ListenableFuture<MediaController> connection;
    private MediaController controller;
    private RadioStation pending;
    private RadioClient request;
    private java.util.concurrent.Future<?> networkTask;
    private AlertDialog dialog;
    private List<RadioStation> stations = new ArrayList<>();
    private boolean closed, favorites = true;
    private int page, generation;
    private String searchedName = "", searchedCountry = "";
    private final Player.Listener listener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events events) { updatePlayback(); }
        @Override public void onPlayerError(PlaybackException error) { playback.setText(R.string.radio_stream_error); }
    };

    public RadioScreen(Context context, Bundle state) {
        this.context = context; repository = MediaRepository.get(context);
        view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL);
        query = new EditText(context); query.setSingleLine(); query.setHint(R.string.radio_query); query.setTag("radio.query"); view.addView(query);
        country = new EditText(context); country.setSingleLine(); country.setHint(R.string.radio_country); country.setTag("radio.country"); view.addView(country);
        status = label(); status.setTag("radio.status");
        LinearLayout actions = new LinearLayout(context);
        search = button(R.string.radio_search, () -> {
            String name = query.getText().toString().trim();
            String code = country.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (name.length() > 200 || !code.matches("[A-Z]{2}|")) { status.setText(R.string.radio_validation); return; }
            searchedName = name; searchedCountry = code; favorites = false; page = 0; load();
        });
        actions.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button(R.string.radio_favorites, () -> { favorites = true; page = 0; load(); }), new LinearLayout.LayoutParams(0, -2, 1));
        view.addView(actions);
        view.addView(status);
        list = new ListView(context); list.setTag("radio.list"); view.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, row, index, id) -> choose(stations.get(index)));
        LinearLayout pages = new LinearLayout(context);
        previous = button(R.string.radio_previous, () -> { if (page > 0) { page--; load(); } });
        next = button(R.string.radio_next, () -> { page++; load(); });
        pages.addView(previous, new LinearLayout.LayoutParams(0, -2, 1)); pages.addView(next, new LinearLayout.LayoutParams(0, -2, 1)); view.addView(pages);
        playback = label(); playback.setTag("radio.playback"); playback.setText(R.string.radio_idle); view.addView(playback);
        LinearLayout controls = new LinearLayout(context);
        controls.addView(button(R.string.radio_pause, () -> { if (controller != null) controller.pause(); }), new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(button(R.string.radio_resume, () -> { if (controller != null && controller.getCurrentMediaItem() != null) { controller.prepare(); controller.play(); } }), new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(button(R.string.radio_stop, () -> { if (controller != null) controller.stop(); }), new LinearLayout.LayoutParams(0, -2, 1)); view.addView(controls);
        connection = new MediaController.Builder(context, new SessionToken(context, new ComponentName(context, PlaybackService.class))).buildAsync();
        connection.addListener(() -> {
            if (closed) return;
            try { controller = connection.get(); controller.addListener(listener); updatePlayback(); if (pending != null) { RadioStation station = pending; pending = null; playReady(station); } }
            catch (Exception error) { playback.setText(R.string.radio_connection_error); }
        }, main::post);
        if (state != null) {
            query.setText(state.getString("query", "")); country.setText(state.getString("country", ""));
            searchedName = state.getString("searchedName", ""); searchedCountry = state.getString("searchedCountry", "");
            favorites = state.getBoolean("favorites", true); page = Math.max(0, state.getInt("page", 0));
        }
        load();
    }
    private TextView label() { TextView label = new TextView(context); label.setTextColor(Color.WHITE); return label; }
    private Button button(int text, Runnable action) { Button b = new Button(context); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b; }
    private void cancelSearch() { if (request != null) request.cancel(); if (networkTask != null) networkTask.cancel(true); }
    private void load() {
        cancelSearch();
        int version = ++generation, requestedPage = page;
        boolean local = favorites;
        status.setText(R.string.radio_loading); previous.setEnabled(false); next.setEnabled(false); search.setEnabled(false);
        if (local) repository.worker.execute(() -> {
            try {
                int count = repository.database.radio().count();
                int effectivePage = Math.min(requestedPage, Math.max(0, (count - 1) / RadioClient.PAGE_SIZE));
                List<RadioStation> loaded = repository.database.radio().favorites(RadioClient.PAGE_SIZE, effectivePage * RadioClient.PAGE_SIZE);
                deliver(version, loaded, effectivePage, (long)(effectivePage + 1) * RadioClient.PAGE_SIZE < count);
            } catch (Exception error) { failed(version, R.string.radio_storage_error); }
        }); else {
            RadioClient client = new RadioClient(); request = client;
            String name = searchedName, code = searchedCountry;
            NETWORK.purge();
            try {
                networkTask = NETWORK.submit(() -> {
                    try { List<RadioStation> loaded = client.search(name, code, requestedPage); deliver(version, loaded, requestedPage, client.hasMore()); }
                    catch (Exception error) { failed(version, R.string.radio_search_error); }
                });
            } catch (RejectedExecutionException error) { failed(version, R.string.radio_search_error); }
        }
    }
    private void deliver(int version, List<RadioStation> loaded, int resultPage, boolean more) {
        main.post(() -> {
            if (closed || version != generation) return;
            page = resultPage; stations = loaded;
            list.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, loaded));
            search.setEnabled(true); previous.setEnabled(page > 0); next.setEnabled(more && page < 10000);
            status.setText(loaded.isEmpty() ? context.getString(R.string.radio_empty) : context.getString(R.string.radio_page,
                context.getString(favorites ? R.string.radio_favorites : R.string.radio_directory), page + 1, loaded.size()));
        });
    }
    private void failed(int version, int message) { main.post(() -> {
        if (!closed && version == generation) { status.setText(message); search.setEnabled(true); previous.setEnabled(page > 0); }
    }); }
    private void choose(RadioStation station) {
        int version = generation;
        repository.worker.execute(() -> {
            try {
                RadioStation stored = repository.database.radio().get(station.id);
                boolean saved = stored != null && stored.favorite;
                main.post(() -> {
                    if (closed || version != generation) return;
                    dialog = new AlertDialog.Builder(context).setTitle(station.title)
                        .setItems(new String[]{context.getString(R.string.radio_play), context.getString(saved ? R.string.radio_remove : R.string.radio_add)},
                            (d, which) -> save(station, which == 0 ? null : !saved, which == 0)).show();
                });
            } catch (Exception error) { failed(version, R.string.radio_storage_error); }
        });
    }
    private void save(RadioStation station, Boolean favorite, boolean play) {
        int version = generation;
        repository.worker.execute(() -> {
            try {
                repository.database.radio().save(station, favorite);
                main.post(() -> {
                    if (closed) return;
                    if (play) playReady(station);
                    else if (version == generation) {
                        if (favorites) load();
                        else status.setText(Boolean.TRUE.equals(favorite) ? R.string.radio_saved : R.string.radio_removed);
                    }
                });
            } catch (Exception error) { failed(version, R.string.radio_storage_error); }
        });
    }
    private void playReady(RadioStation station) {
        if (controller == null) { pending = station; return; }
        controller.setMediaItem(new MediaItem.Builder().setMediaId(RadioStation.PREFIX + station.id).build());
        controller.prepare(); controller.play();
    }
    private void updatePlayback() {
        if (controller == null || closed) return;
        if (controller.getPlayerError() != null) { playback.setText(R.string.radio_stream_error); return; }
        MediaItem item = controller.getCurrentMediaItem();
        playback.setText(item == null ? context.getString(R.string.radio_idle) : context.getString(
            controller.getPlaybackState() == Player.STATE_BUFFERING ? R.string.radio_buffering : controller.isPlaying() ? R.string.radio_playing : R.string.radio_paused,
            item.mediaMetadata.title == null ? "" : item.mediaMetadata.title));
    }
    public LinearLayout getView() { return view; }
    public Bundle saveState() {
        Bundle state = new Bundle(); state.putString("query", query.getText().toString()); state.putString("country", country.getText().toString());
        state.putString("searchedName", searchedName); state.putString("searchedCountry", searchedCountry);
        state.putBoolean("favorites", favorites); state.putInt("page", page); return state;
    }
    public void close() {
        closed = true; cancelSearch(); if (dialog != null) dialog.dismiss();
        if (controller != null) controller.removeListener(listener);
        MediaController.releaseFuture(connection); main.removeCallbacksAndMessages(null);
    }
}
