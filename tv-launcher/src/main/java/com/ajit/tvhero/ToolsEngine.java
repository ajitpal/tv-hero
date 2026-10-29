package com.ajit.tvhero;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs the TV's tools (ToolsCore) in the app's process: ticks while something runs, plays the beeps
 * (briefly ducking any music under them), pauses when a lock screen comes up, and gives the phone its
 * view of the state. Screens (TimerActivity…) only draw it, so a timer keeps going after Back.
 */
final class ToolsEngine {
    private static ToolsEngine instance;

    static synchronized ToolsEngine get(Context ctx) {
        if (instance == null) instance = new ToolsEngine(ctx.getApplicationContext());
        return instance;
    }

    final ToolsCore core = new ToolsCore();
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private ToneGenerator tones;
    private AudioFocusRequest duck;
    private final Runnable tick = this::tick;

    private ToolsEngine(Context app) { this.app = app; }

    static long now() { return SystemClock.elapsedRealtime(); }

    // ---------- commands (main thread) ----------

    void startTimer(long ms, String label) { core.startTimer(ms, label, now()); changed(); }

    void startStopwatch() { core.startStopwatch(now()); changed(); }

    void startWorkout(Workout.Spec spec) {
        core.startWorkout(spec, now());
        changed();
        if (!core.phases.isEmpty()) beep(ToneGenerator.TONE_PROP_ACK, 200);
    }

    void pause() { core.pause(now()); changed(); }

    void resume() { core.resume(now()); changed(); }

    void toggle() { if (core.running) pause(); else resume(); }

    void stop() { core.stop(); changed(); }

    void addMinute() { core.addMinute(now()); changed(); }

    void lap() { core.lap(now()); changed(); }

    void skip() { core.skip(now()); changed(); beep(ToneGenerator.TONE_PROP_ACK, 200); }

    /** A lock screen (Time's up, Bedtime, Paused) came up: nothing keeps beeping behind it. */
    void pauseForLock() { if (core.running) pause(); }

    // ---------- listeners ----------

    void listen(Runnable r) { listeners.add(r); }

    void unlisten(Runnable r) { listeners.remove(r); }

    private void changed() {
        main.removeCallbacks(tick);
        if (core.running) main.postDelayed(tick, 100);
        for (Runnable r : listeners) r.run();
    }

    private void tick() {
        List<ToolsCore.Event> events = core.poll(now());
        for (ToolsCore.Event e : events) {
            switch (e) {
                case COUNTDOWN: beep(ToneGenerator.TONE_PROP_BEEP, 150); break;
                case PHASE: beep(ToneGenerator.TONE_PROP_ACK, 250); break;
                case DONE: done(); break;
            }
        }
        for (Runnable r : listeners) r.run();
        if (core.running) main.postDelayed(tick, 100);
    }

    private void done() {
        // Three long beeps, half a second apart.
        for (int i = 0; i < 3; i++) main.postDelayed(() -> beep(ToneGenerator.TONE_PROP_BEEP2, 400), i * 600L);
        String what = core.tool == ToolsCore.Tool.TIMER
                ? "Timer done" + (core.label.isEmpty() ? "" : ": " + core.label)
                : "Workout complete: " + core.label;
        GuardService g = GuardService.instance;
        if (g != null) g.toolToast(what);
        // Show the finished screen unless a prompt or lock is up (GuardService decides).
        if (g != null) g.showTool(core.tool == ToolsCore.Tool.TIMER ? TimerActivity.class : WorkoutActivity.class);
    }

    private void beep(int tone, int ms) {
        try {
            AudioManager am = app.getSystemService(AudioManager.class);
            if (am != null) {
                if (duck == null) {
                    duck = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                            .build();
                }
                am.requestAudioFocus(duck);
                main.removeCallbacks(release);
                main.postDelayed(release, ms + 700L);
            }
            if (tones == null) tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            tones.startTone(tone, ms);
        } catch (RuntimeException ignored) {
            // no audio device right now: the screen still shows everything
        }
    }

    private final Runnable release = this::releaseAudio;

    private void releaseAudio() {
        AudioManager am = app.getSystemService(AudioManager.class);
        if (am != null && duck != null) am.abandonAudioFocusRequest(duck);
        if (!core.running && tones != null) { tones.release(); tones = null; }
    }

    // ---------- the phone's view ----------

    JSONObject json() throws JSONException {
        long now = now();
        JSONObject o = new JSONObject().put("tool", core.tool.name().toLowerCase(java.util.Locale.ROOT))
                .put("running", core.running).put("finished", core.finished).put("label", core.label)
                .put("elapsed_ms", core.elapsed(now));
        switch (core.tool) {
            case TIMER:
                o.put("total_ms", core.timerMs).put("left_ms", core.timerLeft(now));
                break;
            case STOPWATCH:
                o.put("laps", new JSONArray(core.laps));
                break;
            case WORKOUT: {
                int i = core.phaseIndex(now);
                o.put("total_ms", core.totalMs()).put("phase_index", i).put("phases", core.phases.size());
                if (i < core.phases.size()) {
                    Workout.Phase p = core.phases.get(i);
                    o.put("phase", new JSONObject().put("kind", p.kind).put("label", p.label).put("seconds", p.seconds)
                            .put("round", p.round).put("rounds", p.rounds).put("set", p.set).put("sets", p.sets))
                            .put("phase_left_ms", core.phaseLeft(now));
                }
                break;
            }
            default:
                break;
        }
        JSONArray presets = new JSONArray();
        for (Workout.Spec s : Workout.presets()) presets.put(specJson(s));
        return new JSONObject().put("state", o).put("presets", presets)
                .put("tools", new JSONArray().put("timer").put("stopwatch").put("workout").put("clock"));
    }

    static JSONObject specJson(Workout.Spec s) throws JSONException {
        return new JSONObject().put("name", s.name).put("prepare", s.prepare).put("work", s.work).put("rest", s.rest)
                .put("rounds", s.rounds).put("sets", s.sets).put("set_rest", s.setRest).put("exercises", new JSONArray(s.exercises));
    }

    static Workout.Spec specFrom(JSONObject o) {
        Workout.Spec s = new Workout.Spec();
        s.name = o.optString("name", "Workout");
        s.prepare = o.optInt("prepare", 10);
        s.work = o.optInt("work", 30);
        s.rest = o.optInt("rest", 30);
        s.rounds = o.optInt("rounds", 8);
        s.sets = o.optInt("sets", 1);
        s.setRest = o.optInt("set_rest", 60);
        JSONArray ex = o.optJSONArray("exercises");
        java.util.List<String> list = new java.util.ArrayList<>();
        if (ex != null) for (int i = 0; i < ex.length(); i++) list.add(ex.optString(i));
        s.exercises = list;
        return s.clamped();
    }

}
