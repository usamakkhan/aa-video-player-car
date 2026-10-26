package app.aavideoplayercar.movies;
import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import app.aavideoplayercar.R;
import app.aavideoplayercar.network.HttpDocument;
import java.util.*;
import java.util.concurrent.*;

/** Title/episode exploration; stream URLs stay in memory and are never stored in saved state. */
public final class TitleDialog {
    private static final ThreadPoolExecutor NETWORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
    private final Context context;
    private final AddonClient.Manifest manifest;
    private final AddonClient.Title title;
    private final String addonId;
    private final Runnable dismissed;
    private String currentVideoId;
    private String deferredSourcesVideoId;
    private MoviePlayerDialog player;
    private final AlertDialog dialog;
    private AlertDialog sources;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView status;
    private final ListView episodes;
    private final Button previous, next, movieSources;
    private final Spinner seasons;
    private final List<Integer> seasonValues = new ArrayList<>();
    private List<AddonClient.Video> filtered = List.of();
    private int season = -1, page, generation;
    private boolean closed;
    private HttpDocument request;
    private Future<?> task;
    public TitleDialog(Context context, String addonId, AddonClient.Manifest manifest, AddonClient.Title title, Bundle saved, Runnable dismissed) {
        this.context = context; this.addonId = addonId; this.manifest = manifest; this.title = title; this.dismissed = dismissed;
        if (saved != null) { season = saved.getInt("season", -1); page = Math.max(0, saved.getInt("page")); }
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        TextView description = new TextView(context); description.setTag("movies.detail"); description.setTextIsSelectable(true);
        description.setText(title.description.isEmpty() ? context.getString(R.string.movies_no_description) : title.description);
        int pad = Math.round(16 * context.getResources().getDisplayMetrics().density); description.setPadding(pad,pad,pad,pad);
        ScrollView scroll = new ScrollView(context); scroll.addView(description); content.addView(scroll, new LinearLayout.LayoutParams(-1, Math.round(140 * context.getResources().getDisplayMetrics().density)));
        movieSources = button(R.string.movies_sources, () -> streams(title.id, null)); content.addView(movieSources);
        seasons = new Spinner(context); seasons.setTag("movies.seasons"); content.addView(seasons);
        status = new TextView(context); status.setTag("movies.episodes.status"); content.addView(status);
        episodes = new ListView(context); episodes.setTag("movies.episodes"); content.addView(episodes, new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pages = new LinearLayout(context); content.addView(pages);
        previous = button(R.string.radio_previous, () -> { page--; showEpisodes(); }); pages.addView(previous, new LinearLayout.LayoutParams(0,-2,1));
        next = button(R.string.radio_next, () -> { page++; showEpisodes(); }); pages.addView(next, new LinearLayout.LayoutParams(0,-2,1));
        TreeSet<Integer> unique = new TreeSet<>(); for (var video : title.videos) unique.add(video.season); seasonValues.addAll(unique);
        List<String> labels = new ArrayList<>(); for (int value : seasonValues) labels.add(value < 0 ? context.getString(R.string.movies_unknown_season) : context.getString(R.string.movies_season, value));
        seasons.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, labels));
        if (!seasonValues.isEmpty()) { if (!seasonValues.contains(season)) season = seasonValues.get(0); seasons.setSelection(seasonValues.indexOf(season)); }
        seasons.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { int chosen = seasonValues.get(position); if (chosen != season) { cancel(); generation++; season = chosen; page = 0; } showEpisodes(); }
        });
        episodes.setOnItemClickListener((p,v,index,id) -> { var video = filtered.get(page * 50 + index); streams(video.id, video.streams); });
        movieSources.setVisibility(title.videos.isEmpty() && title.type.equals("movie") ? View.VISIBLE : View.GONE);
        seasons.setVisibility(title.videos.isEmpty() ? View.GONE : View.VISIBLE); showEpisodes();
        dialog = new AlertDialog.Builder(context).setTitle(title.name).setView(content).setPositiveButton(R.string.news_close,null).create();
        dialog.setOnDismissListener(d -> dispose());
        dialog.show(); dialog.getWindow().setLayout(-1,-1);
        if (saved != null) restoreDialogs(saved);
    }
    private Button button(int label, Runnable action) { Button b = new Button(context); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b; }
    private void showEpisodes() {
        filtered = new ArrayList<>(); for (var video : title.videos) if (video.season == season) filtered.add(video);
        filtered.sort(Comparator.comparingInt((AddonClient.Video v) -> v.episode).thenComparing(v -> v.id)); page = Math.min(page, Math.max(0, (filtered.size()-1)/50));
        List<String> labels = new ArrayList<>(); for (int i = page*50; i < Math.min(filtered.size(), (page+1)*50); i++) { var v = filtered.get(i); labels.add(v.episode < 0 ? v.title : context.getString(R.string.movies_episode, v.episode, v.title)); }
        episodes.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels)); episodes.setEnabled(true); seasons.setEnabled(true); movieSources.setEnabled(true);
        previous.setEnabled(page > 0); next.setEnabled((page+1)*50 < filtered.size());
        status.setText(title.type.equals("series") && title.videos.isEmpty() ? context.getString(R.string.movies_no_episodes) : context.getString(R.string.movies_episode_count, filtered.size(), page+1));
    }
    private void cancel() { if (request != null) request.cancel(); if (task != null) task.cancel(true); }
    private void streams(String videoId, List<AddonClient.Stream> embedded) {
        currentVideoId = videoId;
        cancel(); int token = ++generation;
        if (embedded != null) { showSources(embedded); return; }
        if (!manifest.supports("stream", title.type, videoId)) { status.setText(R.string.movies_no_stream_handler); return; }
        status.setText(R.string.movies_loading); episodes.setEnabled(false); seasons.setEnabled(false); movieSources.setEnabled(false); previous.setEnabled(false); next.setEnabled(false);
        HttpDocument client = new HttpDocument(); request = client; NETWORK.purge();
        try { task = NETWORK.submit(() -> {
            try { var streams = AddonClient.streams(client, manifest, title.type, videoId); main.post(() -> { if (!closed && generation == token) { showEpisodes(); showSources(streams); } }); }
            catch (Exception error) { main.post(() -> { if (!closed && generation == token) { showEpisodes(); status.setText(R.string.movies_stream_error); } }); }
        }); } catch (RejectedExecutionException error) { showEpisodes(); status.setText(R.string.movies_stream_error); }
    }
    private boolean ownsVideo(String videoId) {
        if (videoId == null || videoId.isEmpty()) return false;
        if (title.type.equals("movie") && title.videos.isEmpty()) return title.id.equals(videoId);
        for (var video : title.videos) if (video.id.equals(videoId)) return true;
        return false;
    }
    private void restoreSources(String videoId) {
        if (!ownsVideo(videoId) || closed) return;
        for (var video : title.videos) if (video.id.equals(videoId)) { streams(videoId, video.streams); return; }
        streams(videoId, null);
    }
    private void restoreDialogs(Bundle saved) {
        String videoId = saved.getString("sourceVideoId"); if (!ownsVideo(videoId)) return;
        Bundle playing = saved.getBundle("player");
        if (playing == null) { restoreSources(videoId); return; }
        String expected = app.aavideoplayercar.data.MovieVideo.PREFIX + MoviePlayback.identity(addonId, title.type, videoId);
        if (!expected.equals(playing.getString("mediaId"))) { restoreSources(videoId); return; }
        currentVideoId = videoId; deferredSourcesVideoId = videoId;
        player = new MoviePlayerDialog(context, expected, playing, () -> { player = null; String deferred = deferredSourcesVideoId; deferredSourcesVideoId = null; if (!closed && deferred != null) restoreSources(deferred); });
    }
    private int kindLabel(AddonClient.Stream stream) {
        switch (stream.kind) {
            case HTTP: return R.string.movies_play_direct;
            case EXTERNAL: return R.string.movies_external;
            case TORRENT: return R.string.movies_torrent;
            case YOUTUBE: return R.string.movies_youtube;
            default: return R.string.movies_unsupported;
        }
    }
    private void showSources(List<AddonClient.Stream> streams) {
        List<String> labels = new ArrayList<>(); for (var stream : streams) labels.add((stream.name.isEmpty() ? "" : stream.name + "\n") + context.getString(kindLabel(stream)));
        ListView choices = new ListView(context); choices.setTag("movies.streams"); choices.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels));
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL); TextView message = new TextView(context); message.setTag("movies.stream.status"); message.setText(streams.isEmpty() ? R.string.movies_no_streams : R.string.movies_stream_help); content.addView(message); content.addView(choices, new LinearLayout.LayoutParams(-1,0,1));
        sources = new AlertDialog.Builder(context).setTitle(R.string.movies_sources).setView(content).setPositiveButton(R.string.news_close,null).create();
        AlertDialog openedSources = sources; sources.setOnDismissListener(d -> { if (sources == openedSources) sources = null; });
        choices.setOnItemClickListener((p,v,index,id) -> { var stream = streams.get(index); if (stream.kind == AddonClient.StreamKind.HTTP) {
            choices.setEnabled(false); String selectedVideo = currentVideoId; AlertDialog selectedSources = sources; var repository = app.aavideoplayercar.data.MediaRepository.get(context);
            repository.worker.execute(() -> { try {
                var video = MoviePlayback.prepare(repository.database, addonId, title.type, selectedVideo, title.name, stream, index);
                main.post(() -> { if (!closed && sources == selectedSources && selectedSources.isShowing()) { choices.setEnabled(true); player = new MoviePlayerDialog(context, app.aavideoplayercar.data.MovieVideo.PREFIX + video.id, () -> player = null); } });
            } catch(Exception error) { main.post(() -> { if(!closed){ choices.setEnabled(true); message.setText(R.string.movies_play_error); } }); } });
        } else if (stream.kind == AddonClient.StreamKind.EXTERNAL) { try { context.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(HttpDocument.url(stream.url)))); } catch (Exception error) { message.setText(R.string.movies_external_error); } } else message.setText(kindLabel(stream)); });
        sources.show(); sources.getWindow().setLayout(-1,-1);
    }
    public Bundle saveState() { Bundle state = new Bundle(); state.putString("id",title.id); state.putString("type",title.type); state.putString("name",title.name); state.putInt("season",season); state.putInt("page",page);
        if (currentVideoId != null && ((sources != null && sources.isShowing()) || player != null)) state.putString("sourceVideoId",currentVideoId);
        if (player != null) state.putBundle("player",player.saveState()); return state; }
    private void dispose() {
        if (closed) return;
        closed = true; cancel();
        if (player != null) player.close();
        if (sources != null) sources.dismiss();
        main.removeCallbacksAndMessages(null); dismissed.run();
    }
    public void close() { dispose(); dialog.dismiss(); }
}
