package com.ajit.tvhero;

import android.graphics.drawable.GradientDrawable;
import android.widget.ProgressBar;

import java.util.List;

/**
 * Workout (HIIT) timer: pick a preset (or start one sent from the phone), then each phase fills the screen
 * in its own colour with a big countdown, the round and set, what's next, and a bar for the whole workout.
 */
public class WorkoutActivity extends ToolActivity {
    @Override protected ToolsCore.Tool tool() { return ToolsCore.Tool.WORKOUT; }

    private ProgressBar bar;
    private int preset;
    private final List<Workout.Spec> presets = Workout.presets();
    private int lastBg;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xE6FFFFFF));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x40FFFFFF));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(dp(640), dp(8));
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(28);
        column.addView(bar, 3, lp);
    }

    @Override
    protected void render() {
        ToolsCore c = engine.core;
        long now = ToolsEngine.now();
        if (c.tool != ToolsCore.Tool.WORKOUT) {
            Workout.Spec s = presets.get(preset);
            background(0xFF05070C);
            title.setText("Workout");
            digits.setText(s.name);
            digitSize(72);
            digits.setTextColor(0xFFFFFFFF);
            ring.setVisibility(android.view.View.GONE);
            int total = Workout.totalSeconds(Workout.plan(s));
            sub.setText(summary(s) + " · " + clock(total * 1000L, false));
            bar.setVisibility(android.view.View.GONE);
            extra.setText("Build your own on the phone: TV tab → Tools → Workout");
            setButtons(-1,
                    new Btn("◀", () -> { preset = (preset + presets.size() - 1) % presets.size(); render(); }),
                    new Btn("▶", () -> { preset = (preset + 1) % presets.size(); render(); }),
                    new Btn("Start", () -> engine.startWorkout(presets.get(preset))));
            return;
        }
        digitSize(170);
        ring.setVisibility(android.view.View.VISIBLE);
        ring.setColor(0xF2FFFFFF);
        bar.setVisibility(android.view.View.VISIBLE);
        long total = Math.max(1, c.totalMs());
        bar.setProgress((int) Math.min(1000, c.elapsed(now) * 1000 / total));
        int i = c.phaseIndex(now);
        if (c.finished || i >= c.phases.size()) {
            background(0xFF0E3B2E);
            title.setText(c.label);
            digits.setText("Done!");
            digitSize(110);
            digits.setTextColor(0xFFFFFFFF);
            sub.setText("Workout complete · " + clock(c.totalMs(), false));
            extra.setText("");
            ring.setProgress(1f);
            setButtons(0, new Btn("Again", () -> engine.startWorkout(c.spec)), new Btn("Close", this::stopAndClose));
            return;
        }
        Workout.Phase p = c.phases.get(i);
        background(color(p.kind));
        title.setText(p.label);
        long left = c.phaseLeft(now);
        digits.setText(String.valueOf(ceilSeconds(left) / 1000));
        ring.setProgress(p.seconds <= 0 ? 0 : left / (p.seconds * 1000f));
        if (c.running) pulseOnLastSeconds(left);
        digits.setTextColor(c.running ? 0xFFFFFFFF : 0x99FFFFFF);
        String where = p.kind.equals(Workout.PREPARE) ? c.label
                : "Round " + p.round + " of " + p.rounds + (p.sets > 1 ? " · Set " + p.set + " of " + p.sets : "");
        sub.setText(c.running ? where : "Paused · " + where);
        Workout.Phase next = i + 1 < c.phases.size() ? c.phases.get(i + 1) : null;
        extra.setText((next == null ? "Last one!" : "Next: " + next.label + " " + next.seconds + " s")
                + "   ·   " + clock(c.totalMs() - c.elapsed(now), false) + " left");
        setButtons(-1, new Btn(c.running ? "Pause" : "Resume", engine::toggle), new Btn("Skip", engine::skip),
                new Btn("Stop", this::stopAndClose));
    }

    private static String summary(Workout.Spec s) {
        return s.work + " s work · " + (s.rest > 0 ? s.rest + " s rest · " : "") + s.rounds + " rounds"
                + (s.sets > 1 ? " × " + s.sets + " sets" : "");
    }

    private static int color(String kind) {
        switch (kind) {
            case Workout.WORK: return 0xFFB3261E;      // deep red
            case Workout.REST: return 0xFF0B6E5F;      // teal
            case Workout.SET_REST: return 0xFF4B2A7B;  // purple
            default: return 0xFF1F3A8A;                // get ready: blue
        }
    }

    private android.animation.ValueAnimator fade;

    /** Each phase's colour fades in over half a second instead of switching. */
    private void background(int color) {
        if (color == lastBg) return;
        int from = lastBg == 0 ? 0xFF05070C : lastBg;
        lastBg = color;
        if (fade != null) fade.cancel();
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, darker(from)});
        root.setBackground(g);
        android.animation.ArgbEvaluator mix = new android.animation.ArgbEvaluator();
        fade = android.animation.ValueAnimator.ofFloat(0f, 1f).setDuration(500);
        fade.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            int c = (int) mix.evaluate(f, from, color);
            g.setColors(new int[]{c, darker(c)});
        });
        fade.start();
    }

    private static int darker(int c) {
        int r = (c >> 16 & 0xff) / 3, g = (c >> 8 & 0xff) / 3, b = (c & 0xff) / 3;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
