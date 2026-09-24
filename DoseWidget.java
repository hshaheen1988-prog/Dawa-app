package com.hamza.dawa;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
            for (int id : ids) m.updateAppWidget(id, build(c));
        } catch (Exception ignored) { }
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
        for (int id : ids) manager.updateAppWidget(id, build(context));
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

    private static RemoteViews build(Context c) {
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

        String title = labels.optString("title", c.getString(R.string.app_name));
        if (day != null && day.optInt("total") > 0) title += "  ·  " + day.optInt("done") + "/" + day.optInt("total");
        rv.setTextViewText(R.id.w_title, title);

        JSONObject first = null, second = null;
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null || !today.equals(it.optString("date"))) continue;
                if (first == null) first = it; else { second = it; break; }
            }
        }

        if (first == null) {
            String msg;
            if (day == null) msg = labels.optString("open", "…");
            else if (day.optInt("total") == 0) msg = labels.optString("none", "");
            else msg = labels.optString("allDone", "✓");
            rv.setTextViewText(R.id.w_time, "✓");
            rv.setTextViewText(R.id.w_name, msg);
            rv.setViewVisibility(R.id.w_person, View.GONE);
            rv.setViewVisibility(R.id.w_take, View.GONE);
            rv.setViewVisibility(R.id.w_next, View.GONE);
            return rv;
        }

        boolean late = first.optLong("at") < System.currentTimeMillis();
        rv.setTextViewText(R.id.w_time, first.optString("time") + (late ? "  ⚠ " + labels.optString("late", "") : ""));
        rv.setTextViewText(R.id.w_name, first.optString("text"));
        String person = first.optString("person");
        rv.setViewVisibility(R.id.w_person, person.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setTextViewText(R.id.w_person, person);

        String keys = first.optString("keys");
        rv.setViewVisibility(R.id.w_take, View.VISIBLE);
        rv.setTextViewText(R.id.w_take, labels.optString("taken", "✓"));
        Intent take = new Intent(c, DoseWidget.class).setAction(ACTION_TAKE).putExtra("keys", keys);
        rv.setOnClickPendingIntent(R.id.w_take, PendingIntent.getBroadcast(c, keys.hashCode(), take,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        if (second != null) {
            rv.setViewVisibility(R.id.w_next, View.VISIBLE);
            rv.setTextViewText(R.id.w_next, labels.optString("next", "›") + ": " + second.optString("time") + " · "
                    + second.optString("text").replace("\n", " · "));
        } else {
            rv.setViewVisibility(R.id.w_next, View.GONE);
        }
        return rv;
    }
}
