package com.hamza.dawa;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Schedules reminders with AlarmManager so they ring even when the app is closed. */
public final class Alarms {
    static final String CHANNEL = "dawa_reminders_v1";
    static final String ACTION = "com.hamza.dawa.REMINDER";
    private static final String PREFS = "dawa_alarms";
    private static final int MAX_ALARMS = 450; // Android allows ~500 per app

    private Alarms() { }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(c.getString(R.string.channel_desc));
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 400});
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm == null || nm.areNotificationsEnabled();
    }

    private static PendingIntent alarmIntent(Context c, int id, JSONObject item) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION);
        if (item != null) {
            i.putExtra("id", id);
            i.putExtra("title", item.optString("title"));
            i.putExtra("body", item.optString("body"));
            i.putExtra("key", item.optString("key"));
            i.putExtra("action", item.optString("action"));
        }
        return PendingIntent.getBroadcast(c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Cancels everything previously scheduled and schedules the new list. */
    static synchronized void scheduleAll(Context c, String json) {
        SharedPreferences prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;

        String old = prefs.getString("ids", "");
        if (!old.isEmpty()) {
            for (String s : old.split(",")) {
                try {
                    am.cancel(alarmIntent(c, Integer.parseInt(s), null));
                } catch (NumberFormatException ignored) { }
            }
        }

        StringBuilder ids = new StringBuilder();
        long now = System.currentTimeMillis();
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        int count = 0;
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            for (int n = 0; n < arr.length() && count < MAX_ALARMS; n++) {
                JSONObject item = arr.getJSONObject(n);
                long at = item.getLong("at");
                if (at <= now) continue;
                int id = item.getInt("id");
                PendingIntent pi = alarmIntent(c, id, item);
                if (exact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
                if (ids.length() > 0) ids.append(',');
                ids.append(id);
                count++;
            }
        } catch (Exception ignored) { }

        prefs.edit().putString("json", json).putString("ids", ids.toString()).apply();
    }

    /** Re-applies the last saved schedule (after reboot, app update, clock change). */
    static void reschedule(Context c) {
        String json = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", "[]");
        scheduleAll(c, json);
    }

    static void show(Context c, int id, String title, String body, String key, String actionLabel) {
        ensureChannel(c);
        if (!canNotify(c)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;

        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("notifId", id);
        PendingIntent openPi = PendingIntent.getActivity(c, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF0F766E)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setContentIntent(openPi);

        if (key != null && !key.isEmpty()) {
            Intent take = new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("mark", key)
                    .putExtra("notifId", id);
            PendingIntent takePi = PendingIntent.getActivity(c, id ^ 0x40000000, take,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String label = (actionLabel == null || actionLabel.isEmpty()) ? "✓" : actionLabel;
            b.addAction(new Notification.Action.Builder((Icon) null, label, takePi).build());
        }
        nm.notify(id, b.build());
    }
}
