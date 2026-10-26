package app.aavideoplayercar.iptv;

import android.app.AlertDialog;
import android.content.Context;
import android.os.*;
import android.widget.*;
import app.aavideoplayercar.R;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Paged cached show/season/episode browser. No provider work runs on the UI thread. */
public final class SeriesDialog {
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
    private final Context context;
    private final String playlist;
    private final MediaRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Consumer<List<IptvChannel>> play;
    private final AlertDialog dialog;
    private final EditText search;
    private final TextView status;
    private final ListView list;
    private final Spinner seasonPicker;
    private final Button refresh, previous, next, up, find;
    private List<IptvSeries> shows = new ArrayList<>();
    private List<IptvChannel> episodes = new ArrayList<>();
    private List<Integer> seasons = new ArrayList<>();
    private String showId, filter = "";
    private int season = -1, page, generation, totalCount;
    private volatile boolean closed;
    private boolean binding;
    private HttpDocument request;
    private Future<?> task;
    public SeriesDialog(Context context, String playlist, Bundle state, Consumer<List<IptvChannel>> play) {
        this.context = context; this.playlist = playlist; this.play = play; repository = MediaRepository.get(context);
        LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL);
        search = new EditText(context); search.setSingleLine(); search.setHint(R.string.series_search); search.setTag("series.search"); view.addView(search);
        LinearLayout actions = new LinearLayout(context); view.addView(actions);
        find = button(R.string.radio_search, () -> { filter = search.getText().toString().trim(); page = 0; load(); }); find.setTag("series.find"); actions.addView(find, weight());
        refresh = button(R.string.series_refresh, this::refresh); refresh.setTag("series.refresh"); actions.addView(refresh, weight());
        up = button(R.string.series_up, () -> { showId = null; season = -1; page = 0; load(); }); up.setTag("series.up"); view.addView(up);
        seasonPicker = new Spinner(context); seasonPicker.setTag("series.season"); view.addView(seasonPicker);
        status = new TextView(context); status.setTag("series.status"); view.addView(status);
        list = new ListView(context); list.setTag("series.list"); view.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout pages = new LinearLayout(context); view.addView(pages);
        previous = button(R.string.radio_previous, () -> { page--; load(); }); previous.setTag("series.previous"); pages.addView(previous, weight());
        next = button(R.string.radio_next, () -> { page++; load(); }); next.setTag("series.next"); pages.addView(next, weight());
        list.setOnItemClickListener((parent, child, index, id) -> {
            if (showId == null) { showId = shows.get(index).id; season = -1; page = 0; load(); }
            else { List<IptvChannel> queue = new ArrayList<>(episodes.subList(index, episodes.size())); close(); play.accept(queue); }
        });
        list.setOnItemLongClickListener((parent, child, index, id) -> {
            if (showId == null) return false;
            String selected = episodes.get(index).id; int version = generation;
            repository.worker.execute(() -> { try { repository.database.iptv().toggleFavorite(selected); main.post(() -> { if (current(version)) load(); }); } catch (Exception e) { fail(version); } }); return true;
        });
        seasonPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View v, int position, long id) {
                if (!binding && position < seasons.size() && season != seasons.get(position)) { season = seasons.get(position); page = 0; load(); }
            }
        });
        if (state != null) { showId = state.getString("show"); filter = state.getString("filter", ""); search.setText(state.getString("search", "")); season = state.getInt("season", -1); page = Math.max(0, state.getInt("page")); }
        dialog = new AlertDialog.Builder(context).setTitle(R.string.series_open).setView(view).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnDismissListener(d -> close()); dialog.show(); dialog.getWindow().setLayout(-1, -1); load();
    }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private Button button(int label, Runnable action) { Button b = new Button(context); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b; }
    private boolean current(int version) { return !closed && generation == version; }
    private void busy() { status.setText(R.string.iptv_loading); list.setEnabled(false); for (Button b : new Button[]{refresh, previous, next, up, find}) b.setEnabled(false); seasonPicker.setEnabled(false); }
    private void load() {
        cancel(); int version = ++generation, requested = page, selectedSeason = season; String selected = showId, query = filter; busy(); list.setAdapter(null);
        repository.worker.execute(() -> { try {
            var dao = repository.database.iptv(); IptvSeries show = selected == null ? null : dao.series(selected);
            List<Integer> choices = show == null ? new ArrayList<>() : dao.seasons(selected);
            int effectiveSeason = choices.contains(selectedSeason) ? selectedSeason : choices.isEmpty() ? -1 : choices.get(0);
            int count = show == null ? dao.seriesCount(playlist, query) : dao.episodeCount(selected, effectiveSeason);
            int effective = Math.min(requested, Math.max(0, (count - 1) / 50));
            List<IptvSeries> roots = show == null ? dao.seriesPage(playlist, query, 50, effective * 50) : new ArrayList<>();
            List<IptvChannel> rows = show == null ? new ArrayList<>() : dao.episodePage(selected, effectiveSeason, 50, effective * 50);
            main.post(() -> { if (!current(version)) return;
                showId = show == null ? null : show.id; season = effectiveSeason; page = effective; totalCount = count; shows = roots; episodes = rows; seasons = choices;
                list.setAdapter(show == null ? new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, roots) : new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, rows)); list.setEnabled(true);
                binding = true; List<String> labels = new ArrayList<>(); for (int value : choices) labels.add(context.getString(R.string.series_season, value));
                var adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); seasonPicker.setAdapter(adapter);
                if (season >= 0) seasonPicker.setSelection(choices.indexOf(season)); binding = false;
                seasonPicker.setVisibility(show == null ? android.view.View.GONE : android.view.View.VISIBLE); seasonPicker.setEnabled(!choices.isEmpty());
                up.setEnabled(show != null); find.setEnabled(show == null); search.setEnabled(show == null); refresh.setEnabled(true); previous.setEnabled(page > 0); next.setEnabled((long)(page + 1) * 50 < count);
                status.setText(context.getString(R.string.series_status, show == null ? "" : show.title + "\n", context.getString(R.string.iptv_page, page + 1, count), count == 0 ? "\n" + context.getString(R.string.series_hint) : ""));
            });
        } catch (Exception e) { fail(version); } });
    }
    private void refresh() {
        cancel(); int version = ++generation; String selected = showId; busy(); request = new HttpDocument(); HttpDocument client = request; NETWORK.purge();
        try { task = NETWORK.submit(() -> { try {
            XtreamAccount account = repository.database.xtream().account(playlist); if (account == null) throw new java.io.IOException();
            IptvSeries show = selected == null ? null : repository.database.iptv().series(selected);
            if (selected != null && show == null) throw new java.io.IOException();
            var catalogue = show == null ? XtreamSeries.catalogue(client, account) : null;
            var detail = show == null ? null : XtreamSeries.episodes(client, account, new XtreamSeries.Show(show.providerId, show.title, show.category));
            if ((catalogue != null && catalogue.items.isEmpty() && catalogue.skipped > 0) || (detail != null && detail.items.isEmpty() && detail.skipped > 0)) throw new java.io.IOException();
            main.post(() -> { if (!current(version)) return; repository.worker.execute(() -> { try {
                var dao = repository.database.iptv();
                if (catalogue != null) {
                    List<IptvSeries> rows = new ArrayList<>(); for (var item : catalogue.items) {
                        IptvSeries row = new IptvSeries(); row.playlistId = playlist; row.providerId = item.providerId; row.title = item.title; row.category = item.category;
                        row.id = UUID.nameUUIDFromBytes((playlist + "\nshow/" + item.providerId).getBytes(StandardCharsets.UTF_8)).toString(); rows.add(row);
                    } dao.replaceSeries(playlist, rows);
                } else {
                    List<IptvChannel> rows = new ArrayList<>(); List<IptvSeriesEpisode> members = new ArrayList<>();
                    for (var item : detail.items) { rows.add(item.channel); var member = new IptvSeriesEpisode(); member.channelId = item.channel.id; member.seriesId = selected; member.season = item.season; member.number = item.number; members.add(member); }
                    dao.replaceEpisodes(selected, rows, members, System.currentTimeMillis());
                }
                main.post(() -> { if (current(version)) { page = 0; load(); } });
            } catch (Exception e) { fail(version); } }); });
        } catch (Exception e) { fail(version); } }); } catch (RejectedExecutionException e) { fail(version); }
    }
    private void fail(int version) { main.post(() -> { if (current(version)) {
        status.setText(R.string.series_error); refresh.setEnabled(true); up.setEnabled(showId != null); list.setEnabled(true);
        find.setEnabled(showId == null); search.setEnabled(showId == null);
        if (list.getAdapter() != null) { previous.setEnabled(page > 0); next.setEnabled((long)(page + 1) * 50 < totalCount); seasonPicker.setEnabled(!seasons.isEmpty()); }
    } }); }
    private void cancel() { if (request != null) request.cancel(); if (task != null) task.cancel(true); }
    public Bundle saveState() { if (closed) return null; Bundle b = new Bundle(); b.putString("playlist", playlist); b.putString("show", showId); b.putString("filter", filter); b.putString("search", search.getText().toString()); b.putInt("page", page); b.putInt("season", season); return b; }
    public void close() { if (closed) return; closed = true; cancel(); main.removeCallbacksAndMessages(null); dialog.dismiss(); }
}
