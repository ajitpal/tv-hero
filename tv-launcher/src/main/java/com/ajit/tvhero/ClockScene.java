package com.ajit.tvhero;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow. Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /**
     * The backdrop: a diagonal three-colour gradient that follows the time of day (night, dawn, day, evening),
     * deep and low in brightness so the white watch reads well and the panel isn't lit up at night.
     */
    private static final int[][] SKIES = {
            {0xFF04060F, 0xFF0D1A3F, 0xFF221047},   // night
            {0xFF140C2E, 0xFF4A2463, 0xFFA84A5E},   // dawn
            {0xFF061830, 0xFF0F3F6E, 0xFF13697F},   // day
            {0xFF0E0A26, 0xFF33194F, 0xFF7A3352},   // evening
    };

    static int skyFor(int hour) {
        if (hour >= 21 || hour < 5) return 0;
        if (hour < 8) return 1;
        if (hour < 17) return 2;
        return 3;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final ImageView ambient;
    private final GradientDrawable glow;
    private int[] skyColors;
    /** clock_wallpaper or clock_glow false: something else (a picture, or plain black) replaced the coloured glow. */
    private boolean wallpaperShown;
    /** clock_wallpaper: the picture start() decodes, its darkening, and the resources to draw it with. */
    private File wallpaperFile;
    private int wallpaperShade;
    private Resources wallpaperRes;
    /** One number per decode request: a result whose number is no longer the current one is dropped. Main thread only. */
    private int wallpaperGeneration;
    /** clock_wallpaper with a collection: the pictures, how long each lasts, and how the next one is picked. */
    private List<File> rotation;
    private long rotationMs;
    private long shownMs;
    private String order;
    private Map<String, Set<String>> moods;
    private String latitude, longitude;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            advanceWallpaper();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable(GradientDrawable.Orientation.TL_BR, SKIES[0]);
        ambient.setBackground(glow);
        setSky(SKIES[skyFor(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))].clone());
    }

    void start() {
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
        if (wallpaperFile != null && !wallpaperShown) loadWallpaper();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        content.animate().cancel();
        wallpaperGeneration++;   // a decode still running must not attach its picture to a stopped scene
    }

    /**
     * clock_wallpaper: put the picture Home is showing behind the clock, darkened by clock_wallpaper_dim, in
     * place of the glow, and carry a collection's rotation on while the screensaver is up. Decoded off the main
     * thread at screen size in RGB_565 (4 MB at 1080p), like Home's own; until then, or if it cannot be read,
     * the glow stays.
     * Call before start(), which starts the decode (again after a stop() that dropped an unfinished one).
     */
    void useHomeWallpaper(Context ctx) {
        Config cfg;
        try {
            cfg = Config.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        if (!cfg.clockWallpaper) {
            // clock_glow false: plain black behind the clock (OLED); the clock itself still moves every minute.
            if (!cfg.clockGlow) {
                wallpaperShown = true;   // also stops the glow's colour cycle
                ambient.setBackground(null);
            }
            return;
        }
        // What Home plays, and Config puts the single wallpaper in it when a theme has no collection.
        // Reading cfg.wallpaper first held the screensaver on wallpaper.jpg while Home rotated.
        if (cfg.wallpapers.isEmpty()) {
            wallpaperFile = null;
        } else {
            // Start on the picture Home is showing. WallpaperPlayer records it under the same
            // preferences and the screensaver runs in the same process, so this is just a read.
            rotation = new ArrayList<>(cfg.wallpapers);
            String shown = ctx.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
                    .getString("image", "");
            File current = WallpaperRotation.find(rotation, shown);
            wallpaperFile = current != null ? current : rotation.get(0);
            // Home only rotates while it is visible, so the screensaver carries the rotation on
            // itself; without this it holds one picture for as long as it is up.
            rotationMs = cfg.wallpaperRotationMinutes * 60_000L;
            order = cfg.wallpaperOrder;
            moods = cfg.wallpaperMoods;
            latitude = moods.isEmpty() ? "" : ctx.getString(R.string.latitude);
            longitude = moods.isEmpty() ? "" : ctx.getString(R.string.longitude);
        }
        wallpaperShade = (cfg.clockWallpaperDim * 255 / 100) << 24;
        wallpaperRes = ctx.getResources();
    }

    private void loadWallpaper() {
        File file = wallpaperFile;
        int shade = wallpaperShade;
        Resources res = wallpaperRes;
        DisplayMetrics dm = res.getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int generation = ++wallpaperGeneration;
        new Thread(() -> {
            Bitmap bmp = decode(file, w, h);
            if (bmp == null) return;
            handler.post(() -> {
                if (generation != wallpaperGeneration || wallpaperShown) {
                    bmp.recycle();   // stopped meanwhile (or already shown): this picture was never attached
                    return;
                }
                wallpaperShown = true;
                // As the image, not the background: a background is stretched to the view, so a picture of
                // another shape than the screen would be distorted. centerCrop scales and crops it like Home.
                ambient.setBackground(null);
                ambient.setImageDrawable(new LayerDrawable(new Drawable[]{
                        new BitmapDrawable(res, bmp), new ColorDrawable(shade)}));
            });
        }, "clock-wallpaper").start();
    }

    /** WallpaperPlayer's decode: sampled and scaled down to cover the screen, never bigger than 4K. */
    private static Bitmap decode(File file, int screenW, int screenH) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, screenW, screenH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** Step to the next picture once its turn is up; the tick already runs every minute. */
    private void advanceWallpaper() {
        if (rotation == null || rotationMs <= 0 || rotation.size() < 2) return;
        shownMs += MOVE_MS;
        if (shownMs < rotationMs) return;
        shownMs = 0;
        // The same pick as Home's, so a shuffled collection stays shuffled and wallpaper_moods still
        // decides what may show at this hour.
        File next = WallpaperRotation.pick(playable(), wallpaperFile.getName(), order, random);
        if (next.equals(wallpaperFile)) return;   // a mood group of one
        wallpaperFile = next;
        wallpaperShown = false;
        loadWallpaper();
    }

    /** WallpaperPlayer's filter: the pictures wallpaper_moods allows at this hour, or all of them. */
    private List<File> playable() {
        if (moods.isEmpty()) return rotation;
        LocalDateTime now = LocalDateTime.now();
        int[] sun = WallpaperMood.sun(now.toLocalDate(), latitude, longitude, ZoneId.systemDefault());
        return WallpaperMood.select(rotation, moods,
                WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).withLayer().start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        // Near the middle, a little off each minute: enough to spare the panel, calm enough to look composed.
        float x = maxX * (0.3f + 0.4f * random.nextFloat());
        float y = maxY * (0.3f + 0.4f * random.nextFloat());
        content.setX(x);
        content.setY(y);
    }

    /** Every minute: blend towards the sky for this hour (a no-op unless the hour band changed). */
    private void shiftGlow() {
        if (wallpaperShown) return;
        int[] from = skyColors.clone();
        int[] to = SKIES[skyFor(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))];
        if (java.util.Arrays.equals(from, to)) return;
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> {
                int[] c = new int[3];
                for (int k = 0; k < 3; k++) c[k] = (int) mix.evaluate(f, from[k], to[k]);
                setSky(c);
            }, i * GLOW_STEP_MS * 4);
        }
    }

    private void setSky(int[] colors) {
        skyColors = colors;
        glow.setColors(colors);
    }
}
