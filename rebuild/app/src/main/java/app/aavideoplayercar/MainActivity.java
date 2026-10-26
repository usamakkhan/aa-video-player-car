package app.aavideoplayercar;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.aavideoplayercar.playback.PlaybackScreen;
import app.aavideoplayercar.maintenance.MaintenanceScreen;
import app.aavideoplayercar.files.FileManagerScreen;

public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 10, PICK_IMAGE = 11, PICK_AUDIO = 12, PICK_IPTV = 13, PICK_TREE = 14;
    private LinearLayout root;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private android.content.SharedPreferences preferences;
    private int currentModule;
    private WebView web;
    private PlaybackScreen playbackScreen;
    private app.aavideoplayercar.radio.RadioScreen radioScreen;
    private Bundle restoredRadio;
    private app.aavideoplayercar.podcast.PodcastScreen podcastScreen;
    private Bundle restoredPodcast;
    private FileManagerScreen filesScreen;
    private Bundle restoredFiles;
    private app.aavideoplayercar.iptv.IptvScreen iptvScreen;
    private Bundle restoredIptv;
    private app.aavideoplayercar.news.NewsScreen newsScreen;
    private Bundle restoredNews; private Bundle restoredMovies; private app.aavideoplayercar.movies.MoviesScreen moviesScreen;
    private MaintenanceScreen maintenanceScreen;
    private Bundle restoredMaintenance;
    private TextView speed;
    private Bundle restoredWeb;
    private Uri mediaUri;
    private int previousSystemBarsBehavior = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("dashboard", MODE_PRIVATE);
        int selected = state != null ? state.getInt("module", 0)
            : preferences.getBoolean("open_last_module", false) ? preferences.getInt("last_module_id", 0) : 0;
        if (state != null) {
            restoredWeb = state.getBundle("web");
            restoredMaintenance = state.getBundle("maintenanceDraft");
            restoredRadio = state.getBundle("radio");
            restoredPodcast = state.getBundle("podcast");
            restoredIptv = state.getBundle("iptv"); restoredNews = state.getBundle("news"); restoredMovies = state.getBundle("movies"); restoredFiles = state.getBundle("files");
            String uri = state.getString("media");
            mediaUri = uri == null ? null : Uri.parse(uri);
        }
        open(selected);
        if (android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(getIntent().getAction())) {
            open(10);
            String query = getIntent().getStringExtra(android.app.SearchManager.QUERY);
            playbackScreen.playSearch(query == null ? "" : query);
        }
    }
    @Override public void onBackPressed() {
        if (filesScreen != null && filesScreen.onBack()) return;
        if (maintenanceScreen != null && maintenanceScreen.onBack()) return;
        if (podcastScreen != null && podcastScreen.onBack()) return;
        if (iptvScreen != null && iptvScreen.onBack()) return; if (newsScreen != null && newsScreen.onBack()) return;
        if (web != null && web.canGoBack()) web.goBack();
        else if (currentModule != 0) home();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("module", currentModule);
        if (radioScreen != null) state.putBundle("radio", radioScreen.saveState());
        if (podcastScreen != null) state.putBundle("podcast", podcastScreen.saveState());
        if (iptvScreen != null) state.putBundle("iptv", iptvScreen.saveState()); if (newsScreen != null) state.putBundle("news", newsScreen.saveState()); if (moviesScreen != null) state.putBundle("movies", moviesScreen.saveState()); if (filesScreen != null) state.putBundle("files", filesScreen.saveState());
        if (maintenanceScreen != null) state.putBundle("maintenanceDraft", maintenanceScreen.saveState());
        if (web != null) { Bundle saved = new Bundle(); web.saveState(saved); state.putBundle("web", saved); }
        if (mediaUri != null) state.putString("media", mediaUri.toString());
    }
    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        if (currentModule == 11) startLocation();
    }
    @Override protected void onPause() {
        if (newsScreen != null) newsScreen.onPause();
        stopLocation();
        if (web != null) web.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() { releaseScreen(); super.onDestroy(); }
    private void releaseScreen() {
        stopLocation();
        if (playbackScreen != null) { playbackScreen.close(); playbackScreen = null; }
        if (filesScreen != null) { filesScreen.close(); filesScreen = null; }
        if (radioScreen != null) { radioScreen.close(); radioScreen = null; }
        if (podcastScreen != null) { podcastScreen.close(); podcastScreen = null; }
        if (iptvScreen != null) { iptvScreen.close(); iptvScreen = null; } if (newsScreen != null) { newsScreen.close(); newsScreen = null; } if (moviesScreen != null) { moviesScreen.close(); moviesScreen = null; }
        if (maintenanceScreen != null) { maintenanceScreen.close(); maintenanceScreen = null; }
        if (web != null) {
            ((ViewGroup) web.getParent()).removeView(web);
            web.stopLoading(); web.destroy(); web = null;
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void frame(String title) {
        releaseScreen();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 19, 32));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
        if (currentModule != 0) { Button home = button("Home", v -> home()); home.setTag("frame.chrome"); root.addView(home); }
        TextView header = label(title, 24);
        header.setTag("frame.chrome");
        header.setPadding(24, 28, 24, 20);
        root.addView(header);
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        return view;
    }
    private Button button(String text, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(action);
        return b;
    }
    private void home() {
        currentModule = 0;
        mediaUri = null;
        frame("AA Video Player Car");
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(Math.max(2, Math.min(4, preferences.getInt("home_grid_columns", 2))));
        grid.setPadding(12, 0, 12, 20);
        scroll.addView(grid);
        for (ModuleRegistry.Module module : ModuleRegistry.ordered(preferences.getString("module_order", null))) {
            Button tile = button(module.title, v -> open(module.id));
            tile.setOnLongClickListener(v -> { moveModuleFirst(module.id); return true; });
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = 0;
            p.height = dp(88);
            p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            p.setMargins(7, 7, 7, 7);
            grid.addView(tile, p);
        }
    }
    private void moveModuleFirst(int id) {
        java.util.List<ModuleRegistry.Module> order = ModuleRegistry.ordered(preferences.getString("module_order", null));
        ModuleRegistry.Module module = ModuleRegistry.find(id);
        order.remove(module); order.add(0, module);
        preferences.edit().putString("module_order", ModuleRegistry.encode(order)).apply();
        home();
        android.widget.Toast.makeText(this, module.title + " moved to first position", android.widget.Toast.LENGTH_SHORT).show();
    }
    private void open(int id) {
        ModuleRegistry.Module module = ModuleRegistry.find(id);
        if (module == null) { home(); return; }
        currentModule = id;
        preferences.edit().putInt("last_module_id", id).apply();
        switch (module.route) {
            case WEB: browser(module.url, module.title); break;
            case VIDEO: case IMAGE: case AUDIO: mediaScreen(module); break;
            case FILE:
                frame(module.title);
                filesScreen = new FileManagerScreen(this, restoredFiles, this::pickTree); restoredFiles = null;
                root.addView(filesScreen.getView(), new LinearLayout.LayoutParams(-1, 0, 1)); break;
            case SPEED: speedometer(); break;
            case MAINTENANCE: maintenance(); break;
            case SETTINGS: settings(); break;
            case PODCAST:
                frame("Podcast");
                podcastScreen = new app.aavideoplayercar.podcast.PodcastScreen(this, restoredPodcast);
                restoredPodcast = null;
                root.addView(podcastScreen.getView(), new LinearLayout.LayoutParams(-1, 0, 1));
                break;
            case IPTV:
                frame("TV / IPTV");
                iptvScreen = new app.aavideoplayercar.iptv.IptvScreen(this, restoredIptv, () -> pick("*/*", PICK_IPTV), this::iptvFullscreen);
                restoredIptv = null;
                root.addView(iptvScreen.getView(), new LinearLayout.LayoutParams(-1, 0, 1));
                break;
            case MOVIES:
                frame(module.title);
                moviesScreen = new app.aavideoplayercar.movies.MoviesScreen(this, restoredMovies); restoredMovies = null;
                root.addView(moviesScreen.getView(), new LinearLayout.LayoutParams(-1, 0, 1)); break;
            case NEWS:
                frame(getString(R.string.news_title));
                newsScreen = new app.aavideoplayercar.news.NewsScreen(this, restoredNews); restoredNews = null;
                root.addView(newsScreen.getView(), new LinearLayout.LayoutParams(-1, 0, 1)); break;
            case RADIO:
                frame("Radio");
                radioScreen = new app.aavideoplayercar.radio.RadioScreen(this, restoredRadio);
                restoredRadio = null;
                root.addView(radioScreen.getView(), new LinearLayout.LayoutParams(-1, 0, 1));
                break;
            default: unavailable(module.title);
        }
    }
    private void iptvFullscreen(boolean fullscreen) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if ("frame.chrome".equals(child.getTag())) child.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            var bars = getWindow().getInsetsController();
            if (bars != null) {
                if (fullscreen) {
                    previousSystemBarsBehavior = bars.getSystemBarsBehavior();
                    bars.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    bars.hide(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
                } else {
                    restoreSystemBarsBehavior(bars);
                    previousSystemBarsBehavior = -1;
                    bars.show(android.view.WindowInsets.Type.systemBars());
                }
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(fullscreen ? decor.getSystemUiVisibility() | flags : decor.getSystemUiVisibility() & ~flags);
            if (fullscreen) ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(decor.getWindowToken(), 0);
        }
        root.requestApplyInsets();
    }
    @androidx.annotation.RequiresApi(30)
    @android.annotation.SuppressLint("WrongConstant")
    private void restoreSystemBarsBehavior(android.view.WindowInsetsController bars) {
        // This value came directly from getSystemBarsBehavior(); storing it loses the API's IntDef annotation.
        if (previousSystemBarsBehavior >= 0) bars.setSystemBarsBehavior(previousSystemBarsBehavior);
    }
    private void mediaScreen(ModuleRegistry.Module module) {
        frame(module.title);
        String type = module.route == ModuleRegistry.Route.IMAGE ? "image/*"
            : module.route == ModuleRegistry.Route.AUDIO ? "audio/*"
            : module.route == ModuleRegistry.Route.FILE ? "*/*" : "video/*";
        int request = module.route == ModuleRegistry.Route.IMAGE ? PICK_IMAGE
            : module.route == ModuleRegistry.Route.AUDIO ? PICK_AUDIO : PICK_VIDEO;
        root.addView(button("Choose a file", v -> pick(type, request)));
        if (module.route == ModuleRegistry.Route.AUDIO || module.route == ModuleRegistry.Route.VIDEO) {
            playbackScreen = new PlaybackScreen(this, module.route == ModuleRegistry.Route.AUDIO ? "audio/" : "video/", mediaUri);
            root.addView(playbackScreen.getView(), new LinearLayout.LayoutParams(-1, 0, 1));
            mediaUri = null;
        } else if (mediaUri != null) showMedia(type, mediaUri);
    }
    private void browser(String url, String title) {
        frame(title);
        EditText address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.LTGRAY);
        address.setHint("HTTPS address or search");
        root.addView(address);
        root.addView(button("Go", v -> {
            String input = address.getText().toString().trim();
            if (input.isEmpty()) return;
            String target = input.startsWith("https://") ? input
                : "https://www.google.com/search?q=" + Uri.encode(input);
            web.loadUrl(target);
        }));
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return !"https".equalsIgnoreCase(request.getUrl().getScheme());
            }
            @Override public void onPageFinished(WebView view, String finishedUrl) { address.setText(finishedUrl); }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) address.setError("Page unavailable. Check the address and connection.");
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        if (restoredWeb == null || web.restoreState(restoredWeb) == null) web.loadUrl(url);
        restoredWeb = null;
    }
    private void pick(String type, int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, request); }
        catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, "No document picker available", android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private void pickTree() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent,PICK_TREE); }
        catch (android.content.ActivityNotFoundException error) { android.widget.Toast.makeText(this,R.string.files_unavailable,android.widget.Toast.LENGTH_LONG).show(); }
    }
    @android.annotation.SuppressLint("WrongConstant")
    private void keepTreeGrant(Uri tree,int resultFlags) {
        // The picker returns a bit mask of the read/write grants it actually supplied.
        getContentResolver().takePersistableUriPermission(tree,resultFlags);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_TREE) {
            if (result != RESULT_OK || data == null || data.getData() == null) return;
            Uri tree=data.getData(); int flags=data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION)==0) { android.widget.Toast.makeText(this,R.string.files_permission,android.widget.Toast.LENGTH_LONG).show(); return; }
            try { keepTreeGrant(tree,flags); if(filesScreen==null)open(1); filesScreen.setTree(tree); }
            catch (Exception error) { android.widget.Toast.makeText(this,R.string.files_permission,android.widget.Toast.LENGTH_LONG).show(); }
            return;
        }
        if (request != PICK_VIDEO && request != PICK_IMAGE && request != PICK_AUDIO && request != PICK_IPTV) return;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        if (request == PICK_IPTV) {
            if (iptvScreen == null) open(6);
            iptvScreen.importDocument(uri); return;
        }
        mediaUri = uri;
        open(currentModule);
    }
    private void showMedia(String selectedType, Uri uri) {
        String type;
        try { type = getContentResolver().getType(uri); }
        catch (SecurityException e) { root.addView(label("File permission expired. Choose the file again.", 18)); return; }
        if (type == null) type = selectedType;
        if (type.startsWith("image/")) {
            android.widget.ImageView image = new android.widget.ImageView(this);
            image.setImageURI(uri);
            image.setAdjustViewBounds(true);
            root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        } else if (type.startsWith("video/") || type.startsWith("audio/")) {
            playbackScreen = new PlaybackScreen(this, type.startsWith("audio/") ? "audio/" : "video/", uri);
            root.addView(playbackScreen.getView(), new LinearLayout.LayoutParams(-1, 0, 1));
            mediaUri = null;
        } else {
            root.addView(label("This file type is not supported by the media preview. Choose audio, video or an image.", 18));
        }
    }
    private void speedometer() {
        frame("Speedometer");
        speed = label("Waiting for location…", 42);
        speed.setGravity(Gravity.CENTER);
        root.addView(speed, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(button("Allow precise location", v -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 20)));
        startLocation();
    }
    private void startLocation() {
        stopLocation();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            speed.setText("Precise location is needed to measure speed."); return;
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                speed.setText(location.hasSpeed() ? Math.round(Math.max(0, location.getSpeed()) * 3.6) + " km/h" : "Waiting for speed…");
            }
            @Override public void onProviderDisabled(String provider) { speed.setText("Turn on device location to measure speed."); }
            @Override public void onProviderEnabled(String provider) { speed.setText("Waiting for location…"); }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        };
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) speed.setText("Turn on device location to measure speed.");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
        }
        catch (Exception e) { speed.setText("Location unavailable"); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 20 && currentModule == 11) startLocation();
    }
    private void stopLocation() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) { }
        }
        locationListener = null;
    }
    private void maintenance() {
        frame("Vehicle Maintenance");
        maintenanceScreen = new MaintenanceScreen(this, getPreferences(0), restoredMaintenance);
        restoredMaintenance = null;
        root.addView(maintenanceScreen.getView(), new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private void settings() {
        frame("Settings");
        root.addView(label("Package: " + getPackageName() + "\nVersion: 0.1", 18));
        android.widget.Switch reopen = new android.widget.Switch(this);
        reopen.setText("Open last module at startup");
        reopen.setTextColor(Color.WHITE);
        reopen.setChecked(preferences.getBoolean("open_last_module", false));
        reopen.setOnCheckedChangeListener((view, checked) -> preferences.edit().putBoolean("open_last_module", checked).apply());
        root.addView(reopen);
        for (int columns = 2; columns <= 4; columns++) {
            final int count = columns;
            root.addView(button(columns + " dashboard columns", v -> {
                preferences.edit().putInt("home_grid_columns", count).apply(); home();
            }));
        }
        root.addView(label("Long-press a dashboard tile to move it to the first position.", 18));
        root.addView(button("Reset dashboard order", v -> { preferences.edit().remove("module_order").apply(); home(); }));
        root.addView(button("Open app settings", v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))));
    }
    private void unavailable(String module) {
        frame(module);
        TextView message = label("This integration is mapped from the original app but has not been implemented in this independent rebuild.", 18);
        message.setPadding(24, 24, 24, 24);
        root.addView(message);
    }
}
