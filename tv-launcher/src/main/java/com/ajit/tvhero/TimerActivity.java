package com.ajit.tvhero;

/** Countdown timer: pick minutes, start; then pause, +1 min, stop. Keeps running after Back. */
public class TimerActivity extends ToolActivity {
    private static final int[] PRESETS = {1, 3, 5, 10, 15, 20, 30, 45, 60};
    private int minutes = 5;

    @Override
    protected void render() {
        ToolsCore c = engine.core;
        long now = ToolsEngine.now();
        if (c.tool != ToolsCore.Tool.TIMER) {
            title.setText("Timer");
            digits.setTextColor(0xFFFFFFFF);
            digits.setText(clock(minutes * 60_000L, false));
            sub.setText("Choose the time, then Start");
            extra.setText("◀ ▶ to choose · OK to press · Back keeps a running timer going");
            setButtons(-1,
                    new Btn("− 1", () -> { minutes = Math.max(1, minutes - 1); render(); }),
                    new Btn("+ 1", () -> { minutes = Math.min(180, minutes + 1); render(); }),
                    new Btn(nextPresetLabel(), () -> { minutes = nextPreset(); render(); }),
                    new Btn("Start", () -> engine.startTimer(minutes * 60_000L, "")));
            return;
        }
        digits.setText(clock(c.finished ? 0 : ceilSeconds(c.timerLeft(now)), false));
        if (c.finished) {
            title.setText("Time's up");
            sub.setText(c.label.isEmpty() ? "The timer has finished" : c.label);
            digits.setTextColor(0xFFFF9F0A);
            setButtons(0, new Btn("+ 1 min", engine::addMinute), new Btn("Done", () -> { engine.stop(); finish(); }));
        } else {
            title.setText(c.label.isEmpty() ? "Timer" : c.label);
            digits.setTextColor(c.running ? 0xFFFFFFFF : 0x99FFFFFF);
            sub.setText(c.running ? "Ends at " + endsAt(c.timerLeft(now)) : "Paused");
            setButtons(-1, new Btn(c.running ? "Pause" : "Resume", engine::toggle), new Btn("+ 1 min", engine::addMinute),
                    new Btn("Stop", () -> { engine.stop(); render(); }));
        }
        extra.setText("Back keeps it running · it beeps at the end, wherever you are");
    }

    private int nextPreset() {
        for (int p : PRESETS) if (p > minutes) return p;
        return PRESETS[0];
    }

    private String nextPresetLabel() { return nextPreset() + " min"; }

    private static String endsAt(long leftMs) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.MILLISECOND, (int) leftMs);
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(c.getTime());
    }
}
