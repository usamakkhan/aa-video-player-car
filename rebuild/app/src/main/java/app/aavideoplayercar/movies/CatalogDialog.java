package app.aavideoplayercar.movies;
import android.app.AlertDialog;
import android.content.Context;
import android.os.*;
import android.widget.*;
import app.aavideoplayercar.R;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import java.util.*;
import java.util.concurrent.*;

/** Bounded provider batches with local 50-item pages; only explicit actions trigger requests. */
public final class CatalogDialog {
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String addonId;
    private final Runnable dismissed;
    private final AlertDialog dialog;
    private AlertDialog filtersDialog; private TitleDialog details; private Bundle restoredTitle;
    private final ListView list;
    private final TextView status;
    private final Button filters, refresh, previous, next;
    private AddonClient.Manifest manifest;
    private String endpoint;
    private boolean cached;
    private AddonClient.Catalog catalog;
    private List<AddonClient.Title> titles = List.of();
    private Map<String, String> values = new LinkedHashMap<>();
    private int page, offset, generation, consumed;
    private final ArrayList<Integer> history = new ArrayList<>();
    private boolean closed, loading;
    private Future<?> task;
    private HttpDocument request;
    private Bundle restore;
    public CatalogDialog(Context context, String addonId, Bundle restore, Runnable dismissed) {
        this.context = context; this.addonId = addonId; this.dismissed = dismissed; this.restore = restore; restoredTitle = restore == null ? null : restore.getBundle("title");
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actions = new LinearLayout(context); content.addView(actions);
        actions.addView(button(R.string.movies_catalogs, () -> { cancel(); generation++; catalog = null; titles = List.of(); history.clear(); page = offset = 0; show(); }), weight());
        filters = button(R.string.movies_filters, this::filters); actions.addView(filters, weight());
        refresh = button(R.string.movies_load, () -> { if (manifest == null) loadManifest(); else if (catalog != null) fetch(offset, page); }); actions.addView(refresh, weight());
        status = new TextView(context); status.setTag("movies.catalog.status"); content.addView(status);
        list = new ListView(context); list.setTag("movies.catalog.list"); content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout pages = new LinearLayout(context); content.addView(pages);
        previous = button(R.string.radio_previous, () -> { if (page > 0) { page--; show(); } else if (!history.isEmpty()) fetch(history.get(history.size() - 1), Integer.MAX_VALUE); }); pages.addView(previous, weight());
        next = button(R.string.radio_next, () -> { if ((page + 1) * 50 < titles.size()) { page++; show(); } else fetch(offset + consumed, 0); }); pages.addView(next, weight());
        list.setOnItemClickListener((p,v,index,id) -> {
            if (catalog == null) { catalog = manifest.catalogs.get(index); values = new LinkedHashMap<>(); titles = List.of(); history.clear(); page = offset = 0; show(); if (catalog.required.isEmpty() || catalog.required.equals(Set.of("skip"))) fetch(0, 0); else filters(); }
            else detail(titles.get(page * 50 + index));
        });
        dialog = new AlertDialog.Builder(context).setTitle(R.string.movies_catalogs).setView(content).setPositiveButton(R.string.news_close, null).create();
        dialog.setOnDismissListener(d -> dispose());
        dialog.show(); dialog.getWindow().setLayout(-1, -1); loadManifest();
    }
    private Button button(int label, Runnable action) { Button b = new Button(context); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private void cancel() { if (request != null) request.cancel(); if (task != null) task.cancel(true); loading = false; }
    private boolean current(int token) { return !closed && token == generation; }
    private void busy() { loading = true; list.setEnabled(false); filters.setEnabled(false); refresh.setEnabled(false); previous.setEnabled(false); next.setEnabled(false); status.setText(R.string.movies_loading); }
    private void show() {
        loading = false; list.setEnabled(manifest != null); filters.setEnabled(catalog != null); refresh.setEnabled(true);
        List<String> labels = new ArrayList<>();
        if (catalog == null) { if (manifest != null) for (var c : manifest.catalogs) labels.add(c.name + " · " + c.type); }
        else { page = Math.min(page, Math.max(0, (titles.size() - 1) / 50)); for (int i = page * 50; i < Math.min(titles.size(), (page + 1) * 50); i++) labels.add(titles.get(i).name); }
        list.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels));
        previous.setEnabled(catalog != null && (page > 0 || !history.isEmpty()));
        next.setEnabled(catalog != null && ((page + 1) * 50 < titles.size() || (!titles.isEmpty() && catalog.extras.contains("skip") && offset + consumed <= 9999999)));
        String summary = catalog == null ? context.getString(R.string.movies_catalog_count, labels.size()) : context.getString(R.string.movies_batch, catalog.name, offset + page * 50, labels.size());
        status.setText(cached ? context.getString(R.string.movies_cached, summary) : summary);
    }
    private void fail(int token) { main.post(() -> { if (current(token)) { show(); status.setText(R.string.movies_catalog_error); } }); }
    private void loadManifest() {
        cancel(); int token = ++generation; busy(); var repository = MediaRepository.get(context);
        repository.worker.execute(() -> { try {
            MovieAddon addon = repository.database.movieAddons().get(addonId); if (addon == null) throw new java.io.IOException(); String url = AddonRegistration.endpoint(addon);
            main.post(() -> { if (!current(token)) return; HttpDocument client = new HttpDocument(); request = client; NETWORK.purge();
                try { task = NETWORK.submit(() -> { try { var loaded = AddonClient.manifest(client, url); repository.worker.execute(() -> {
                    try { MovieCache.save(repository.database.movieCache(), addonId, MovieCache.key(addonId, url, "manifest", ""), AddonClient.manifestSnapshot(loaded)); } catch (Exception ignored) { }
                    main.post(() -> applyManifest(token, url, loaded, false));
                }); } catch (Exception error) { repository.worker.execute(() -> {
                    try { String payload = MovieCache.get(repository.database.movieCache(), addonId, MovieCache.key(addonId, url, "manifest", "")); if (payload == null) throw new java.io.IOException();
                        var loaded = AddonClient.cachedManifest(payload, url); main.post(() -> applyManifest(token, url, loaded, true));
                    } catch (Exception unavailable) { fail(token); }
                }); } }); } catch (RejectedExecutionException error) { fail(token); }
            });
        } catch (Exception error) { fail(token); } });
    }
    private void applyManifest(int token, String url, AddonClient.Manifest loaded, boolean fromCache) {
        if (!current(token)) return; endpoint = url; manifest = loaded; cached = fromCache;
        if (restore != null) {
            for (var c : loaded.catalogs) if (c.id.equals(restore.getString("catalog")) && c.type.equals(restore.getString("type"))) catalog = c;
            if (catalog != null) { Bundle fields = restore.getBundle("fields"); if (fields != null) for (String key : fields.keySet()) if (catalog.extras.contains(key)) values.put(key, fields.getString(key, "")); offset = Math.max(0, restore.getInt("offset")); page = Math.max(0, restore.getInt("page")); }
            int[] prior = restore.getIntArray("history"); if (prior != null) for (int i : prior) if (i >= 0 && i < offset && history.size() < 1000) history.add(i);
            restore = null;
        }
        if (catalog != null) fetch(offset, page); else show();
    }
    private void fetch(int requestedOffset, int requestedPage) {
        cancel(); int token = ++generation; busy(); var selected = catalog; var extras = new LinkedHashMap<>(values);
        if (selected.extras.contains("skip")) extras.put("skip", Integer.toString(requestedOffset));
        HttpDocument client = new HttpDocument(); request = client; NETWORK.purge();
        var repository = MediaRepository.get(context); String url = endpoint;
        try { task = NETWORK.submit(() -> { try { var loaded = AddonClient.catalogPage(client, manifest, selected, extras); repository.worker.execute(() -> {
            try { MovieCache.save(repository.database.movieCache(), addonId, MovieCache.key(addonId, url, "catalog:" + manifest.version, MovieCache.catalogIdentity(selected, extras, requestedOffset)), AddonClient.catalogSnapshot(loaded)); } catch (Exception ignored) { }
            main.post(() -> applyPage(token, requestedOffset, requestedPage, loaded, false));
        }); } catch (Exception error) { repository.worker.execute(() -> {
            try { String payload = MovieCache.get(repository.database.movieCache(), addonId, MovieCache.key(addonId, url, "catalog:" + manifest.version, MovieCache.catalogIdentity(selected, extras, requestedOffset))); if (payload == null) throw new java.io.IOException();
                var loaded = AddonClient.cachedCatalog(payload, selected.type); main.post(() -> applyPage(token, requestedOffset, requestedPage, loaded, true));
            } catch (Exception unavailable) { fail(token); }
        }); } }); } catch (RejectedExecutionException error) { fail(token); }
    }
    private void applyPage(int token, int requestedOffset, int requestedPage, AddonClient.CatalogPage loaded, boolean fromCache) {
        if (!current(token)) return;
        if (requestedOffset > offset && history.size() < 1000) history.add(offset);
        else if (requestedOffset < offset) { while (!history.isEmpty() && history.get(history.size() - 1) >= requestedOffset) history.remove(history.size() - 1); }
        titles = loaded.items; consumed = loaded.consumed; offset = requestedOffset; page = requestedPage; cached = fromCache; show();
        if (restoredTitle != null) detail(new AddonClient.Title(restoredTitle.getString("id"), restoredTitle.getString("type"), restoredTitle.getString("name"), "", "", List.of()));
    }
    private void filters() {
        LinearLayout form = new LinearLayout(context); form.setOrientation(LinearLayout.VERTICAL); Map<String, EditText> fields = new LinkedHashMap<>();
        for (String name : catalog.extras) {
            if (name.equals("skip")) continue;
            EditText field = new EditText(context); field.setSingleLine(); field.setHint(name + (catalog.required.contains(name) ? " *" : "")); field.setTag("movies.filter." + name); field.setText(values.getOrDefault(name, ""));
            field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)}); form.addView(field); fields.put(name, field);
        }
        ScrollView scroll = new ScrollView(context); scroll.addView(form);
        filtersDialog = new AlertDialog.Builder(context).setTitle(R.string.movies_filters).setView(scroll).setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.movies_load, null).create();
        filtersDialog.show(); filtersDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Map<String,String> submitted = new LinkedHashMap<>(); boolean valid = true;
            for (var entry : fields.entrySet()) { String value = entry.getValue().getText().toString().trim(); if (value.isEmpty() && catalog.required.contains(entry.getKey())) { entry.getValue().setError(context.getString(R.string.movies_required)); valid = false; } if (!value.isEmpty()) submitted.put(entry.getKey(), value); }
            if (!valid) return; values = submitted; titles = List.of(); history.clear(); page = offset = 0; filtersDialog.dismiss(); fetch(0, 0);
        });
    }
    private void detail(AddonClient.Title preview) {
        if (!manifest.supports("meta", preview.type, preview.id)) { showDetail(preview); return; }
        cancel(); int token = ++generation; busy(); HttpDocument client = new HttpDocument(); request = client; NETWORK.purge();
        var repository = MediaRepository.get(context); String url = endpoint;
        try { task = NETWORK.submit(() -> { try { var title = AddonClient.meta(client, manifest, preview.type, preview.id); repository.worker.execute(() -> {
            try { MovieCache.save(repository.database.movieCache(), addonId, MovieCache.key(addonId, url, "meta:" + manifest.version, preview.type + "\n" + preview.id), AddonClient.metaSnapshot(title)); } catch (Exception ignored) { }
            main.post(() -> { if (current(token)) { cached = false; show(); showDetail(title); } });
        }); } catch (Exception error) { repository.worker.execute(() -> {
            try { String payload = MovieCache.get(repository.database.movieCache(), addonId, MovieCache.key(addonId, url, "meta:" + manifest.version, preview.type + "\n" + preview.id)); if (payload == null) throw new java.io.IOException();
                var title = AddonClient.cachedMeta(payload, preview.type, preview.id); main.post(() -> { if (current(token)) { cached = true; show(); showDetail(title); } });
            } catch (Exception unavailable) { fail(token); }
        }); } }); } catch (RejectedExecutionException error) { fail(token); }
    }
    private void showDetail(AddonClient.Title title) {
        Bundle saved = restoredTitle; restoredTitle = null; details = new TitleDialog(context, addonId, manifest, title, saved, () -> details = null);
    }
    public Bundle saveState() {
        Bundle state = new Bundle(); if (details != null) state.putBundle("title", details.saveState()); if (catalog != null) { state.putString("catalog", catalog.id); state.putString("type", catalog.type); }
        state.putIntArray("history", history.stream().mapToInt(Integer::intValue).toArray()); state.putInt("offset", offset); state.putInt("page", page); Bundle fields = new Bundle(); for (var value : values.entrySet()) fields.putString(value.getKey(), value.getValue()); state.putBundle("fields", fields); return state;
    }
    private void dispose() {
        if (closed) return;
        closed = true; cancel();
        if (details != null) details.close();
        if (filtersDialog != null) filtersDialog.dismiss();
        main.removeCallbacksAndMessages(null); dismissed.run();
    }
    public void close() { dispose(); dialog.dismiss(); }
}
