package com.hamza.dawa;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Rings a medicine alarm and reads it out loud:
 * tone → "Time for your medicine: Warfarin, one and a half tablets, after food" → tone …
 * until the user taps Taken / Snooze / Close, or 5 minutes pass.
 */
public class AlarmService extends Service implements TextToSpeech.OnInitListener {
    private static final long MAX_RING_MS = 5 * 60_000L;
    private static final long TONE_MS = 5_000L;
    private static final long GAP_MS = 2_500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private TextToSpeech tts;
    private boolean ttsReady;
    private Vibrator vibrator;
    private Bundle extras;
    private long startedAt;
    private boolean running;
    private Runnable afterSpeech;
    private int speechToken;

    static void start(Context c, Bundle x) {
        c.startForegroundService(new Intent(c, AlarmService.class).putExtras(x));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, AlarmService.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle x = intent != null ? intent.getExtras() : null;
        if (x == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Alarms.ensureChannel(this);
        Notification n = Alarms.buildAlarm(this, x, true);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(x.getInt("id", 1), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(x.getInt("id", 1), n);
            }
        } catch (Exception e) {
            // Not allowed to run in the foreground right now: fall back to the ringing notification
            Alarms.show(this, x);
            stopSelf();
            return START_NOT_STICKY;
        }

        stopSound();
        extras = x;
        startedAt = System.currentTimeMillis();
        running = true;
        startVibration();
        if (tts == null) {
            tts = new TextToSpeech(getApplicationContext(), this);
        } else {
            applyLanguage();
        }
        cycle();
        return START_NOT_STICKY;
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) return;
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setSpeechRate(0.9f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { handler.post(() -> speechFinished(id)); }
            @Override public void onError(String id) { handler.post(() -> speechFinished(id)); }
        });
        applyLanguage();
    }

    private void applyLanguage() {
        if (tts == null || extras == null) return;
        String lang = extras.getString("lang", "ar");
        int r = tts.setLanguage("en".equals(lang) ? Locale.ENGLISH : new Locale("ar"));
        ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    /** One round: tone, then speech, then a short pause. */
    private void cycle() {
        if (!running) return;
        if (System.currentTimeMillis() - startedAt > MAX_RING_MS) {
            finishRinging();
            return;
        }
        playTone();
        handler.postDelayed(() -> {
            if (!running) return;
            pauseTone();
            speak(() -> handler.postDelayed(this::cycle, GAP_MS));
        }, TONE_MS);
    }

    private void playTone() {
        try {
            if (player == null) {
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                player.setDataSource(this, Alarms.alarmSound());
                player.setLooping(true);
                player.prepare();
            }
            player.seekTo(0);
            player.start();
        } catch (Exception e) {
            releasePlayer();
        }
    }

    private void pauseTone() {
        try {
            if (player != null && player.isPlaying()) player.pause();
        } catch (Exception ignored) { }
    }

    private void speak(Runnable next) {
        String text = extras != null ? extras.getString("speech", "") : "";
        if (!ttsReady || text.isEmpty() || tts == null) {
            next.run();
            return;
        }
        afterSpeech = next;
        final int token = ++speechToken;
        String id = "dawa-" + token;
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id);
        // Safety net in case the engine never reports back
        handler.postDelayed(() -> speechFinished(id), 25_000L);
    }

    private void speechFinished(String id) {
        if (!("dawa-" + speechToken).equals(id)) return;
        speechToken++; // ignore duplicates (onDone + safety net)
        Runnable r = afterSpeech;
        afterSpeech = null;
        if (r != null && running) r.run();
    }

    private void startVibration() {
        try {
            vibrator = getSystemService(Vibrator.class);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 500, 700, 2000}, 0),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            }
        } catch (Exception ignored) { }
    }

    private void stopSound() {
        handler.removeCallbacksAndMessages(null);
        afterSpeech = null;
        pauseTone();
        try { if (tts != null) tts.stop(); } catch (Exception ignored) { }
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) { }
    }

    /** Stops the sound but leaves the notification so the dose can still be marked. */
    private void finishRinging() {
        running = false;
        stopSound();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH);
        stopSelf();
    }

    private void releasePlayer() {
        try { if (player != null) player.release(); } catch (Exception ignored) { }
        player = null;
    }

    @Override
    public void onDestroy() {
        running = false;
        stopSound();
        releasePlayer();
        try { if (tts != null) tts.shutdown(); } catch (Exception ignored) { }
        tts = null;
        super.onDestroy();
    }
}
