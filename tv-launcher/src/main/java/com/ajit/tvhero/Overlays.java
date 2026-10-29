package com.ajit.tvhero;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The guard's own screens, drawn as accessibility overlays: they appear at once (no activity start
 * that Android may hold back), stay above every app, and take the remote's keys while shown.
 * Plain views only: this TV has 1 GB of RAM.
 */
final class Overlays {
    /** What a full-screen overlay is for; the guard hides it when the reason goes away. */
    enum Kind { NONE, BLOCKED, TIMES_UP, BEDTIME, LOCKED, PICKER, PIN, PAIRING, CONFIRM, MENU }

    static final int[] AVATAR = {0xFF3D7BFF, 0xFFE5484D, 0xFF30A46C, 0xFFE8B931, 0xFF8E4EC6,
            0xFFF76B15, 0xFF12A594, 0xFFD6409F, 0xFF6E56CF, 0xFF0090FF};
    private static final int BG = Color.argb(245, 5, 7, 12);

    private final Context ctx;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private View full, toast;
    private Kind kind = Kind.NONE;

    Overlays(Context ctx) {
        this.ctx = ctx;
        this.wm = ctx.getSystemService(WindowManager.class);
    }

    Kind kind() { return kind; }

    boolean showing() { return full != null; }

    // ---------- building blocks ----------

    static final class Action {
        final String label;
        final Runnable run;
        Action(String label, Runnable run) { this.label = label; this.run = run; }
    }

    /** Title, message and up to three buttons; Back runs [onBack] when given. */
    void message(Kind k, String emoji, String title, String body, Runnable onBack, Action... actions) {
        LinearLayout box = column();
        if (emoji != null) box.addView(text(emoji, 56, Color.WHITE, false));
        box.addView(text(title, 32, Color.WHITE, true), gap(12));
        if (body != null) {
            TextView b = text(body, 18, Color.argb(200, 255, 255, 255), false);
            b.setMaxWidth(dp(620));
            box.addView(b, gap(12));
        }
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER);
        View first = null;
        for (Action a : actions) {
            TextView btn = button(a.label, () -> a.run.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(10), 0, dp(10), 0);
            row.addView(btn, lp);
            if (first == null) first = btn;
        }
        if (actions.length > 0) box.addView(row, gap(36));
        showFull(k, box, onBack);
        if (first != null) first.requestFocus();
    }

    /** Who's watching? One round avatar per kid, plus Parent (PIN). */
    void picker(List<Profiles.Kid> kids, java.util.function.Consumer<Profiles.Kid> onKid, Runnable onParent) {
        picker(kids, onKid, null, onParent, null);
    }

    /** Who's watching? Kids, then Guest, then Parent (PIN), and a small Parent Menu entry below. */
    void picker(List<Profiles.Kid> kids, java.util.function.Consumer<Profiles.Kid> onKid, Runnable onGuest,
                Runnable onParent, Runnable onMenu) {
        picker(kids, onKid, onGuest, onParent, onMenu, null);
    }

    /** [onBack] null: Back does nothing (a choice is needed, e.g. when the TV wakes). */
    void picker(List<Profiles.Kid> kids, java.util.function.Consumer<Profiles.Kid> onKid, Runnable onGuest,
                Runnable onParent, Runnable onMenu, Runnable onBack) {
        LinearLayout box = column();
        box.addView(text("Who's watching?", 36, Color.WHITE, true));
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER);
        View first = null;
        for (Profiles.Kid k : kids) {
            View v = avatar(k.name, AVATAR[Math.floorMod(k.color, AVATAR.length)], () -> onKid.accept(k));
            row.addView(v);
            if (first == null) first = v;
        }
        if (onGuest != null) row.addView(avatar("Guest", 0xFF5A5F6B, onGuest));
        View parent = avatar("Parent", 0xFF3A3F4B, onParent);
        row.addView(parent);
        if (first == null) first = parent;
        box.addView(row, gap(40));
        if (onMenu != null) box.addView(button("Parent Menu", onMenu), gap(28));
        showFull(Kind.PICKER, box, onBack);
        first.requestFocus();
    }

    /** A number pad for the parent PIN. [onPin] returns false for a wrong PIN. */
    void pin(String title, java.util.function.Predicate<String> onPin, Runnable onCancel) {
        LinearLayout box = column();
        box.addView(text(title, 30, Color.WHITE, true));
        TextView dots = text("○ ○ ○ ○", 34, Color.WHITE, false);
        dots.setLetterSpacing(0.2f);
        box.addView(dots, gap(16));
        TextView hint = text("Use the remote: arrows and OK", 16, Color.argb(160, 255, 255, 255), false);
        box.addView(hint, gap(8));
        StringBuilder entered = new StringBuilder();
        Runnable redraw = () -> {
            StringBuilder s = new StringBuilder();
            int n = Math.max(4, entered.length());
            for (int i = 0; i < n; i++) s.append(i < entered.length() ? "● " : "○ ");
            dots.setText(s.toString().trim());
        };
        GridLayout grid = new GridLayout(ctx);
        grid.setColumnCount(3);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"};
        View first = null;
        for (String key : keys) {
            TextView b = button(key, () -> {
                if (key.equals("⌫")) { if (entered.length() > 0) entered.setLength(entered.length() - 1); }
                else if (key.equals("OK")) {
                    if (entered.length() >= 4 && !onPin.test(entered.toString())) {
                        hint.setText("Wrong PIN. Try again.");
                        hint.setTextColor(0xFFFF8A80);
                        entered.setLength(0);
                    }
                } else if (entered.length() < 6) entered.append(key);
                redraw.run();
            });
            b.setMinWidth(dp(96));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(dp(8), dp(8), dp(8), dp(8));
            grid.addView(b, lp);
            if (first == null) first = b;
        }
        box.addView(grid, gap(20));
        showFull(Kind.PIN, box, onCancel);
        first.requestFocus();
    }

    static final class Item {
        final String label, value;
        final Runnable run;
        Item(String label, String value, Runnable run) { this.label = label; this.value = value; this.run = run; }
    }

    /** A focusable list, like a TV settings page: label on the left, value on the right. */
    void menu(Kind k, String title, String footer, List<Item> items, Runnable onBack) {
        menu(k, title, footer, items, onBack, 0);
    }

    /** As above, with focus on row [focus], so a list redrawn after a toggle keeps its place. */
    void menu(Kind k, String title, String footer, List<Item> items, Runnable onBack, int focus) {
        LinearLayout box = column();
        box.addView(text(title, 32, Color.WHITE, true));
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        View first = null;
        for (Item it : items) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding(dp(24), dp(14), dp(24), dp(14));
            TextView l = text(it.label, 22, Color.WHITE, false);
            l.setGravity(Gravity.START);
            TextView v = text(it.value == null ? "" : it.value, 20, Color.argb(170, 255, 255, 255), false);
            v.setGravity(Gravity.END);
            row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            StateListDrawable bg = new StateListDrawable();
            GradientDrawable on = new GradientDrawable();
            on.setColor(Color.WHITE);
            on.setCornerRadius(dp(14));
            bg.addState(new int[]{android.R.attr.state_focused}, on);
            bg.addState(new int[]{}, new GradientDrawable());
            row.setBackground(bg);
            row.setOnFocusChangeListener((view, has) -> {
                l.setTextColor(has ? Color.BLACK : Color.WHITE);
                v.setTextColor(has ? Color.argb(170, 0, 0, 0) : Color.argb(170, 255, 255, 255));
            });
            if (it.run != null) row.setOnClickListener(view -> it.run.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(680), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            list.addView(row, lp);
            if (first == null || list.getChildCount() - 1 == focus) first = row;
        }
        // Long lists (every app on the TV) scroll; the focused row is kept in view by the ScrollView.
        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(list);
        int rows = items.size();
        int maxH = dp(footer != null ? 400 : 460);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                rows > 6 ? maxH : ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        slp.topMargin = dp(24);
        box.addView(scroll, slp);
        if (footer != null) {
            TextView f = text(footer, 16, Color.argb(150, 255, 255, 255), false);
            f.setMaxWidth(dp(680));
            box.addView(f, gap(18));
        }
        showFull(k, box, onBack);
        if (first != null) first.requestFocus();
    }

    /** One line of text typed with the TV keyboard (or the phone's Bluetooth keyboard). */
    void textInput(String title, String hint, String initial, boolean secret, java.util.function.Consumer<String> onDone, Runnable onCancel) {
        LinearLayout box = column();
        box.addView(text(title, 30, Color.WHITE, true));
        android.widget.EditText field = new android.widget.EditText(ctx);
        field.setSingleLine(true);
        if (secret) field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setText(initial == null ? "" : initial);
        field.setHint(hint);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.argb(120, 255, 255, 255));
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(secret ? 64 : 24)});
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(40, 255, 255, 255));
        bg.setCornerRadius(dp(14));
        field.setBackground(bg);
        field.setPadding(dp(20), dp(14), dp(20), dp(14));
        Runnable done = () -> {
            String v = field.getText().toString().trim();
            if (!v.isEmpty()) onDone.accept(v);
        };
        field.setOnEditorActionListener((v, actionId, e) -> { done.run(); return true; });
        box.addView(field, new LinearLayout.LayoutParams(dp(520), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER);
        TextView cancel = button("Cancel", onCancel), save = button("Save", done);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), 0, dp(10), 0);
        row.addView(cancel, lp);
        row.addView(save, lp);
        box.addView(row, gap(28));
        showFull(Kind.MENU, box, onCancel);
        field.requestFocus();
        field.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm = ctx.getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (imm != null) imm.showSoftInput(field, 0);
        }, 250);
    }

    /** A small note at the top right that leaves after 8 seconds and never takes the remote. */
    void toast(String msg) {
        main.post(() -> {
            hideToast();
            TextView t = text(msg, 18, Color.WHITE, true);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.argb(230, 20, 24, 34));
            bg.setCornerRadius(dp(18));
            t.setBackground(bg);
            t.setPadding(dp(22), dp(14), dp(22), dp(14));
            WindowManager.LayoutParams lp = params(false);
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = dp(40);
            lp.y = dp(32);
            try { wm.addView(t, lp); toast = t; } catch (RuntimeException ignored) { }
            main.postDelayed(this::hideToast, 8000);
        });
    }

    void hide() {
        if (full != null) {
            try { wm.removeView(full); } catch (RuntimeException ignored) { }
            full = null;
        }
        kind = Kind.NONE;
    }

    private void hideToast() {
        if (toast != null) {
            try { wm.removeView(toast); } catch (RuntimeException ignored) { }
            toast = null;
        }
    }

    // ---------- internals ----------

    private void showFull(Kind k, View content, Runnable onBack) {
        hide();
        FrameLayout root = new FrameLayout(ctx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP && onBack != null) onBack.run();
                    return true;   // Back never falls through to the app underneath
                }
                return super.dispatchKeyEvent(e);
            }
        };
        root.setBackgroundColor(BG);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        try {
            wm.addView(root, params(true));
            full = root;
            kind = k;
        } catch (RuntimeException e) {
            android.util.Log.w(GuardService.TAG, "overlay failed: " + e.getMessage());
        }
    }

    private WindowManager.LayoutParams params(boolean focusable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                (focusable ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        return lp;
    }

    private LinearLayout column() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        return box;
    }

    private LinearLayout.LayoutParams gap(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(top);
        return lp;
    }

    private TextView text(String s, int sp, int color, boolean medium) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** A pill button: white when focused, glass otherwise; OK and Enter press it. */
    private TextView button(String label, Runnable onClick) {
        TextView b = text(label, 20, Color.WHITE, true);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(28), dp(12), dp(28), dp(12));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, pill(Color.WHITE));
        bg.addState(new int[]{}, pill(Color.argb(46, 255, 255, 255)));
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, has) -> ((TextView) v).setTextColor(has ? Color.BLACK : Color.WHITE));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private View avatar(String name, int color, Runnable onClick) {
        LinearLayout col = column();
        col.setFocusable(true);
        col.setClickable(true);
        col.setPadding(dp(18), dp(12), dp(18), dp(12));
        TextView circle = text(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 40, Color.WHITE, true);
        GradientDrawable c = new GradientDrawable();
        c.setShape(GradientDrawable.OVAL);
        c.setColor(color);
        circle.setBackground(c);
        int size = dp(112);
        col.addView(circle, new LinearLayout.LayoutParams(size, size));
        TextView label = text(name, 20, Color.argb(200, 255, 255, 255), false);
        col.addView(label, gap(10));
        col.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(120).start();
            c.setStroke(has ? dp(4) : 0, Color.WHITE);
            label.setTextColor(has ? Color.WHITE : Color.argb(200, 255, 255, 255));
        });
        col.setOnClickListener(v -> onClick.run());
        return col;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(28));
        return d;
    }

    int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
