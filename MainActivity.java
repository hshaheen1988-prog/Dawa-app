package com.hamza.dawa;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {
    private static final int FILE_REQUEST = 41;
    private static final int NOTIF_PERMISSION_REQUEST = 42;
    private static final String START_URL = "file:///android_asset/www/index.html";

    private static WeakReference<MainActivity> current = new WeakReference<>(null);

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingMark;
    private boolean pageReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new WeakReference<>(this);
        Alarms.ensureChannel(this);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(this), "DawaNative");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme) || "tel".equals(scheme) || "mailto".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (ActivityNotFoundException ignored) { }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                deliverPendingMark();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, null), FILE_REQUEST);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        handleIntent(getIntent());
        web.loadUrl(START_URL);
        askNotificationPermission();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_REQUEST);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        deliverPendingMark();
    }

    /** Notification "Taken" button opens the app with the dose key to mark. */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String mark = intent.getStringExtra("mark");
        int notifId = intent.getIntExtra("notifId", -1);
        if (notifId != -1) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(notifId);
        }
        if (mark != null) {
            pendingMark = mark.replaceAll("[^A-Za-z0-9|:\\-]", "");
            intent.removeExtra("mark");
        }
    }

    private void deliverPendingMark() {
        if (!pageReady || pendingMark == null) return;
        String key = pendingMark;
        pendingMark = null;
        web.evaluateJavascript("window.nativeMark && window.nativeMark('" + key + "')", null);
    }

    /** Called when a dose is marked from a notification or the widget while the app is open. */
    static void refreshIfShowing() {
        runJs("window.nativeResume && window.nativeResume()");
    }

    static void runJs(String js) {
        MainActivity a = current.get();
        if (a != null) a.runOnUiThread(() -> {
            if (a.pageReady) a.web.evaluateJavascript(js, null);
        });
    }

    @Override
    protected void onDestroy() {
        if (current.get() == this) current.clear();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pageReady) web.evaluateJavascript("window.nativeResume && window.nativeResume()", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.nativeBack ? window.nativeBack() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }
}
