package app.aavideoplayercar.podcast;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.widget.*;
import androidx.media3.exoplayer.offline.*;
import app.aavideoplayercar.R;
import app.aavideoplayercar.data.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PodcastDownloadUi {
    private final Context context;
    private final MediaRepository repository;
    private final Consumer<String> play;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private int generation, page;
    private boolean closed;
    private String mode, selectedEpisode;
    private ListView currentList;
    private int scrollPosition, scrollTop;
    public PodcastDownloadUi(Context context, Consumer<String> play) {
        this.context = context; this.play = play; repository = MediaRepository.get(context);
    }
    private void dismiss() {
        generation++; main.removeCallbacksAndMessages(null);
        if (dialog != null) { dialog.setOnDismissListener(null); dialog.dismiss(); }
        mode = null; selectedEpisode = null; currentList = null;
    }
    public void close() { closed = true; dismiss(); }
    private void error(Exception error) { if (!closed) Toast.makeText(context, R.string.podcast_download_error, Toast.LENGTH_LONG).show(); }
    private void command(Runnable action) { try { action.run(); } catch (RuntimeException error) { error(error); } }
    public void showLibrary() {
        showLibrary(0, 0, 0);
    }
    private void showLibrary(int restoredPage, int restoredPosition, int restoredTop) {
        dismiss(); mode = "library"; page = restoredPage; scrollPosition = restoredPosition; scrollTop = restoredTop;
        LinearLayout layout = new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL);
        CheckBox wifi = new CheckBox(context); wifi.setText(R.string.podcast_download_wifi);
        wifi.setChecked(context.getSharedPreferences("downloads", 0).getBoolean("wifiOnly", true));
        wifi.setOnCheckedChangeListener((button, checked) -> {
            context.getSharedPreferences("downloads", 0).edit().putBoolean("wifiOnly", checked).apply();
            command(() -> DownloadService.sendSetRequirements(context, PodcastDownloadService.class, PodcastDownloads.requirements(checked), false));
        }); layout.addView(wifi);
        TextView summary = new TextView(context); layout.addView(summary);
        ListView list = new ListView(context); list.setTag("podcast.download.list"); currentList = list;
        layout.addView(list, new LinearLayout.LayoutParams(-1, Math.round(280 * context.getResources().getDisplayMetrics().density)));
        LinearLayout paging = new LinearLayout(context);
        Button previous = new Button(context); previous.setText(R.string.radio_previous); previous.setTag("podcast.download.previous");
        Button next = new Button(context); next.setText(R.string.radio_next); next.setTag("podcast.download.next");
        previous.setEnabled(false); next.setEnabled(false);
        paging.addView(previous, new LinearLayout.LayoutParams(0, -2, 1)); paging.addView(next, new LinearLayout.LayoutParams(0, -2, 1)); layout.addView(paging);
        dialog = new AlertDialog.Builder(context).setTitle(R.string.podcast_downloads).setView(layout).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnDismissListener(d -> { generation++; mode = null; currentList = null; main.removeCallbacksAndMessages(null); }); dialog.show();
        Runnable refresh = new Runnable() {
            @Override public void run() {
                if (closed || dialog == null || !dialog.isShowing()) return;
                int version = generation, selectedPage = page;
                repository.worker.execute(() -> {
                    try {
                        PodcastDownloads downloads = PodcastDownloads.get(context);
                        PodcastDownloads.Page loaded = downloads.page(selectedPage, 50);
                        List<Download> rows = loaded.rows;
                        long bytes = downloads.cache.getCacheSpace();
                        main.post(() -> {
                            if (closed || version != generation) return;
                            captureScroll();
                            if (page != loaded.number) { scrollPosition = 0; scrollTop = 0; }
                            page = loaded.number;
                            List<String> labels = new ArrayList<>();
                            for (Download download : rows) labels.add(new String(download.request.data, StandardCharsets.UTF_8) + "\n" + describe(download));
                            list.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels));
                            list.setSelectionFromTop(Math.min(scrollPosition, Math.max(0, rows.size() - 1)), scrollTop);
                            list.setOnItemClickListener((parent, view, index, id) -> showEpisode(rows.get(index).request.id.substring(PodcastEpisode.PREFIX.length())));
                            summary.setText(context.getString(R.string.podcast_download_storage, bytes / (1024 * 1024), page + 1));
                            previous.setEnabled(page > 0); next.setEnabled(loaded.hasNext);
                            main.postDelayed(this, 2000);
                        });
                    } catch (Exception error) { main.post(() -> { if (!closed && version == generation) summary.setText(R.string.podcast_download_error); }); }
                });
            }
        };
        previous.setOnClickListener(v -> { if (page > 0) { page--; resetPage(list, previous, next); refresh.run(); } });
        next.setOnClickListener(v -> { page++; resetPage(list, previous, next); refresh.run(); });
        refresh.run();
    }
    public void showEpisode(String id) {
        dismiss(); mode = "episode"; selectedEpisode = id; int version = generation;
        repository.worker.execute(() -> {
            try {
                PodcastEpisode episode = repository.database.podcasts().episode(id);
                Download download = PodcastDownloads.get(context).index.getDownload(PodcastEpisode.PREFIX + id);
                DownloadRequest prepared = null;
                if (episode != null && (download == null || download.state == Download.STATE_FAILED)) {
                    try { prepared = PodcastDownloads.get(context).request(episode); }
                    catch (java.io.IOException ignored) { /* Keep playback/removal available; report inability to download on selection. */ }
                }
                DownloadRequest request = prepared;
                main.post(() -> {
                    if (closed || version != generation) return;
                    List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
                    if (episode != null) {
                        labels.add(context.getString(R.string.podcast_download_play)); actions.add(() -> play.accept(id));
                        if (download == null || download.state == Download.STATE_FAILED) {
                            labels.add(context.getString(download == null ? R.string.podcast_download : R.string.podcast_download_retry));
                            actions.add(() -> {
                                if (request == null) { error(new IllegalStateException("Download unavailable")); return; }
                                // Dispatch while the user's confirmed action is still on screen. Navigation cannot discard it.
                                DownloadService.sendAddDownload(context, PodcastDownloadService.class, request, false);
                            });
                        }
                    }
                    if (download != null) {
                        if (download.state == Download.STATE_STOPPED) {
                            labels.add(context.getString(R.string.podcast_download_resume));
                            actions.add(() -> DownloadService.sendSetStopReason(context, PodcastDownloadService.class, download.request.id, 0, false));
                        } else if (download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED) {
                            labels.add(context.getString(R.string.podcast_download_pause));
                            actions.add(() -> DownloadService.sendSetStopReason(context, PodcastDownloadService.class, download.request.id, PodcastDownloads.USER_PAUSED, false));
                        }
                        labels.add(context.getString(R.string.podcast_download_remove));
                        actions.add(() -> DownloadService.sendRemoveDownload(context, PodcastDownloadService.class, download.request.id, false));
                    }
                    String title = episode == null ? context.getString(R.string.podcast_downloads) : episode.title;
                    dialog = new AlertDialog.Builder(context).setTitle(title + (download == null ? "" : "\n" + describe(download)))
                        .setItems(labels.toArray(new String[0]), (d, which) -> command(actions.get(which)))
                        .setNegativeButton(android.R.string.cancel, null).show();
                    dialog.setOnDismissListener(d -> { mode = null; selectedEpisode = null; });
                });
            } catch (Exception error) { main.post(() -> { if (version == generation) error(error); }); }
        });
    }
    private void resetPage(ListView list, Button previous, Button next) {
        generation++; main.removeCallbacksAndMessages(null); scrollPosition = 0; scrollTop = 0;
        list.setAdapter(null); previous.setEnabled(false); next.setEnabled(false);
    }
    private void captureScroll() {
        if (currentList != null && currentList.getChildCount() > 0) {
            scrollPosition = currentList.getFirstVisiblePosition(); scrollTop = currentList.getChildAt(0).getTop();
        }
    }
    public Bundle saveState() {
        captureScroll(); Bundle state = new Bundle(); state.putString("mode", mode); state.putString("episode", selectedEpisode);
        state.putInt("page", page); state.putInt("position", scrollPosition); state.putInt("top", scrollTop); return state;
    }
    public void restoreState(Bundle state) {
        if (state == null) return;
        if ("library".equals(state.getString("mode"))) showLibrary(Math.max(0, state.getInt("page")), Math.max(0, state.getInt("position")), state.getInt("top"));
        else if ("episode".equals(state.getString("mode")) && state.getString("episode") != null) showEpisode(state.getString("episode"));
    }
    private String describe(Download download) {
        int resource;
        switch (download.state) {
            case Download.STATE_COMPLETED: resource = R.string.podcast_download_complete; break;
            case Download.STATE_FAILED: resource = R.string.podcast_download_failed; break;
            case Download.STATE_STOPPED: resource = R.string.podcast_download_paused; break;
            case Download.STATE_REMOVING: case Download.STATE_RESTARTING: resource = R.string.podcast_download_removing; break;
            case Download.STATE_DOWNLOADING: resource = R.string.podcast_download_running; break;
            default: resource = R.string.podcast_download_waiting;
        }
        return context.getString(R.string.podcast_download_status, context.getString(resource), download.getBytesDownloaded() / (1024 * 1024));
    }
}
