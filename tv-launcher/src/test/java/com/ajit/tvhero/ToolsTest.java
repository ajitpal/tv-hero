package com.ajit.tvhero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ToolsTest {
    @Test public void tabataPlan() {
        List<Workout.Phase> p = Workout.plan(Workout.presets().get(0));
        // get ready + 8 work + 7 rest (no rest after the last round)
        assertEquals(16, p.size());
        assertEquals(Workout.PREPARE, p.get(0).kind);
        assertEquals(Workout.WORK, p.get(1).kind);
        assertEquals(20, p.get(1).seconds);
        assertEquals(Workout.REST, p.get(2).kind);
        assertEquals(Workout.WORK, p.get(15).kind);
        assertEquals(10 + 8 * 20 + 7 * 10, Workout.totalSeconds(p));
    }

    @Test public void setsGetASetRestAndExercisesCycle() {
        Workout.Spec s = Workout.spec("C", 0, 40, 20, 2, 2, 60);
        s.exercises = List.of("Squats", "Push-ups", "Plank");
        List<Workout.Phase> p = Workout.plan(s);
        // set 1: W R W, set rest, set 2: W R W
        assertEquals(7, p.size());
        assertEquals("Squats", p.get(0).label);
        assertEquals("Push-ups", p.get(2).label);
        assertEquals(Workout.SET_REST, p.get(3).kind);
        assertEquals("Plank", p.get(4).label);
        assertEquals("Squats", p.get(6).label);
        assertEquals(2, p.get(6).set);
    }

    @Test public void emomNamesMinutes() {
        List<Workout.Phase> p = Workout.plan(Workout.presets().get(3));
        assertEquals("Minute 1", p.get(1).label);
        assertEquals("Minute 10", p.get(p.size() - 1).label);
    }

    @Test public void clampsSilly() {
        Workout.Spec s = Workout.spec("", -5, 0, -1, 999, 99, 99999).clamped();
        assertEquals("Workout", s.name);
        assertEquals(0, s.prepare);
        assertEquals(1, s.work);
        assertEquals(0, s.rest);
        assertEquals(50, s.rounds);
        assertEquals(10, s.sets);
        assertEquals(600, s.setRest);
    }

    @Test public void timerCountsDownBeepsThenEnds() {
        ToolsCore t = new ToolsCore();
        t.startTimer(10_000, "Tea", 0);
        assertEquals(10_000, t.timerLeft(0));
        assertTrue(t.poll(6_000).isEmpty());
        assertEquals(List.of(ToolsCore.Event.COUNTDOWN), t.poll(7_100));   // 3 s left
        assertEquals(List.of(ToolsCore.Event.COUNTDOWN), t.poll(8_100));   // 2 s left
        assertEquals(List.of(ToolsCore.Event.DONE), t.poll(10_050));
        assertTrue(t.finished);
        assertFalse(t.running);
        assertTrue(t.poll(20_000).isEmpty());
    }

    @Test public void pauseStopsTheClock() {
        ToolsCore t = new ToolsCore();
        t.startTimer(60_000, "", 0);
        t.pause(10_000);
        assertEquals(50_000, t.timerLeft(40_000));
        t.resume(40_000);
        assertEquals(40_000, t.timerLeft(50_000));
    }

    @Test public void addMinuteRestartsAFinishedTimer() {
        ToolsCore t = new ToolsCore();
        t.startTimer(5_000, "", 0);
        t.poll(6_000);
        assertTrue(t.finished);
        t.addMinute(6_000);
        assertTrue(t.running);
        assertEquals(60_000, t.timerLeft(6_000));
    }

    @Test public void workoutPhasesAndSkip() {
        ToolsCore t = new ToolsCore();
        t.startWorkout(Workout.spec("T", 10, 20, 10, 2, 1, 0), 0);   // ready 10, W 20, R 10, W 20
        assertEquals(0, t.phaseIndex(5_000));
        assertEquals(5_000, t.phaseLeft(5_000));
        List<ToolsCore.Event> ev = new ArrayList<>(t.poll(10_500));
        assertTrue(ev.contains(ToolsCore.Event.PHASE));
        assertEquals(1, t.phaseIndex(10_500));
        t.skip(12_000);                       // into the rest
        assertEquals(2, t.phaseIndex(12_000));
        assertEquals(10_000, t.phaseLeft(12_000));
        assertTrue("no beeps for skipped seconds", t.poll(12_100).isEmpty());
        assertEquals(List.of(ToolsCore.Event.DONE), t.poll(60_000));
        assertEquals(t.phases.size(), t.phaseIndex(60_000));
    }

    @Test public void stopwatchLaps() {
        ToolsCore t = new ToolsCore();
        t.startStopwatch(0);
        t.lap(1_500);
        t.lap(4_000);
        assertEquals(List.of(1_500L, 4_000L), t.laps);
        assertTrue(t.poll(9_999_000).isEmpty());
        t.stop();
        assertEquals(ToolsCore.Tool.NONE, t.tool);
    }
}
