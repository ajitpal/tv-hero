package com.ajit.tvhero;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * The Free TV screen (2.11), opened from Home's top bar: the apps, YouTube channels and playlist channels a
 * parent picked, grouped, one per row. Up and Down move, OK opens. A child sees only what a parent ticked
 * for them; opening one lets its app through the guard once, like a link allowed from the phone.
 */
public class FreeTvActivity extends Activity {

    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        build();
    }

    private void build() {
        Profiles profiles = Profiles.get(this);
        FreeTv store = FreeTv.get(this);
        String profile = profiles.activeId();
        List<FreeTv.Item> items = FreeTv.visible(store.items(), profile, store.ticks(profile));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF05070C);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(96), px(48), px(96), px(48));
        scroll.addView(list);

        TextView title = text("Free TV", 34, Color.WHITE, true);
        list.addView(title);
        TextView sub = text(items.isEmpty()
                ? (Profiles.PARENT.equals(profile) ? "Nothing here yet. Add apps, YouTube channels or a playlist on the phone: Settings → Free TV."
                : "Nothing here yet. Ask a parent to add something for you.")
                : "Picked by your family. Playlist channels play in " + playerName(store) + ".", 16, 0xB3FFFFFF, false);
        sub.setPadding(0, px(6), 0, px(24));
        list.addView(sub);

        String group = null;
        View first = null;
        for (FreeTv.Item i : items) {
            String g = i.group.isEmpty() ? kindName(i.kind) : i.group;
            if (!g.equals(group)) {
                group = g;
                TextView h = text(g.toUpperCase(Locale.ROOT), 13, 0x99FFFFFF, true);
                h.setLetterSpacing(0.12f);
                h.setPadding(px(4), px(18), 0, px(8));
                list.addView(h);
            }
            View row = row(i);
            list.addView(row);
            if (first == null) first = row;
        }
        setContentView(scroll);
        if (first != null) first.requestFocus();
    }

    private View row(FreeTv.Item i) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(px(14), px(10), px(18), px(10));
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0x33FFFFFF);
        on.setCornerRadius(px(14));
        on.setStroke(px(2), 0xCCFFFFFF);
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, new GradientDrawable());
        row.setBackground(bg);

        TextView tile = text(i.title.substring(0, 1).toUpperCase(Locale.ROOT), 20, Color.WHITE, true);
        tile.setGravity(Gravity.CENTER);
        GradientDrawable t = new GradientDrawable();
        t.setCornerRadius(px(10));
        t.setColor(color(i.title));
        tile.setBackground(t);
        row.addView(tile, new LinearLayout.LayoutParams(px(48), px(48)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(px(16), 0, 0, 0);
        words.addView(text(i.title, 20, Color.WHITE, false));
        words.addView(text(kindName(i.kind), 13, 0x99FFFFFF, false));
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> open(i));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(6);
        row.setLayoutParams(lp);
        return row;
    }

    private void open(FreeTv.Item i) {
        FreeTv store = FreeTv.get(this);
        Intent intent = store.intent(i);
        if (intent == null) {
            Toast.makeText(this, i.kind.equals(FreeTv.STREAM) ? "Install a video player such as VLC to watch playlist channels."
                    : "This can't be opened on this TV right now.", Toast.LENGTH_LONG).show();
            return;
        }
        String pkg = intent.getPackage() != null ? intent.getPackage() : intent.getComponent() != null ? intent.getComponent().getPackageName() : "";
        if (!pkg.isEmpty() && GuardStore.get(this).isRestricted(pkg)) {
            Toast.makeText(this, "A parent has blocked that app.", Toast.LENGTH_LONG).show();
            return;
        }
        Profiles.Kid kid = Profiles.get(this).activeKid();
        if (kid != null && !pkg.isEmpty() && !kid.all.contains(pkg)) GuardService.allowOnce(pkg);   // ticked by a parent for this child
        GuardStore.get(this).log("freetv", i.title, Profiles.get(this).activeId());
        try { startActivity(intent); } catch (RuntimeException e) { Toast.makeText(this, "That didn't open.", Toast.LENGTH_SHORT).show(); }
    }

    private String playerName(FreeTv store) {
        String p = store.player();
        if (p == null) return "a video player (install VLC)";
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p, 0)).toString(); }
        catch (Exception e) { return p; }
    }

    static String kindName(String kind) {
        switch (kind) {
            case FreeTv.APP: return "App";
            case FreeTv.YOUTUBE: return "YouTube channel";
            default: return "Channel";
        }
    }

    /** A calm colour per title, so letter tiles tell channels apart without logos. */
    static int color(String title) {
        int[] palette = {0xFF2F6FDE, 0xFF2E9E6A, 0xFFB5591F, 0xFF8B4DC9, 0xFFB8384A, 0xFF1F8A99, 0xFF6B7280, 0xFF9A7B12};
        return palette[Math.floorMod(title.toLowerCase(Locale.ROOT).hashCode(), palette.length)];
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private int px(float dp) { return Math.round(dp * d); }
}
