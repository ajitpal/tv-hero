package com.ajit.tvhero;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * tv-hero Keyboard (2.10): typing from the phone over the home Wi-Fi. The TV switches to it only while the
 * phone's Type screen is open (see PhoneTyping) and back to its own keyboard afterwards.
 *
 * It only writes: it commits text, deletes and presses Enter in the box that has focus. It never reads the
 * box or anything else on the screen (no getTextBeforeCursor, no extracted text), and shows who is typing.
 */
public class PhoneKeyboard extends InputMethodService {
    static volatile PhoneKeyboard instance;
    static volatile String phone = "";
    private TextView bar;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() { return false; }

    @Override
    public View onCreateInputView() {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        bar = new TextView(this);
        bar.setTextColor(Color.WHITE);
        bar.setTextSize(18);
        bar.setGravity(Gravity.CENTER);
        int px = (int) (22 * d), py = (int) (12 * d);
        bar.setPadding(px, py, px, py);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235, 20, 24, 34));
        bg.setCornerRadius(24 * d);
        bar.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        lp.bottomMargin = (int) (24 * d);
        root.addView(bar, lp);
        updateBar();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        updateBar();
    }

    private void updateBar() {
        if (bar == null) return;
        String who = phone == null || phone.isEmpty() ? "your phone" : phone;
        bar.setText("⌨  Typing from " + who + "  ·  type on the phone");
    }

    /** Is a text box focused and ready for text? */
    boolean hasField() {
        return getCurrentInputStarted() && getCurrentInputConnection() != null;
    }

    boolean commit(String text) {
        InputConnection ic = getCurrentInputConnection();
        return ic != null && ic.commitText(text, 1);
    }

    /** Deletes [count] characters (code points, so an emoji goes in one press) before the cursor. */
    boolean backspace(int count) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        return ic.deleteSurroundingTextInCodePoints(count, 0);
    }

    /** The box's own action (Search, Go, Done) where it has one, otherwise Enter. */
    boolean enter() {
        if (getCurrentInputConnection() == null) return false;
        if (!sendDefaultEditorAction(true)) sendKeyChar('\n');
        return true;
    }
}
