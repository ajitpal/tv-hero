package com.ajit.tvhero;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Home: a short line about whatever has focus, above a tile or under a top-bar button. For an app it is
 * what this profile watched there lately (titles come from the apps' own media sessions, which the TV
 * already keeps for the phone's activity view), when it was last opened and the minutes today; for a
 * button it says what the button does. Only this profile's history is shown, and Guest leaves none.
 */
final class FocusInfo {
    private final Context ctx;
    private final FrameLayout root;
    private final RecyclerView grid;
    private final AppAdapter adapter;
    private final ViewGroup topButtons;
    private final LinearLayout panel;
    private final TextView title, line1, line2;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View pending;
    private final Runnable show = this::showPending;

    static FocusInfo attach(Context ctx, FrameLayout root, RecyclerView grid, AppAdapter adapter, ViewGroup topButtons) {
        FocusInfo f = new FocusInfo(ctx, root, grid, adapter, topButtons);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> f.focused(newFocus));
        return f;
    }

    private FocusInfo(Context ctx, FrameLayout root, RecyclerView grid, AppAdapter adapter, ViewGroup topButtons) {
        this.ctx = ctx;
        this.root = root;
        this.grid = grid;
        this.adapter = adapter;
        this.topButtons = topButtons;
        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setAlpha(0f);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        title = text(24, 0xFFFFFFFF, true);
        line1 = text(17, 0xD9FFFFFF, false);
        line2 = text(15, 0x99FFFFFF, false);
        panel.addView(title);
        panel.addView(line1);
        panel.addView(line2);
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView text(int sp, int color, boolean medium) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setMaxWidth(dp(760));
        t.setShadowLayer(dp(6), 0, dp(1), 0xCC000000);
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private void focused(View v) {
        pending = v;
        handler.removeCallbacks(show);
        panel.animate().cancel();
        panel.animate().alpha(0f).setDuration(90).start();
        // A short pause, so scrolling quickly along the row doesn't flicker text.
        if (v != null) handler.postDelayed(show, 260);
    }

    private void showPending() {
        View v = pending;
        if (v == null || !v.hasFocus() && !v.isFocused()) return;
        String[] info = null;
        boolean below = false;
        if (isInside(v, grid)) {
            View cell = childOf(v, grid);
            int pos = cell == null ? RecyclerView.NO_POSITION : grid.getChildAdapterPosition(cell);
            String pkg = pos == RecyclerView.NO_POSITION ? null : adapter.packageAt(pos);
            if (pkg != null) info = app(pkg, adapter.labelAt(pos));
        } else if (isInside(v, topButtons)) {
            info = button(v);
            below = true;
        }
        if (info == null) return;
        title.setText(info[0]);
        line1.setText(info[1]);
        line1.setVisibility(info[1].isEmpty() ? View.GONE : View.VISIBLE);
        line2.setText(info[2]);
        line2.setVisibility(info[2].isEmpty() ? View.GONE : View.VISIBLE);
        place(v, below);
    }

    /** Above a tile (aligned with it), or under a top-bar button (right-aligned with it). */
    private void place(View v, boolean below) {
        int[] at = new int[2], base = new int[2];
        v.getLocationInWindow(at);
        root.getLocationInWindow(base);
        int x = at[0] - base[0], y = at[1] - base[1];
        panel.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
        int w = panel.getMeasuredWidth(), h = panel.getMeasuredHeight();
        float tx, ty;
        if (below) {
            panel.setGravity(Gravity.END);
            for (int i = 0; i < panel.getChildCount(); i++) ((TextView) panel.getChildAt(i)).setGravity(Gravity.END);
            tx = Math.max(dp(24), Math.min(root.getWidth() - w - dp(24), x + v.getWidth() - w));
            ty = y + v.getHeight() + dp(18);
        } else {
            panel.setGravity(Gravity.START);
            for (int i = 0; i < panel.getChildCount(); i++) ((TextView) panel.getChildAt(i)).setGravity(Gravity.START);
            tx = Math.max(dp(24), Math.min(root.getWidth() - w - dp(24), x));
            ty = Math.max(dp(8), y - h - dp(22));
        }
        panel.setTranslationX(tx);
        panel.setTranslationY(ty + dp(6));
        panel.animate().alpha(1f).translationY(ty).setDuration(220).start();
    }

    // ---------- what to say ----------

    private String[] app(String pkg, String label) {
        Profiles profiles = Profiles.get(ctx);
        String who = profiles.activeId();
        long now = System.currentTimeMillis();
        JSONArray all = Profiles.GUEST.equals(who) ? new JSONArray() : profiles.sessions(now - 7L * 24 * 3600_000);
        Set<String> titles = new LinkedHashSet<>();
        long last = 0, todayMs = 0;
        long midnight = midnight();
        for (int i = all.length() - 1; i >= 0; i--) {
            JSONObject s = all.optJSONObject(i);
            if (s == null || !pkg.equals(s.optString("package")) || !who.equals(s.optString("profile"))) continue;
            last = Math.max(last, s.optLong("end"));
            if (s.optLong("end") >= midnight) todayMs += s.optLong("end") - Math.max(midnight, s.optLong("start"));
            String t = s.optString("title").trim();
            if (!t.isEmpty() && titles.size() < 3) titles.add(t);
        }
        String recent = titles.isEmpty() ? "" : "Recently: " + join(new ArrayList<>(titles));
        List<String> facts = new ArrayList<>();
        if (last > 0) facts.add("Last watched " + ago(now - last));
        if (todayMs >= 60_000) facts.add(todayMs / 60_000 + " min today");
        String hint = facts.isEmpty() ? hint(pkg) : String.join(" · ", facts);
        if (recent.isEmpty()) return new String[]{label, hint, ""};
        return new String[]{label, recent, hint};
    }

    private static String hint(String pkg) {
        switch (pkg) {
            case "com.google.android.youtube.tv": return "Videos, music and live streams";
            case "com.brave.browser": return "YouTube in Brave: no ads, one tab";
            case "in.startv.hotstar": return "Shows, films and live cricket";
            case "com.mitv.tvhome.mitvplus": return "Free live channels";
            case "com.whatsapp": return "Messages and video calls · type with the phone's keyboard";
            case "com.phlox.tvwebbrowser": return "Web browser made for the remote";
            case "com.android.tv": return "Live TV and HDMI inputs";
            case "com.google.android.youtube.tvkids": return "YouTube for children";
            default: return "Not opened this week";
        }
    }

    private String[] button(View v) {
        CharSequence cd = v.getContentDescription();
        String d = cd == null ? "" : cd.toString();
        int id = v.getId();
        ToolsCore c = ToolsEngine.get(ctx).core;
        if (d.startsWith("Profile:")) {
            String name = d.substring(8, d.indexOf('.') > 0 ? d.indexOf('.') : d.length()).trim();
            return new String[]{name + " is watching", "Switch profile, Guest or Parent", "The Parent Menu is here too"};
        }
        if (d.startsWith("Clock screensaver")) return new String[]{"Clock", "The watch screensaver", "Any key brings you back"};
        if (d.startsWith("Tools")) {
            String running = c.tool == ToolsCore.Tool.NONE ? "Timer, Stopwatch and Workout"
                    : (c.running ? "Running: " : "Paused: ") + (c.label.isEmpty() ? c.tool.name().charAt(0) + c.tool.name().substring(1).toLowerCase() : c.label);
            return new String[]{"Tools", running, "Also from the phone: TV tab → Tools"};
        }
        if (id == R.id.btnInputs) return new String[]{"Inputs", "Live TV, HDMI and other sources", ""};
        if (id == R.id.btnWifi) return new String[]{"Network", d.isEmpty() ? "Wi-Fi settings" : d, "Hold for Wi-Fi settings"};
        if (id == R.id.btnSettings) return new String[]{"Settings", "TV settings", Profiles.get(ctx).activeKid() != null ? "Parent only" : ""};
        if (id == R.id.btnLibraryTop) return new String[]{"All Apps", "Everything this profile can open", ""};
        return d.isEmpty() ? null : new String[]{d, "", ""};
    }

    // ---------- helpers ----------

    private static boolean isInside(View v, View parent) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) if (p == parent) return true;
        return false;
    }

    private static View childOf(View v, ViewGroup parent) {
        View p = v;
        while (p != null && p.getParent() != parent) p = p.getParent() instanceof View ? (View) p.getParent() : null;
        return p;
    }

    private static String join(List<String> t) {
        StringBuilder s = new StringBuilder();
        for (String x : t) {
            if (s.length() > 0) s.append("  ·  ");
            s.append('“').append(x.length() > 48 ? x.substring(0, 47) + "…" : x).append('”');
        }
        return s.toString();
    }

    static String ago(long ms) {
        long m = ms / 60_000;
        if (m < 2) return "just now";
        if (m < 60) return m + " min ago";
        long h = m / 60;
        if (h < 24) return h + (h == 1 ? " hour ago" : " hours ago");
        long d = h / 24;
        return d == 1 ? "yesterday" : d + " days ago";
    }

    private static long midnight() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private int dp(int v) { return Math.round(v * ctx.getResources().getDisplayMetrics().density); }
}
