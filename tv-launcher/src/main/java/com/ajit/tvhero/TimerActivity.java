package com.ajit.tvhero;

import android.util.TypedValue;
import android.view.View;

/**
 * Countdown timer: pick minutes, start; then pause, +1 min, stop. A ring empties as time runs out and the
 * last three seconds beat. Back asks whether to keep it running in the background.
 */
public class TimerActivity extends ToolActivity {
    private static final int[] PRESETS = {1, 3, 5, 10, 15, 20, 30, 45, 60};
    private int minutes = 5;

    @Override protected ToolsCore.Tool tool() { return ToolsCore.Tool.TIMER; }

    @Override
    protected void render() {
        ToolsCore c = engine.core;
        long now = ToolsEngine.now();
        digitSize(112);
        ring.setVisibility(View.VISIBLE);
        if (c.tool != ToolsCore.Tool.TIMER) {
            title.setText("Timer");
            digits.setTextColor(0xFFFFFFFF);
            digits.setText(clock(minutes * 60_000L, false));
            ring.setColor(0xFFFF9F0A);
            ring.setProgress(minutes / 60f);
            sub.setText("Choose the time, then Start");
            extra.setText("Back keeps a running timer going");
            setButtons(-1,
                    new Btn("− 1", () -> { minutes = Math.max(1, minutes - 1); render(); }),
                    new Btn("+ 1", () -> { minutes = Math.min(180, minutes + 1); render(); }),
                    new Btn(nextPreset() + " min", () -> { minutes = nextPreset(); render(); }),
                    new Btn("Start", () -> engine.startTimer(minutes * 60_000L, "")));
            return;
        }
        long left = c.finished ? 0 : c.timerLeft(now);
        digits.setText(clock(ceilSeconds(left), false));
        ring.setProgress(c.timerMs <= 0 ? 0 : (float) left / c.timerMs);
        if (c.finished) {
            title.setText("Time's up");
            sub.setText(c.label.isEmpty() ? "The timer has finished" : c.label);
            digits.setTextColor(0xFFFF9F0A);
            ring.setColor(0xFFFF9F0A);
            setButtons(0, new Btn("+ 1 min", engine::addMinute), new Btn("Close", this::stopAndClose));
        } else {
            title.setText(c.label.isEmpty() ? "Timer" : c.label);
            digits.setTextColor(c.running ? 0xFFFFFFFF : 0x99FFFFFF);
            ring.setColor(left <= 10_000 ? 0xFFFF453A : 0xFFFF9F0A);
            sub.setText(c.running ? "Ends at " + endsAt(left) : "Paused");
            if (c.running) pulseOnLastSeconds(left);
            setButtons(-1, new Btn(c.running ? "Pause" : "Resume", engine::toggle), new Btn("+ 1 min", engine::addMinute),
                    new Btn("Stop", this::stopAndClose));
        }
        extra.setText("Back: keep it running in the corner, or stop it");
    }

    private int nextPreset() {
        for (int p : PRESETS) if (p > minutes) return p;
        return PRESETS[0];
    }

    private static String endsAt(long leftMs) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.MILLISECOND, (int) leftMs);
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(c.getTime());
    }
}
