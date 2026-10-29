package com.ajit.tvhero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class QuizTest {
    private static void checkBank(String name, String[] bank) {
        Set<String> seen = new HashSet<>();
        for (String line : bank) {
            String[] p = line.split("\\|");
            assertEquals(name + ": " + line, 6, p.length);
            int level = p[0].charAt(0) - '0';
            assertTrue(name + " level: " + line, level >= 1 && level <= 3);
            Set<String> opts = new HashSet<>(java.util.List.of(p[2], p[3], p[4], p[5]));
            assertEquals(name + " options must differ: " + line, 4, opts.size());
            assertTrue(name + " duplicate: " + line, seen.add(p[1] + p[2]));
        }
        for (int lvl = 1; lvl <= 3; lvl++) {
            int n = 0;
            for (String line : bank) if (line.charAt(0) - '0' <= lvl) n++;
            assertTrue(name + " needs questions at level " + lvl, n >= 5);
        }
    }

    @Test public void banksAreWellFormed() {
        checkBank("english", Quiz.ENGLISH_BANK);
        checkBank("gk", Quiz.GK_BANK);
        checkBank("hindi", Quiz.HINDI_BANK);
        checkBank("puzzles", Quiz.PUZZLE_BANK);
    }

    @Test public void everyQuestionHasFourDistinctOptionsAndOneRightAnswer() {
        Quiz q = new Quiz(new Random(7));
        for (String topic : Quiz.TOPICS) {
            for (int level = 1; level <= 3; level++) {
                for (int i = 0; i < 400; i++) {
                    Quiz.Question x = q.make(topic, level);
                    assertEquals(x.text, 4, x.options.size());
                    assertEquals(x.text + " " + x.options, 4, new HashSet<>(x.options).size());
                    assertTrue(x.text, x.answer >= 0 && x.answer < 4);
                }
            }
        }
    }

    private static int eval(String text) {
        // "a op b = ?", "a + ? = c", "? × b = c", "Double a = ?", "b% of a = ?"
        String t = text.replace("−", "-").replace("×", "*").replace("÷", "/");
        if (t.startsWith("Double ")) return 2 * Integer.parseInt(t.substring(7, t.indexOf(" =")).trim());
        if (t.contains("% of ")) {
            int b = Integer.parseInt(t.substring(0, t.indexOf('%')));
            int a = Integer.parseInt(t.substring(t.indexOf("of ") + 3, t.indexOf(" =")));
            return a * b / 100;
        }
        String[] s = t.split(" ");
        if (s[0].equals("?")) return Integer.parseInt(s[4]) / Integer.parseInt(s[2]);
        if (s[2].equals("?")) return Integer.parseInt(s[4]) - Integer.parseInt(s[0]);
        int a = Integer.parseInt(s[0]), b = Integer.parseInt(s[2]);
        switch (s[1]) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            default: return a / b;
        }
    }

    @Test public void mathsAnswersAreRight() {
        Quiz q = new Quiz(new Random(3));
        for (int level = 1; level <= 3; level++) {
            for (int i = 0; i < 2000; i++) {
                Quiz.Question x = q.maths(level);
                int want = eval(x.text);
                assertEquals(x.text, String.valueOf(want), x.correct());
                assertTrue(x.text, want >= 0);
                for (String o : x.options) assertTrue(x.text + " " + o, Integer.parseInt(o) >= 0);
            }
        }
    }

    @Test public void dayPuzzleIsRight() {
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        Quiz q = new Quiz(new Random(11));
        int checked = 0;
        for (int i = 0; i < 3000; i++) {
            Quiz.Question x = q.puzzle(3);
            if (!x.text.startsWith("Today is ")) continue;
            String today = x.text.substring(9, x.text.indexOf('.'));
            int n = Integer.parseInt(x.text.replaceAll(".* in (\\d+) days?\\?", "$1"));
            int idx = java.util.Arrays.asList(days).indexOf(today);
            assertEquals(x.text, days[(idx + n) % 7], x.correct());
            checked++;
        }
        assertTrue(checked > 100);
    }

    @Test public void topicsAreRespectedAndRepeatsAvoided() {
        Quiz q = new Quiz(new Random(5));
        Set<String> texts = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            Quiz.Question x = q.next(Set.of(Quiz.GK), 3);
            assertEquals(Quiz.GK, x.topic);
            texts.add(x.text);
        }
        assertEquals(20, texts.size());
    }

    @Test public void awardStopsAtTheDailyCap() {
        assertEquals(5, Quiz.award(0, 5, 15));
        assertEquals(5, Quiz.award(10, 5, 15));
        assertEquals(0, Quiz.award(15, 5, 15));
        assertEquals(3, Quiz.award(12, 5, 15));
        assertEquals(0, Quiz.award(20, 5, 15));
    }
}
