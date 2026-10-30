package com.ajit.tvhero;

import android.util.TypedValue;
import android.view.View;

/** Stopwatch with laps; the ring sweeps once a minute. */
public class StopwatchActivity extends ToolActivity {
    @Override protected ToolsCore.Tool tool() { return ToolsCore.Tool.STOPWATCH; }

    @Override
    protected void render() {
        ToolsCore c = engine.core;
        title.setText("Stopwatch");
        digitSize(84);
        ring.setVisibility(View.VISIBLE);
        ring.setColor(0xFF0A84FF);
        if (c.tool != ToolsCore.Tool.STOPWATCH) {
            digits.setText(clock(0, true));
            digits.setTextColor(0xFFFFFFFF);
            ring.setProgress(0);
            sub.setText("");
            extra.setText("");
            setButtons(0, new Btn("Start", engine::startStopwatch));
            return;
        }
        long now = ToolsEngine.now();
        long e = c.elapsed(now);
        digits.setText(clock(e, true));
        ring.setProgress((e % 60_000) / 60_000f);
        digits.setTextColor(c.running ? 0xFFFFFFFF : 0x99FFFFFF);
        sub.setText(c.running ? "" : "Paused");
        StringBuilder laps = new StringBuilder();
        for (int i = c.laps.size() - 1; i >= Math.max(0, c.laps.size() - 4); i--) {
            long prev = i == 0 ? 0 : c.laps.get(i - 1);
            if (laps.length() > 0) laps.append('\n');
            laps.append("Lap ").append(i + 1).append("   ").append(clock(c.laps.get(i) - prev, true))
                    .append("   (").append(clock(c.laps.get(i), true)).append(')');
        }
        extra.setText(laps.toString());
        setButtons(-1, new Btn(c.running ? "Pause" : "Resume", engine::toggle), new Btn("Lap", engine::lap),
                new Btn("Stop", this::stopAndClose));
    }
}
