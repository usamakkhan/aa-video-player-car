package app.aavideoplayercar.maintenance;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import app.aavideoplayercar.data.MaintenanceDao;
import app.aavideoplayercar.data.MaintenanceRecord;
import app.aavideoplayercar.data.MediaRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MaintenanceScreen {
    private final Context context;
    private final LinearLayout root;
    private final MediaRepository repository;
    private final MaintenanceDao dao;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean closed, initialized;
    private int generation, limit = 50;
    private Bundle draft;
    private EditText[] fields;
    private AlertDialog confirmation;
    private TextView error;
    private static final String[] KEYS = {"title", "type", "serviceDate", "serviceKm", "dueDate", "dueKm", "notes"};
    private static final String[] LABELS = {"Service title", "Service type", "Service date (YYYY-MM-DD, optional)", "Service mileage (km, optional)", "Due date (YYYY-MM-DD, optional)", "Due mileage (km, optional)", "Notes"};

    public MaintenanceScreen(Context context, SharedPreferences legacy, Bundle restored) {
        this.context = context; repository = MediaRepository.get(context); dao = repository.database.maintenance();
        root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL);
        root.addView(label("Loading service records…"));
        draft = restored;
        String oldNotes = legacy.getString("maintenance", "");
        repository.worker.execute(() -> {
            try {
                dao.importLegacyNotes(oldNotes);
                main.post(() -> {
                    if (closed) return;
                    initialized = true;
                    if (draft != null) edit(draft); else list();
                });
            } catch (Exception e) { main.post(() -> {
                if (closed) return;
                root.removeAllViews(); root.addView(label("Could not load records. Return home and retry. Existing notes have not been removed."));
            }); }
        });
    }
    private TextView label(String text) {
        TextView view = new TextView(context); view.setText(text); view.setTextColor(Color.WHITE); view.setTextSize(16); return view;
    }
    private Button button(String text, View.OnClickListener action) {
        Button view = new Button(context); view.setText(text); view.setAllCaps(false); view.setOnClickListener(action); return view;
    }
    private void list() {
        draft = null; fields = null; root.removeAllViews(); final int request = ++generation;
        root.addView(button("Add service record", v -> {
            Bundle data = new Bundle(); data.putString("id", UUID.randomUUID().toString());
            data.putLong("created", System.currentTimeMillis()); data.putString("serviceDate", LocalDate.now().toString()); data.putString("type", "Service"); edit(data);
        }));
        TextView state = label("Loading…"); root.addView(state);
        ListView records = new ListView(context); records.setTag("maintenance.records"); root.addView(records, new LinearLayout.LayoutParams(-1, 0, 1));
        Button more = button("Load more records", v -> { limit += 50; list(); }); more.setVisibility(View.GONE); root.addView(more);
        repository.worker.execute(() -> {
            try {
                List<MaintenanceRecord> loaded = dao.list(limit, 0); int count = dao.count();
                main.post(() -> {
                    if (closed || request != generation) return;
                    state.setText(count == 0 ? "No service records yet." : loaded.size() + " of " + count + " records. Tap a record to edit.");
                    List<String> rows = new ArrayList<>();
                    for (MaintenanceRecord item : loaded) {
                        String row = item.title + " · " + item.type;
                        if (item.serviceDay != null) row += "\nServiced " + ServiceInput.date(item.serviceDay);
                        if (item.serviceKm != null) row += " · " + item.serviceKm + " km";
                        if (item.dueDay != null) row += "\n" + (item.dueDay < LocalDate.now().toEpochDay() ? "Overdue: " : "Due: ") + ServiceInput.date(item.dueDay);
                        if (item.dueKm != null) row += "\nDue at " + item.dueKm + " km";
                        rows.add(row);
                    }
                    records.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, rows));
                    records.setOnItemClickListener((parent, view, position, id) -> edit(bundle(loaded.get(position))));
                    more.setVisibility(loaded.size() < count ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) { main.post(() -> { if (!closed && request == generation) state.setText("Could not load records. Return home and retry."); }); }
        });
    }
    private Bundle bundle(MaintenanceRecord item) {
        Bundle b = new Bundle(); b.putString("id", item.id); b.putBoolean("existing", true); b.putLong("created", item.createdAt);
        b.putString("title", item.title); b.putString("type", item.type); b.putString("notes", item.notes);
        b.putString("serviceDate", ServiceInput.date(item.serviceDay)); b.putString("serviceKm", ServiceInput.mileage(item.serviceKm));
        b.putString("dueDate", ServiceInput.date(item.dueDay)); b.putString("dueKm", ServiceInput.mileage(item.dueKm)); return b;
    }
    private void edit(Bundle data) {
        ++generation; draft = new Bundle(data); root.removeAllViews();
        ScrollView scroll = new ScrollView(context); root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout form = new LinearLayout(context); form.setOrientation(LinearLayout.VERTICAL); scroll.addView(form);
        fields = new EditText[KEYS.length];
        for (int i = 0; i < fields.length; i++) {
            form.addView(label(LABELS[i]));
            EditText input = new EditText(context); input.setTag("maintenance." + KEYS[i]);
            input.setTextColor(Color.WHITE); input.setText(draft.getString(KEYS[i], ""));
            input.setInputType(i == 3 || i == 5 ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | (i == 6 ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
            if (i == 6) input.setMinLines(3); else input.setSingleLine(true);
            fields[i] = input; form.addView(input);
        }
        error = label(""); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); form.addView(error);
        Button save = button("Save service record", null); form.addView(save);
        save.setOnClickListener(v -> save(save));
        form.addView(button("Cancel editing", v -> list()));
        if (draft.getBoolean("existing")) form.addView(button("Delete service record", v -> {
            String id = draft.getString("id");
            final int request = generation;
            confirmation = new AlertDialog.Builder(context).setTitle("Delete this service record?")
                .setMessage("This removes this record from your maintenance history.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                    repository.worker.execute(() -> {
                        try { dao.delete(id); main.post(() -> { if (!closed && request == generation) list(); }); }
                        catch (Exception e) { main.post(() -> { if (!closed && request == generation) error.setText("Could not delete the record. Retry."); }); }
                    });
                }).show();
        }));
    }
    private void save(Button save) {
        try {
            ServiceInput input = new ServiceInput(fields[0].getText().toString(), fields[1].getText().toString(), fields[2].getText().toString(), fields[3].getText().toString(), fields[4].getText().toString(), fields[5].getText().toString(), fields[6].getText().toString());
            MaintenanceRecord item = new MaintenanceRecord(); item.id = draft.getString("id");
            item.title = input.title; item.type = input.type.isEmpty() ? "Service" : input.type; item.notes = input.notes;
            item.serviceDay = input.serviceDay; item.serviceKm = input.serviceKm; item.dueDay = input.dueDay; item.dueKm = input.dueKm;
            item.createdAt = draft.getLong("created"); item.updatedAt = System.currentTimeMillis();
            final int request = generation;
            save.setEnabled(false);
            repository.worker.execute(() -> {
                try { dao.save(item); main.post(() -> { if (!closed && request == generation) list(); }); }
                catch (Exception e) { main.post(() -> { if (!closed && request == generation) { error.setText("Could not save. Your draft is retained; retry."); save.setEnabled(true); } }); }
            });
        } catch (IllegalArgumentException e) { error.setText(e.getMessage()); }
    }
    public Bundle saveState() {
        if (draft == null) return null;
        Bundle saved = new Bundle(draft);
        if (fields != null) for (int i = 0; i < fields.length; i++) saved.putString(KEYS[i], fields[i].getText().toString());
        return saved;
    }
    public boolean onBack() { if (initialized && draft != null) { list(); return true; } return false; }
    public LinearLayout getView() { return root; }
    public void close() { closed = true; if (confirmation != null) confirmation.dismiss(); main.removeCallbacksAndMessages(null); }
}
