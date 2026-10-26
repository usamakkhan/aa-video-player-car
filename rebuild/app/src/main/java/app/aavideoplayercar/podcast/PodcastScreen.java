package app.aavideoplayercar.podcast;

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
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;
import app.aavideoplayercar.R;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import app.aavideoplayercar.playback.PlaybackService;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PodcastScreen {
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4));
    private static final int PAGE_SIZE = 50;
    private final Context context;
    private final PodcastDownloadUi downloadUi;
    @FunctionalInterface public interface DirectorySource {
        List<PodcastDirectory.Show> search(String name, String country, HttpDocument request) throws java.io.IOException;
    }
    private final DirectorySource directory;
    private final MediaRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout view;
    private final EditText url;
    private final TextView status, playing;
    private final Button subscribe, refresh, remove, previous, next;
    private final ListView list;
    private final PlayerView playerView;
    private final ListenableFuture<MediaController> connection;
    private MediaController controller;
    private String pendingId;
    private HttpDocument request;
    private Future<?> task;
    private AlertDialog dialog;
    private String feedId;
    private int page, generation;
    private boolean closed, discovery;
    private String searchName = "", searchCountry = "US";
    private List<PodcastDirectory.Show> discovered = new ArrayList<>();
    private List<PodcastFeed> feeds = new ArrayList<>();
    private List<PodcastEpisode> episodes = new ArrayList<>();
    private final Player.Listener listener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events events) {
            if (closed || controller == null) return;
            if (controller.getPlayerError() != null) { playing.setText(R.string.podcast_play_error); return; }
            MediaItem item = controller.getCurrentMediaItem();
            playing.setText(item == null ? context.getString(R.string.podcast_idle) : context.getString(R.string.podcast_now_playing, item.mediaMetadata.title));
        }
    };
    public PodcastScreen(Context context, Bundle state) {
        this(context, state, PodcastDirectory::search);
    }
    public PodcastScreen(Context context, Bundle state, DirectorySource directory) {
        this.directory = java.util.Objects.requireNonNull(directory);
        this.context = context; repository = MediaRepository.get(context);
        downloadUi = new PodcastDownloadUi(context, id -> play(id, true));
        view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL);
        url = new EditText(context); url.setSingleLine(); url.setHint(R.string.podcast_url); url.setTag("podcast.url");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); view.addView(url);
        status = label(); status.setTag("podcast.status");
        LinearLayout add = new LinearLayout(context);
        subscribe = button(R.string.podcast_subscribe, () -> fetch(url.getText().toString(), true));
        add.addView(subscribe, new LinearLayout.LayoutParams(0, -2, 1));
        add.addView(button(R.string.podcast_subscriptions, () -> { cancelNetwork(); feedId = null; page = 0; load(); }), new LinearLayout.LayoutParams(0, -2, 1)); view.addView(add);
        view.addView(button(R.string.podcast_discover, this::showDirectorySearch));
        view.addView(button(R.string.podcast_downloads, downloadUi::showLibrary));
        LinearLayout actions = new LinearLayout(context);
        refresh = button(R.string.podcast_refresh, () -> {
            String selected = feedId; int version = generation; busy();
            repository.worker.execute(() -> {
                try {
                    PodcastFeed feed = repository.database.podcasts().feed(selected);
                    main.post(() -> { if (!closed && version == generation) { if (feed != null) fetch(feed.url, false); else { feedId = null; page = 0; load(); } } });
                } catch (Exception error) { fail(version, R.string.podcast_storage_error); }
            });
        });
        remove = button(R.string.podcast_remove, this::confirmRemove);
        actions.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1)); actions.addView(remove, new LinearLayout.LayoutParams(0, -2, 1)); view.addView(actions);
        view.addView(status);
        list = new ListView(context); list.setTag("podcast.list"); view.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, row, index, id) -> {
            if (discovery) chooseShow(discovered.get(index));
            else if (feedId == null) { feedId = feeds.get(index).id; page = 0; load(); }
            else play(episodes.get(index).id);
        });
        list.setOnItemLongClickListener((parent, row, index, id) -> {
            if (discovery || feedId == null) return false;
            downloadUi.showEpisode(episodes.get(index).id); return true;
        });
        LinearLayout pages = new LinearLayout(context);
        previous = button(R.string.radio_previous, () -> { if (page > 0) { page--; load(); } });
        next = button(R.string.radio_next, () -> { page++; load(); });
        pages.addView(previous, new LinearLayout.LayoutParams(0, -2, 1)); pages.addView(next, new LinearLayout.LayoutParams(0, -2, 1)); view.addView(pages);
        playing = label(); playing.setText(R.string.podcast_idle); view.addView(playing);
        TextView downloadHint = label(); downloadHint.setText(R.string.podcast_download_hint); view.addView(downloadHint);
        playerView = new PlayerView(context); playerView.setControllerShowTimeoutMs(0);
        view.addView(playerView, new LinearLayout.LayoutParams(-1, Math.round(150 * context.getResources().getDisplayMetrics().density)));
        String region = java.util.Locale.getDefault().getCountry();
        if (region.matches("[A-Z]{2}")) searchCountry = region;
        if (state != null) {
            url.setText(state.getString("url", "")); feedId = state.getString("feedId"); page = Math.max(0, state.getInt("page", 0));
            searchName = state.getString("searchName", ""); searchCountry = state.getString("searchCountry", searchCountry); discovery = state.getBoolean("discovery", false);
        }
        connection = new MediaController.Builder(context, new SessionToken(context, new ComponentName(context, PlaybackService.class))).buildAsync();
        connection.addListener(() -> {
            if (closed) return;
            try { controller = connection.get(); controller.addListener(listener); playerView.setPlayer(controller); if (pendingId != null) { String id = pendingId; pendingId = null; play(id); } }
            catch (Exception error) { playing.setText(R.string.podcast_connect_error); }
        }, main::post);
        if (discovery) searchDirectory(); else load();
        if (state != null) downloadUi.restoreState(state.getBundle("downloads"));
        if (state != null && state.getBoolean("searchDialog", false)) {
            showDirectorySearch();
            ((EditText)dialog.getWindow().getDecorView().findViewWithTag("podcast.search.name")).setText(state.getString("draftName", ""));
            ((EditText)dialog.getWindow().getDecorView().findViewWithTag("podcast.search.country")).setText(state.getString("draftCountry", searchCountry));
        }
    }
    private TextView label() { TextView label = new TextView(context); label.setTextColor(Color.WHITE); return label; }
    private Button button(int text, Runnable action) { Button b = new Button(context); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b; }
    private void busy() {
        status.setText(R.string.podcast_loading); subscribe.setEnabled(false); refresh.setEnabled(false); remove.setEnabled(false);
        previous.setEnabled(false); next.setEnabled(false); list.setEnabled(false); list.setAdapter(null);
    }
    private void load() {
        discovery = false;
        int version = ++generation, selectedPage = page; String selected = feedId; busy();
        repository.worker.execute(() -> {
            try {
                var dao = repository.database.podcasts();
                PodcastFeed feed = selected == null ? null : dao.feed(selected);
                int count = feed == null ? dao.feedCount() : dao.episodeCount(selected);
                int effectivePage = Math.min(selectedPage, Math.max(0, (count - 1) / PAGE_SIZE));
                List<PodcastFeed> loadedFeeds = feed == null ? dao.feeds(PAGE_SIZE, effectivePage * PAGE_SIZE) : new ArrayList<>();
                List<PodcastEpisode> loadedEpisodes = feed == null ? new ArrayList<>() : dao.episodes(selected, PAGE_SIZE, effectivePage * PAGE_SIZE);
                main.post(() -> {
                    if (closed || version != generation) return;
                    feedId = feed == null ? null : feed.id; page = effectivePage; feeds = loadedFeeds; episodes = loadedEpisodes;
                    list.setAdapter(feed == null ? new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, feeds) : new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, episodes));
                    list.setEnabled(true); subscribe.setEnabled(true); refresh.setEnabled(feed != null); remove.setEnabled(feed != null);
                    previous.setEnabled(page > 0); next.setEnabled((long)(page + 1) * PAGE_SIZE < count);
                    status.setText(count == 0 ? context.getString(feed == null ? R.string.podcast_empty : R.string.podcast_no_episodes)
                        : context.getString(R.string.podcast_page, feed == null ? context.getString(R.string.podcast_subscriptions) : feed.title, page + 1, count));
                });
            } catch (Exception error) { fail(version, R.string.podcast_storage_error); }
        });
    }
    private void fetch(String input, boolean adding) {
        String source;
        try { source = HttpDocument.url(input); }
        catch (Exception error) { status.setText(R.string.podcast_invalid_url); return; }
        cancelNetwork(); discovery = false; int version = ++generation; busy(); HttpDocument client = new HttpDocument(); request = client;
        NETWORK.purge();
        try {
            task = NETWORK.submit(() -> {
                try {
                    HttpDocument.Result document = client.get(source);
                    FeedParser.Result result = FeedParser.parse(document.bytes, source, document.url);
                    main.post(() -> {
                        if (closed || version != generation) return;
                        repository.worker.execute(() -> {
                            try {
                                boolean saved = repository.database.podcasts().importFeed(result.feed, result.episodes, adding);
                                main.post(() -> { if (!closed && version == generation) { feedId = saved ? result.feed.id : null; page = 0; load(); } });
                            } catch (Exception error) { fail(version, R.string.podcast_storage_error); }
                        });
                    });
                } catch (Exception error) { fail(version, R.string.podcast_error); }
            });
        } catch (RejectedExecutionException error) { fail(version, R.string.podcast_error); }
    }
    private void fail(int version, int message) { main.post(() -> {
        if (closed || version != generation) return;
        status.setText(message); subscribe.setEnabled(true); refresh.setEnabled(feedId != null); remove.setEnabled(feedId != null);
        previous.setEnabled(page > 0); list.setEnabled(true);
    }); }
    private void confirmRemove() {
        String selected = feedId; int version = generation;
        dialog = new AlertDialog.Builder(context).setTitle(R.string.podcast_remove).setMessage(R.string.podcast_remove_message)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.podcast_remove, (d, which) -> {
                cancelNetwork(); busy();
                repository.worker.execute(() -> {
                    try {
                        repository.database.podcasts().unsubscribe(selected);
                        PodcastDownloads.get(context).pruneOrphans();
                        main.post(() -> { if (!closed && version == generation) { feedId = null; page = 0; load(); } });
                    } catch (Exception error) { fail(version, R.string.podcast_storage_error); }
                });
            }).show();
    }
    private void play(String id) {
        play(id, false);
    }
    private void play(String id, boolean refreshSource) {
        if (controller == null) { pendingId = id; return; }
        MediaItem current = controller.getCurrentMediaItem();
        boolean same = current != null && current.mediaId.equals(PodcastEpisode.PREFIX + id);
        if (!same || refreshSource || controller.getPlayerError() != null) {
            List<MediaItem> queue = new ArrayList<>(); int selected = -1;
            for (PodcastEpisode episode : episodes) { if (episode.id.equals(id)) selected = queue.size(); queue.add(new MediaItem.Builder().setMediaId(PodcastEpisode.PREFIX + episode.id).build()); }
            if (selected < 0) { selected = queue.size(); queue.add(new MediaItem.Builder().setMediaId(PodcastEpisode.PREFIX + id).build()); }
            controller.setMediaItems(queue, selected, same ? controller.getCurrentPosition() : androidx.media3.common.C.TIME_UNSET);
        }
        controller.prepare(); controller.play();
    }
    private void showDirectorySearch() {
        LinearLayout fields = new LinearLayout(context); fields.setOrientation(LinearLayout.VERTICAL);
        EditText name = new EditText(context); name.setSingleLine(); name.setHint(R.string.podcast_search_name); name.setTag("podcast.search.name");
        String typed = url.getText().toString().trim();
        name.setText(searchName.isEmpty() && !typed.startsWith("http") ? typed : searchName);
        EditText country = new EditText(context); country.setSingleLine(); country.setHint(R.string.podcast_search_country); country.setText(searchCountry); country.setTag("podcast.search.country");
        fields.addView(name); fields.addView(country);
        dialog = new AlertDialog.Builder(context).setTitle(R.string.podcast_discover).setView(fields)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.radio_search, null).show();
        AlertDialog searchDialog = dialog;
        searchDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String term = name.getText().toString().trim();
            String region = country.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            try { PodcastDirectory.requestUrl(term, region); }
            catch (Exception error) {
                EditText invalid = term.isEmpty() || term.length() > 200 ? name : country;
                invalid.setError(context.getString(R.string.podcast_directory_invalid)); invalid.requestFocus(); return;
            }
            searchName = term; searchCountry = region; searchDialog.dismiss(); searchDirectory();
        });
    }
    private void searchDirectory() {
        try { PodcastDirectory.requestUrl(searchName, searchCountry); }
        catch (Exception error) { status.setText(R.string.podcast_directory_invalid); return; }
        cancelNetwork(); discovery = true; feedId = null; page = 0;
        int version = ++generation; busy(); HttpDocument client = new HttpDocument(); request = client;
        String name = searchName, country = searchCountry;
        NETWORK.purge();
        try {
            task = NETWORK.submit(() -> {
                try {
                    List<PodcastDirectory.Show> results = directory.search(name, country, client);
                    main.post(() -> {
                        if (closed || version != generation) return;
                        discovered = results;
                        list.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, results));
                        list.setEnabled(true); subscribe.setEnabled(true);
                        status.setText(results.isEmpty() ? context.getString(R.string.podcast_directory_empty)
                            : context.getString(R.string.podcast_directory, country, results.size()));
                    });
                } catch (Exception error) { fail(version, R.string.podcast_directory_error); }
            });
        } catch (RejectedExecutionException error) { fail(version, R.string.podcast_directory_error); }
    }
    private void chooseShow(PodcastDirectory.Show show) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle(show.title).setMessage(show.publisher + "\n" + show.feedUrl)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.podcast_subscribe, (d, which) -> {
                url.setText(show.feedUrl); fetch(show.feedUrl, true);
            });
        if (!show.storeUrl.isEmpty()) builder.setNeutralButton(R.string.podcast_view_store, (d, which) -> {
            try { context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(show.storeUrl))); }
            catch (android.content.ActivityNotFoundException error) { status.setText(R.string.podcast_directory_error); }
        });
        dialog = builder.show();
    }
    private void cancelNetwork() { if (request != null) request.cancel(); if (task != null) task.cancel(true); }
    public boolean onBack() { if (feedId == null && !discovery) return false; cancelNetwork(); feedId = null; page = 0; load(); return true; }
    public LinearLayout getView() { return view; }
    public Bundle saveState() {
        Bundle state = new Bundle(); state.putString("url", url.getText().toString()); state.putString("feedId", feedId);
        state.putBundle("downloads", downloadUi.saveState());
        state.putInt("page", page); state.putString("searchName", searchName); state.putString("searchCountry", searchCountry); state.putBoolean("discovery", discovery);
        if (dialog != null && dialog.isShowing()) {
            EditText draftName = dialog.getWindow().getDecorView().findViewWithTag("podcast.search.name");
            EditText draftCountry = dialog.getWindow().getDecorView().findViewWithTag("podcast.search.country");
            if (draftName != null && draftCountry != null) {
                state.putBoolean("searchDialog", true);
                state.putString("draftName", draftName.getText().toString()); state.putString("draftCountry", draftCountry.getText().toString());
            }
        }
        return state;
    }
    public void close() {
        closed = true; downloadUi.close(); cancelNetwork(); if (dialog != null) dialog.dismiss();
        if (controller != null) controller.removeListener(listener); playerView.setPlayer(null);
        MediaController.releaseFuture(connection); main.removeCallbacksAndMessages(null);
    }
}
