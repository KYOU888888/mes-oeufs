package fr.mesoeufs;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // sauvegarde des données sur le téléphone
        s.setAllowFileAccess(true);
        web.setBackgroundColor(Color.parseColor("#FBF6EC"));
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    class Bridge {
        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Mes oeufs", text));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        android.webkit.CookieManager.getInstance().flush();
    }
}
