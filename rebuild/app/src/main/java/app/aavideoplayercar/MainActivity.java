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
import android.widget.VideoView;

public class MainActivity extends Activity {
    private static final String[] MODULES = {"YouTube", "YouTube Web", "YouTube Classic", "YouTube Kids", "YouTube Music", "Web Browser", "TV / IPTV", "File Manager", "Video Player", "Photo Gallery", "Radio", "Music", "Speedometer", "Screen Mirror", "Podcast", "News", "Movies & Series", "Vehicle Maintenance", "Settings"};
    private static final int PICK_VIDEO = 10, PICK_IMAGE = 11, PICK_AUDIO = 12;
    private LinearLayout root;
    private LocationManager locationManager;
    private LocationListener locationListener;

    @Override public void onCreate(Bundle state) { super.onCreate(state); home(); }
    @Override public void onBackPressed() { stopLocation(); home(); }
    @Override protected void onDestroy() { stopLocation(); super.onDestroy(); }

    private void frame(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 19, 32));
        setContentView(root);
        TextView header = label(title, 24);
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
        frame("AA Video Player Car");
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(12, 0, 12, 20);
        scroll.addView(grid);
        for (String module : MODULES) {
            Button tile = button(module, v -> open(module));
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = 0;
            p.height = 125;
            p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            p.setMargins(7, 7, 7, 7);
            grid.addView(tile, p);
        }
    }
    private void open(String module) {
        stopLocation();
        switch (module) {
            case "Web Browser": browser("https://www.google.com", module); break;
            case "YouTube": case "YouTube Web": case "YouTube Classic": browser("https://m.youtube.com", module); break;
            case "YouTube Kids": browser("https://www.youtubekids.com", module); break;
            case "YouTube Music": browser("https://music.youtube.com", module); break;
            case "Video Player": pick("video/*", PICK_VIDEO); break;
            case "Photo Gallery": pick("image/*", PICK_IMAGE); break;
            case "Music": pick("audio/*", PICK_AUDIO); break;
            case "File Manager": pick("*/*", PICK_VIDEO); break;
            case "Speedometer": speedometer(); break;
            case "Vehicle Maintenance": maintenance(); break;
            case "Settings": settings(); break;
            default: unavailable(module);
        }
    }
    private void browser(String url, String title) {
        frame(title);
        WebView web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        web.loadUrl(url);
    }
    private void pick(String type, int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        startActivityForResult(intent, request);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == PICK_IMAGE) {
            frame("Photo Gallery");
            android.widget.ImageView image = new android.widget.ImageView(this);
            image.setImageURI(uri);
            image.setAdjustViewBounds(true);
            root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            frame(request == PICK_AUDIO ? "Music" : "Video Player");
            VideoView video = new VideoView(this);
            video.setVideoURI(uri);
            video.setMediaController(new android.widget.MediaController(this));
            root.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
            video.start();
        }
    }
    private void speedometer() {
        frame("Speedometer");
        TextView speed = label("Waiting for location…", 42);
        speed.setGravity(Gravity.CENTER);
        root.addView(speed, new LinearLayout.LayoutParams(-1, 0, 1));
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 20);
            return;
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                speed.setText(Math.round(Math.max(0, location.getSpeed()) * 3.6) + " km/h");
            }
        };
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener); }
        catch (Exception e) { speed.setText("Location unavailable"); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 20 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) speedometer();
    }
    private void stopLocation() {
        if (locationManager != null && locationListener != null) locationManager.removeUpdates(locationListener);
        locationListener = null;
    }
    private void maintenance() {
        frame("Vehicle Maintenance");
        EditText entry = new EditText(this);
        entry.setHint("Service note, date and mileage");
        entry.setTextColor(Color.WHITE);
        entry.setHintTextColor(Color.LTGRAY);
        root.addView(entry);
        TextView saved = label(getPreferences(0).getString("maintenance", "No service records yet"), 18);
        saved.setPadding(24, 20, 24, 20);
        root.addView(button("Save record", v -> {
            String value = entry.getText().toString().trim();
            if (value.isEmpty()) return;
            String previous = getPreferences(0).getString("maintenance", "");
            String updated = value + "\n" + previous;
            getPreferences(0).edit().putString("maintenance", updated).apply();
            saved.setText(updated);
            entry.setText("");
        }));
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll);
        scroll.addView(saved);
    }
    private void settings() {
        frame("Settings");
        root.addView(label("Package: " + getPackageName() + "\nVersion: 0.1", 18));
        root.addView(button("Open app settings", v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))));
    }
    private void unavailable(String module) {
        frame(module);
        TextView message = label("This integration is mapped from the original app but has not been implemented in this independent rebuild.", 18);
        message.setPadding(24, 24, 24, 24);
        root.addView(message);
    }
}
