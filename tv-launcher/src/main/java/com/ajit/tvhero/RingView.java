package com.ajit.tvhero;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * A progress ring for the tools: a faint track, a coloured arc that grows clockwise from 12 o'clock with
 * rounded ends, and a small bright dot at its tip. Redrawn only when the progress or colour changes.
 */
public class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float progress = 0f;

    public RingView(Context ctx) {
        super(ctx);
        float w = 10 * ctx.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(0x1FFFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(0xFFFF9F0A);
        dot.setColor(0xFFFFFFFF);
        dot.setShadowLayer(w, 0, 0, 0xFFFFFFFF);
        setLayerType(LAYER_TYPE_SOFTWARE, dot);   // the dot's glow needs a software layer; the view is small
    }

    /** 0…1 of the ring; values outside are clamped. */
    void setProgress(float p) {
        float v = Math.max(0f, Math.min(1f, p));
        if (Math.abs(v - progress) < 0.0015f) return;
        progress = v;
        invalidate();
    }

    void setColor(int color) {
        if (arc.getColor() == color) return;
        arc.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = arc.getStrokeWidth();
        float size = Math.min(getWidth(), getHeight()) - w * 2;
        float left = (getWidth() - size) / 2f, top = (getHeight() - size) / 2f;
        box.set(left, top, left + size, top + size);
        c.drawOval(box, track);
        if (progress <= 0f) return;
        float sweep = 360f * progress;
        c.drawArc(box, -90f, sweep, false, arc);
        double a = Math.toRadians(sweep - 90f);
        float r = size / 2f;
        c.drawCircle((float) (box.centerX() + r * Math.cos(a)), (float) (box.centerY() + r * Math.sin(a)), w * 0.42f, dot);
    }
}
