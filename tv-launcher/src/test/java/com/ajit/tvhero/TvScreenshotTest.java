package com.ajit.tvhero;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assume.assumeTrue;

/**
 * Renders the TV's own screens (the guard's lock screens and prompts, Earn Time, the Parent Menu and the
 * tools) with sample data to docs/screenshots/tv/, at the TV's 1280 × 720. Names are made up; nothing comes
 * from a real TV. Runs only with -Pscreenshots=record.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-tvdpi")
public class TvScreenshotTest {
    private static final int W = 1280, H = 720;
    private final File dir = new File(System.getProperty("screenshotDir", "build/screenshots"));
    private Activity host;
    private Overlays ui;

    @Before public void setUp() {
        assumeTrue("record".equals(System.getProperty("screenshots")));
        dir.mkdirs();
        host = Robolectric.buildActivity(Activity.class).setup().get();
        ui = new Overlays(host);
    }

    @After public void tearDown() {
        if (ui != null) { ui.hide(); ui.hideMini(); }
        if (host != null) ToolsEngine.get(host).stop();
    }

    private static Overlays.Action a(String label) { return new Overlays.Action(label, () -> { }); }

    private static Profiles.Kid kid(String name, int color) {
        Profiles.Kid k = new Profiles.Kid();
        k.id = name.toLowerCase();
        k.name = name;
        k.color = color;
        return k;
    }

    // ---------- the guard's screens ----------

    @Test public void whosWatching() throws Exception {
        List<Profiles.Kid> kids = Arrays.asList(kid("Aarav", 0), kid("Anya", 1), kid("Kabir", 2));
        ui.picker(kids, k -> { }, () -> { }, () -> { }, () -> { });
        overlay("04-whos-watching");
    }

    @Test public void timesUp() throws Exception {
        ui.message(Overlays.Kind.TIMES_UP, "⏰", "That's all the TV for today, Aarav",
                "Your 90 minutes are used up. Answer questions to earn up to 15 more minutes, or ask a parent.", null,
                a("⭐ Earn Time"), a("Switch Profile"), a("Ask for Time"), a("Parent PIN"));
        overlay("05-times-up-earn-time");
    }

    @Test public void question() throws Exception {
        ui.quiz("🧩  Puzzles · Medium", "What comes next?  2, 4, 8, 16, …", Arrays.asList("24", "32", "18", "30"),
                "⭐ 5 of 15 bonus minutes earned today", i -> { }, () -> { });
        overlay("06-earn-time-question");
    }

    @Test public void rightAnswer() throws Exception {
        ui.message(Overlays.Kind.QUIZ, "🎉", "Brilliant!", "+5 minutes of TV. ⭐ 10 of 15 bonus minutes earned today", () -> { },
                a("Next Question"), a("Watch TV"));
        overlay("07-earn-time-correct");
    }

    @Test public void wrongAnswer() throws Exception {
        ui.message(Overlays.Kind.QUIZ, "🤔", "Not quite: it's Delhi", "No minutes lost. Have another go!", () -> { },
                a("Next Question"), a("Back"));
        overlay("08-earn-time-not-quite");
    }

    @Test public void bedtime() throws Exception {
        ui.message(Overlays.Kind.BEDTIME, "🌙", "It's bedtime, Anya", "The TV turns off in a minute. Good night!", null,
                a("Switch Profile"), a("Ask for Time"), a("Parent PIN"));
        overlay("09-bedtime");
    }

    @Test public void paused() throws Exception {
        ui.message(Overlays.Kind.LOCKED, "⏸", "The TV is paused", "A parent paused the TV from their phone.", null, a("Parent PIN"));
        overlay("10-paused");
    }

    @Test public void waitingForApproval() throws Exception {
        ui.message(Overlays.Kind.BLOCKED, "🔒", "Racing Game is waiting for approval",
                "This app was just installed. A parent can approve it in the tv-hero app on their phone.", () -> { }, a("OK"));
        overlay("11-app-waiting-for-approval");
    }

    @Test public void notOnList() throws Exception {
        ui.message(Overlays.Kind.BLOCKED, "🔒", "Settings isn't on Kabir's list", "Settings and app installs are for parents.",
                () -> { }, a("OK"), a("Switch to Parent"));
        overlay("12-parents-only");
    }

    @Test public void parentMenu() throws Exception {
        List<Overlays.Item> items = Arrays.asList(
                new Overlays.Item("Switch to Parent", "All apps", null),
                new Overlays.Item("Profiles", "3 profiles", null),
                new Overlays.Item("Apps for Everyone", "4 apps", null),
                new Overlays.Item("When the TV Turns On", "Ask Who's Watching", null),
                new Overlays.Item("Parent PIN", "Change", null),
                new Overlays.Item("Who Can Control This TV", "2 phones", null),
                new Overlays.Item("Internet Control", "Off", null),
                new Overlays.Item("Profile Switches", "1 a day", null),
                new Overlays.Item("Guest Time", "30 min a day", null));
        ui.menu(Overlays.Kind.MENU, "Parent Menu", "Everything here can also be done from the tv-hero app on your phone.",
                items, () -> { });
        overlay("13-parent-menu");
    }

    @Test public void parentPin() throws Exception {
        ui.pin("Parent PIN: 30 more minutes", p -> true, () -> { });
        overlay("14-parent-pin");
    }

    @Test public void pairing() throws Exception {
        ui.message(Overlays.Kind.PAIRING, "📱", "482 917",
                "“Mum's phone” wants to control this TV.\nAllow it only if the phone shows this same code.\n"
                        + "If the codes differ, or you didn't start this, choose Don't Allow.", () -> { },
                a("Don't Allow"), a("Allow"));
        overlay("15-pairing-code");
    }

    // ---------- tools ----------

    @Test public void tools() throws Exception {
        activity(ToolsActivity.class, "16-tools", null);
    }

    @Test public void timer() throws Exception {
        activity(TimerActivity.class, "17-timer", e -> e.startTimer(10 * 60_000L, "Homework"), 3 * 60_000L + 400, 0);
    }

    @Test public void workout() throws Exception {
        activity(WorkoutActivity.class, "18-workout-tabata", e -> e.startWorkout(Workout.presets().get(0)), 10_000L + 6_400, 0);
    }

    /** The corner status of a running workout over a video in another app. */
    @Test public void cornerTimer() throws Exception {
        ui.mini("Work   14   ·   3/8", 0xFFFF453A);
        idle(600);
        View mini = field("mini");
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        drawVideoFrame(c);
        int ws = View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.AT_MOST);
        mini.measure(ws, View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.AT_MOST));
        mini.layout(0, 0, mini.getMeasuredWidth(), mini.getMeasuredHeight());
        mini.setAlpha(1f);
        c.save();
        c.translate(W - ui.dp(36) - mini.getMeasuredWidth(), ui.dp(28));
        mini.draw(c);
        c.restore();
        save(bmp, "19-corner-timer-over-video");
    }

    // ---------- helpers ----------

    private interface Setup { void run(ToolsEngine e); }

    private void activity(Class<? extends Activity> cls, String name, Setup setup) throws Exception {
        activity(cls, name, setup, 0, 0);
    }

    private void activity(Class<? extends Activity> cls, String name, Setup setup, long advance, int focus) throws Exception {
        Activity act = Robolectric.buildActivity(cls).setup().get();
        ToolsEngine e = ToolsEngine.get(act);
        if (setup != null) setup.run(e);
        idle(advance > 0 ? advance : 800);
        idle(800);
        View root = act.getWindow().getDecorView();
        select(root, focus);
        idle(300);
        save(draw(root), name);
        e.stop();
    }

    private void overlay(String name) throws Exception {
        overlay(name, 0);
    }

    /** As on the TV, the [focus]-th button (or row) is selected: white, ready for OK. */
    private void overlay(String name, int focus) throws Exception {
        View root = field("full");
        select(root, focus);
        idle(800);
        save(draw(root), name);
    }

    /** Robolectric runs in touch mode, where TV buttons can't take focus: let the chosen one take it. */
    private static void select(View root, int index) {
        java.util.ArrayList<View> found = new java.util.ArrayList<>();
        collect(root, found);
        if (index < 0 || index >= found.size()) return;
        View v = found.get(index);
        v.setFocusableInTouchMode(true);
        v.requestFocus();
    }

    private static void collect(View v, java.util.List<View> out) {
        if (v.isFocusable() && v.getVisibility() == View.VISIBLE && !(v instanceof android.view.ViewGroup && ((android.view.ViewGroup) v).getChildCount() > 0 && !v.isClickable())) out.add(v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    private <T> T field(String f) throws Exception {
        Field fl = Overlays.class.getDeclaredField(f);
        fl.setAccessible(true);
        @SuppressWarnings("unchecked") T v = (T) fl.get(ui);
        if (v == null) throw new AssertionError("overlay not shown: " + f);
        return v;
    }

    private static void idle(long ms) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS);
    }

    private static Bitmap draw(View v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.rgb(5, 7, 12));
        v.draw(c);
        return bmp;
    }

    private void save(Bitmap bmp, String name) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    /** A made-up nature video, with a player's progress bar: stands in for any app the corner timer floats over. */
    private static void drawVideoFrame(Canvas c) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, H, new int[]{0xFF2B4C7E, 0xFF7FA6C9, 0xFFF2C6A0}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(880, 300, 260, 0xAAFFE7B8, 0x00FFE7B8, Shader.TileMode.CLAMP));
        c.drawCircle(880, 300, 260, p);
        p.setShader(null);
        p.setColor(0xFFFFF1D6);
        c.drawCircle(880, 300, 46, p);
        int[] hills = {0xFF3E5C76, 0xFF2C4357, 0xFF1C2B38};
        float[][] tops = {{0, 430, 300, 360, 620, 420, 980, 340, 1280, 410}, {0, 520, 260, 470, 560, 530, 900, 460, 1280, 520}, {0, 610, 400, 560, 800, 620, 1100, 580, 1280, 600}};
        for (int i = 0; i < 3; i++) {
            Path path = new Path();
            float[] t = tops[i];
            path.moveTo(0, H);
            path.lineTo(t[0], t[1]);
            for (int j = 2; j < t.length; j += 2) path.quadTo((t[j - 2] + t[j]) / 2, Math.min(t[j - 1], t[j + 1]) - 30, t[j], t[j + 1]);
            path.lineTo(W, H);
            path.close();
            p.setColor(hills[i]);
            c.drawPath(path, p);
        }
        p.setShader(new LinearGradient(0, H - 170, 0, H, 0x00000000, 0xCC000000, Shader.TileMode.CLAMP));
        c.drawRect(0, H - 170, W, H, p);
        p.setShader(null);
        p.setColor(0x55FFFFFF);
        c.drawRoundRect(new RectF(60, H - 64, W - 60, H - 58), 3, 3, p);
        p.setColor(0xFFFFFFFF);
        c.drawRoundRect(new RectF(60, H - 64, 60 + (W - 120) * 0.38f, H - 58), 3, 3, p);
        c.drawCircle(60 + (W - 120) * 0.38f, H - 61, 9, p);
        p.setTextSize(30);
        c.drawText("Morning Stretch for Kids", 60, H - 92, p);
        p.setTextSize(20);
        p.setColor(0xB3FFFFFF);
        c.drawText("12:40 / 33:15", W - 200, H - 92, p);
    }
}
