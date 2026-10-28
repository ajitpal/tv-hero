package com.ajit.tvhero;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * tv-hero's guard: an accessibility service, because Android keeps those running and tells them
 * which app comes to the front. It
 *  - sends a blocked or not-yet-approved app back to Home and explains why on screen,
 *  - puts every newly installed app on hold until a parent approves it (GuardStore),
 *  - hosts the control server the phone app talks to, and advertises it on the network.
 * It reads only window-change events (the package in front), never screen content.
 */
public class GuardService extends AccessibilityService {
    static final String TAG = "TvHeroGuard";
    static final String NSD_TYPE = "_tvhero._tcp";

    private final Handler main = new Handler(Looper.getMainLooper());
    private ControlServer server;
    private NsdManager.RegistrationListener nsdListener;
    private String lastBlocked = "";
    private long lastBlockedAt;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getData() == null) return;
            String pkg = intent.getData().getSchemeSpecificPart();
            boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            GuardStore store = GuardStore.get(context);
            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction()) && !replacing) {
                if (store.onInstalled(pkg)) {
                    store.log("installed_pending", pkg);
                    Log.i(TAG, "new app waits for approval: " + pkg);
                } else {
                    store.log("installed", pkg);
                }
            } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
                store.onRemoved(pkg);
                store.log("removed", pkg);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 150;
            setServiceInfo(info);
        }
        GuardStore.get(this).reconcile();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packages, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packages, f);
        server = new ControlServer(new Api(this, this::globalAction));
        server.start();
        registerNsd();
        Log.i(TAG, "guard running");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName())) return;
        GuardStore store = GuardStore.get(this);
        if (!store.isRestricted(pkg)) return;
        long now = SystemClock.elapsedRealtime();
        if (pkg.equals(lastBlocked) && now - lastBlockedAt < 1500) return;
        lastBlocked = pkg;
        lastBlockedAt = now;
        GuardStore.State state = store.state(pkg);
        store.log(state == GuardStore.State.PENDING ? "opened_pending" : "opened_blocked", pkg);
        // Cover the app with the explanation instead of pressing Home first: Android holds back
        // activity starts for a few seconds after a Home press, so the screen would never show.
        BlockedActivity.show(this, pkg, state == GuardStore.State.PENDING);
        Log.i(TAG, "blocked " + pkg + " (" + state + ")");
        main.postDelayed(() -> {
            ActivityManager am = getSystemService(ActivityManager.class);
            if (am != null) am.killBackgroundProcesses(pkg);
        }, 1500);
    }

    boolean globalAction(String action) {
        int a;
        switch (action == null ? "" : action) {
            case "home": a = GLOBAL_ACTION_HOME; break;
            case "back": a = GLOBAL_ACTION_BACK; break;
            case "recents": a = GLOBAL_ACTION_RECENTS; break;
            case "notifications": a = GLOBAL_ACTION_NOTIFICATIONS; break;
            case "quick_settings": a = GLOBAL_ACTION_QUICK_SETTINGS; break;
            default: return false;
        }
        main.post(() -> performGlobalAction(a));
        return true;
    }

    private void registerNsd() {
        NsdManager nsd = getSystemService(NsdManager.class);
        if (nsd == null) return;
        NsdServiceInfo si = new NsdServiceInfo();
        si.setServiceName("tv-hero " + Build.MODEL);
        si.setServiceType(NSD_TYPE);
        si.setPort(ControlServer.PORT);
        nsdListener = new NsdManager.RegistrationListener() {
            @Override public void onRegistrationFailed(NsdServiceInfo s, int e) { Log.w(TAG, "NSD registration failed: " + e); }
            @Override public void onUnregistrationFailed(NsdServiceInfo s, int e) { }
            @Override public void onServiceRegistered(NsdServiceInfo s) { Log.i(TAG, "NSD: " + s.getServiceName()); }
            @Override public void onServiceUnregistered(NsdServiceInfo s) { }
        };
        try {
            nsd.registerService(si, NsdManager.PROTOCOL_DNS_SD, nsdListener);
        } catch (RuntimeException e) {
            Log.w(TAG, "NSD: " + e.getMessage());
            nsdListener = null;
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(packages); } catch (IllegalArgumentException ignored) { }
        if (server != null) server.stop();
        NsdManager nsd = getSystemService(NsdManager.class);
        if (nsd != null && nsdListener != null) {
            try { nsd.unregisterService(nsdListener); } catch (RuntimeException ignored) { }
        }
        super.onDestroy();
    }
}
