package com.ajit.tvhero;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Photos from the phone (2.10), streamed and never saved. The phone runs the slideshow and sends each photo
 * just before it shows; this screen holds only the photo on screen and, during the cross-fade, the next one,
 * as bitmaps in memory. Nothing is written to storage or a cache. The screen closes when the phone stops,
 * 30 seconds after the last photo, at Back or Home, and at standby.
 */
public class PhotoActivity extends Activity {
    /** No photo for this long (phone closed, phone off the Wi-Fi): back to what was on before. */
    static final long QUIET_MS = 30_000;
    /** Largest decoded size: the TV's UI is 1280x720. */
    static final int MAX_W = 1280, MAX_H = 720;

    private static Handler main;

    /** Made on first use, so the class loads in plain JVM unit tests. */
    private static synchronized Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }
    private static volatile PhotoActivity visible;
    private static Bitmap pending;
    private static String pendingCaption = "";
    private static volatile long lastPhotoAt;
    /** A slideshow stopped at the TV (Back): its later photos are refused, so it doesn't reopen. */
    private static volatile String stoppedSession = "";
    private static volatile String session = "";

    private FrameLayout root;
    private ImageView front, back;
    private TextView caption;
    private Bitmap frontBmp;

    enum Result { SHOWN, STOPPED_ON_TV, BAD_IMAGE }

    /** Shows [jpeg] (decoded here, on the caller's thread). */
    static Result show(Context ctx, String sessionId, byte[] jpeg, String text) {
        if (sessionId != null && !sessionId.isEmpty() && sessionId.equals(stoppedSession)) return Result.STOPPED_ON_TV;
        Bitmap bmp = decode(jpeg);
        if (bmp == null) return Result.BAD_IMAGE;
        session = sessionId == null ? "" : sessionId;
        lastPhotoAt = SystemClock.elapsedRealtime();
        String cap = text == null ? "" : text;
        main().post(() -> {
            PhotoActivity a = visible;
            if (a != null && !a.isFinishing()) { a.swap(bmp, cap); return; }
            if (pending != null && pending != bmp) pending.recycle();
            pending = bmp;
            pendingCaption = cap;
            Context app = ctx.getApplicationContext();
            app.startActivity(new Intent(app, PhotoActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        });
        return Result.SHOWN;
    }

    /** Ends the slideshow (the phone pressed Stop, or the TV went to standby). */
    static void stop() {
        main().post(() -> {
            if (pending != null) { pending.recycle(); pending = null; }
            PhotoActivity a = visible;
            if (a != null) a.finish();
        });
    }

    static boolean showing() { return visible != null; }

    /** Decodes a JPEG no larger than the TV's screen, in 16-bit colour (half the memory). */
    static Bitmap decode(byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** The power-of-two sample size that brings [w]x[h] within the screen. */
    static int sampleSize(int w, int h) {
        int s = 1;
        while (w / (s * 2) >= MAX_W && h / (s * 2) >= MAX_H) s *= 2;   // never smaller than the screen
        return s;
    }

    // ---------- the screen ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        back = image();
        front = image();
        root.addView(back);
        root.addView(front);
        caption = new TextView(this);
        caption.setTextColor(Color.argb(200, 255, 255, 255));
        caption.setTextSize(16);
        caption.setShadowLayer(6, 0, 1, Color.BLACK);
        float d = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, (int) (36 * d), (int) (28 * d));
        root.addView(caption, lp);
        setContentView(root);
    }

    private ImageView image() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = this;
        if (pending != null) {
            Bitmap b = pending;
            pending = null;
            swap(b, pendingCaption);
        }
        main().removeCallbacks(watchdog);
        main().postDelayed(watchdog, 5_000);
    }

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (SystemClock.elapsedRealtime() - lastPhotoAt > QUIET_MS) { finish(); return; }
            main().postDelayed(this, 5_000);
        }
    };

    /** Cross-fades to [bmp]; the old photo is released once it has faded out. */
    void swap(Bitmap bmp, String text) {
        caption.setText(text);
        final Bitmap old = frontBmp;
        back.setImageBitmap(old);
        back.setAlpha(1f);
        front.setAlpha(0f);
        front.setImageBitmap(bmp);
        frontBmp = bmp;
        front.animate().alpha(1f).setDuration(400).withEndAction(() -> {
            back.setImageDrawable(null);
            if (old != null && old != frontBmp) old.recycle();
        }).start();
    }

    @Override
    public void onBackPressed() {
        stoppedSession = session;   // the phone is told "stopped on the TV" at its next photo
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Home, another app, a lock screen app or standby: the slideshow ends rather than waiting behind.
        if (!isFinishing()) { stoppedSession = session; finish(); }
    }

    @Override
    protected void onDestroy() {
        main().removeCallbacks(watchdog);
        if (visible == this) visible = null;
        front.animate().cancel();
        front.setImageDrawable(null);
        back.setImageDrawable(null);
        if (frontBmp != null) { frontBmp.recycle(); frontBmp = null; }
        super.onDestroy();
    }
}
