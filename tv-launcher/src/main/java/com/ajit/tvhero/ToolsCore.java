package com.ajit.tvhero;

import java.util.ArrayList;
import java.util.List;

/**
 * The state of the TV's tools (one at a time): a countdown timer, a stopwatch with laps, or a workout.
 * Pure Java, driven by the time passed in, so it is unit-tested; ToolsEngine adds the ticking, sounds and
 * the phone's view of it.
 */
final class ToolsCore {
    enum Tool { NONE, TIMER, STOPWATCH, WORKOUT }

    /** Things that happened since the last poll; ToolsEngine turns them into sounds. */
    enum Event { COUNTDOWN, PHASE, DONE }

    Tool tool = Tool.NONE;
    private long startedAt, accumulated;
    boolean running, finished;

    long timerMs;
    String label = "";
    final List<Long> laps = new ArrayList<>();
    List<Workout.Phase> phases = new ArrayList<>();
    Workout.Spec spec;
    private long polledAt;   // elapsed at the last poll

    long elapsed(long now) { return accumulated + (running ? now - startedAt : 0); }

    // ---------- commands ----------

    void startTimer(long ms, String name, long now) {
        reset(Tool.TIMER);
        timerMs = Math.max(1000, Math.min(24 * 3600_000L, ms));
        label = name == null ? "" : name;
        go(now);
    }

    void startStopwatch(long now) {
        reset(Tool.STOPWATCH);
        go(now);
    }

    void startWorkout(Workout.Spec s, long now) {
        reset(Tool.WORKOUT);
        spec = s.clamped();
        phases = Workout.plan(spec);
        label = spec.name;
        go(now);
    }

    void pause(long now) {
        if (!running) return;
        accumulated = elapsed(now);
        running = false;
    }

    void resume(long now) {
        if (running || finished || tool == Tool.NONE) return;
        go(now);
    }

    void stop() { reset(Tool.NONE); }

    /** Timer: one more minute (also after it finished, which starts it again). */
    void addMinute(long now) {
        if (tool != Tool.TIMER) return;
        if (finished) {
            accumulated = timerMs;
            finished = false;
            go(now);
        }
        timerMs = Math.min(24 * 3600_000L, timerMs + 60_000);
    }

    void lap(long now) {
        if (tool == Tool.STOPWATCH && running && laps.size() < 99) laps.add(elapsed(now));
    }

    /** Workout: straight to the next phase. */
    void skip(long now) {
        if (tool != Tool.WORKOUT || finished) return;
        int i = phaseIndex(now);
        long target = phaseStart(i + 1);
        accumulated = Math.min(target, totalMs());
        polledAt = accumulated;   // a skip is announced by the screen, not with a beep for every second skipped
        if (running) startedAt = now;
    }

    private void reset(Tool t) {
        tool = t;
        running = false;
        finished = false;
        accumulated = 0;
        polledAt = 0;
        laps.clear();
        phases = new ArrayList<>();
        spec = null;
        label = "";
        timerMs = 0;
    }

    private void go(long now) {
        startedAt = now;
        running = true;
    }

    // ---------- queries ----------

    long timerLeft(long now) { return Math.max(0, timerMs - elapsed(now)); }

    long totalMs() { return Workout.totalSeconds(phases) * 1000L; }

    long phaseStart(int index) {
        long t = 0;
        for (int i = 0; i < index && i < phases.size(); i++) t += phases.get(i).seconds * 1000L;
        return t;
    }

    /** The phase running at [now]; phases.size() once the workout is over. */
    int phaseIndex(long now) {
        long e = elapsed(now), t = 0;
        for (int i = 0; i < phases.size(); i++) {
            t += phases.get(i).seconds * 1000L;
            if (e < t) return i;
        }
        return phases.size();
    }

    long phaseLeft(long now) {
        int i = phaseIndex(now);
        if (i >= phases.size()) return 0;
        return phaseStart(i + 1) - elapsed(now);
    }

    /**
     * What happened between the last poll and [now]: the last three seconds of a timer or phase
     * (COUNTDOWN, once per second), a new phase (PHASE), the end (DONE, also stops the clock).
     */
    List<Event> poll(long now) {
        List<Event> out = new ArrayList<>();
        if (tool == Tool.NONE || tool == Tool.STOPWATCH || finished) return out;
        long before = polledAt, after = elapsed(now);
        polledAt = after;
        if (after <= before) return out;
        long end = tool == Tool.TIMER ? timerMs : totalMs();
        if (after >= end) {
            out.add(Event.DONE);
            finished = true;
            running = false;
            accumulated = end;
            return out;
        }
        if (tool == Tool.WORKOUT) {
            for (int i = 1; i < phases.size(); i++) {
                long b = phaseStart(i);
                if (before < b && after >= b) out.add(Event.PHASE);
            }
            long boundary = phaseStart(phaseIndex(now) + 1);
            if (crossedCountdown(before, after, boundary)) out.add(Event.COUNTDOWN);
        } else if (crossedCountdown(before, after, end)) {
            out.add(Event.COUNTDOWN);
        }
        return out;
    }

    /** True when a "3", "2" or "1 second left" mark before [boundary] lies in (before, after]. */
    private static boolean crossedCountdown(long before, long after, long boundary) {
        for (int s = 1; s <= 3; s++) {
            long mark = boundary - s * 1000L;
            if (mark > before && mark <= after && mark > 0) return true;
        }
        return false;
    }
}
