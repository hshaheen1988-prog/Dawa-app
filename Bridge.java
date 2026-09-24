package com.hamza.dawa;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

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

    @JavascriptInterface
    public int version() {
        return 2;
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
