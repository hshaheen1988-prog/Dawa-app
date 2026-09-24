package com.hamza.dawa;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.speech.tts.TextToSpeech;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/** Methods the web app can call through window.DawaNative. */
public class Bridge {
    private final Activity activity;
    private static TextToSpeech voiceTest;
    private static WebView printView; // kept alive while the print dialog uses it

    Bridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public boolean isNative() {
        return true;
    }

    @JavascriptInterface
    public int version() {
        return 3;
    }

    /** Data for the home-screen widget. */
    @JavascriptInterface
    public void setWidget(String json) {
        DoseWidget.save(activity.getApplicationContext(), json);
    }

    /** Speaks a sample reminder so the user can hear the voice alarm. */
    @JavascriptInterface
    public void testVoice(String text, String lang) {
        activity.runOnUiThread(() -> {
            try { if (voiceTest != null) voiceTest.shutdown(); } catch (Exception ignored) { }
            final TextToSpeech[] holder = new TextToSpeech[1];
            holder[0] = new TextToSpeech(activity.getApplicationContext(), status -> {
                TextToSpeech tts = holder[0];
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    MainActivity.runJs("window.nativeVoiceResult && window.nativeVoiceResult('noengine')");
                    return;
                }
                int r = tts.setLanguage("en".equals(lang) ? Locale.ENGLISH : new Locale("ar"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    MainActivity.runJs("window.nativeVoiceResult && window.nativeVoiceResult('missing')");
                    try {
                        activity.startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                    } catch (Exception ignored) { }
                    return;
                }
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                tts.setSpeechRate(0.9f);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dawa-test");
                MainActivity.runJs("window.nativeVoiceResult && window.nativeVoiceResult('ok')");
            });
            voiceTest = holder[0];
        });
    }

    /** Opens the system print screen for an HTML report ("Save as PDF"). */
    @JavascriptInterface
    public void printHtml(String html, String name) {
        activity.runOnUiThread(() -> {
            WebView wv = new WebView(activity);
            final boolean[] done = {false};
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (done[0]) return;
                    done[0] = true;
                    PrintManager pm = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
                    String job = (name == null || name.isEmpty()) ? "Dawa report" : name;
                    PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(job);
                    pm.print(job, adapter, new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
                }
            });
            printView = wv;
            wv.loadDataWithBaseURL("file:///android_asset/www/", html, "text/html", "UTF-8", null);
        });
    }

    /** Replaces all scheduled reminders with the given JSON list. */
    @JavascriptInterface
    public void schedule(String json) {
        Alarms.scheduleAll(activity.getApplicationContext(), json);
    }

    /** Doses marked "taken" from the alarm screen / notification while the app was closed. */
    @JavascriptInterface
    public String takePendingMarks() {
        return Alarms.takePendingMarks(activity.getApplicationContext());
    }

    @JavascriptInterface
    public void notifyNow(String title, String body) {
        try {
            android.os.Bundle b = new android.os.Bundle();
            b.putInt("id", 7_000_001);
            b.putString("title", title);
            b.putString("body", body);
            Alarms.show(activity.getApplicationContext(), b);
        } catch (Exception ignored) { }
    }

    /** Fires a real alarm (same path as medicine reminders) after a few seconds. */
    @JavascriptInterface
    public void testAlarm(String json, int seconds) {
        try {
            Alarms.testAlarm(activity.getApplicationContext(), new JSONObject(json), Math.max(3, seconds));
        } catch (Exception ignored) { }
    }

    /** Everything that can stop alarms from ringing, for the in-app checkup screen. */
    @JavascriptInterface
    public String status() {
        try {
            JSONObject o = new JSONObject();
            o.put("notif", Alarms.canNotify(activity));
            o.put("exact", Alarms.canExact(activity));
            o.put("fullScreen", Alarms.canFullScreen(activity));
            o.put("battery", Alarms.batteryUnrestricted(activity));
            o.put("count", Alarms.scheduledCount(activity));
            o.put("next", Alarms.nextAt(activity));
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("maker", Build.MANUFACTURER);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public boolean notificationsAllowed() {
        return Alarms.canNotify(activity);
    }

    @JavascriptInterface
    public boolean exactAlarmsAllowed() {
        return Alarms.canExact(activity);
    }

    private void open(Intent primary) {
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(primary);
            } catch (Exception e) {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName())));
            }
        });
    }

    @JavascriptInterface
    public void openNotificationSettings() {
        open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
    }

    @JavascriptInterface
    public void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + activity.getPackageName())));
        } else {
            openAppSettings();
        }
    }

    @JavascriptInterface
    public void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + activity.getPackageName())));
        } else {
            openNotificationSettings();
        }
    }

    @SuppressLint("BatteryLife")
    @JavascriptInterface
    public void openBatterySettings() {
        open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + activity.getPackageName())));
    }

    @JavascriptInterface
    public void openAppSettings() {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName())));
    }

    /** Shares text (backup, doctor report) via WhatsApp, email, Drive... */
    @JavascriptInterface
    public void share(String title, String content) {
        activity.runOnUiThread(() -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, title);
            send.putExtra(Intent.EXTRA_TEXT, content);
            activity.startActivity(Intent.createChooser(send, title));
        });
    }
}
