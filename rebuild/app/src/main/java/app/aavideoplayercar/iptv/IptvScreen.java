package app.aavideoplayercar.iptv;

import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import androidx.media3.ui.PlayerView;
import app.aavideoplayercar.R;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import app.aavideoplayercar.playback.PlaybackService;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class IptvScreen {
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4));
    private final Context context;
    private final MediaRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout view;
    private final EditText source, name, query;
    private final TextView status, playing;
    private final CheckBox favorites;
    private final ListView list;
    private final Button add, refresh, remove, previous, next, playbackType, fullscreenButton, guide;
    private EpgDialog guideDialog;
    private SeriesDialog seriesDialog;
    private Button seriesButton;
    private final java.util.function.Consumer<Boolean> fullscreenHost;
    private boolean fullscreen;
    private final PlayerView playerView;
    private final ListenableFuture<MediaController> connection;
    private MediaController controller;
    private List<IptvPlaylist> playlists = new ArrayList<>();
    private List<IptvChannel> channels = new ArrayList<>();
    private List<IptvChannel> pendingEpisodes;
    private String playlistId, pendingId, filter = "";
    private int page, generation;
    private boolean closed;
    private HttpDocument request;
    private PlaylistDocument documentRequest;
    private Future<?> task;
    private AlertDialog dialog;
    private final Player.Listener listener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events events) {
            if (closed || controller == null) return;
            MediaItem item = controller.getCurrentMediaItem();
            playing.setText(controller.getPlayerError() != null ? context.getString(R.string.iptv_play_error)
                : item == null ? context.getString(R.string.iptv_hint) : String.valueOf(item.mediaMetadata.title));
        }
    };
    public IptvScreen(Context context, Bundle state, Runnable chooseDocument, java.util.function.Consumer<Boolean> fullscreenHost) {
        this.context = context; this.fullscreenHost = fullscreenHost; repository = MediaRepository.get(context);
        view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL);
        name = field(R.string.iptv_name, "iptv.name"); source = field(R.string.iptv_url, "iptv.url");
        LinearLayout imports = row(); add = button(R.string.iptv_import, () -> fetch(source.getText().toString(), name.getText().toString(), true));
        imports.addView(add, weight()); imports.addView(button(R.string.iptv_playlists, () -> { playlistId = null; page = 0; load(); }), weight());
        LinearLayout sourceActions = row(); sourceActions.addView(button(R.string.iptv_choose_file, chooseDocument), weight()); sourceActions.addView(button(R.string.xtream_open, this::accountDialog), weight());
        LinearLayout actions = row();
        refresh = button(R.string.iptv_refresh, () -> {
            String selected = playlistId; int version = generation; busy();
            repository.worker.execute(() -> {
                try {
                    IptvPlaylist playlist = repository.database.iptv().playlist(selected);
                    main.post(() -> { if (!closed && generation == version) {
                        if (playlist == null) { playlistId = null; page = 0; load(); } else fetch(playlist.url, playlist.title, false);
                    } });
                } catch (Exception error) { fail(version); }
            });
        });
        remove = button(R.string.iptv_remove, this::confirmRemove); actions.addView(refresh, weight()); actions.addView(remove, weight());
        LinearLayout channelActions = row();
        playbackType = button(R.string.iptv_playback_type, this::choosePlaybackType); channelActions.addView(playbackType, weight());
        guide = button(R.string.epg_open, this::chooseGuide); channelActions.addView(guide, weight());
        seriesButton = button(R.string.series_open, () -> { if (seriesDialog != null) seriesDialog.close(); seriesDialog = new SeriesDialog(context, playlistId, null, this::playEpisodes); }); seriesButton.setTag("iptv.series"); channelActions.addView(seriesButton, weight());
        LinearLayout searching = row(); query = new EditText(context); query.setSingleLine(); query.setHint(R.string.iptv_search_hint); query.setTag("iptv.query"); searching.addView(query, weight());
        searching.addView(button(R.string.radio_search, () -> { filter = query.getText().toString().trim(); page = 0; load(); }));
        favorites = new CheckBox(context); favorites.setText(R.string.iptv_favorites); view.addView(favorites);
        status = new TextView(context); status.setTag("iptv.status"); view.addView(status);
        list = new ListView(context); list.setTag("iptv.list"); view.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, row, index, id) -> { if (playlistId == null) { playlistId = playlists.get(index).id; page = 0; load(); } else play(channels.get(index).id); });
        list.setOnItemLongClickListener((parent, row, index, id) -> {
            if (playlistId == null) return false;
            String selected = channels.get(index).id; int version = generation;
            repository.worker.execute(() -> { try { repository.database.iptv().toggleFavorite(selected); main.post(() -> { if (!closed && version == generation) load(); }); } catch (Exception error) { fail(version); } }); return true;
        });
        LinearLayout pages = row(); previous = button(R.string.radio_previous, () -> { if (page > 0) { page--; load(); } }); next = button(R.string.radio_next, () -> { page++; load(); });
        pages.addView(previous, weight()); pages.addView(next, weight());
        playing = new TextView(context); playing.setText(R.string.iptv_hint); view.addView(playing);
        fullscreenButton = button(R.string.iptv_fullscreen, () -> setFullscreen(!fullscreen)); fullscreenButton.setTag("iptv.fullscreen"); view.addView(fullscreenButton);
        playerView = new PlayerView(context); playerView.setControllerShowTimeoutMs(0);
        playerView.setTag("iptv.player");
        view.addView(playerView, new LinearLayout.LayoutParams(-1, Math.round(170 * context.getResources().getDisplayMetrics().density)));
        if (state != null) {
            name.setText(state.getString("name", "")); source.setText(state.getString("url", "")); query.setText(state.getString("query", ""));
            filter = state.getString("filter", ""); playlistId = state.getString("playlist"); page = Math.max(0, state.getInt("page")); favorites.setChecked(state.getBoolean("favorites"));
        }
        favorites.setOnCheckedChangeListener((button, checked) -> { page = 0; load(); });
        connection = new MediaController.Builder(context, new SessionToken(context, new ComponentName(context, PlaybackService.class))).buildAsync();
        connection.addListener(() -> { if (!closed) {
            try { controller = connection.get(); controller.addListener(listener); playerView.setPlayer(controller); if (pendingEpisodes != null) { var queue = pendingEpisodes; pendingEpisodes = null; playEpisodes(queue); } else if (pendingId != null) { String id = pendingId; pendingId = null; play(id); } }
            catch (Exception error) { playing.setText(R.string.iptv_play_error); }
        } }, main::post); load();
        if (state != null && state.getBoolean("fullscreen")) setFullscreen(true);
        Bundle seriesState = state == null ? null : state.getBundle("series"); if (seriesState != null) seriesDialog = new SeriesDialog(context, seriesState.getString("playlist"), seriesState, this::playEpisodes);
        Bundle guideState = state == null ? null : state.getBundle("guide");
        if (guideState != null) guideDialog = new EpgDialog(context, guideState.getString("playlist"), guideState.getString("channel"), guideState.getString("title"), guideState);
    }
    private void setFullscreen(boolean enabled) {
        fullscreen = enabled;
        for (int i = 0; i < view.getChildCount(); i++) {
            android.view.View child = view.getChildAt(i);
            if (child != playerView && child != fullscreenButton) child.setVisibility(enabled ? android.view.View.GONE : android.view.View.VISIBLE);
        }
        playerView.setLayoutParams(enabled ? new LinearLayout.LayoutParams(-1, 0, 1)
            : new LinearLayout.LayoutParams(-1, Math.round(170 * context.getResources().getDisplayMetrics().density)));
        fullscreenButton.setText(enabled ? R.string.iptv_exit_fullscreen : R.string.iptv_fullscreen);
        fullscreenHost.accept(enabled);
    }
    private EditText field(int hint, String tag) { EditText field = new EditText(context); field.setHint(hint); field.setSingleLine(); field.setTag(tag); view.addView(field); return field; }
    private LinearLayout row() { LinearLayout row = new LinearLayout(context); view.addView(row); return row; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private Button button(int label, Runnable action) { Button button = new Button(context); button.setText(label); button.setAllCaps(false); button.setOnClickListener(v -> action.run()); return button; }
    private void busy() { status.setText(R.string.iptv_loading); list.setAdapter(null); add.setEnabled(false); refresh.setEnabled(false); remove.setEnabled(false); playbackType.setEnabled(false); guide.setEnabled(false); seriesButton.setEnabled(false); previous.setEnabled(false); next.setEnabled(false); }
    private void load() { load(0); }
    private void load(int skipped) {
        cancel(); int version = ++generation, requestedPage = page; String selected = playlistId, search = filter; boolean onlyFavorites = favorites.isChecked(); busy();
        repository.worker.execute(() -> {
            try {
                var dao = repository.database.iptv(); IptvPlaylist playlist = selected == null ? null : dao.playlist(selected);
                boolean hasAccount = playlist != null && repository.database.xtream().account(selected) != null;
                int count = playlist == null ? dao.playlistCount() : dao.count(selected, search, onlyFavorites);
                int effective = Math.min(requestedPage, Math.max(0, (count - 1) / 50));
                List<IptvPlaylist> roots = playlist == null ? dao.playlists(50, effective * 50) : new ArrayList<>();
                List<IptvChannel> rows = playlist == null ? new ArrayList<>() : dao.channels(selected, search, onlyFavorites, 50, effective * 50);
                main.post(() -> {
                    if (closed || version != generation) return;
                    playlistId = playlist == null ? null : playlist.id; page = effective; playlists = roots; channels = rows;
                    list.setAdapter(playlist == null ? new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, roots) : new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, rows));
                    add.setEnabled(true); refresh.setEnabled(playlist != null); remove.setEnabled(playlist != null); previous.setEnabled(page > 0); next.setEnabled((long)(page + 1) * 50 < count);
                    playbackType.setEnabled(!rows.isEmpty());
                    guide.setEnabled(!rows.isEmpty()); seriesButton.setEnabled(hasAccount);
                    status.setText(context.getString(R.string.iptv_page, page + 1, count) + (skipped > 0 ? context.getString(R.string.iptv_skipped, skipped) : ""));
                });
            } catch (Exception error) { fail(version); }
        });
    }
    private void fetch(String input, String title, boolean adding) {
        if (input.startsWith(XtreamClient.PREFIX)) { fetchAccount(input.substring(XtreamClient.PREFIX.length()), title, null, null, null); return; }
        String url;
        try { url = M3uParser.sourceUrl(input); if (title.trim().isEmpty() || title.trim().length() > 200) throw new IllegalArgumentException(); }
        catch (Exception invalid) { status.setText(R.string.iptv_invalid); return; }
        cancel(); int version = ++generation; busy(); HttpDocument client = new HttpDocument(); request = client;
        PlaylistDocument local = new PlaylistDocument(); documentRequest = local; boolean isDocument = "content".equalsIgnoreCase(android.net.Uri.parse(url).getScheme()); NETWORK.purge();
        try { task = NETWORK.submit(() -> {
            try {
                byte[] bytes; String resolved = url;
                if (isDocument) bytes = local.read(context.getContentResolver(), android.net.Uri.parse(url));
                else { var document = client.get(url, "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain"); bytes = document.bytes; resolved = document.url; }
                var parsed = M3uParser.parse(bytes, url, resolved, title);
                main.post(() -> { if (!closed && version == generation) repository.worker.execute(() -> {
                    try {
                        boolean saved = repository.database.iptv().replace(parsed.playlist, parsed.channels, adding);
                        main.post(() -> { if (!closed && version == generation) { playlistId = saved ? parsed.playlist.id : null; page = 0; load(parsed.skipped); } });
                    } catch (Exception error) { fail(version); }
                }); });
            } catch (Exception error) { fail(version, isDocument ? R.string.iptv_document_error : R.string.iptv_error); }
        }); } catch (RejectedExecutionException error) { fail(version); }
    }
    private void accountDialog() {
        LinearLayout fields = new LinearLayout(context); fields.setOrientation(LinearLayout.VERTICAL);
        EditText accountName = new EditText(context); accountName.setHint(R.string.iptv_name); accountName.setTag("xtream.name"); fields.addView(accountName);
        EditText server = new EditText(context); server.setHint(R.string.xtream_server); server.setTag("xtream.server"); fields.addView(server);
        EditText username = new EditText(context); username.setHint(R.string.xtream_user); username.setTag("xtream.user"); fields.addView(username);
        EditText password = new EditText(context); password.setHint(R.string.xtream_password); password.setTag("xtream.password"); fields.addView(password);
        for (EditText field : new EditText[]{accountName, server, username, password}) { field.setSingleLine(); field.setSaveEnabled(false); field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO); }
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        username.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        dialog = new AlertDialog.Builder(context).setTitle(R.string.xtream_title).setView(fields).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xtream_import, (d, which) -> {
                String secret = password.getText().toString(), user = username.getText().toString(); password.setText(""); username.setText("");
                fetchAccount(null, accountName.getText().toString(), server.getText().toString(), user, secret);
            }).show();
    }
    private void fetchAccount(String existingId, String title, String server, String username, String password) {
        cancel(); int version = ++generation; busy(); HttpDocument client = new HttpDocument(); request = client; NETWORK.purge();
        try { task = NETWORK.submit(() -> {
            try {
                XtreamAccount account = existingId == null ? XtreamClient.account(server, username, password) : repository.database.xtream().account(existingId);
                if (account == null) throw new java.io.IOException();
                var parsed = XtreamClient.load(client, account, title);
                main.post(() -> { if (!closed && version == generation) repository.worker.execute(() -> {
                    try {
                        boolean saved = repository.database.runInTransaction(() -> {
                            if (!repository.database.iptv().replace(parsed.playlist, parsed.channels, existingId == null)) return false;
                            repository.database.xtream().save(account); return true;
                        });
                        main.post(() -> { if (!closed && version == generation) { playlistId = saved ? parsed.playlist.id : null; page = 0; load(parsed.skipped); } });
                    } catch (Exception error) { fail(version, R.string.xtream_error); }
                }); });
            } catch (Exception error) { fail(version, R.string.xtream_error); }
        }); } catch (RejectedExecutionException error) { fail(version, R.string.xtream_error); }
    }
    private void fail(int version) { fail(version, R.string.iptv_error); }
    private void fail(int version, int message) { main.post(() -> { if (!closed && version == generation) { status.setText(message); add.setEnabled(true); refresh.setEnabled(playlistId != null); remove.setEnabled(playlistId != null); } }); }
    public void importDocument(android.net.Uri uri) {
        if (!"content".equalsIgnoreCase(uri.getScheme())) { status.setText(R.string.iptv_document_error); return; }
        source.setText(uri.toString());
        if (name.getText().toString().trim().isEmpty()) name.setText(R.string.iptv_local_playlist);
        fetch(uri.toString(), name.getText().toString(), true);
    }
    private void confirmRemove() {
        String selected = playlistId; int version = generation;
        dialog = new AlertDialog.Builder(context).setTitle(R.string.iptv_remove).setMessage(R.string.iptv_remove_message).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.iptv_remove, (d, which) -> { cancel(); busy(); repository.worker.execute(() -> {
                try { repository.database.iptv().delete(selected); main.post(() -> { if (!closed && version == generation) { playlistId = null; page = 0; load(); } }); }
                catch (Exception error) { fail(version); }
            }); }).show();
    }
    private void playEpisodes(List<IptvChannel> queue) {
        if (queue.isEmpty()) return; if (controller == null) { pendingEpisodes = new ArrayList<>(queue); return; } channels = queue; play(queue.get(0).id); load();
    }
    private void play(String id) {
        if (controller == null) { pendingId = id; return; }
        MediaItem current = controller.getCurrentMediaItem();
        IptvChannel selectedChannel = null;
        for (IptvChannel channel : channels) if (channel.id.equals(id)) selectedChannel = channel;
        boolean changedType = current != null && selectedChannel != null && !Objects.equals(current.mediaMetadata.mediaType, selectedChannel.toMediaItem().mediaMetadata.mediaType);
        if (current == null || !current.mediaId.equals(IptvChannel.PREFIX + id) || controller.getPlayerError() != null || changedType) {
            List<MediaItem> items = new ArrayList<>(); int selected = -1;
            for (IptvChannel channel : channels) { if (channel.id.equals(id)) selected = items.size(); items.add(new MediaItem.Builder().setMediaId(IptvChannel.PREFIX + channel.id).build()); }
            if (selected < 0) { selected = items.size(); items.add(new MediaItem.Builder().setMediaId(IptvChannel.PREFIX + id).build()); }
            controller.setMediaItems(items, selected, C.TIME_UNSET);
        }
        controller.prepare(); controller.play();
    }
    private void choosePlaybackType() {
        List<IptvChannel> pageChannels = new ArrayList<>(channels); int version = generation;
        String[] labels = new String[pageChannels.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = pageChannels.get(i).title;
        dialog = new AlertDialog.Builder(context).setTitle(R.string.iptv_select_channel)
            .setItems(labels, (d, index) -> { if (!closed && version == generation) editPlaybackType(pageChannels.get(index), version); })
            .setNegativeButton(android.R.string.cancel, null).show();
    }
    private void chooseGuide() {
        List<IptvChannel> pageChannels = new ArrayList<>(channels); int version = generation;
        String[] labels = new String[pageChannels.size()]; for (int i = 0; i < labels.length; i++) labels[i] = pageChannels.get(i).title;
        dialog = new AlertDialog.Builder(context).setTitle(R.string.iptv_select_channel).setItems(labels, (d, index) -> {
            if (closed || version != generation) return;
            IptvChannel channel = pageChannels.get(index);
            if (channel.tvgId.isEmpty()) { status.setText(R.string.epg_no_id); return; }
            if (guideDialog != null) guideDialog.close();
            guideDialog = new EpgDialog(context, channel.playlistId, channel.tvgId, channel.title, null);
        }).setNegativeButton(android.R.string.cancel, null).show();
    }
    private void editPlaybackType(IptvChannel channel, int version) {
        int[] mode = {channel.playbackMode};
        dialog = new AlertDialog.Builder(context).setTitle(channel.title)
            .setSingleChoiceItems(R.array.iptv_playback_types, channel.playbackMode, (d, which) -> mode[0] = which)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.iptv_save_type, (d, which) -> {
                if (closed || version != generation) return;
                busy(); repository.worker.execute(() -> {
                    try { repository.database.iptv().playbackMode(channel.id, mode[0]); main.post(() -> { if (!closed && version == generation) load(); }); }
                    catch (Exception error) { fail(version); }
                });
            }).show();
    }
    private void cancel() { if (request != null) request.cancel(); if (documentRequest != null) documentRequest.cancel(); if (task != null) task.cancel(true); }
    public boolean onBack() { if (fullscreen) { setFullscreen(false); return true; } if (playlistId == null) return false; playlistId = null; page = 0; load(); return true; }
    public LinearLayout getView() { return view; }
    public Bundle saveState() { Bundle state = new Bundle(); if (seriesDialog != null) state.putBundle("series", seriesDialog.saveState()); if (guideDialog != null) state.putBundle("guide", guideDialog.saveState()); state.putBoolean("fullscreen", fullscreen); state.putString("name", name.getText().toString()); state.putString("url", source.getText().toString()); state.putString("query", query.getText().toString()); state.putString("filter", filter); state.putString("playlist", playlistId); state.putInt("page", page); state.putBoolean("favorites", favorites.isChecked()); return state; }
    public void close() { closed = true; if (seriesDialog != null) seriesDialog.close(); if (guideDialog != null) guideDialog.close(); if (fullscreen) setFullscreen(false); cancel(); if (dialog != null) dialog.dismiss(); if (controller != null) controller.removeListener(listener); playerView.setPlayer(null); MediaController.releaseFuture(connection); main.removeCallbacksAndMessages(null); }
}
