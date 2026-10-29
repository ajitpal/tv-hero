package com.ajit.tvhero;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;

/**
 * The screensaver's analogue watch: a quiet dial with hour bars and minute ticks, slim white hour and
 * minute hands, an orange seconds hand that steps once a second, and a date window at 3 o'clock.
 * Plain Canvas drawing, redrawn once a second only while attached: cheap on a 1 GB TV.
 */
public class WatchFaceView extends View {
    private static final int ACCENT = 0xFFFF9F0A;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seconds = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dateBox = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dateText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Runnable next = this::invalidate;
    private boolean running;

    public WatchFaceView(Context ctx) { this(ctx, null); }

    public WatchFaceView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0x59FFFFFF);
        tick.setStrokeCap(Paint.Cap.ROUND);
        hand.setColor(0xF2FFFFFF);
        hand.setStrokeCap(Paint.Cap.ROUND);
        hand.setShadowLayer(6f, 0f, 2f, 0x66000000);
        seconds.setColor(ACCENT);
        seconds.setStrokeCap(Paint.Cap.ROUND);
        dateBox.setColor(0x26FFFFFF);
        dateText.setColor(0xE6FFFFFF);
        dateText.setTextAlign(Paint.Align.CENTER);
        dateText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setColor(0x80FFFFFF);
        brand.setTextAlign(Paint.Align.CENTER);
        brand.setLetterSpacing(0.3f);
        brand.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(next);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float r = Math.min(w, h) / 2f;
        face.setShader(new RadialGradient(w / 2f, h / 2f - r * 0.3f, r * 1.3f,
                new int[]{0x2EFFFFFF, 0x0DFFFFFF, 0x05FFFFFF}, null, Shader.TileMode.CLAMP));
        ring.setStrokeWidth(Math.max(2f, r * 0.012f));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - ring.getStrokeWidth();
        c.drawCircle(cx, cy, r, face);
        c.drawCircle(cx, cy, r, ring);

        // Minute ticks and hour bars
        for (int i = 0; i < 60; i++) {
            boolean hour = i % 5 == 0;
            tick.setColor(hour ? 0xE6FFFFFF : 0x59FFFFFF);
            tick.setStrokeWidth(hour ? r * 0.028f : r * 0.009f);
            float outer = r * 0.93f, inner = hour ? r * (i % 15 == 0 ? 0.76f : 0.81f) : r * 0.89f;
            double a = Math.toRadians(i * 6);
            float sx = (float) Math.sin(a), cy2 = (float) -Math.cos(a);
            c.drawLine(cx + sx * inner, cy + cy2 * inner, cx + sx * outer, cy + cy2 * outer, tick);
        }

        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR), m = now.get(Calendar.MINUTE), s = now.get(Calendar.SECOND);

        // Name and date window
        brand.setTextSize(r * 0.075f);
        c.drawText("TV HERO", cx, cy - r * 0.38f, brand);
        float bw = r * 0.2f, bh = r * 0.14f, bx = cx + r * 0.52f;
        box.set(bx - bw / 2, cy - bh / 2, bx + bw / 2, cy + bh / 2);
        c.drawRoundRect(box, bh * 0.2f, bh * 0.2f, dateBox);
        dateText.setTextSize(bh * 0.72f);
        c.drawText(String.valueOf(now.get(Calendar.DAY_OF_MONTH)), bx, cy + bh * 0.26f, dateText);

        // Hands
        drawHand(c, cx, cy, (h + m / 60f) * 30f, r * 0.5f, r * 0.06f, r * 0.045f, hand);
        drawHand(c, cx, cy, (m + s / 60f) * 6f, r * 0.74f, r * 0.08f, r * 0.03f, hand);
        drawHand(c, cx, cy, s * 6f, r * 0.84f, r * 0.2f, r * 0.012f, seconds);
        c.drawCircle(cx, cy, r * 0.045f, hand);
        c.drawCircle(cx, cy, r * 0.022f, seconds);

        if (running) {
            postDelayed(next, 1000 - (System.currentTimeMillis() % 1000) + 5);
        }
    }

    private static void drawHand(Canvas c, float cx, float cy, float deg, float len, float tail, float width, Paint p) {
        double a = Math.toRadians(deg);
        float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
        p.setStrokeWidth(width);
        c.drawLine(cx - dx * tail, cy - dy * tail, cx + dx * len, cy + dy * len, p);
    }
}
