package fr.mesoeufs;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    private static final int PICK_PHOTO = 42;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private static final int REQ_LOC = 8;
    private static final String PERM_LOC = "android.permission.ACCESS_COARSE_LOCATION";
    private boolean locDone = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // sauvegarde des données sur le téléphone
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setBackgroundColor(Color.parseColor("#FBF6EC"));
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("image/*");
                Intent chooser = Intent.createChooser(pick, "Photo de l'animal");
                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (camera.resolveActivity(getPackageManager()) != null) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                }
                try {
                    startActivityForResult(chooser, PICK_PHOTO);
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PHOTO || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            } else if (data.getExtras() != null && data.getExtras().get("data") instanceof Bitmap) {
                // photo prise avec l'appareil photo (miniature)
                try {
                    Bitmap bm = (Bitmap) data.getExtras().get("data");
                    File f = new File(getCacheDir(), "photo" + System.currentTimeMillis() + ".jpg");
                    FileOutputStream out = new FileOutputStream(f);
                    bm.compress(Bitmap.CompressFormat.JPEG, 92, out);
                    out.close();
                    result = new Uri[]{Uri.fromFile(f)};
                } catch (Exception ignored) { }
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        // le bouton retour ferme d'abord la fiche ouverte dans l'appli
        web.evaluateJavascript("(function(){return (window.__back&&window.__back())?'x':''})()", v -> {
            if (v == null || !v.contains("x")) super.onBackPressed();
        });
    }

    /* ---------- localisation (pour la météo) ---------- */
    private void startLocation() {
        if (checkSelfPermission(PERM_LOC) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{PERM_LOC}, REQ_LOC);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) { locError("off"); return; }
        Location best = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException ignored) { }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 6L * 3600 * 1000) { sendLocation(best); return; }
        String prov = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null);
        if (prov == null) { if (best != null) sendLocation(best); else locError("off"); return; }
        final Location fallback = best;
        locDone = false;
        try {
            lm.requestSingleUpdate(prov, new LocationListener() {
                @Override public void onLocationChanged(Location l) { if (!locDone) { locDone = true; sendLocation(l); } }
                public void onStatusChanged(String p, int st, Bundle b) { }
                public void onProviderEnabled(String p) { }
                public void onProviderDisabled(String p) { }
            }, Looper.getMainLooper());
        } catch (SecurityException e) { locDone = true; locError("denied"); return; }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!locDone) { locDone = true; if (fallback != null) sendLocation(fallback); else locError("timeout"); }
        }, 25000);
    }

    private void sendLocation(Location l) {
        final double lat = l.getLatitude(), lon = l.getLongitude();
        new Thread(() -> {
            String name = "";
            try {
                List<Address> a = new Geocoder(MainActivity.this, Locale.FRANCE).getFromLocation(lat, lon, 1);
                if (a != null && !a.isEmpty()) {
                    Address ad = a.get(0);
                    name = ad.getLocality() != null ? ad.getLocality() : (ad.getSubAdminArea() != null ? ad.getSubAdminArea() : "");
                }
            } catch (Exception ignored) { }
            final String js = "window.__onLoc&&window.__onLoc(" + lat + "," + lon + "," + JSONObject.quote(name) + ")";
            runOnUiThread(() -> web.evaluateJavascript(js, null));
        }).start();
    }

    private void locError(String code) {
        runOnUiThread(() -> web.evaluateJavascript("window.__onLocErr&&window.__onLocErr('" + code + "')", null));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_LOC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startLocation();
        else locError("denied");
    }

    private void askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread(() -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7));
        }
    }

    class Bridge {
        @JavascriptInterface
        public void getLocation() {
            runOnUiThread(() -> startLocation());
        }

        @JavascriptInterface
        public void setReminder(boolean on, int h, int m) {
            SharedPreferences.Editor e = getSharedPreferences("rappel", MODE_PRIVATE).edit();
            e.putBoolean("on", on).putInt("h", h).putInt("m", m).apply();
            ReminderReceiver.schedule(MainActivity.this);
            if (on) askNotifPermission();
        }

        @JavascriptInterface
        public void setCouveeAlerts(String json) {
            getSharedPreferences("rappel", MODE_PRIVATE).edit().putString("cv", json).apply();
            ReminderReceiver.scheduleCouvees(MainActivity.this);
            if (json != null && json.length() > 2) askNotifPermission();
        }

        @JavascriptInterface
        public void testNotif() {
            askNotifPermission();
            ReminderReceiver.show(MainActivity.this, "Mes œufs 🥚", "Les notifications fonctionnent !", 99);
        }

        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Mes oeufs", text));
        }
    }
}
