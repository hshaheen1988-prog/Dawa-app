package com.hamza.dawa;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/** Full-screen alarm shown over the lock screen: who, which medicines, how much. */
public class AlarmActivity extends Activity {
    private static WeakReference<AlarmActivity> current = new WeakReference<>(null);
    private Bundle extras;

    static void finishIfShowing() {
        AlarmActivity a = current.get();
        if (a != null) a.runOnUiThread(a::finish);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new WeakReference<>(this);
        wakeUp();
        extras = getIntent().getExtras() != null ? getIntent().getExtras() : new Bundle();
        setContentView(buildUi());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getExtras() != null) extras = intent.getExtras();
        setContentView(buildUi());
    }

    @Override
    protected void onDestroy() {
        if (current.get() == this) current.clear();
        super.onDestroy();
    }

    private void wakeUp() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = getSystemService(KeyguardManager.class);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF0F766E);
        getWindow().setNavigationBarColor(0xFF0D9488);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View buildUi() {
        boolean rtl = TextUtils.getLayoutDirectionFromLocale(java.util.Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
                || containsArabic(extras.getString("body", ""));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(32));
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0F766E, 0xFF0D9488, 0xFF115E59});
        root.setBackground(bg);

        TextView clock = text(extras.getString("timeLabel", ""), 56, Color.WHITE, true);
        root.addView(clock);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x26FFFFFF);
        icon.setBackground(circle);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(120), dp(120));
        ip.topMargin = dp(16);
        ip.bottomMargin = dp(16);
        root.addView(icon, ip);

        root.addView(text(Alarms.label(extras, "lblHeader", "💊"), 16, 0xDDFFFFFF, false));
        TextView who = text(extras.getString("person", ""), 26, Color.WHITE, true);
        who.setPadding(0, dp(2), 0, dp(18));
        root.addView(who);

        // Medicines card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(22));
        cardBg.setColor(Color.WHITE);
        card.setBackground(cardBg);
        String[] lines = extras.getString("body", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            TextView line = text(lines[i], 20, 0xFF13201E, true);
            line.setGravity(Gravity.START);
            line.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            line.setPadding(0, dp(i == 0 ? 0 : 10), 0, 0);
            card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(card);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, sp);

        String keys = extras.getString("keys", "");
        if (!keys.isEmpty()) {
            Button taken = button(Alarms.label(extras, "lblTaken", "✓"), Color.WHITE, 0xFF0F766E);
            taken.setOnClickListener(v -> {
                Alarms.addPendingMarks(this, keys);
                DoseWidget.markTaken(this, keys);
                done();
            });
            root.addView(taken, buttonLp(dp(20)));
        }
        Button snooze = button(Alarms.label(extras, "lblSnooze", "10'"), 0x33FFFFFF, Color.WHITE);
        snooze.setOnClickListener(v -> {
            Alarms.snooze(this, extras, 10);
            done();
        });
        root.addView(snooze, buttonLp(dp(12)));

        Button close = button(Alarms.label(extras, "lblClose", "×"), Color.TRANSPARENT, 0xCCFFFFFF);
        close.setOnClickListener(v -> done());
        root.addView(close, buttonLp(dp(4)));
        return root;
    }

    private void done() {
        AlarmService.stop(this);
        Alarms.cancel(this, extras.getInt("id", 1));
        // Open the app so the dose shows as taken right away
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km == null || !km.isKeyguardLocked()) startActivity(open);
        finish();
    }

    private static boolean containsArabic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x0600 && ch <= 0x06FF) return true;
        }
        return false;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private Button button(String s, int bgColor, int textColor) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(textColor);
        b.setStateListAnimator(null);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(18));
        d.setColor(bgColor);
        b.setBackground(d);
        return b;
    }

    private LinearLayout.LayoutParams buttonLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        lp.topMargin = top;
        return lp;
    }

    @Override
    public void onBackPressed() {
        // Back just hides the screen; the notification keeps the alarm available
        finish();
    }
}
