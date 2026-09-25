package com.hamza.dawa;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Home-screen widget: next dose + a "Taken" button that works without opening the app. */
public class DoseWidget extends AppWidgetProvider {
    static final String ACTION_TAKE = "com.hamza.dawa.WIDGET_TAKE";
    private static final String PREFS = "dawa_widget";

    /** Called by the app whenever data changes. */
    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("data", json).apply();
        updateAll(c);
    }

    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, DoseWidget.class));
            if (ids == null || ids.length == 0) return;
            for (int id : ids) m.updateAppWidget(id, build(c, rowsFor(m, id)));
        } catch (Exception ignored) { }
    }

    /** How many dose rows fit the widget's current height. */
    private static int rowsFor(AppWidgetManager m, int id) {
        try {
            Bundle o = m.getAppWidgetOptions(id);
            int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (h == 0) h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180);
            return h < 150 ? 1 : h < 215 ? 2 : 3;
        } catch (Exception e) {
            return 3;
        }
    }

    /** Removes the taken item locally so the widget moves on right away. */
    static synchronized void markTaken(Context c, String keys) {
        if (keys == null || keys.isEmpty()) return;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONObject data = new JSONObject(p.getString("data", "{}"));
            JSONArray items = data.optJSONArray("items");
            JSONArray kept = new JSONArray();
            JSONObject days = data.optJSONObject("days");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    if (keys.equals(it.optString("keys"))) {
                        String date = it.optString("date");
                        if (days != null && days.has(date)) {
                            JSONObject d = days.getJSONObject(date);
                            d.put("done", d.optInt("done") + keys.split(",").length);
                        }
                    } else {
                        kept.put(it);
                    }
                }
            }
            data.put("items", kept);
            p.edit().putString("data", data.toString()).apply();
        } catch (Exception ignored) { }
        updateAll(c);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context, rowsFor(manager, id)));
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle newOptions) {
        manager.updateAppWidget(id, build(context, rowsFor(manager, id)));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TAKE.equals(intent.getAction())) {
            String keys = intent.getStringExtra("keys");
            Family.handleTaken(context, keys, goAsync());
            MainActivity.refreshIfShowing();
        }
    }

    private static final int[] ROWS = {R.id.w_row1, R.id.w_row2, R.id.w_row3};
    private static final int[] TIMES = {R.id.w_time1, R.id.w_time2, R.id.w_time3};
    private static final int[] NAMES = {R.id.w_name1, R.id.w_name2, R.id.w_name3};
    private static final int[] CHECKS = {R.id.w_chk1, R.id.w_chk2, R.id.w_chk3};

    private static RemoteViews build(Context c, int maxRows) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_dose);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.w_root, open);

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        JSONObject data;
        try {
            data = new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", "{}"));
        } catch (Exception e) {
            data = new JSONObject();
        }
        JSONObject labels = data.optJSONObject("labels");
        if (labels == null) labels = new JSONObject();
        JSONObject days = data.optJSONObject("days");
        JSONObject day = days != null ? days.optJSONObject(today) : null;
        JSONArray items = data.optJSONArray("items");

        // Header: title, count and progress
        rv.setTextViewText(R.id.w_title, labels.optString("title", c.getString(R.string.app_name)));
        int total = day != null ? day.optInt("total") : 0, done = day != null ? day.optInt("done") : 0;
        rv.setTextViewText(R.id.w_count, total > 0 ? done + "/" + total : "");
        rv.setProgressBar(R.id.w_prog, 100, total > 0 ? Math.round(done * 100f / total) : 0, false);
        rv.setViewVisibility(R.id.w_prog, total > 0 ? View.VISIBLE : View.GONE);

        // Notes: INR test today / tomorrow, followed family members
        String note = data.optString("note", "");
        rv.setViewVisibility(R.id.w_note, note.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setTextViewText(R.id.w_note, note);

        // Today's remaining doses, earliest (overdue) first
        java.util.List<JSONObject> list = new java.util.ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it != null && today.equals(it.optString("date"))) list.add(it);
            }
        }
        long now = System.currentTimeMillis();
        int shown = Math.min(Math.max(1, maxRows), list.size());
        for (int r = 0; r < ROWS.length; r++) {
            if (r >= shown) {
                rv.setViewVisibility(ROWS[r], View.GONE);
                continue;
            }
            JSONObject it = list.get(r);
            boolean late = it.optLong("at") < now;
            String person = it.optString("person");
            rv.setViewVisibility(ROWS[r], View.VISIBLE);
            rv.setTextViewText(TIMES[r], it.optString("time") + (late ? "  ⚠ " + labels.optString("late", "") : "")
                    + (person.isEmpty() ? "" : "  · " + person));
            rv.setTextColor(TIMES[r], late ? 0xFFFDE68A : 0xFFFFFFFF);
            rv.setTextViewText(NAMES[r], it.optString("text").replace("\n", "  ·  "));
            String keys = it.optString("keys");
            Intent take = new Intent(c, DoseWidget.class).setAction(ACTION_TAKE).putExtra("keys", keys);
            rv.setOnClickPendingIntent(CHECKS[r], PendingIntent.getBroadcast(c, keys.hashCode(), take,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            rv.setOnClickPendingIntent(ROWS[r], open);
        }

        // Empty state
        if (list.isEmpty()) {
            String msg;
            if (day == null) msg = labels.optString("open", "…");
            else if (total == 0) msg = labels.optString("none", "");
            else msg = labels.optString("allDone", "✓");
            rv.setViewVisibility(R.id.w_empty, View.VISIBLE);
            rv.setTextViewText(R.id.w_empty, msg);
        } else {
            rv.setViewVisibility(R.id.w_empty, View.GONE);
        }

        // "+N more" or tomorrow's first dose
        int rest = list.size() - shown;
        String more = "";
        if (rest > 0) {
            more = labels.optString("more", "+{n}").replace("{n}", String.valueOf(rest));
        } else if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it != null && !today.equals(it.optString("date"))) {
                    more = labels.optString("tomorrow", "") + ": " + it.optString("time") + " · " + it.optString("text").replace("\n", " · ");
                    break;
                }
            }
        }
        rv.setViewVisibility(R.id.w_more, more.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setTextViewText(R.id.w_more, more);
        return rv;
    }
}
