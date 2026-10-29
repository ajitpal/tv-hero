package com.ajit.tvhero;

/** Stopwatch with laps. */
public class StopwatchActivity extends ToolActivity {
    @Override
    protected void render() {
        ToolsCore c = engine.core;
        title.setText("Stopwatch");
        if (c.tool != ToolsCore.Tool.STOPWATCH) {
            digits.setText(clock(0, true));
            digits.setTextColor(0xFFFFFFFF);
            sub.setText("");
            extra.setText("");
            setButtons(0, new Btn("Start", engine::startStopwatch));
            return;
        }
        long now = ToolsEngine.now();
        digits.setText(clock(c.elapsed(now), true));
        digits.setTextColor(c.running ? 0xFFFFFFFF : 0x99FFFFFF);
        sub.setText(c.running ? "" : "Paused");
        StringBuilder laps = new StringBuilder();
        for (int i = c.laps.size() - 1; i >= Math.max(0, c.laps.size() - 5); i--) {
            long prev = i == 0 ? 0 : c.laps.get(i - 1);
            if (laps.length() > 0) laps.append('\n');
            laps.append("Lap ").append(i + 1).append("   ").append(clock(c.laps.get(i) - prev, true))
                    .append("   (").append(clock(c.laps.get(i), true)).append(')');
        }
        extra.setText(laps.toString());
        setButtons(-1, new Btn(c.running ? "Pause" : "Resume", engine::toggle), new Btn("Lap", engine::lap),
                new Btn("Reset", () -> { engine.stop(); render(); }));
    }
}
