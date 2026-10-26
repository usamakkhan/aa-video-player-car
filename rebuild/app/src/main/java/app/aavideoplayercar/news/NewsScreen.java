package app.aavideoplayercar.news;
import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.widget.*;
import app.aavideoplayercar.R;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import java.util.*;
import java.util.concurrent.*;

/** Cached news reader; networking and database work stay off the UI thread. */
public final class NewsScreen {
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
    private final Context context;
    private final MediaRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout view;
    private final EditText url, query;
    private final CheckBox unread;
    private final TextView status;
    private final ListView list;
    private final Button add, refresh, remove, previous, next;
    private List<NewsSource> sources = new ArrayList<>();
    private List<NewsArticle> articles = new ArrayList<>();
    private String sourceId, filter = "", articleId;
    private int page, generation, articleScroll, totalCount;
    private boolean closed, openingReader;
    private AlertDialog reader, confirmation;
    private ScrollView readerScroll;
    private NewsSpeech speech;
    private Future<?> task;
    private HttpDocument request;
    public NewsScreen(Context context, Bundle state) {
        this.context = context; repository = MediaRepository.get(context); view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL);
        url = new EditText(context); url.setSingleLine(); url.setHint(R.string.news_url); url.setTag("news.url"); view.addView(url);
        LinearLayout imports = row(); add = button(R.string.news_add, () -> fetch(url.getText().toString(), true)); imports.addView(add, weight());
        imports.addView(button(R.string.news_sources, () -> { sourceId = null; page = 0; load(0); }), weight());
        LinearLayout actions = row(); refresh = button(R.string.news_refresh, this::refresh); actions.addView(refresh, weight()); remove = button(R.string.news_remove, this::remove); actions.addView(remove, weight());
        LinearLayout search = row(); query = new EditText(context); query.setHint(R.string.news_search); query.setSingleLine(); query.setTag("news.query"); search.addView(query, weight());
        search.addView(button(R.string.radio_search, () -> { filter = query.getText().toString().trim(); page = 0; load(0); }));
        unread = new CheckBox(context); unread.setText(R.string.news_unread); unread.setTag("news.unread"); view.addView(unread);
        status = new TextView(context); status.setTag("news.status"); view.addView(status);
        list = new ListView(context); list.setTag("news.list"); view.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout pages = row(); previous = button(R.string.radio_previous, () -> { page--; load(0); }); pages.addView(previous, weight()); next = button(R.string.radio_next, () -> { page++; load(0); }); pages.addView(next, weight());
        list.setOnItemClickListener((p, v, index, id) -> { if (sourceId == null) { sourceId = sources.get(index).id; page = 0; load(0); } else openArticle(articles.get(index).id, 0); });
        if (state != null) { sourceId = state.getString("source"); url.setText(state.getString("url", "")); query.setText(state.getString("query", "")); filter = state.getString("filter", ""); page = Math.max(0, state.getInt("page")); unread.setChecked(state.getBoolean("unread")); articleId = state.getString("article"); articleScroll = state.getInt("scroll"); }
        unread.setOnCheckedChangeListener((b, checked) -> { page = 0; load(0); }); load(0);
    }
    private LinearLayout row() { var row = new LinearLayout(context); view.addView(row); return row; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private Button button(int label, Runnable action) { var b = new Button(context); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b; }
    private boolean current(int version) { return !closed && generation == version; }
    private void busy() { status.setText(R.string.news_loading); list.setEnabled(false); add.setEnabled(false); refresh.setEnabled(false); remove.setEnabled(false); previous.setEnabled(false); next.setEnabled(false); }
    private void load(int skipped) {
        cancel(); openingReader = false; int version = ++generation, requested = page; String selected = sourceId, search = filter; boolean onlyUnread = unread.isChecked(); busy(); list.setAdapter(null);
        repository.worker.execute(() -> { try {
            var dao = repository.database.news(); var source = selected == null ? null : dao.source(selected);
            int count = source == null ? dao.sourceCount() : dao.count(selected, search, onlyUnread); int effective = Math.min(requested, Math.max(0, (count - 1) / 50));
            var roots = source == null ? dao.sources(50, effective * 50) : new ArrayList<NewsSource>();
            var rows = source == null ? new ArrayList<NewsArticle>() : dao.articles(selected, search, onlyUnread, 50, effective * 50);
            main.post(() -> { if (!current(version)) return;
                sourceId = source == null ? null : source.id; sources = roots; articles = rows; page = effective; totalCount = count; list.setEnabled(true);
                List<String> labels = new ArrayList<>(); if (source == null) for (var s : roots) labels.add(s.title); else for (var a : rows) labels.add(a.read ? a.title : context.getString(R.string.news_unread_title, a.title));
                list.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels)); add.setEnabled(true); refresh.setEnabled(source != null); remove.setEnabled(source != null); previous.setEnabled(page > 0); next.setEnabled((long)(page + 1) * 50 < count);
                status.setText(source == null && count == 0 ? context.getString(R.string.news_empty) : context.getString(R.string.news_status, page + 1, count, skipped == 0 ? "" : context.getString(R.string.news_skipped, skipped)));
                if (articleId != null && reader == null) openArticle(articleId, articleScroll);
            });
        } catch (Exception e) { fail(version); } });
    }
    private void refresh() {
        String selected = sourceId; int version = generation; busy();
        repository.worker.execute(() -> { try { var source = repository.database.news().source(selected); main.post(() -> { if (current(version)) { if (source == null) load(0); else fetch(source.url, false); } }); } catch (Exception e) { fail(version); } });
    }
    private void fetch(String input, boolean adding) {
        cancel(); int version = ++generation; String expected = sourceId; busy(); HttpDocument client = new HttpDocument(); request = client; NETWORK.purge();
        try { task = NETWORK.submit(() -> { try {
            var document = client.get(input); var parsed = NewsParser.parse(document.bytes, input, document.url);
            if (!adding && !parsed.id.equals(expected)) throw new java.io.IOException();
            if (parsed.articles.isEmpty() && parsed.skipped > 0) throw new java.io.IOException();
            NewsSource source = new NewsSource(); source.id = parsed.id; source.url = parsed.url; source.title = parsed.title; source.updatedAt = System.currentTimeMillis();
            List<NewsArticle> rows = new ArrayList<>(); for (var item : parsed.articles) { var a = new NewsArticle(); a.id = item.id; a.sourceId = source.id; a.title = item.title; a.body = item.body; a.url = item.url; a.publishedAt = item.publishedAt; rows.add(a); }
            main.post(() -> { if (!current(version)) return; repository.worker.execute(() -> { try {
                boolean saved = repository.database.news().replace(source, rows, adding);
                main.post(() -> { if (current(version)) { sourceId = saved ? source.id : null; page = 0; load(parsed.skipped); } });
            } catch (Exception e) { fail(version); } }); });
        } catch (Exception e) { fail(version); } }); } catch (RejectedExecutionException e) { fail(version); }
    }
    private void openArticle(String id, int scroll) {
        if (openingReader || reader != null) return; openingReader = true; list.setEnabled(false);
        int version = generation;
        repository.worker.execute(() -> { try {
            var dao = repository.database.news(); var article = dao.article(id); if (article != null) dao.setRead(id, true);
            main.post(() -> { if (!current(version)) return; openingReader = false; if (article == null) { articleId = null; list.setEnabled(true); status.setText(R.string.news_missing); return; }
                articleId = id; readerScroll = new ScrollView(context); readerScroll.setTag("news.reader"); TextView body = new TextView(context); body.setText(article.body.isEmpty() ? context.getString(R.string.news_no_body) : article.body); body.setTextIsSelectable(true); int padding = Math.round(16 * context.getResources().getDisplayMetrics().density); body.setPadding(padding, padding, padding, padding); readerScroll.addView(body);
                LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
                if (article.publishedAt > 0) {
                    TextView published = new TextView(context); published.setTag("news.published");
                    String date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(new Date(article.publishedAt));
                    published.setText(context.getString(R.string.news_published, date)); published.setPadding(padding, 0, padding, padding); content.addView(published);
                }
                LinearLayout speechButtons = new LinearLayout(context); content.addView(speechButtons);
                Button speak = button(R.string.news_speak, () -> { if (speech != null) speech.speak(article.title + "\n" + article.body); }); speak.setTag("news.speak"); speechButtons.addView(speak, weight());
                Button stop = button(R.string.news_stop, () -> { if (speech != null) speech.stop(); }); stop.setTag("news.stop"); speechButtons.addView(stop, weight());
                TextView speechStatus = new TextView(context); speechStatus.setTag("news.speech.status"); content.addView(speechStatus);
                content.addView(readerScroll, new LinearLayout.LayoutParams(-1, 0, 1));
                speech = NewsSpeech.create(context, state -> {
                    speak.setEnabled(state != NewsSpeech.State.LOADING && state != NewsSpeech.State.CLOSED);
                    stop.setEnabled(state == NewsSpeech.State.SPEAKING);
                    speechStatus.setText(state == NewsSpeech.State.LOADING ? R.string.news_speech_loading : state == NewsSpeech.State.SPEAKING ? R.string.news_speech_active : state == NewsSpeech.State.ERROR ? R.string.news_speech_error : state == NewsSpeech.State.READY ? R.string.news_speech_ready : R.string.news_speech_stopped);
                });
                reader = new AlertDialog.Builder(context).setTitle(article.title).setView(content).setPositiveButton(R.string.news_close, null).setNegativeButton(R.string.news_mark_unread, (d, which) -> repository.worker.execute(() -> { try { dao.setRead(id, false); } catch (Exception e) { fail(version); } }))
                    .setNeutralButton(R.string.news_original, null).create();
                reader.setOnDismissListener(d -> { if (speech != null) { speech.close(); speech = null; } reader = null; readerScroll = null; articleId = null; if (!closed) load(0); }); reader.show(); reader.getWindow().setLayout(-1, -1);
                reader.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!article.url.isEmpty()); reader.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { try { context.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(HttpDocument.url(article.url)))); } catch (Exception e) { status.setText(R.string.news_error); } });
                readerScroll.post(() -> { if (readerScroll != null) readerScroll.scrollTo(0, Math.max(0, scroll)); });
            });
        } catch (Exception e) { fail(version); } });
    }
    private void remove() {
        String selected = sourceId; int version = generation;
        confirmation = new AlertDialog.Builder(context).setTitle(R.string.news_remove).setMessage(R.string.news_remove_message).setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.news_remove, (d, which) -> {
            busy(); repository.worker.execute(() -> { try { repository.database.news().delete(selected); main.post(() -> { if (current(version)) { sourceId = null; page = 0; load(0); } }); } catch (Exception e) { fail(version); } });
        }).show();
    }
    private void fail(int version) { main.post(() -> { if (current(version)) { openingReader = false; status.setText(R.string.news_error); add.setEnabled(true); refresh.setEnabled(sourceId != null); remove.setEnabled(sourceId != null); list.setEnabled(true); if (list.getAdapter() != null) { previous.setEnabled(page > 0); next.setEnabled((long)(page + 1) * 50 < totalCount); } } }); }
    private void cancel() { if (request != null) request.cancel(); if (task != null) task.cancel(true); }
    public void onPause() { if (speech != null) speech.stop(); }
    public LinearLayout getView() { return view; }
    public boolean onBack() { if (sourceId == null) return false; sourceId = null; page = 0; load(0); return true; }
    public Bundle saveState() { Bundle b = new Bundle(); b.putString("source", sourceId); b.putString("url", url.getText().toString()); b.putString("query", query.getText().toString()); b.putString("filter", filter); b.putBoolean("unread", unread.isChecked()); b.putInt("page", page); b.putString("article", articleId); b.putInt("scroll", readerScroll == null ? 0 : readerScroll.getScrollY()); return b; }
    public void close() { closed = true; cancel(); if (reader != null) reader.dismiss(); if (confirmation != null) confirmation.dismiss(); main.removeCallbacksAndMessages(null); }
}
