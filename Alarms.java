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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Medicine alarms. Each alarm carries everything needed to show itself
 * (person, medicines + doses, labels) so it works with the app closed.
 */
public final class Alarms {
    static final String CHANNEL = "dawa_alarm_v2";
    /** Silent channel: the AlarmService plays the tone and the spoken reminder itself. */
    static final String CHANNEL_VOICE = "dawa_alarm_voice_v1";
    static final String ACTION_FIRE = "com.hamza.dawa.FIRE";
    static final String ACTION_TAKEN = "com.hamza.dawa.TAKEN";
    static final String ACTION_SNOOZE = "com.hamza.dawa.SNOOZE";
    static final String ACTION_DISMISS = "com.hamza.dawa.DISMISS";
    static final int TEST_ID = 0x1ABCDEF;
    private static final String PREFS = "dawa_alarms";
    private static final int MAX_ALARMS = 450;
    private static final String[] EXTRA_KEYS = {"title", "body", "person", "timeLabel", "keys",
            "lblTaken", "lblSnooze", "lblClose", "lblHeader", "speech", "lang", "voice", "kind", "code", "alt"};

    private Alarms() { }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_VOICE) == null) {
            NotificationChannel v = new NotificationChannel(CHANNEL_VOICE,
                    c.getString(R.string.channel_voice_name), NotificationManager.IMPORTANCE_HIGH);
            v.setDescription(c.getString(R.string.channel_desc));
            v.setSound(null, null);
            v.enableVibration(false);
            v.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            v.setBypassDnd(true);
            nm.createNotificationChannel(v);
        }
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        // Old v1 channel (plain notification sound) is replaced by a real alarm channel
        try { nm.deleteNotificationChannel("dawa_reminders_v1"); } catch (Exception ignored) { }
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(c.getString(R.string.channel_desc));
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 600, 400, 600, 400, 600});
        ch.enableLights(true);
        ch.setLightColor(0xFF14B8A6);
        ch.setSound(alarmSound(), new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);
    }

    static Uri alarmSound() {
        Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        return u;
    }

    /* ---------------- permission / health checks ---------------- */

    static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return true;
        if (!nm.areNotificationsEnabled()) return false;
        NotificationChannel ch = nm.getNotificationChannel(CHANNEL);
        return ch == null || ch.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    static boolean canFullScreen(Context c) {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm == null || nm.canUseFullScreenIntent();
    }

    static boolean batteryUnrestricted(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        return pm == null || pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /* ---------------- scheduling ---------------- */

    private static Intent fireIntent(Context c, int id, JSONObject item) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_FIRE).putExtra("id", id);
        if (item != null) {
            for (String k : EXTRA_KEYS) i.putExtra(k, item.optString(k));
        }
        return i;
    }

    private static PendingIntent firePi(Context c, int id, Intent i) {
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void setAlarm(Context c, long at, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        if (canExact(c)) {
            // Shown as an alarm clock by the system: most reliable, survives Doze and OEM battery savers best
            PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
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
                    int id = Integer.parseInt(s);
                    am.cancel(firePi(c, id, fireIntent(c, id, null)));
                } catch (NumberFormatException ignored) { }
            }
        }

        StringBuilder ids = new StringBuilder();
        long now = System.currentTimeMillis(), next = 0;
        int count = 0;
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            for (int n = 0; n < arr.length() && count < MAX_ALARMS; n++) {
                JSONObject item = arr.getJSONObject(n);
                long at = item.getLong("at");
                if (at <= now) continue;
                int id = item.getInt("id");
                setAlarm(c, at, firePi(c, id, fireIntent(c, id, item)));
                if (ids.length() > 0) ids.append(',');
                ids.append(id);
                if (next == 0 || at < next) next = at;
                count++;
            }
        } catch (Exception ignored) { }

        prefs.edit().putString("json", json).putString("ids", ids.toString())
                .putInt("count", count).putLong("next", next).apply();
    }

    static void reschedule(Context c) {
        scheduleAll(c, c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", "[]"));
    }

    static int scheduledCount(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("count", 0);
    }

    static long nextAt(Context c) {
        long n = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("next", 0);
        return n > System.currentTimeMillis() ? n : 0;
    }

    /** Re-fires the same alarm payload after some minutes. */
    static void snooze(Context c, Bundle extras, int minutes) {
        int id = extras.getInt("id", 1) ^ 0x20000000;
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_FIRE).putExtras(extras).putExtra("id", id);
        setAlarm(c, System.currentTimeMillis() + minutes * 60_000L, firePi(c, id, i));
    }

    static void testAlarm(Context c, JSONObject item, int seconds) {
        Intent i = fireIntent(c, TEST_ID, item).putExtra("test", true);
        setAlarm(c, System.currentTimeMillis() + seconds * 1000L, firePi(c, TEST_ID, i));
    }

    /* ---------------- "taken" from outside the app ---------------- */

    static synchronized void addPendingMarks(Context c, String keys) {
        if (keys == null || keys.isEmpty()) return;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(p.getString("pendingMarks", "[]"));
            JSONObject o = new JSONObject();
            o.put("keys", keys);
            o.put("at", System.currentTimeMillis());
            arr.put(o);
            p.edit().putString("pendingMarks", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    static synchronized String takePendingMarks(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = p.getString("pendingMarks", "[]");
        p.edit().remove("pendingMarks").apply();
        return s;
    }

    /* ---------------- showing ---------------- */

    private static PendingIntent actionPi(Context c, String action, int reqCode, Bundle extras) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action).putExtras(extras);
        return PendingIntent.getBroadcast(c, reqCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean isAlarm(Bundle x) {
        return !x.getString("keys", "").isEmpty() || x.getBoolean("test", false);
    }

    static boolean wantsVoice(Bundle x) {
        return isAlarm(x) && "1".equals(x.getString("voice", "")) && !x.getString("speech", "").isEmpty();
    }

    /** Alarm notification: full-screen alarm screen + Taken / Snooze buttons. */
    static Notification buildAlarm(Context c, Bundle x, boolean forService) {
        int id = x.getInt("id", 1);
        String title = x.getString("title", "");
        String body = x.getString("body", "");
        Intent full = new Intent(c, AlarmActivity.class).putExtras(x)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(c, id, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, forService ? CHANNEL_VOICE : CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF0F766E)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true);   // alarm screen, even on the lock screen
        if (forService) {
            b.setOngoing(true).setDeleteIntent(actionPi(c, ACTION_DISMISS, id ^ 0x08000000, x));
        } else {
            b.setAutoCancel(true).setTimeoutAfter(10 * 60_000L);
        }
        String keys = x.getString("keys", "");
        if (!keys.isEmpty()) {
            b.addAction(new Notification.Action.Builder((Icon) null, label(x, "lblTaken", "✓"),
                    actionPi(c, ACTION_TAKEN, id ^ 0x40000000, x)).build());
        }
        b.addAction(new Notification.Action.Builder((Icon) null, label(x, "lblSnooze", "10'"),
                actionPi(c, ACTION_SNOOZE, id ^ 0x10000000, x)).build());
        Notification n = b.build();
        if (!forService) n.flags |= Notification.FLAG_INSISTENT; // keep ringing until the user acts
        return n;
    }

    static void show(Context c, Bundle x) {
        ensureChannel(c);
        if (!canNotify(c)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        int id = x.getInt("id", 1);
        if (!isAlarm(x)) {
            // Plain notice (low stock, INR test day): no alarm screen, no endless ringing
            String body = x.getString("body", "");
            PendingIntent open = PendingIntent.getActivity(c, id, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            nm.notify(id, new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat).setColor(0xFF0F766E)
                    .setContentTitle(x.getString("title", "")).setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setAutoCancel(true).setContentIntent(open).build());
            return;
        }
        nm.notify(id, buildAlarm(c, x, false));
    }

    static String label(Bundle x, String key, String fallback) {
        String s = x.getString(key);
        return s == null || s.isEmpty() ? fallback : s;
    }

    static void cancel(Context c, int id) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(id);
    }
}
