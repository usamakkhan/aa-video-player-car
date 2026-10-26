package app.aavideoplayercar.iptv;

import android.app.AlertDialog;
import android.content.Context;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.aavideoplayercar.R;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** One playlist's cached XMLTV source, viewed through a selected channel ID. */
public final class EpgDialog {
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
    private final Context context;
    private final MediaRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String playlist, channel, title;
    private final EditText source;
    private final TextView status;
    private final ListView list;
    private final CheckBox showPast, accountSource;
    private final Button jumpNow;
    private final Button refresh, previous, next;
    private final AlertDialog dialog;
    private AlertDialog detail;
    private List<EpgProgramme> programmes = new ArrayList<>();
    private List<String> renderedLabels = new ArrayList<>();
    private int page, generation;
    private volatile boolean closed;
    private boolean loading;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (!loading) renderRows();
            main.postDelayed(this, 30000);
        }
    };
    private HttpDocument request;
    private Future<?> task;
    public EpgDialog(Context context, String playlist, String channel, String title, Bundle state) {
        this.context = context; this.playlist = playlist; this.channel = channel; this.title = title;
        repository = MediaRepository.get(context);
        LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL);
        source = new EditText(context); source.setSingleLine(); source.setHint(R.string.epg_url); source.setTag("epg.url"); view.addView(source);
        accountSource = new CheckBox(context); accountSource.setText(R.string.epg_account_source); accountSource.setTag("epg.account"); accountSource.setVisibility(View.GONE); view.addView(accountSource);
        accountSource.setOnCheckedChangeListener((button, checked) -> source.setEnabled(!checked));
        refresh = button(R.string.epg_import, this::refresh); view.addView(refresh);
        LinearLayout timeControls = new LinearLayout(context);
        showPast = new CheckBox(context); showPast.setText(R.string.epg_show_past); showPast.setTag("epg.past");
        timeControls.addView(showPast, new LinearLayout.LayoutParams(0, -2, 1));
        jumpNow = button(R.string.epg_jump_now, () -> { if (showPast.isChecked()) showPast.setChecked(false); else { page = 0; load(false, 0); } });
        timeControls.addView(jumpNow); view.addView(timeControls);
        status = new TextView(context); status.setTag("epg.status"); view.addView(status);
        list = new ListView(context); list.setTag("epg.list"); view.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, row, index, id) -> {
            EpgProgramme item = programmes.get(index);
            detail = new AlertDialog.Builder(context).setTitle(item.title).setMessage(label(item) + "\n\n" + item.description)
                .setPositiveButton(android.R.string.ok, null).show();
        });
        LinearLayout pages = new LinearLayout(context); previous = button(R.string.radio_previous, () -> { page--; load(false, 0); }); next = button(R.string.radio_next, () -> { page++; load(false, 0); });
        pages.addView(previous, new LinearLayout.LayoutParams(0, -2, 1)); pages.addView(next, new LinearLayout.LayoutParams(0, -2, 1)); view.addView(pages);
        if (state != null) { source.setText(state.getString("url", "")); page = Math.max(0, state.getInt("page")); showPast.setChecked(state.getBoolean("past")); accountSource.setChecked(state.getBoolean("account")); }
        showPast.setOnCheckedChangeListener((button, checked) -> { page = 0; load(false, 0); });
        dialog = new AlertDialog.Builder(context).setTitle(context.getString(R.string.epg_title, title)).setView(view).setNegativeButton(R.string.epg_close, null).create();
        dialog.setOnDismissListener(d -> close()); dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        load(state == null, 0);
        main.postDelayed(clockTick, 30000);
    }
    private Button button(int label, Runnable action) { Button button = new Button(context); button.setText(label); button.setAllCaps(false); button.setOnClickListener(v -> action.run()); return button; }
    private void busy() { loading = true; accountSource.setEnabled(false); showPast.setEnabled(false); jumpNow.setEnabled(false); refresh.setEnabled(false); previous.setEnabled(false); next.setEnabled(false); status.setText(R.string.iptv_loading); }
    private String label(EpgProgramme item) {
        DateFormat date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        String times = date.format(new Date(item.startMs)) + (item.endMs == 0 ? context.getString(R.string.epg_unknown_end) : " – " + date.format(new Date(item.endMs)));
        boolean now = item.startMs <= System.currentTimeMillis() && item.endMs > System.currentTimeMillis();
        return (now ? context.getString(R.string.epg_now) : "") + item.title + "\n" + times;
    }
    private void load(boolean initializeSource, int skipped) {
        int version = ++generation, requested = page; boolean past = showPast.isChecked(); long now = System.currentTimeMillis(); busy();
        repository.worker.execute(() -> {
            try {
                var dao = repository.database.epg(); EpgSource saved = dao.source(playlist);
                boolean hasAccount = repository.database.xtream().account(playlist) != null;
                int total = dao.count(playlist, channel), count = past ? total : dao.upcomingCount(playlist, channel, now);
                int effective = Math.max(0, Math.min(requested, (count - 1) / 50));
                var rows = past ? dao.page(playlist, channel, 50, effective * 50) : dao.upcomingPage(playlist, channel, now, 50, effective * 50);
                main.post(() -> {
                    if (closed || version != generation) return;
                    if (initializeSource && source.getText().length() == 0) {
                        boolean storedAccount = saved != null && saved.url.equals(XtreamClient.guideReference(playlist));
                        accountSource.setChecked(storedAccount || (saved == null && hasAccount));
                        if (saved != null && !storedAccount) source.setText(saved.url);
                    }
                    accountSource.setVisibility(hasAccount || accountSource.isChecked() ? View.VISIBLE : View.GONE); accountSource.setEnabled(true);
                    page = effective; programmes = rows; renderRows(); list.setSelection(0);
                    loading = false; showPast.setEnabled(true); jumpNow.setEnabled(true);
                    refresh.setEnabled(true); previous.setEnabled(page > 0); next.setEnabled((long)(page + 1) * 50 < count);
                    String empty = count != 0 ? "" : context.getString(total == 0 ? R.string.epg_no_match : R.string.epg_no_upcoming);
                    String updated = saved == null ? "" : context.getString(R.string.epg_updated, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(saved.updatedAt)));
                    status.setText(context.getString(R.string.epg_status, context.getString(R.string.epg_page, page + 1, count, TimeZone.getDefault().getID()), empty,
                        skipped > 0 ? context.getString(R.string.iptv_skipped, skipped) : "", updated));
                });
            } catch (Exception error) { fail(version); }
        });
    }
    private void renderRows() {
        int first = list.getFirstVisiblePosition(); View top = list.getChildAt(0); int offset = top == null ? 0 : top.getTop();
        List<String> labels = new ArrayList<>(); for (var item : programmes) labels.add(label(item));
        if (list.getAdapter() != null && labels.equals(renderedLabels)) return;
        renderedLabels = labels;
        list.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels));
        list.setSelectionFromTop(first, offset);
    }
    private void refresh() {
        String url; boolean fromAccount = accountSource.isChecked();
        try { url = fromAccount ? XtreamClient.guideReference(playlist) : HttpDocument.url(source.getText().toString()); }
        catch (Exception invalid) { status.setText(R.string.epg_error); return; }
        if (request != null) request.cancel(); if (task != null) task.cancel(true);
        int version = ++generation; busy(); HttpDocument client = new HttpDocument(); request = client; NETWORK.purge();
        try { task = NETWORK.submit(() -> {
            try {
                HttpDocument.Result document;
                if (fromAccount) {
                    var account = repository.database.xtream().account(playlist);
                    if (account == null) throw new java.io.IOException("Account removed");
                    document = client.getWithoutRedirects(XtreamClient.guideEndpoint(account), "application/xml, text/xml");
                } else document = client.get(url, "application/xml, text/xml");
                var parsed = XmltvParser.parse(document.bytes, playlist);
                EpgSource saved = new EpgSource(); saved.playlistId = playlist; saved.url = url; saved.updatedAt = System.currentTimeMillis();
                main.post(() -> { if (!closed && version == generation) repository.worker.execute(() -> {
                    try {
                        if (closed) return;
                        boolean replaced = repository.database.epg().replace(saved, parsed.programmes);
                        main.post(() -> { if (!closed && version == generation) { if (replaced) { page = 0; load(false, parsed.skipped); } else fail(version); } });
                    } catch (Exception error) { fail(version); }
                }); });
            } catch (Exception error) { fail(version, fromAccount ? R.string.epg_account_error : R.string.epg_error); }
        }); } catch (RejectedExecutionException error) { fail(version); }
    }
    private void fail(int version) { fail(version, R.string.epg_error); }
    private void fail(int version, int message) { main.post(() -> { if (!closed && version == generation) { loading = false; accountSource.setEnabled(true); showPast.setEnabled(true); jumpNow.setEnabled(true); status.setText(message); refresh.setEnabled(true); previous.setEnabled(page > 0); next.setEnabled(false); } }); }
    public Bundle saveState() {
        if (closed) return null;
        Bundle state = new Bundle(); state.putBoolean("account", accountSource.isChecked()); state.putBoolean("past", showPast.isChecked()); state.putString("playlist", playlist); state.putString("channel", channel); state.putString("title", title); state.putString("url", source.getText().toString()); state.putInt("page", page); return state;
    }
    public void close() {
        if (closed) return; closed = true;
        if (request != null) request.cancel(); if (task != null) task.cancel(true); main.removeCallbacksAndMessages(null);
        if (detail != null) detail.dismiss(); dialog.dismiss();
    }
}
