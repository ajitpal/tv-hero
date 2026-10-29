package com.ajit.tvhero;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * tv-hero's guard: an accessibility service, because Android keeps those running and tells them
 * which app comes to the front. It
 *  - blocks apps a parent blocked, new installs still waiting for approval, and in a kid profile
 *    everything outside that kid's allowed apps (and always Settings and the Play Store),
 *  - counts each profile's screen time, warns before the limit and bedtime, then locks the TV,
 *    and at bedtime puts it to sleep,
 *  - shows Who's watching? when the TV wakes, a parent lock, a sleep timer, a night volume cap,
 *  - keeps a short history of what was watched (app and, where the app shares it, the title),
 *  - hosts the control server the phone app talks to, and advertises it on the network.
 * It reads only window changes and media titles, never screen content.
 */
public class GuardService extends AccessibilityService {
    static final String TAG = "TvHeroGuard";
    static final String NSD_TYPE = "_tvhero._tcp";
    private static final long TICK_MS = 20_000;

    static GuardService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ControlServer server, secureServer;
    private NsdManager.RegistrationListener nsdListener;
    private Overlays ui;
    private Profiles profiles;
    private GuardStore store;

    // What is in front, and since when
    private String fgPkg = "";
    private String fgTitle = "";
    private long fgSince = System.currentTimeMillis();
    private long lastTick = SystemClock.elapsedRealtime();
    private long bedtimeSleepAt;
    private String lastBlocked = "";
    private long lastBlockedAt;
    private boolean pickerDone = true;
    private final Set<String> warned = new HashSet<>();

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getData() == null) return;
            packagesChanged();
            String pkg = intent.getData().getSchemeSpecificPart();
            boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction()) && !replacing) {
                store.log(store.onInstalled(pkg) ? "installed_pending" : "installed", pkg);
            } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
                store.onRemoved(pkg);
                store.log("removed", pkg);
            }
        }
    };

    void packagesChanged() { launchableAt = 0; }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                closeSession();
                if (profiles.isGuest()) {   // guest mode ends at standby
                    String w = profiles.onWake();
                    profiles.setActive("ask".equals(w) ? Profiles.PARENT : w);
                }
                pickerDone = false;
                bedtimeSleepAt = 0;
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                lastTick = SystemClock.elapsedRealtime();
                fgSince = System.currentTimeMillis();
                onWake();
            } else if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                capVolume();
            }
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try { tick(); } catch (RuntimeException e) { Log.w(TAG, "tick", e); }
            main.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        instance = this;
        store = GuardStore.get(this);
        profiles = Profiles.get(this);
        ui = new Overlays(this);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 150;
            setServiceInfo(info);
        }
        store.reconcile();
        // The TV starts in the start-up profile (Parent Menu > When the TV Turns On), not in whoever was last.
        // Once per boot only: Android also reconnects this service mid-session (uiautomator, crashes,
        // updates), and that must not throw a parent who switched with the PIN back to the start-up profile.
        if (profiles.firstStartThisBoot()) {
            String wake = profiles.onWake();
            if (!"ask".equals(wake)) profiles.setActive(wake);
        }
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        f.addDataScheme("package");
        IntentFilter s = new IntentFilter(Intent.ACTION_SCREEN_ON);
        s.addAction(Intent.ACTION_SCREEN_OFF);
        s.addAction("android.media.VOLUME_CHANGED_ACTION");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packages, f, Context.RECEIVER_EXPORTED);
            registerReceiver(screen, s, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packages, f);
            registerReceiver(screen, s);
        }
        int legacy = store.removeLegacyDevices();
        if (legacy > 0) store.log("device_removed", legacy + " phone(s) from before encryption: pair again", "TV");
        Api api = new Api(this, new Api.Actions() {
            @Override public boolean globalAction(String action) { return GuardService.this.globalAction(action); }
            @Override public void refresh() { main.post(GuardService.this::refreshNow); }
            @Override public JSONObject now() { return GuardService.this.nowJson(); }
        });
        // Port 8765: plain HTTP, pairing only. Port 8766: mutual TLS, every command.
        server = new ControlServer(api, com.ajit.tvhero.protocol.Protocol.PAIRING_PORT, null);
        server.start();
        try {
            secureServer = new ControlServer(api, com.ajit.tvhero.protocol.Protocol.SECURE_PORT,
                    TlsIdentity.get().serverContext(fp -> store.deviceByFingerprint(fp) != null));
            secureServer.start();
        } catch (Exception e) {
            Log.e(TAG, "no secure channel: phones can't control the TV until this is fixed", e);
        }
        registerNsd();
        main.postDelayed(tick, 3000);
        Log.i(TAG, "guard running");
    }

    // ---------- foreground app ----------

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName()) && ui.showing()) return;   // our own overlay
        if (playbackLock() && !pkg.equals(getPackageName()) && !Profiles.KID_ALWAYS.contains(pkg) && launchable(pkg)) {
            // The TV is locked: an app started behind the screen (a shortcut key on the remote) goes away at once.
            stopPlayback();
            sendAway(pkg);
            return;
        }
        if (!pkg.equals(fgPkg)) {
            closeSession();
            fgPkg = pkg;
            fgTitle = "";
            fgSince = System.currentTimeMillis();
        }
        check(pkg);
    }

    private java.util.Set<String> launchableCache = java.util.Collections.emptySet();
    private long launchableAt;

    /**
     * Profiles limit the apps a person can open. System components that pop up on their own (TV input
     * services at boot, dialogs, the keyboard) have no launcher entry and are never blocked by a profile.
     */
    private boolean launchable(String pkg) {
        long now = SystemClock.elapsedRealtime();
        if (now - launchableAt > 5 * 60_000 || launchableCache.isEmpty()) {
            launchableCache = new java.util.HashSet<>(store.launchablePackages());
            launchableAt = now;
        }
        return launchableCache.contains(pkg);
    }

    /** Blocks [pkg] if the rules say so. */
    private void check(String pkg) {
        if (pkg.equals(getPackageName()) || isScreenLock()) return;
        GuardStore.State st = store.state(pkg);
        Profiles.Kid kid = profiles.activeKid();
        String title = null, body = null;
        boolean pending = false;
        if (st != GuardStore.State.ALLOWED) {
            pending = st == GuardStore.State.PENDING;
            title = label(pkg) + (pending ? " is waiting for approval" : " is blocked");
            body = pending ? "This app was just installed. A parent can approve it in the tv-hero app on their phone."
                    : "A parent has blocked this app. Ask them to allow it in the tv-hero app on their phone.";
        } else if (kid != null && !Profiles.KID_ALWAYS.contains(pkg)
                && (Profiles.KID_NEVER.contains(pkg) || (!kid.all.contains(pkg) && launchable(pkg)))) {
            title = label(pkg) + " isn't on " + kid.name + "'s list";
            body = Profiles.KID_NEVER.contains(pkg) ? "Settings and app installs are for parents."
                    : "Ask a parent to add it to your apps in the tv-hero app.";
        }
        if (title == null) return;
        long now = SystemClock.elapsedRealtime();
        if (pkg.equals(lastBlocked) && now - lastBlockedAt < 1500) return;
        lastBlocked = pkg;
        lastBlockedAt = now;
        store.log(pending ? "opened_pending" : (kid != null && st == GuardStore.State.ALLOWED ? "opened_not_allowed" : "opened_blocked"), pkg);
        sendAway(pkg);
        if (kid != null && !kid.id.equals(Profiles.GUEST) && st == GuardStore.State.ALLOWED && !Profiles.KID_NEVER.contains(pkg)) {
            final Profiles.Kid k = kid;
            ui.message(Overlays.Kind.BLOCKED, "🔒", title, body, ui::hide,
                    new Overlays.Action("OK", ui::hide),
                    new Overlays.Action("Ask a Parent", () -> {
                        store.addRequest(k.id, k.name, pkg);
                        store.log("request", pkg, k.name);
                        ui.hide();
                        ui.toast("Asked. A parent will see it on their phone.");
                    }));
        } else if (kid != null && Profiles.KID_NEVER.contains(pkg)) {
            // Settings, Play Store, installers: a parent in front of the TV can switch to Parent right here.
            ui.message(Overlays.Kind.BLOCKED, "🔒", title, body, ui::hide,
                    new Overlays.Action("OK", ui::hide),
                    new Overlays.Action("Switch to Parent", () -> askPin("Parent PIN", () -> {
                        profiles.setActive(Profiles.PARENT);
                        pickerDone = true;
                        store.log("profile", "Parent", "TV");
                        refreshNow();
                        ui.toast("Parent: all apps. Open it again.");
                    }, ui::hide)));
        } else {
            ui.message(Overlays.Kind.BLOCKED, "🔒", title, body, ui::hide,
                    new Overlays.Action("OK", ui::hide));
        }
    }

    /** Home, then stop the app once it's in the background. */
    private void sendAway(String pkg) {
        performGlobalAction(GLOBAL_ACTION_HOME);
        main.postDelayed(() -> {
            ActivityManager am = getSystemService(ActivityManager.class);
            if (am != null && !pkg.equals(getPackageName())) am.killBackgroundProcesses(pkg);
        }, 1500);
    }

    // ---------- profiles, time, bedtime ----------

    private boolean isScreenLock() {
        Overlays.Kind k = ui.kind();
        return k == Overlays.Kind.TIMES_UP || k == Overlays.Kind.BEDTIME || k == Overlays.Kind.LOCKED
                || k == Overlays.Kind.PICKER || k == Overlays.Kind.PIN || k == Overlays.Kind.PAIRING
                || k == Overlays.Kind.CONFIRM || k == Overlays.Kind.MENU || k == Overlays.Kind.QUIZ;
    }

    private boolean screenOn() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm == null || pm.isInteractive();
    }

    private void onWake() {
        String w = profiles.onWake();
        List<Profiles.Kid> kids = profiles.kids();
        if (kids.isEmpty()) return;
        if (!"ask".equals(w)) {
            profiles.setActive(w);
            refreshNow();
            return;
        }
        pickerDone = false;
        showPicker();
    }

    void showPicker() { showPicker(false); }

    /** [fromHome]: opened with the profile button, so Back closes it. At wake it waits for a choice. */
    void showPicker(boolean fromHome) {
        List<Profiles.Kid> kids = profiles.kids();
        if (kids.isEmpty() && !fromHome) { pickerDone = true; return; }
        ui.picker(kids, k -> {
            Runnable go = () -> {
                profiles.setActive(k.id);
                pickerDone = true;
                ui.hide();
                store.log("profile", k.name, "TV");
                refreshNow();
            };
            if (escapesLimits(k)) askPin("Parent PIN for " + k.name, go, () -> showPicker(fromHome));
            else go.run();
        }, () -> {
            Runnable go = () -> {
                profiles.setActive(Profiles.GUEST);
                pickerDone = true;
                ui.hide();
                store.log("profile", "Guest", "TV");
                refreshNow();
            };
            if (escapesLimits(profiles.guest())) askPin("Parent PIN for Guest", go, () -> showPicker(fromHome));
            else go.run();
        }, () -> askPin("Parent PIN", () -> {
            profiles.setActive(Profiles.PARENT);
            pickerDone = true;
            store.log("profile", "Parent", "TV");
            refreshNow();
        }, () -> showPicker(fromHome)), () -> askPin("Parent PIN", this::parentMenu, () -> showPicker(fromHome)),
                fromHome ? () -> { ui.hide(); pickerDone = true; } : null);
    }

    // ---------- Parent Menu (on the TV, after the PIN) ----------

    void parentMenu() { parentMenu(0); }

    /** Rows of the Parent Menu, counted without "Switch to Parent" (shown only outside Parent). */
    private static final int ROW_PROFILES = 1, ROW_COMMON = 2, ROW_WAKE = 3, ROW_PIN = 4, ROW_PHONES = 5;

    private void parentMenu(int focus) {
        java.util.List<Overlays.Item> items = new java.util.ArrayList<>();
        String active = profiles.activeId();
        boolean switchRow = !Profiles.PARENT.equals(active);
        if (focus > 0 && !switchRow) focus--;
        if (switchRow) {
            items.add(new Overlays.Item("Switch to Parent", "All apps", () -> {
                profiles.setActive(Profiles.PARENT);
                store.log("profile", "Parent", "TV");
                ui.hide();
                pickerDone = true;
                refreshNow();
            }));
        }
        int n = profiles.kids().size();
        items.add(new Overlays.Item("Profiles", n + (n == 1 ? " profile" : " profiles"), () -> profilesMenu(0)));
        items.add(new Overlays.Item("Apps for Everyone", profiles.commonApps().size() + " apps", () -> commonAppsMenu(0)));
        items.add(new Overlays.Item("When the TV Turns On", wakeLabel(profiles.onWake()), this::wakeMenu));
        items.add(new Overlays.Item("Parent PIN", profiles.pinSet() ? "Change" : "Not set", this::changePin));
        int phones = store.devicesJson().length();
        items.add(new Overlays.Item("Who Can Control This TV", phones + (phones == 1 ? " phone" : " phones"), this::phonesMenu));
        boolean net = store.internetControl();
        items.add(new Overlays.Item("Internet Control", net ? "On" : "Off", () -> {
            if (net) { store.setInternetControl(false, "TV"); store.log("internet_off", "", "TV"); parentMenu(); }
            else confirmInternet("the Parent Menu", ok -> { if (ok) { store.setInternetControl(true, "TV"); store.log("internet_on", "", "TV"); } parentMenu(); });
        }));
        boolean adb = android.provider.Settings.Global.getInt(getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, 0) == 1;
        items.add(new Overlays.Item("Debugging (ADB)", adb ? "On \u00b7 press to turn off" : "Off", () -> {
            if (adb) {
                try { android.provider.Settings.Global.putInt(getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, 0); } catch (SecurityException ignored) { }
                store.log("adb_off", "", "TV");
            }
            parentMenu();
        }));
        items.add(new Overlays.Item("Guest Mode", profiles.isGuest() ? "On" : "Off", () -> {
            profiles.setActive(profiles.isGuest() ? Profiles.PARENT : Profiles.GUEST);
            store.log("profile", profiles.isGuest() ? "Guest" : "Parent", "TV");
            ui.hide();
            pickerDone = true;
            refreshNow();
        }));
        int gl = profiles.guestLimit(), guestRow = items.size() + (switchRow ? 0 : 1);
        items.add(new Overlays.Item("Guest Time", gl == 0 ? "No limit" : minutes(gl) + " a day", () -> {
            profiles.setGuestLimit(nextOf(LIMITS, profiles.guestLimit()));
            store.log("guest_limit", String.valueOf(profiles.guestLimit()), "TV");
            parentMenu(guestRow);
        }));
        items.add(new Overlays.Item("Done", null, () -> { ui.hide(); pickerDone = true; refreshNow(); }));
        ui.menu(Overlays.Kind.MENU, "Parent Menu", profiles.pinSet() ? "Everything here can also be done from the tv-hero app on your phone."
                        : "Tip: set a Parent PIN. It's quicker with the remote than the password.",
                items, () -> { ui.hide(); pickerDone = true; refreshNow(); }, focus);
    }

    private String wakeLabel(String w) {
        if ("ask".equals(w)) return "Ask Who's Watching";
        if (Profiles.PARENT.equals(w)) return "Parent";
        Profiles.Kid k = profiles.kid(w);
        return k == null ? "Ask Who's Watching" : k.name;
    }

    private void wakeMenu() {
        java.util.List<Overlays.Item> items = new java.util.ArrayList<>();
        String now = profiles.onWake();
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (Profiles.Kid k : profiles.kids()) ids.add(k.id);
        ids.add(Profiles.PARENT);
        ids.add("ask");
        for (String id : ids) {
            items.add(new Overlays.Item(wakeLabel(id), id.equals(now) ? "\u2713" : "", () -> {
                profiles.setOnWake(id);
                store.log("on_wake", wakeLabel(id), "TV");
                parentMenu(ROW_WAKE);
            }));
        }
        ui.menu(Overlays.Kind.MENU, "When the TV Turns On", "The TV opens in this profile. Parent always needs the PIN on the TV.",
                items, () -> parentMenu(ROW_WAKE));
    }

    private void changePin() {
        ui.pin("New parent PIN (4\u20136 digits)", first -> {
            main.post(() -> ui.pin("Enter the new PIN again", again -> {
                if (!again.equals(first)) return false;
                profiles.setPin(first);
                store.log("pin_changed", "", "TV");
                main.post(() -> { ui.toast("Parent PIN saved"); parentMenu(ROW_PIN); });
                return true;
            }, () -> parentMenu(ROW_PIN)));
            return true;
        }, () -> parentMenu(ROW_PIN));
    }

    // ---------- profiles, on the TV ----------

    /** Launchable apps a profile could be given (never Settings, Play Store or installers), sorted by name. */
    private java.util.List<String[]> choosableApps() {
        java.util.List<String> pkgs = new java.util.ArrayList<>();
        for (String p : store.launchablePackages()) {
            if (Profiles.KID_NEVER.contains(p) || store.state(p) != GuardStore.State.ALLOWED) continue;
            if (p.equals(getPackageName())) continue;
            pkgs.add(p);
        }
        java.util.Map<String, String> labels = new java.util.HashMap<>(GuardStore.labels(this, pkgs));
        labels.putAll(Config.packageMap(java.util.Arrays.asList(getResources().getStringArray(R.array.app_labels))));   // Home's names
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String p : pkgs) out.add(new String[]{p, labels.containsKey(p) ? labels.get(p) : p});
        out.sort((x, y) -> x[1].compareToIgnoreCase(y[1]));
        return out;
    }

    private void profilesMenu(int focus) {
        java.util.List<Overlays.Item> items = new java.util.ArrayList<>();
        for (Profiles.Kid k : profiles.kids()) {
            String v = k.all.size() + " apps" + (k.dailyLimit > 0 ? " \u00b7 " + minutes(k.dailyLimit) + " a day" : "")
                    + (k.bedtimeOn ? " \u00b7 bedtime " + clock(k.bedStart) : "");
            items.add(new Overlays.Item(k.name, v, () -> profileMenu(k.id, 0)));
        }
        if (profiles.kids().size() < Profiles.MAX_KIDS) {
            items.add(new Overlays.Item("Add Profile", "+", () -> ui.textInput("New profile", "Name", "", false, name -> {
                try {
                    JSONObject o = new JSONObject().put("name", name).put("color", profiles.kids().size()).put("allowed", new org.json.JSONArray());
                    Profiles.Kid k = profiles.save(o);
                    store.log("profile_saved", name, "TV");
                    if (k != null) profileMenu(k.id, 0); else profilesMenu(0);
                } catch (org.json.JSONException e) { profilesMenu(0); }
            }, () -> profilesMenu(0))));
        }
        items.add(new Overlays.Item("Back", null, () -> parentMenu(ROW_PROFILES)));
        ui.menu(Overlays.Kind.MENU, "Profiles", "Every profile also gets the Apps for Everyone. Settings and the Play Store are for Parent only.",
                items, () -> parentMenu(ROW_PROFILES), focus);
    }

    private static final int[] LIMITS = {0, 30, 60, 90, 120, 180, 240};
    private static final int[] BEDTIMES = {-1, 20 * 60, 20 * 60 + 30, 21 * 60, 21 * 60 + 30, 22 * 60, 22 * 60 + 30};

    private void profileMenu(String id, int focus) {
        Profiles.Kid k = profiles.kid(id);
        if (k == null) { profilesMenu(0); return; }
        java.util.List<Overlays.Item> items = new java.util.ArrayList<>();
        items.add(new Overlays.Item("Apps", k.allowed.size() + " own + " + profiles.commonApps().size() + " for everyone", () -> profileAppsMenu(id, 0)));
        items.add(new Overlays.Item("Daily Limit", k.dailyLimit == 0 ? "No limit" : minutes(k.dailyLimit), () -> {
            int i = 0;
            for (int j = 0; j < LIMITS.length; j++) if (LIMITS[j] == k.dailyLimit) i = j;
            k.dailyLimit = LIMITS[(i + 1) % LIMITS.length];
            saveKid(k);
            profileMenu(id, 1);
        }));
        items.add(new Overlays.Item("Bedtime", k.bedtimeOn ? clock(k.bedStart) + " \u2013 " + clock(k.bedEnd) : "Off", () -> {
            int i = 0;
            for (int j = 0; j < BEDTIMES.length; j++) if (k.bedtimeOn ? BEDTIMES[j] == k.bedStart : BEDTIMES[j] < 0) i = j;
            int next = BEDTIMES[(i + 1) % BEDTIMES.length];
            k.bedtimeOn = next >= 0;
            if (next >= 0) { k.bedStart = next; if (k.bedEnd == k.bedStart) k.bedEnd = 7 * 60; }
            saveKid(k);
            profileMenu(id, 2);
        }));
        items.add(new Overlays.Item("Earn Time", k.quizOn ? "Up to " + k.quizCap + " min a day" : "Off", () -> quizMenu(id, 0)));
        items.add(new Overlays.Item("Rename", k.name, () -> ui.textInput("Rename profile", "Name", k.name, false, name -> {
            k.name = name;
            saveKid(k);
            profileMenu(id, 4);
        }, () -> profileMenu(id, 4))));
        items.add(new Overlays.Item("Remove Profile", null, () -> ui.message(Overlays.Kind.CONFIRM, "\uD83D\uDDD1", "Remove " + k.name + "?",
                "Their apps, limits and history are deleted. Apps themselves stay on the TV.", () -> profileMenu(id, 5),
                new Overlays.Action("Cancel", () -> profileMenu(id, 5)),
                new Overlays.Action("Remove", () -> {
                    profiles.delete(id);
                    if (id.equals(profiles.onWake())) profiles.setOnWake("ask");
                    store.log("profile_removed", k.name, "TV");
                    profilesMenu(0);
                }))));
        items.add(new Overlays.Item("Back", null, () -> profilesMenu(0)));
        ui.menu(Overlays.Kind.MENU, k.name, "Press OK on Daily Limit or Bedtime to step through the choices. Bedtime ends at " + clock(k.bedEnd) + ".",
                items, () -> profilesMenu(0), focus);
    }

    private static final int[] QUIZ_MINUTES = {2, 5, 10};
    private static final int[] QUIZ_CAPS = {10, 15, 20, 30};

    private static int nextOf(int[] list, int now) {
        for (int i = 0; i < list.length; i++) if (list[i] == now) return list[(i + 1) % list.length];
        return list[0];
    }

    /** Earn Time for one profile: on/off, minutes per right answer, most per day, level, topics. */
    private void quizMenu(String id, int focus) {
        Profiles.Kid k = profiles.kid(id);
        if (k == null) { profilesMenu(0); return; }
        java.util.List<Overlays.Item> items = new java.util.ArrayList<>();
        items.add(new Overlays.Item("Earn Time", k.quizOn ? "On" : "Off", () -> {
            k.quizOn = !k.quizOn; saveKid(k); quizMenu(id, 0);
        }));
        items.add(new Overlays.Item("Each Right Answer", minutes(k.quizMinutes), () -> {
            k.quizMinutes = nextOf(QUIZ_MINUTES, k.quizMinutes); saveKid(k); quizMenu(id, 1);
        }));
        items.add(new Overlays.Item("Most per Day", minutes(k.quizCap), () -> {
            k.quizCap = nextOf(QUIZ_CAPS, k.quizCap); saveKid(k); quizMenu(id, 2);
        }));
        items.add(new Overlays.Item("Level", Quiz.levelLabel(k.quizLevel), () -> {
            k.quizLevel = k.quizLevel % 3 + 1; saveKid(k); quizMenu(id, 3);
        }));
        int row = 4;
        for (String t : Quiz.TOPICS) {
            int at = row++;
            boolean on = k.quizTopics.contains(t);
            items.add(new Overlays.Item(Quiz.topicLabel(t), on ? "On" : "Off", () -> {
                if (on && k.quizTopics.size() == 1) { ui.toast("Keep at least one topic"); return; }
                if (on) k.quizTopics.remove(t); else k.quizTopics.add(t);
                saveKid(k);
                quizMenu(id, at);
            }));
        }
        items.add(new Overlays.Item("Back", null, () -> profileMenu(id, 3)));
        ui.menu(Overlays.Kind.MENU, "Earn Time \u00b7 " + k.name,
                "When time's up, " + k.name + " can answer questions: each right answer adds minutes, up to the most per day. "
                        + "Three wrong in a row means a " + Quiz.BREAK_MINUTES + "-minute break. Questions are built in; nothing leaves the TV.",
                items, () -> profileMenu(id, 3), focus);
    }

    private void saveKid(Profiles.Kid k) {
        try {
            profiles.save(k.json());
            store.log("profile_saved", k.name, "TV");
            sendBroadcast(new Intent(GuardStore.ACTION_CHANGED).setPackage(getPackageName()));
        } catch (org.json.JSONException ignored) { }
    }

    private void profileAppsMenu(String id, int focus) {
        Profiles.Kid k = profiles.kid(id);
        if (k == null) { profilesMenu(0); return; }
        java.util.Set<String> common = profiles.commonApps();
        java.util.List<Overlays.Item> items = new java.util.ArrayList<>();
        java.util.List<String[]> apps = choosableApps();
        for (int i = 0; i < apps.size(); i++) {
            final int row = i;
            String pkg = apps.get(i)[0];
            if (common.contains(pkg)) {
                items.add(new Overlays.Item(apps.get(i)[1], "Everyone", () -> ui.toast("Change it in Apps for Everyone")));
            } else {
                boolean on = k.allowed.contains(pkg);
                items.add(new Overlays.Item(apps.get(i)[1], on ? "On" : "Off", () -> {
                    if (on) k.allowed.remove(pkg); else k.allowed.add(pkg);
                    saveKid(k);
                    profileAppsMenu(id, row);
                }));
            }
        }
        items.add(new Overlays.Item("Back", null, () -> profileMenu(id, 0)));
        ui.menu(Overlays.Kind.MENU, k.name + ": Apps", "On: shows on " + k.name + "'s Home and opens. Off: hidden and blocked.",
                items, () -> profileMenu(id, 0), focus);
    }

    private void commonAppsMenu(int focus) {
        java.util.Set<String> common = profiles.commonApps();
        java.util.List<Overlays.Item> items = new java.util.ArrayList<>();
        java.util.List<String[]> apps = choosableApps();
        for (int i = 0; i < apps.size(); i++) {
            final int row = i;
            String pkg = apps.get(i)[0];
            boolean on = common.contains(pkg);
            items.add(new Overlays.Item(apps.get(i)[1], on ? "On" : "Off", () -> {
                java.util.Set<String> next = profiles.commonApps();
                if (on) next.remove(pkg); else next.add(pkg);
                profiles.setCommonApps(next);
                store.log("common_apps", (on ? "-" : "+") + pkg, "TV");
                sendBroadcast(new Intent(GuardStore.ACTION_CHANGED).setPackage(getPackageName()));
                commonAppsMenu(row);
            }));
        }
        items.add(new Overlays.Item("Back", null, () -> parentMenu(ROW_COMMON)));
        ui.menu(Overlays.Kind.MENU, "Apps for Everyone", "These apps are on in every profile, on top of each profile's own apps.",
                items, () -> parentMenu(ROW_COMMON), focus);
    }

    private static String minutes(int m) {
        return m < 60 ? m + " min" : (m % 60 == 0 ? (m / 60) + " h" : (m / 60) + " h " + (m % 60) + " min");
    }

    private static String clock(int minuteOfDay) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private void phonesMenu() {
        java.util.List<Overlays.Item> items = new java.util.ArrayList<>();
        org.json.JSONArray d = store.devicesJson();
        for (int i = 0; i < d.length(); i++) {
            org.json.JSONObject o = d.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id"), name = o.optString("name");
            long seen = o.optLong("last_seen");
            String value = seen == 0 ? "Not used yet" : ("internet".equals(o.optString("last_via")) ? "Internet · " : "Home · ")
                    + android.text.format.DateUtils.getRelativeTimeSpanString(seen);
            items.add(new Overlays.Item(name, value, () -> ui.message(Overlays.Kind.CONFIRM, "📱", "Remove " + name + "?",
                    "It can no longer control this TV. It can only pair again from this room.", this::phonesMenu,
                    new Overlays.Action("Cancel", this::phonesMenu),
                    new Overlays.Action("Remove", () -> { store.removeDevice(id); store.log("device_removed", name, "TV"); phonesMenu(); }))));
        }
        items.add(new Overlays.Item("Back", null, () -> parentMenu(ROW_PHONES)));
        ui.menu(Overlays.Kind.MENU, "Who Can Control This TV", "Removing a phone takes effect at once.", items, () -> parentMenu(ROW_PHONES));
    }

    // ---------- prompts the phone app waits for ----------

    /**
     * Allow / Don't Allow for a pairing request, with the comparison code. The person checks that the
     * phone shows the same code; focus starts on Don't Allow so a stray OK press can't let a phone in.
     */
    void confirmPairing(String phone, String code, java.util.function.Consumer<Boolean> answer) {
        main.post(() -> ui.message(Overlays.Kind.PAIRING, "📱", com.ajit.tvhero.protocol.Pairing.display(code),
                "\u201c" + phone + "\u201d wants to control this TV.\nAllow it only if the phone shows this same code.\n"
                        + "If the codes differ, or you didn't start this, choose Don't Allow.",
                () -> { ui.hide(); answer.accept(false); },
                new Overlays.Action("Don't Allow", () -> { ui.hide(); answer.accept(false); }),
                new Overlays.Action("Allow", () -> { ui.hide(); answer.accept(true); })));
    }

    void endPairingPrompt() {
        main.post(() -> { if (ui.kind() == Overlays.Kind.PAIRING) ui.hide(); });
    }

    void confirmInternet(String by, java.util.function.Consumer<Boolean> answer) {
        main.post(() -> ui.message(Overlays.Kind.CONFIRM, "🌐", "Allow control over the internet?",
                "Paired phones (" + store.devicesJson().length() + ") will be able to control this TV from anywhere, through Tailscale. Requested from " + by + ". You can turn it off any time.",
                () -> { ui.hide(); answer.accept(false); },
                new Overlays.Action("Don't Allow", () -> { ui.hide(); answer.accept(false); }),
                new Overlays.Action("Allow", () -> { ui.hide(); answer.accept(true); })));
    }

    private final java.util.Map<String, Long> remoteNoticeAt = new java.util.HashMap<>();

    /** Remote control is never invisible: a note on the TV when a phone starts controlling over the internet. */
    void remoteNotice(String phone) {
        long now = System.currentTimeMillis();
        Long last;
        synchronized (remoteNoticeAt) { last = remoteNoticeAt.put(phone, now); }
        if (last == null || now - last > 10 * 60_000) ui.toast("🌐 " + phone + " is controlling this TV over the internet");
    }

    /** Asks for the parent PIN on the TV; without one set, explains how to set it. */
    private void askPin(String title, Runnable onOk, Runnable onCancel) {
        if (!profiles.pinSet()) {   // no PIN yet: the parent password works on the TV too (TV keyboard)
            ui.textInput("Parent password", "The password from the tv-hero app", "", true, pw -> {
                GuardStore.PasswordResult r = store.checkPassword(pw);
                if (r == GuardStore.PasswordResult.OK) {
                    ui.hide();
                    onOk.run();
                } else {
                    ui.toast(r == GuardStore.PasswordResult.LOCKED ? "Too many tries. Wait 5 minutes." : "Wrong password");
                }
            }, onCancel);
            return;
        }
        ui.pin(title, pin -> {
            if (!profiles.checkPin(pin)) return false;
            ui.hide();
            onOk.run();
            return true;
        }, onCancel);
    }

    /** Re-evaluates everything now, e.g. after the phone changed a rule. */
    void refreshNow() {
        Overlays.Kind shown = ui.kind();
        if (shown == Overlays.Kind.TIMES_UP || shown == Overlays.Kind.BEDTIME || shown == Overlays.Kind.LOCKED) ui.hide();
        sendBroadcast(new Intent(GuardStore.ACTION_CHANGED).setPackage(getPackageName()));
        tick();
        if (!fgPkg.isEmpty()) check(fgPkg);
    }

    private void tick() {
        long nowEl = SystemClock.elapsedRealtime();
        long elapsed = Math.min(nowEl - lastTick, 2 * TICK_MS);
        lastTick = nowEl;
        if (!screenOn()) return;
        if (playbackLock()) stopPlayback(); else releaseFocus();
        String active = profiles.activeId();
        Profiles.Kid kid = profiles.activeKid();
        if (!isScreenLock()) profiles.addUsage(active, (int) (elapsed / 1000));
        updateTitle();
        capVolume();

        // Sleep timer
        long sleepAt = profiles.sleepAt();
        if (sleepAt > 0) {
            long left = sleepAt - System.currentTimeMillis();
            if (left <= 0) { profiles.setSleepAt(0); sleep(); return; }
            if (left <= 65_000 && warned.add("sleep" + sleepAt)) ui.toast("The TV turns off in 1 minute");
        }

        if (profiles.kids().isEmpty() && !pickerDone && (ui.kind() == Overlays.Kind.PICKER || ui.kind() == Overlays.Kind.PIN)) {
            ui.hide();
            pickerDone = true;
        }
        Overlays.Kind prompt = ui.kind();
        if (prompt == Overlays.Kind.PAIRING || prompt == Overlays.Kind.CONFIRM || prompt == Overlays.Kind.MENU) return;
        if (profiles.locked()) {
            if (ui.kind() != Overlays.Kind.LOCKED && ui.kind() != Overlays.Kind.PIN) showLocked();
            return;
        }
        if (!pickerDone && ui.kind() != Overlays.Kind.PICKER && ui.kind() != Overlays.Kind.PIN && !profiles.kids().isEmpty()
                && "ask".equals(profiles.onWake())) {
            showPicker();
            return;
        }
        if (kid == null) {
            if (ui.kind() == Overlays.Kind.TIMES_UP || ui.kind() == Overlays.Kind.BEDTIME || ui.kind() == Overlays.Kind.LOCKED) ui.hide();
            return;
        }
        int m = Profiles.minuteOfDay();
        boolean bedtime = kid.inBedtime(m) && System.currentTimeMillis() > profiles.bedtimeSnoozeUntil();
        int left = profiles.minutesLeft(kid);
        if (bedtime) {
            if (ui.kind() != Overlays.Kind.BEDTIME && ui.kind() != Overlays.Kind.PIN) showBedtime(kid);
            if (bedtimeSleepAt == 0) bedtimeSleepAt = nowEl + 60_000;
            else if (nowEl >= bedtimeSleepAt) { bedtimeSleepAt = 0; sleep(); }
            return;
        }
        bedtimeSleepAt = 0;
        if (left == 0) {
            Overlays.Kind on = ui.kind();
            if (on != Overlays.Kind.TIMES_UP && on != Overlays.Kind.PIN && on != Overlays.Kind.QUIZ) showTimesUp(kid);
            return;
        }
        if (ui.kind() == Overlays.Kind.TIMES_UP || ui.kind() == Overlays.Kind.BEDTIME || ui.kind() == Overlays.Kind.LOCKED) ui.hide();
        int toBed = kid.minutesToBedtime(m);
        String day = Profiles.today();
        if (left > 0 && left <= 5 && warned.add(day + kid.id + "L" + (left <= 1 ? 1 : 5))) {
            ui.toast(left <= 1 ? "1 minute of TV left today" : left + " minutes of TV left today");
        }
        if (toBed >= 0 && toBed <= 5 && warned.add(day + kid.id + "B" + (toBed <= 1 ? 1 : 5))) {
            ui.toast(toBed <= 1 ? "Bedtime in 1 minute" : "Bedtime in " + toBed + " minutes");
        }
    }

    private void showLocked() {
        stopPlayback();
        if (!fgPkg.isEmpty() && !fgPkg.equals(getPackageName())) sendAway(fgPkg);
        ui.message(Overlays.Kind.LOCKED, "⏸", "The TV is paused", "A parent paused the TV from their phone.", null,
                new Overlays.Action("Parent PIN", () -> askPin("Parent PIN to unlock", () -> {
                    profiles.setLocked(false);
                    store.log("unlocked", "PIN");
                    refreshNow();
                }, this::showLocked)));
    }

    private void showTimesUp(Profiles.Kid kid) {
        stopPlayback();
        if (!fgPkg.isEmpty() && !fgPkg.equals(getPackageName())) sendAway(fgPkg);
        if (ui.kind() != Overlays.Kind.TIMES_UP && warned.add(Profiles.today() + kid.id + "T" + profiles.extraMinutes(kid.id))) {
            store.log("times_up", kid.name);
        }
        int earned = profiles.quizEarned(kid.id);
        boolean canEarn = kid.quizOn && earned < kid.quizCap;
        String body = "Your " + kid.dailyLimit + " minutes are used up.";
        if (canEarn) body += " Answer questions to earn up to " + (kid.quizCap - earned) + " more minutes, or ask a parent.";
        else if (kid.quizOn && kid.quizCap > 0) body += " You earned all " + kid.quizCap + " bonus minutes today. Well done!";
        else body += " A parent can add more time from their phone.";
        java.util.List<Overlays.Action> actions = new java.util.ArrayList<>();
        if (canEarn) actions.add(new Overlays.Action("\u2B50 Earn Time", () -> startQuiz(kid.id)));
        actions.add(new Overlays.Action("Switch Profile", this::showPicker));
        actions.add(new Overlays.Action("Ask for Time", () -> askForTime(kid, "time:30")));
        actions.add(new Overlays.Action("Parent PIN", () -> askPin("Parent PIN: 30 more minutes", () -> {
            profiles.addExtra(kid.id, 30);
            store.log("extra_time", kid.name);
            refreshNow();
        }, () -> showTimesUp(kid))));
        ui.message(Overlays.Kind.TIMES_UP, "⏰", "That's all the TV for today, " + kid.name, body, null, actions.toArray(new Overlays.Action[0]));
    }

    private void showBedtime(Profiles.Kid kid) {
        stopPlayback();
        if (!fgPkg.isEmpty() && !fgPkg.equals(getPackageName())) sendAway(fgPkg);
        store.log("bedtime", kid.name);
        ui.message(Overlays.Kind.BEDTIME, "🌙", "It's bedtime, " + kid.name,
                "The TV turns off in a minute. Good night!", null,
                new Overlays.Action("Switch Profile", this::showPicker),
                new Overlays.Action("Ask for Time", () -> askForTime(kid, "bedtime:60")),
                new Overlays.Action("Parent PIN", () -> askPin("Parent PIN: 1 more hour tonight", () -> {
                    profiles.snoozeBedtime(60);
                    bedtimeSleepAt = 0;
                    refreshNow();
                }, () -> showBedtime(kid))));
    }

    // ---------- Earn Time: questions on the Time's up screen ----------

    private final Quiz quiz = new Quiz();
    private static final String[] CHEERS = {"Correct!", "Well done!", "Brilliant!", "Super!", "You got it!"};

    private static String topicIcon(String t) {
        switch (t) {
            case Quiz.MATHS: return "\uD83D\uDD22";
            case Quiz.ENGLISH: return "\uD83D\uDD24";
            case Quiz.HINDI: return "\u0905";
            case Quiz.PUZZLES: return "\uD83E\uDDE9";
            default: return "\uD83C\uDF0D";
        }
    }

    private String quizProgress(Profiles.Kid kid) {
        return "\u2B50 " + profiles.quizEarned(kid.id) + " of " + kid.quizCap + " bonus minutes earned today";
    }

    /** Back from the questions: Time's up again unless the minutes earned let the TV go on. */
    private void endQuiz() {
        ui.hide();
        refreshNow();
    }

    private void startQuiz(String kidId) {
        Profiles.Kid kid = profiles.kid(kidId);
        if (kid == null || !kid.quizOn) { endQuiz(); return; }
        stopPlayback();
        long pause = profiles.quizBreakUntil(kid.id);
        if (pause > 0) {
            int mins = (int) Math.max(1, (pause - System.currentTimeMillis() + 59_999) / 60_000);
            ui.message(Overlays.Kind.QUIZ, "\u2615", "Time for a short break",
                    "Three answers in a row weren't right. The questions come back in " + mins + (mins == 1 ? " minute." : " minutes."),
                    this::endQuiz, new Overlays.Action("OK", this::endQuiz));
            return;
        }
        if (profiles.quizEarned(kid.id) >= kid.quizCap) { endQuiz(); return; }
        Quiz.Question q = quiz.next(kid.quizTopics, kid.quizLevel);
        ui.quiz(topicIcon(q.topic) + "  " + Quiz.topicLabel(q.topic) + " \u00b7 " + Quiz.levelLabel(kid.quizLevel),
                q.text, q.options, quizProgress(kid), pick -> quizAnswer(kid, q, pick), this::endQuiz);
    }

    private void quizAnswer(Profiles.Kid kid, Quiz.Question q, int pick) {
        boolean right = pick == q.answer;
        int got = profiles.quizAnswer(kid, right);
        int earned = profiles.quizEarned(kid.id);
        if (right) {
            if (got > 0) store.log("quiz_earned", "+" + got + " min (" + Quiz.topicLabel(q.topic) + ")", kid.name);
            boolean more = earned < kid.quizCap;
            String body = (got > 0 ? "+" + got + (got == 1 ? " minute" : " minutes") + " of TV. " : "")
                    + (more ? quizProgress(kid) : "That's all " + kid.quizCap + " bonus minutes for today.");
            Overlays.Action watch = new Overlays.Action("Watch TV", this::endQuiz);
            if (more) {
                ui.message(Overlays.Kind.QUIZ, "\uD83C\uDF89", CHEERS[earned % CHEERS.length], body, this::endQuiz,
                        new Overlays.Action("Next Question", () -> startQuiz(kid.id)), watch);
            } else {
                ui.message(Overlays.Kind.QUIZ, "\uD83C\uDFC6", CHEERS[earned % CHEERS.length], body, this::endQuiz, watch);
            }
        } else if (profiles.quizBreakUntil(kid.id) > 0) {
            store.log("quiz_break", Quiz.BREAK_MINUTES + " min", kid.name);
            ui.message(Overlays.Kind.QUIZ, "\u2615", "Not quite: it's " + q.correct(),
                    "That's three in a row. Take a " + Quiz.BREAK_MINUTES + "-minute break, then try again.",
                    this::endQuiz, new Overlays.Action("OK", this::endQuiz));
        } else {
            ui.message(Overlays.Kind.QUIZ, "\uD83E\uDD14", "Not quite: it's " + q.correct(),
                    "No minutes lost. Have another go!", this::endQuiz,
                    new Overlays.Action("Next Question", () -> startQuiz(kid.id)),
                    new Overlays.Action("Back", this::endQuiz));
        }
    }

    /** A limited profile (daily limit or bedtime) switching to one with no daily limit would skip it: PIN. */
    private boolean escapesLimits(Profiles.Kid target) {
        Profiles.Kid now = profiles.activeKid();
        boolean limitedNow = playbackLock() || (now != null && (now.dailyLimit > 0 || now.bedtimeOn));
        boolean limitedThen = target != null && target.dailyLimit > 0;
        return limitedNow && !limitedThen;
    }

    /** "Ask for More Time": a request on the parents' phones (Needs You). The screen stays up. */
    private void askForTime(Profiles.Kid kid, String what) {
        store.addRequest(kid.id, kid.name, what);
        store.log("request", what, kid.name);
        ui.toast("Asked. A parent will see it on their phone.");
    }

    // ---------- stopping playback behind Time's up / Bedtime / Paused ----------

    private android.media.AudioFocusRequest focus;

    private boolean playbackLock() {
        Overlays.Kind k = ui.kind();
        return k == Overlays.Kind.TIMES_UP || k == Overlays.Kind.BEDTIME || k == Overlays.Kind.LOCKED
                || k == Overlays.Kind.QUIZ;
    }

    /**
     * The overlay only covers the picture. A video app playing media runs a foreground service, so
     * killBackgroundProcesses leaves it alone and the sound goes on. So: pause every media session that
     * plays (we can see them through the notification listener), send a pause key as a backstop, and
     * hold the audio focus while the screen is up so well-behaved apps stay quiet. Called again on
     * every tick, so pressing Play behind the screen doesn't help.
     */
    void stopPlayback() {
        try {
            MediaSessionManager msm = getSystemService(MediaSessionManager.class);
            for (MediaController c : msm.getActiveSessions(new ComponentName(this, MediaListener.class))) {
                if (c.getPackageName().equals(getPackageName())) continue;
                android.media.session.PlaybackState st = c.getPlaybackState();
                int state = st == null ? -1 : st.getState();
                if (state == android.media.session.PlaybackState.STATE_PLAYING
                        || state == android.media.session.PlaybackState.STATE_BUFFERING
                        || state == android.media.session.PlaybackState.STATE_FAST_FORWARDING
                        || state == android.media.session.PlaybackState.STATE_REWINDING) {
                    c.getTransportControls().pause();
                    Log.i(TAG, "paused " + c.getPackageName());
                }
            }
        } catch (RuntimeException e) {   // SecurityException without the listener permission
            Log.w(TAG, "can't reach media sessions: " + e.getMessage());
        }
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        if (am == null) return;
        if (am.isMusicActive()) {
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE));
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE));
        }
        if (focus == null) {
            focus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setOnAudioFocusChangeListener(change -> { })
                    .build();
            am.requestAudioFocus(focus);
        }
    }

    private void releaseFocus() {
        if (focus == null) return;
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        if (am != null) am.abandonAudioFocusRequest(focus);
        focus = null;
    }

    /** Standby: the accessibility lock-screen action sends Android 9 TVs to sleep. */
    void sleep() {
        closeSession();
        ui.hide();
        store.log("sleep", "");
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    // ---------- what is playing ----------

    private void updateTitle() {
        if (fgPkg.isEmpty()) return;
        try {
            MediaSessionManager msm = getSystemService(MediaSessionManager.class);
            List<MediaController> list = msm.getActiveSessions(new ComponentName(this, MediaListener.class));
            for (MediaController c : list) {
                if (!c.getPackageName().equals(fgPkg) || c.getMetadata() == null) continue;
                MediaMetadata md = c.getMetadata();
                String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (t == null) t = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                if (t != null && !t.equals(fgTitle)) {
                    closeSession();
                    fgTitle = t;
                    fgSince = System.currentTimeMillis();
                }
                return;
            }
        } catch (SecurityException e) {
            // Notification access not granted: titles stay empty, app names still recorded.
        }
    }

    private void closeSession() {
        if (fgPkg.isEmpty() || fgPkg.equals(getPackageName())) return;
        long now = System.currentTimeMillis();
        profiles.addSession(profiles.activeId(), fgPkg, fgTitle, fgSince, now);
        fgSince = now;
    }

    JSONObject nowJson() {
        try {
            Profiles.Kid kid = profiles.activeKid();
            return new JSONObject()
                    .put("screen_on", screenOn())
                    .put("profile", profiles.activeId())
                    .put("profile_name", kid == null ? "Parent" : kid.name)
                    .put("package", fgPkg)
                    .put("label", fgPkg.isEmpty() ? "" : label(fgPkg))
                    .put("title", fgTitle)
                    .put("since", fgSince)
                    .put("overlay", ui.kind().name().toLowerCase())
                    .put("minutes_left", profiles.minutesLeft(kid))
                    .put("locked", profiles.locked())
                    .put("sleep_at", profiles.sleepAt());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // ---------- night volume cap ----------

    private void capVolume() {
        int cap = profiles.quietCapNow();
        if (cap < 0) return;
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int limit = Math.max(1, (int) Math.ceil(max * cap / 100.0));
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > limit) {
            try { am.setStreamVolume(AudioManager.STREAM_MUSIC, limit, 0); } catch (SecurityException ignored) { }
        }
    }

    // ---------- helpers ----------

    String label(String pkg) {
        return GuardStore.labels(this, java.util.Collections.singletonList(pkg)).get(pkg);
    }

    boolean globalAction(String action) {
        int a;
        switch (action == null ? "" : action) {
            case "home": a = GLOBAL_ACTION_HOME; break;
            case "back": a = GLOBAL_ACTION_BACK; break;
            case "recents": a = GLOBAL_ACTION_RECENTS; break;
            case "notifications": a = GLOBAL_ACTION_NOTIFICATIONS; break;
            case "quick_settings": a = GLOBAL_ACTION_QUICK_SETTINGS; break;
            case "sleep": main.post(this::sleep); return true;
            case "wake": {
                android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
                if (pm == null) return false;
                @SuppressWarnings("deprecation")
                android.os.PowerManager.WakeLock wl = pm.newWakeLock(android.os.PowerManager.FULL_WAKE_LOCK
                        | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "tvhero:wake");
                wl.acquire(3000);
                return true;
            }
            case "picker": main.post(() -> showPicker(true)); return true;
            case "picker_done": main.post(() -> {
                pickerDone = true;
                if (ui.kind() == Overlays.Kind.PICKER || ui.kind() == Overlays.Kind.PIN) ui.hide();
            }); return true;
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
        si.setPort(com.ajit.tvhero.protocol.Protocol.PAIRING_PORT);
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
        instance = null;
        main.removeCallbacks(tick);
        closeSession();
        if (ui != null) ui.hide();
        try { unregisterReceiver(packages); } catch (IllegalArgumentException ignored) { }
        try { unregisterReceiver(screen); } catch (IllegalArgumentException ignored) { }
        if (server != null) server.stop();
        if (secureServer != null) secureServer.stop();
        NsdManager nsd = getSystemService(NsdManager.class);
        if (nsd != null && nsdListener != null) {
            try { nsd.unregisterService(nsdListener); } catch (RuntimeException ignored) { }
        }
        super.onDestroy();
    }
}
