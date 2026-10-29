package com.ajit.tvhero;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A workout as a list of timed phases: get ready, then work and rest for each round, a longer rest between
 * sets, and the end. Pure Java (unit-tested); ToolsCore runs it.
 */
final class Workout {
    static final String PREPARE = "prepare", WORK = "work", REST = "rest", SET_REST = "set_rest";

    static final class Phase {
        final String kind, label;
        final int seconds, round, rounds, set, sets;

        Phase(String kind, String label, int seconds, int round, int rounds, int set, int sets) {
            this.kind = kind;
            this.label = label;
            this.seconds = seconds;
            this.round = round;
            this.rounds = rounds;
            this.set = set;
            this.sets = sets;
        }
    }

    /** The settings a workout is built from (also what the phone sends). */
    static final class Spec {
        String name = "Workout";
        int prepare = 10, work = 30, rest = 30, rounds = 8, sets = 1, setRest = 60;
        List<String> exercises = Collections.emptyList();

        Spec copy() {
            Spec s = new Spec();
            s.name = name; s.prepare = prepare; s.work = work; s.rest = rest; s.rounds = rounds; s.sets = sets;
            s.setRest = setRest; s.exercises = exercises;
            return s;
        }

        /** Keeps values sensible: phases up to 10 min, 1–50 rounds, 1–10 sets. */
        Spec clamped() {
            Spec s = copy();
            s.name = name == null || name.trim().isEmpty() ? "Workout" : name.trim().substring(0, Math.min(30, name.trim().length()));
            s.prepare = clamp(prepare, 0, 120);
            s.work = clamp(work, 1, 600);
            s.rest = clamp(rest, 0, 600);
            s.rounds = clamp(rounds, 1, 50);
            s.sets = clamp(sets, 1, 10);
            s.setRest = clamp(setRest, 0, 600);
            List<String> ex = new ArrayList<>();
            if (exercises != null) for (String e : exercises) {
                if (e != null && !e.trim().isEmpty() && ex.size() < 50) ex.add(e.trim().substring(0, Math.min(30, e.trim().length())));
            }
            s.exercises = ex;
            return s;
        }
    }

    /** Built-in presets, shown on the TV and the phone. */
    static List<Spec> presets() {
        List<Spec> out = new ArrayList<>();
        out.add(spec("Tabata", 10, 20, 10, 8, 1, 0));
        out.add(spec("HIIT 30/30", 10, 30, 30, 10, 1, 0));
        out.add(spec("HIIT 45/15", 10, 45, 15, 12, 1, 0));
        out.add(spec("EMOM 10", 10, 60, 0, 10, 1, 0));
        out.add(spec("Circuit 3 × 5", 10, 40, 20, 5, 3, 60));
        return out;
    }

    static Spec spec(String name, int prepare, int work, int rest, int rounds, int sets, int setRest) {
        Spec s = new Spec();
        s.name = name; s.prepare = prepare; s.work = work; s.rest = rest; s.rounds = rounds; s.sets = sets; s.setRest = setRest;
        return s;
    }

    static List<Phase> plan(Spec in) {
        Spec s = in.clamped();
        List<Phase> out = new ArrayList<>();
        if (s.prepare > 0) out.add(new Phase(PREPARE, "Get ready", s.prepare, 0, s.rounds, 1, s.sets));
        int n = 0;
        for (int set = 1; set <= s.sets; set++) {
            for (int round = 1; round <= s.rounds; round++) {
                String label = s.exercises.isEmpty()
                        ? (s.rest == 0 && s.work == 60 ? "Minute " + round : "Work")
                        : s.exercises.get(n % s.exercises.size());
                n++;
                out.add(new Phase(WORK, label, s.work, round, s.rounds, set, s.sets));
                boolean lastOfSet = round == s.rounds;
                if (s.rest > 0 && !lastOfSet) out.add(new Phase(REST, "Rest", s.rest, round, s.rounds, set, s.sets));
            }
            if (set < s.sets && s.setRest > 0) out.add(new Phase(SET_REST, "Set rest", s.setRest, s.rounds, s.rounds, set, s.sets));
        }
        return out;
    }

    static int totalSeconds(List<Phase> phases) {
        int t = 0;
        for (Phase p : phases) t += p.seconds;
        return t;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
