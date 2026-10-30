package com.ajit.tvhero;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Base for the tool screens: a dark full-screen page with a title, big digits, a line under them and a
 * row of pill buttons, redrawn ten times a second while visible. Play/Pause on the remote toggles the
 * tool. Back leaves the screen; the tool keeps running (ToolsEngine).
 */
abstract class ToolActivity extends Activity {
    protected ToolsEngine engine;
    protected FrameLayout root;
    protected LinearLayout column, buttons;
    protected TextView title, digits, sub, extra;
    protected RingView ring;
    protected FrameLayout dial;
    /** How many tool screens are on screen; the guard hides the floating mini timer while any is. */
    static int visible;
    private long pulsedSecond = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable redraw = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 100);
        }
    };
    private final Runnable onChange = this::render;
    private String buttonKey = "";

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        engine = ToolsEngine.get(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05070C);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        title = text(26, 0xCCFFFFFF, "sans-serif-medium");
        title.setLetterSpacing(0.12f);
        title.setAllCaps(true);
        digits = text(150, Color.WHITE, null);
        digits.setTypeface(getResources().getFont(R.font.outfit_extralight));
        digits.setIncludeFontPadding(false);
        sub = text(24, 0xB3FFFFFF, null);
        extra = text(18, 0x99FFFFFF, null);
        buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        column.addView(title, gap(0));
        dial = new FrameLayout(this);
        ring = new RingView(this);
        ring.setVisibility(View.GONE);
        dial.addView(ring, new FrameLayout.LayoutParams(dp(400), dp(400), Gravity.CENTER));
        dial.addView(digits, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        column.addView(dial, gap(8));
        column.addView(sub, gap(4));
        column.addView(buttons, gap(40));
        column.addView(extra, gap(24));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);
        // Opening: the page rises in softly.
        column.setAlpha(0f);
        column.setTranslationY(dp(24));
        column.animate().alpha(1f).translationY(0).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        engine.listen(onChange);
        handler.post(redraw);
        visible++;
        GuardService g = GuardService.instance;
        if (g != null) g.updateMini();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(redraw);
        engine.unlisten(onChange);
        visible = Math.max(0, visible - 1);
        super.onPause();
        GuardService g = GuardService.instance;
        if (g != null) g.updateMini();
    }

    /** Draws the current state; called ten times a second and on every change. */
    protected abstract void render();

    /** The tool this screen shows (NONE for the Tools menu): Back asks before leaving it running. */
    protected abstract ToolsCore.Tool tool();

    /**
     * Back: a running or paused tool asks whether to keep it running in the background (with the small
     * timer over other apps) or to stop it and close. A finished or unused tool just closes.
     */
    @Override
    public void onBackPressed() {
        ToolsCore c = engine.core;
        if (tool() == ToolsCore.Tool.NONE || c.tool != tool()) { super.onBackPressed(); return; }
        if (c.finished) { engine.stop(); finish(); return; }
        String name = c.label.isEmpty() ? (tool() == ToolsCore.Tool.TIMER ? "the timer" : "the stopwatch") : c.label;
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Leave " + name + "?")
                .setMessage("Keep it running and it stays in the corner of the screen over other apps.")
                .setPositiveButton("Keep Running", (x, w) -> finish())
                .setNegativeButton("Stop and Close", (x, w) -> { engine.stop(); finish(); })
                .setNeutralButton("Cancel", null)
                .create();
        d.show();
        android.widget.Button keep = d.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        if (keep != null) keep.requestFocus();
    }

    /** Stop: ends the tool and closes its screen. */
    protected void stopAndClose() {
        engine.stop();
        finish();
    }

    /** A gentle beat on each of the last three seconds. */
    protected void pulseOnLastSeconds(long leftMs) {
        long second = (leftMs + 999) / 1000;
        if (second == pulsedSecond) return;
        pulsedSecond = second;
        if (second < 1 || second > 3) return;
        digits.animate().cancel();
        digits.setScaleX(1.14f);
        digits.setScaleY(1.14f);
        digits.animate().scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY
                || k == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
            engine.toggle();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------- building blocks ----------

    static final class Btn {
        final String label;
        final Runnable run;
        Btn(String label, Runnable run) { this.label = label; this.run = run; }
    }

    /**
     * Replaces the buttons only when their labels change, so focus doesn't jump while the digits tick.
     * Focus goes to [focus] (an index) after a change.
     */
    protected void setButtons(int focus, Btn... list) {
        StringBuilder key = new StringBuilder();
        for (Btn b : list) key.append(b.label).append('|');
        if (key.toString().equals(buttonKey)) return;
        int had = -1;
        for (int i = 0; i < buttons.getChildCount(); i++) if (buttons.getChildAt(i).hasFocus()) had = i;
        buttonKey = key.toString();
        buttons.removeAllViews();
        for (Btn b : list) {
            TextView v = pill(b.label, b.run);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(10), 0, dp(10), 0);
            buttons.addView(v, lp);
        }
        int f = had >= 0 && had < list.length && list.length == buttons.getChildCount() && focus < 0 ? had : Math.max(0, focus);
        if (f < buttons.getChildCount()) buttons.getChildAt(f).requestFocus();
    }

    protected TextView pill(String label, Runnable run) {
        TextView b = text(22, Color.WHITE, "sans-serif-medium");
        b.setText(label);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(30), dp(14), dp(30), dp(14));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, round(Color.WHITE));
        bg.addState(new int[]{}, round(0x2EFFFFFF));
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, has) -> ((TextView) v).setTextColor(has ? Color.BLACK : Color.WHITE));
        b.setOnClickListener(v -> run.run());
        return b;
    }

    protected TextView text(int sp, int color, String family) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (family != null) t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return t;
    }

    protected GradientDrawable round(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(32));
        return d;
    }

    protected LinearLayout.LayoutParams gap(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(top);
        return lp;
    }

    protected int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /** 1:05:09, 05:09 or, with tenths, 05:09.4. */
    static String clock(long ms, boolean tenths) {
        long t = Math.max(0, ms);
        long h = t / 3_600_000, m = t / 60_000 % 60, s = t / 1000 % 60, d = t / 100 % 10;
        String base = h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
                : String.format(java.util.Locale.ROOT, "%02d:%02d", m, s);
        return tenths ? base + "." + d : base;
    }

    /** Seconds rounded up, so a countdown shows 3, 2, 1 and reaches 0 only at the end. */
    static long ceilSeconds(long ms) { return (ms + 999) / 1000 * 1000; }
}
