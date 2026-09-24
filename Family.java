package com.hamza.dawa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Family sharing in the background (app closed):
 *  - "check" alarms on a follower's phone ask the server whether a dose was taken
 *  - "Taken" from a notification / alarm screen / widget is sent to the server right away
 * Failed requests are kept in an outbox and retried later.
 */
final class Family {
    private static final String PREFS = "dawa_family";

    private Family() { }

    static void configure(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("cfg", json).apply();
        async(null, () -> flushOutbox(c));
    }

    private static JSONObject cfg(Context c) {
        try {
            return new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cfg", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static boolean ready(JSONObject cfg) {
        return !cfg.optString("id").isEmpty() && !cfg.optString("secret").isEmpty() && !cfg.optString("url").isEmpty();
    }

    static void async(BroadcastReceiver.PendingResult pr, Runnable r) {
        new Thread(() -> {
            try {
                r.run();
            } catch (Exception ignored) {
            } finally {
                if (pr != null) pr.finish();
            }
        }).start();
    }

    private static String rpc(Context c, String fn, JSONObject args) throws Exception {
        JSONObject cfg = cfg(c);
        if (!ready(cfg)) throw new IllegalStateException("not configured");
        args.put("p_id", cfg.optString("id"));
        args.put("p_secret", cfg.optString("secret"));
        HttpURLConnection con = (HttpURLConnection) new URL(cfg.optString("url") + "/rest/v1/rpc/" + fn).openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(7000);
        con.setReadTimeout(7000);
        con.setDoOutput(true);
        String key = cfg.optString("key");
        con.setRequestProperty("apikey", key);
        con.setRequestProperty("Authorization", "Bearer " + key);
        con.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = con.getOutputStream()) {
            os.write(args.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        InputStream is = code < 400 ? con.getInputStream() : con.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (is != null) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
        }
        con.disconnect();
        String body = bo.toString("UTF-8");
        if (code >= 400) throw new java.io.IOException("HTTP " + code + " " + body);
        return body;
    }

    private static String nowIso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    /* ---------------- follower: was the dose taken? ---------------- */

    static void check(Context c, Bundle x, BroadcastReceiver.PendingResult pr) {
        async(pr, () -> {
            flushOutbox(c);
            String[] keys = x.getString("keys", "").split(",");
            boolean missed;
            boolean unknown = false;
            try {
                JSONObject args = new JSONObject();
                args.put("p_code", x.getString("code", ""));
                JSONArray arr = new JSONArray();
                for (String k : keys) if (!k.isEmpty()) arr.put(k);
                args.put("p_keys", arr);
                JSONObject status = new JSONObject(rpc(c, "fam_status", args));
                missed = false;
                for (String k : keys) {
                    if (!k.isEmpty() && !"taken".equals(status.optString(k)) && !"skipped".equals(status.optString(k))) missed = true;
                }
            } catch (Exception e) {
                missed = true;      // could not confirm: better to warn than to stay silent
                unknown = true;
            }
            if (!missed) return;
            Bundle n = new Bundle();
            n.putInt("id", x.getInt("id", 1));
            n.putString("title", x.getString("title", ""));
            n.putString("body", unknown ? x.getString("alt", x.getString("body", "")) : x.getString("body", ""));
            Alarms.show(c, n);
        });
    }

    /* ---------------- "Taken" from outside the app ---------------- */

    /** Own keys → app + server; "F|code|key" keys (a followed person) → server only. */
    static void handleTaken(Context c, String keysCsv, BroadcastReceiver.PendingResult pr) {
        if (keysCsv == null || keysCsv.isEmpty()) {
            if (pr != null) pr.finish();
            return;
        }
        List<String> own = new ArrayList<>();
        Map<String, JSONArray> remote = new LinkedHashMap<>();
        for (String k : keysCsv.split(",")) {
            if (k.isEmpty()) continue;
            if (k.startsWith("F|")) {
                String[] parts = k.split("\\|", 3);
                if (parts.length < 3) continue;
                JSONArray a = remote.get(parts[1]);
                if (a == null) {
                    a = new JSONArray();
                    remote.put(parts[1], a);
                }
                a.put(parts[2]);
            } else {
                own.add(k);
            }
        }
        if (!own.isEmpty()) {
            String ownCsv = String.join(",", own);
            Alarms.addPendingMarks(c, ownCsv);
            DoseWidget.markTaken(c, ownCsv);
        }
        async(pr, () -> {
            String at = nowIso();
            JSONObject cfg = cfg(c);
            if (!own.isEmpty() && !cfg.optString("code").isEmpty()) {
                JSONArray a = new JSONArray();
                for (String k : own) a.put(k);
                send(c, JSONObject.NULL, a, at);
            }
            for (Map.Entry<String, JSONArray> e : remote.entrySet()) send(c, e.getKey(), e.getValue(), at);
        });
    }

    private static void send(Context c, Object code, JSONArray keys, String at) {
        JSONObject args = new JSONObject();
        try {
            args.put("p_code", code);
            args.put("p_keys", keys);
            args.put("p_status", "taken");
            args.put("p_at", at);
            rpc(c, "fam_mark_many", new JSONObject(args.toString()));
        } catch (Exception e) {
            queue(c, args);
        }
    }

    /* ---------------- outbox ---------------- */

    private static synchronized void queue(Context c, JSONObject args) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray box = new JSONArray(p.getString("outbox", "[]"));
            if (box.length() < 200) box.put(args);
            p.edit().putString("outbox", box.toString()).apply();
        } catch (Exception ignored) { }
    }

    static void flushOutbox(Context c) {
        JSONArray box;
        synchronized (Family.class) {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            try {
                box = new JSONArray(p.getString("outbox", "[]"));
            } catch (Exception e) {
                box = new JSONArray();
            }
            if (box.length() == 0) return;
            p.edit().putString("outbox", "[]").apply();
        }
        for (int i = 0; i < box.length(); i++) {
            JSONObject args = box.optJSONObject(i);
            if (args == null) continue;
            try {
                rpc(c, "fam_mark_many", new JSONObject(args.toString()));
            } catch (Exception e) {
                queue(c, args);
            }
        }
    }
}
