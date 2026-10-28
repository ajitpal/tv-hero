package com.ajit.tvhero;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shown instead of an app that is blocked or still waiting for a parent's approval. */
public class BlockedActivity extends Activity {
    static final String EXTRA_PACKAGE = "package";
    static final String EXTRA_PENDING = "pending";

    static void show(Context ctx, String pkg, boolean pending) {
        Intent i = new Intent(ctx, BlockedActivity.class)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_PENDING, pending)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setBackgroundColor(Color.rgb(5, 7, 12));
        render(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        render(intent);
    }

    private void render(Intent intent) {
        String pkg = intent.getStringExtra(EXTRA_PACKAGE);
        boolean pending = intent.getBooleanExtra(EXTRA_PENDING, false);
        String label = pkg;
        try {
            PackageManager pm = getPackageManager();
            label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) { }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(64), dp(48), dp(64), dp(48));

        TextView lock = text("🔒", 64, Color.WHITE);
        TextView title = text(pending ? label + " is waiting for approval" : label + " is blocked", 34, Color.WHITE);
        TextView body = text(pending
                ? "This app was just installed. A parent can approve it in the tv-hero app on their phone."
                : "A parent has blocked this app. Ask them to allow it in the tv-hero app on their phone.",
                18, Color.argb(200, 255, 255, 255));
        body.setMaxWidth(dp(560));

        Button ok = new Button(this);
        ok.setText("Go to Home");
        ok.setAllCaps(false);
        ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        ok.setTextColor(Color.BLACK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(24));
        ok.setBackground(bg);
        ok.setPadding(dp(32), dp(10), dp(32), dp(10));
        ok.setOnClickListener(v -> goHome());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        box.addView(lock);
        box.addView(title, lp);
        box.addView(body, lp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(32);
        box.addView(ok, blp);
        setContentView(box);
        ok.requestFocus();
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            goHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
