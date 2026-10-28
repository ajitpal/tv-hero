package com.ajit.tvhero;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * config.xml weather_pill: a text pill first in the top bar with the weather add-on's place and temperature
 * ("Home · 21°") and a line icon for the sky, like Inputs. The launcher itself still reads no network: the add-on
 * fetches the weather and keeps its last reading in a content provider, which this reads when Home starts and
 * whenever the add-on reports a new reading. Opening the pill opens the add-on.
 */
final class WeatherPill {
    private final Activity activity;
    private final TextView button;
    private final Uri uri;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentObserver observer;
    private boolean watching;

    private WeatherPill(Activity activity, TextView button, String pkg) {
        this.activity = activity;
        this.button = button;
        this.uri = Uri.parse("content://" + pkg + ".now/now");
        this.observer = new ContentObserver(main) {
            @Override public void onChange(boolean self) {
                read();
            }
        };
    }

    /** The pill at the start of the bar, or null when weather_pill is empty or the add-on is not installed. */
    static WeatherPill add(Activity activity, ViewGroup bar, String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        pkg = pkg.trim();
        PackageManager pm = activity.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null || pm.resolveContentProvider(pkg + ".now", 0) == null) return null;
        TextView button = (TextView) activity.getLayoutInflater().inflate(R.layout.top_weather_pill, bar, false);
        Intent target = launch;
        button.setOnClickListener(v -> activity.startActivity(new Intent(target)));
        bar.addView(button, 0);
        return new WeatherPill(activity, button, pkg);
    }

    View view() {
        return button;
    }

    void onStart() {
        if (!watching) {
            try {
                activity.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException ignored) {
                // the add-on changed under us: keep the last text
            }
        }
        read();
    }

    void onStop() {
        if (!watching) return;
        activity.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    /** Off the main thread: the first query starts the add-on's process. */
    private void read() {
        new Thread(() -> {
            String label = null;
            int temp = 0;
            int code = -1;
            boolean day = true;
            try (Cursor c = activity.getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    label = c.getString(c.getColumnIndexOrThrow("label"));
                    if (!c.isNull(c.getColumnIndexOrThrow("temp"))) {
                        temp = c.getInt(c.getColumnIndexOrThrow("temp"));
                        code = c.getInt(c.getColumnIndexOrThrow("code"));
                        day = c.getInt(c.getColumnIndexOrThrow("day")) != 0;
                    }
                }
            } catch (RuntimeException e) {
                label = null;   // add-on gone or older: keep the pill as it was
            }
            String place = label;
            int degrees = temp;
            int sky = code;
            boolean daytime = day;
            main.post(() -> show(place, degrees, sky, daytime));
        }, "weather-pill").start();
    }

    private void show(String label, int temp, int code, boolean day) {
        if (activity.isDestroyed() || label == null) return;
        String text = code < 0 ? label : activity.getString(R.string.weather_pill_text, label, temp);
        button.setText(text);
        button.setContentDescription(text);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(code, day), 0, 0, 0);
    }

    /** WMO weather code to one of four line icons; no reading yet shows the cloud. */
    static int icon(int code, boolean day) {
        if (code == 0 || code == 1) return day ? R.drawable.ic_weather_clear : R.drawable.ic_weather_night;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95) return R.drawable.ic_weather_rain;
        return R.drawable.ic_weather_cloud;
    }
}
