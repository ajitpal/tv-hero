package com.ajit.tvhero;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shows the 6-digit pairing code while a phone pairs with this TV. Closes itself after 2 minutes. */
public class PairingActivity extends Activity {
    static final String EXTRA_CODE = "code";
    static final String EXTRA_DEVICE = "device";
    static final String ACTION_DISMISS = "dismiss";
    private static PairingActivity current;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void show(Context ctx, String code, String device) {
        ctx.startActivity(new Intent(ctx, PairingActivity.class)
                .putExtra(EXTRA_CODE, code).putExtra(EXTRA_DEVICE, device)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    static void dismiss(Context ctx) {
        if (current != null) current.finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        render(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        render(intent);
    }

    private void render(Intent intent) {
        getWindow().getDecorView().setBackgroundColor(Color.argb(235, 5, 7, 12));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView t1 = text("Pair " + intent.getStringExtra(EXTRA_DEVICE) + " with this TV", 26, Color.WHITE);
        TextView code = text(spaced(intent.getStringExtra(EXTRA_CODE)), 72, Color.WHITE);
        code.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        code.setLetterSpacing(0.08f);
        TextView t2 = text("Enter this code in the tv-hero app on your phone. It expires in 2 minutes.", 18,
                Color.argb(190, 255, 255, 255));
        box.addView(t1);
        box.addView(code);
        box.addView(t2);
        setContentView(box);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::finish, 120_000);
    }

    private static String spaced(String c) {
        return c == null || c.length() != 6 ? String.valueOf(c) : c.substring(0, 3) + " " + c.substring(3);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 12, 0, 12);
        return t;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (current == this) current = null;
        super.onDestroy();
    }
}
