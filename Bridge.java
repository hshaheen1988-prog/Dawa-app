package com.hamza.dawa;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

/** Methods the web app can call through window.DawaNative. */
public class Bridge {
    private final Activity activity;

    Bridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public boolean isNative() {
        return true;
    }

    /** Replaces all scheduled reminders with the given JSON list. */
    @JavascriptInterface
    public void schedule(String json) {
        Alarms.scheduleAll(activity.getApplicationContext(), json);
    }

    @JavascriptInterface
    public void notifyNow(String title, String body) {
        Alarms.show(activity.getApplicationContext(), 7_000_001, title, body, null, null);
    }

    @JavascriptInterface
    public boolean notificationsAllowed() {
        return Alarms.canNotify(activity);
    }

    @JavascriptInterface
    public boolean exactAlarmsAllowed() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = activity.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    /** Opens the system screen where the user can allow notifications for this app. */
    @JavascriptInterface
    public void openNotificationSettings() {
        activity.runOnUiThread(() -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            try {
                activity.startActivity(i);
            } catch (Exception e) {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName())));
            }
        });
    }

    /** Shares the backup as text (save to Drive, WhatsApp, email...). */
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
