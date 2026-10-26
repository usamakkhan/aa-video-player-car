package app.aavideoplayercar.movies;

import android.app.AlertDialog;
import android.content.Context;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import app.aavideoplayercar.R;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import java.util.*;
import java.util.concurrent.*;

/** Add-on management. Configured URL drafts are deliberately excluded from saved UI state. */
public final class MoviesScreen {
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
    private final Context context;
    private final MediaRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout view;
    private final EditText url;
    private final TextView status;
    private final ListView list;
    private final Button install, refresh, replace, remove, browse;
    private List<MovieAddon> rows = List.of();
    private String selected;
    private int generation;
    private boolean closed;
    private Future<?> task;
    private HttpDocument request;
    private AlertDialog confirmation; private CatalogDialog catalogs; private Bundle restoredCatalogs;
    public MoviesScreen(Context context, Bundle saved) {
        this.context = context; repository = MediaRepository.get(context);
        selected = saved == null ? null : saved.getString("selected"); restoredCatalogs = saved == null ? null : saved.getBundle("catalogs");
        view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL);
        url = new EditText(context); url.setHint(R.string.movies_url); url.setTag("movies.url"); url.setSingleLine();
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); url.setSaveEnabled(false); url.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO); view.addView(url);
        install = button(R.string.movies_install, () -> fetch(url.getText().toString(), null)); view.addView(install);
        LinearLayout actions = new LinearLayout(context); view.addView(actions);
        refresh = button(R.string.movies_refresh, this::refresh); actions.addView(refresh, weight());
        replace = button(R.string.movies_replace, () -> fetch(url.getText().toString(), selected)); actions.addView(replace, weight());
        remove = button(R.string.movies_remove, this::remove); view.addView(remove);
        browse = button(R.string.movies_browse, () -> openCatalogs(null)); view.addView(browse);
        status = new TextView(context); status.setTag("movies.status"); view.addView(status);
        list = new ListView(context); list.setTag("movies.list"); view.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView scope = new TextView(context); scope.setText(R.string.movies_scope); view.addView(scope);
        list.setOnItemClickListener((p, v, position, id) -> { selected = rows.get(position).id; showSelection(); }); load(false);
    }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private Button button(int text, Runnable action) { Button b = new Button(context); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b; }
    private boolean current(int token) { return !closed && generation == token; }
    private void busy() { status.setText(R.string.movies_loading); install.setEnabled(false); refresh.setEnabled(false); replace.setEnabled(false); remove.setEnabled(false); browse.setEnabled(false); list.setEnabled(false); }
    private void showSelection() {
        MovieAddon chosen = null; for (MovieAddon a : rows) if (a.id.equals(selected)) chosen = a;
        if (chosen == null) selected = null;
        install.setEnabled(true); list.setEnabled(true); refresh.setEnabled(chosen != null); replace.setEnabled(chosen != null); remove.setEnabled(chosen != null); browse.setEnabled(chosen != null);
        status.setText(chosen != null ? context.getString(R.string.movies_selected, chosen.title, chosen.version) : rows.isEmpty() ? context.getString(R.string.movies_empty) : context.getString(R.string.movies_count, rows.size()));
    }
    private void load(boolean failed) {
        int token = ++generation; busy();
        repository.worker.execute(() -> { try { List<MovieAddon> result = repository.database.movieAddons().all(); main.post(() -> {
            if (!current(token)) return; rows = result; list.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, rows)); showSelection(); if (restoredCatalogs != null && selected != null) { Bundle state = restoredCatalogs; restoredCatalogs = null; openCatalogs(state); } if (failed) status.setText(R.string.movies_error);
        }); } catch (Exception error) { main.post(() -> { if (current(token)) { showSelection(); status.setText(R.string.movies_error); } }); } });
    }
    private void openCatalogs(Bundle state) { if (catalogs == null && selected != null) catalogs = new CatalogDialog(context, selected, state, () -> catalogs = null); }
    private void refresh() {
        int token = ++generation; String id = selected; busy();
        repository.worker.execute(() -> { try {
            MovieAddon addon = repository.database.movieAddons().get(id); if (addon == null) { main.post(() -> { if (current(token)) load(false); }); return; }
            String input = AddonRegistration.endpoint(addon); main.post(() -> { if (current(token)) fetch(input, id); });
        } catch (Exception error) { fail(token); } });
    }
    private void fetch(String input, String existing) {
        cancel(); int token = ++generation; busy(); HttpDocument client = new HttpDocument(); request = client; NETWORK.purge();
        try { task = NETWORK.submit(() -> { try {
            AddonClient.Manifest manifest = AddonClient.manifest(client, input);
            main.post(() -> { if (!current(token)) return; repository.worker.execute(() -> { try {
                String id = AddonRegistration.save(repository.database.movieAddons(), manifest, existing);
                main.post(() -> { if (current(token)) { selected = id; url.setText(""); load(false); } });
            } catch (Exception error) { fail(token); } }); });
        } catch (Exception error) { fail(token); } }); } catch (RejectedExecutionException error) { fail(token); }
    }
    private void remove() {
        String id = selected; int token = generation;
        confirmation = new AlertDialog.Builder(context).setTitle(R.string.movies_remove).setMessage(R.string.movies_confirm_remove)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.movies_remove, (d, which) -> {
                busy(); repository.worker.execute(() -> { try { repository.database.movieAddons().delete(id); main.post(() -> { if (current(token)) { selected = null; load(false); } }); } catch (Exception error) { fail(token); } });
            }).show();
    }
    private void fail(int token) { main.post(() -> { if (current(token)) load(true); }); }
    private void cancel() { if (request != null) request.cancel(); if (task != null) task.cancel(true); }
    public LinearLayout getView() { return view; }
    public Bundle saveState() { Bundle state = new Bundle(); state.putString("selected", selected); if (catalogs != null) state.putBundle("catalogs", catalogs.saveState()); return state; }
    public void close() { closed = true; cancel(); if (catalogs != null) catalogs.close(); if (confirmation != null) confirmation.dismiss(); main.removeCallbacksAndMessages(null); }
}
