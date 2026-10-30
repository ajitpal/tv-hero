package com.ajit.tvhero;

import android.content.Intent;

/** Tools: Timer, Stopwatch, Workout and the clock screensaver. Also the "tv-hero Tools" entry on TVs that keep their own home. */
public class ToolsActivity extends ToolActivity {
    @Override protected ToolsCore.Tool tool() { return ToolsCore.Tool.NONE; }

    @Override
    protected void render() {
        ToolsCore c = engine.core;
        title.setText("Tools");
        digits.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 64);
        digits.setText(c.tool == ToolsCore.Tool.NONE ? "What do you need?" : running(c));
        sub.setText(c.tool == ToolsCore.Tool.NONE ? "" : "Running in the background");
        extra.setText("The phone can start and control these too: TV tab → Tools");
        setButtons(-1,
                new Btn("Timer", () -> open(TimerActivity.class)),
                new Btn("Stopwatch", () -> open(StopwatchActivity.class)),
                new Btn("Workout", () -> open(WorkoutActivity.class)),
                new Btn("Clock", () -> open(ClockActivity.class)));
    }

    private String running(ToolsCore c) {
        long now = ToolsEngine.now();
        switch (c.tool) {
            case TIMER: return "Timer " + clock(ceilSeconds(c.timerLeft(now)), false);
            case STOPWATCH: return "Stopwatch " + clock(c.elapsed(now), false);
            default: return c.label + " " + clock(c.totalMs() - c.elapsed(now), false);
        }
    }

    private void open(Class<?> c) {
        startActivity(new Intent(this, c));
    }
}
