package com.ajit.tvhero;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parental rules kept on the TV: which apps are blocked, which new installs wait for approval,
 * the parent password (PBKDF2 hash only) and the phones paired with this TV.
 *
 * Rules live on the TV so they keep working when no phone is around. All access goes through
 * this class; it is small and synchronised because the control server and the guard service
 * call it from different threads.
 */
public final class GuardStore {

    static final String PREFS = "guard";
    /** Broadcast inside this app when rules change, so Home rebuilds without blocked apps. */
    public static final String ACTION_CHANGED = "com.ajit.tvhero.GUARD_CHANGED";

    /** Apps that can never be blocked: blocking them would lock the TV or this app out. */
    static final Set<String> NEVER_BLOCK = Set.of(
            "com.ajit.tvhero", "android", "com.android.systemui", "com.android.tv.settings",
            "com.google.android.inputmethod.latin", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.droidlogic.tvinput", "com.android.tv",
            "com.google.android.tv");

    private static GuardStore instance;
    private final Context app;
    private final SharedPreferences prefs;

    private GuardStore(Context ctx) {
        app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized GuardStore get(Context ctx) {
        if (instance == null) instance = new GuardStore(ctx);
        return instance;
    }

    // ---------- app states ----------

    public enum State { ALLOWED, BLOCKED, PENDING }

    public synchronized State state(String pkg) {
        if (pkg == null || NEVER_BLOCK.contains(pkg)) return State.ALLOWED;
        if (set("pending").contains(pkg)) return State.PENDING;
        if (set("blocked").contains(pkg)) return State.BLOCKED;
        return State.ALLOWED;
    }

    public synchronized boolean isRestricted(String pkg) {
        return state(pkg) != State.ALLOWED;
    }

    /** Blocked and pending apps: Home hides them. */
    public synchronized Set<String> restricted() {
        Set<String> out = new HashSet<>(set("blocked"));
        out.addAll(set("pending"));
        return out;
    }

    public synchronized boolean block(String pkg) {
        if (pkg == null || NEVER_BLOCK.contains(pkg)) return false;
        Set<String> b = set("blocked");
        b.add(pkg);
        Set<String> p = set("pending");
        p.remove(pkg);
        prefs.edit().putStringSet("blocked", b).putStringSet("pending", p).apply();
        changed();
        return true;
    }

    /** Unblock or approve: the app becomes allowed. */
    public synchronized void allow(String pkg) {
        Set<String> b = set("blocked");
        Set<String> p = set("pending");
        b.remove(pkg);
        p.remove(pkg);
        Set<String> k = set("known");
        k.add(pkg);
        prefs.edit().putStringSet("blocked", b).putStringSet("pending", p).putStringSet("known", k).apply();
        changed();
    }

    public synchronized Set<String> pending() {
        return set("pending");
    }

    public synchronized boolean blockNewInstalls() {
        return prefs.getBoolean("block_new_installs", true);
    }

    public synchronized void setBlockNewInstalls(boolean on) {
        prefs.edit().putBoolean("block_new_installs", on).apply();
    }

    /**
     * Called for every newly installed package (not updates). Returns true when it now waits
     * for a parent's approval.
     */
    public synchronized boolean onInstalled(String pkg) {
        Set<String> k = set("known");
        if (k.contains(pkg)) return false;
        k.add(pkg);
        SharedPreferences.Editor e = prefs.edit().putStringSet("known", k);
        boolean hold = blockNewInstalls() && !NEVER_BLOCK.contains(pkg);
        if (hold) {
            Set<String> p = set("pending");
            p.add(pkg);
            e.putStringSet("pending", p);
            JSONObject when = obj("installed_at");
            try { when.put(pkg, System.currentTimeMillis()); } catch (JSONException ignored) { }
            e.putString("installed_at", when.toString());
        }
        e.apply();
        if (hold) changed();
        return hold;
    }

    public synchronized void onRemoved(String pkg) {
        Set<String> k = set("known"), b = set("blocked"), p = set("pending");
        k.remove(pkg);
        b.remove(pkg);
        p.remove(pkg);
        prefs.edit().putStringSet("known", k).putStringSet("blocked", b).putStringSet("pending", p).apply();
        changed();
    }

    /**
     * First run: every app already on the TV counts as known and allowed. Later runs: an app
     * that appeared while this service was not running (installed during a reboot, say) is
     * treated as a new install.
     */
    public synchronized void reconcile() {
        Set<String> installed = installedPackages();
        if (!prefs.getBoolean("baseline_done", false)) {
            prefs.edit().putStringSet("known", installed).putBoolean("baseline_done", true).apply();
            return;
        }
        for (String pkg : installed) onInstalled(pkg);
        Set<String> k = set("known");
        if (k.retainAll(installed)) prefs.edit().putStringSet("known", k).apply();
    }

    Set<String> installedPackages() {
        Set<String> out = new HashSet<>();
        for (ApplicationInfo ai : app.getPackageManager().getInstalledApplications(0)) out.add(ai.packageName);
        return out;
    }

    /** Apps a person can open: anything with a TV or phone launcher entry. */
    public List<String> launchablePackages() {
        PackageManager pm = app.getPackageManager();
        Set<String> out = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) out.add(ri.activityInfo.packageName);
        }
        out.remove(app.getPackageName());
        List<String> list = new ArrayList<>(out);
        Collections.sort(list);
        return list;
    }

    // ---------- parent password ----------

    public synchronized boolean passwordSet() {
        return prefs.contains("pw_hash");
    }

    public synchronized void setPassword(String password) {
        byte[] salt = GuardCrypto.random(16);
        prefs.edit()
                .putString("pw_salt", GuardCrypto.b64(salt))
                .putString("pw_hash", GuardCrypto.b64(GuardCrypto.pbkdf2(password, salt)))
                .putInt("pw_fail", 0).putLong("pw_lock_until", 0).apply();
    }

    /** Checks the password with a lock-out of 5 minutes after 5 wrong tries. */
    public synchronized PasswordResult checkPassword(String password) {
        long now = System.currentTimeMillis();
        if (now < prefs.getLong("pw_lock_until", 0)) return PasswordResult.LOCKED;
        if (!passwordSet() || password == null) return PasswordResult.WRONG;
        byte[] salt = GuardCrypto.unb64(prefs.getString("pw_salt", ""));
        byte[] want = GuardCrypto.unb64(prefs.getString("pw_hash", ""));
        if (GuardCrypto.equal(want, GuardCrypto.pbkdf2(password, salt))) {
            prefs.edit().putInt("pw_fail", 0).apply();
            return PasswordResult.OK;
        }
        int fails = prefs.getInt("pw_fail", 0) + 1;
        SharedPreferences.Editor e = prefs.edit().putInt("pw_fail", fails);
        if (fails >= 5) e.putLong("pw_lock_until", now + 5 * 60_000L).putInt("pw_fail", 0);
        e.apply();
        return fails >= 5 ? PasswordResult.LOCKED : PasswordResult.WRONG;
    }

    public enum PasswordResult { OK, WRONG, LOCKED }

    // ---------- paired phones ----------

    /**
     * A phone the person allowed on the TV, identified by its certificate's fingerprint. It starts
     * "pending": it may only finish pairing (with the parent password) over the encrypted channel.
     */
    public synchronized String addPendingDevice(String name, String certB64, String fingerprint) {
        String id = GuardCrypto.hex(GuardCrypto.random(8));
        JSONObject devices = obj("devices");
        dropStalePending(devices);
        removeFingerprint(devices, fingerprint);   // one entry per certificate, never two
        try {
            devices.put(id, new JSONObject()
                    .put("name", name == null || name.isEmpty() ? "Phone" : name)
                    .put("cert", certB64)
                    .put("fp", fingerprint)
                    .put("state", "pending")
                    .put("paired_at", System.currentTimeMillis()));
        } catch (JSONException ignored) { }
        prefs.edit().putString("devices", devices.toString()).apply();
        return id;
    }

    public synchronized void activateDevice(String id) {
        JSONObject devices = obj("devices");
        JSONObject d = devices.optJSONObject(id);
        if (d == null) return;
        try { d.put("state", "active").put("paired_at", System.currentTimeMillis()); } catch (JSONException ignored) { }
        prefs.edit().putString("devices", devices.toString()).apply();
    }

    /** The device id for a TLS client certificate fingerprint, or null when no phone with it is paired. */
    public synchronized String deviceByFingerprint(String fp) {
        if (fp == null || fp.isEmpty()) return null;
        JSONObject devices = obj("devices");
        for (java.util.Iterator<String> it = devices.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONObject d = devices.optJSONObject(id);
            if (d == null || !fp.equals(d.optString("fp"))) continue;
            if ("pending".equals(d.optString("state")) && d.optLong("paired_at") < System.currentTimeMillis() - PENDING_TTL_MS) continue;
            return id;
        }
        return null;
    }

    private static final long PENDING_TTL_MS = 10 * 60_000L;

    private static void removeFingerprint(JSONObject devices, String fp) {
        if (fp == null || fp.isEmpty()) return;
        java.util.List<String> drop = new java.util.ArrayList<>();
        for (java.util.Iterator<String> it = devices.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONObject d = devices.optJSONObject(id);
            if (d != null && fp.equals(d.optString("fp"))) drop.add(id);
        }
        for (String id : drop) devices.remove(id);
    }

    public synchronized boolean devicePending(String id) {
        JSONObject d = obj("devices").optJSONObject(id == null ? "" : id);
        return d != null && "pending".equals(d.optString("state"));
    }

    /** Phones paired with protocol 1 (shared HMAC secret, no certificate) can't use the encrypted channel: removed. */
    public synchronized int removeLegacyDevices() {
        JSONObject devices = obj("devices");
        java.util.List<String> drop = new java.util.ArrayList<>();
        for (java.util.Iterator<String> it = devices.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONObject d = devices.optJSONObject(id);
            if (d == null || d.optString("fp").isEmpty()) drop.add(id);
        }
        for (String id : drop) devices.remove(id);
        if (!drop.isEmpty()) prefs.edit().putString("devices", devices.toString()).apply();
        return drop.size();
    }

    /** A phone that was allowed but never finished pairing within 10 minutes is forgotten. */
    private static void dropStalePending(JSONObject devices) {
        long cutoff = System.currentTimeMillis() - PENDING_TTL_MS;
        java.util.List<String> drop = new java.util.ArrayList<>();
        for (java.util.Iterator<String> it = devices.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONObject d = devices.optJSONObject(id);
            if (d != null && "pending".equals(d.optString("state")) && d.optLong("paired_at") < cutoff) drop.add(id);
        }
        for (String id : drop) devices.remove(id);
    }

    public synchronized String deviceName(String id) {
        JSONObject d = obj("devices").optJSONObject(id == null ? "" : id);
        return d == null ? "" : d.optString("name");
    }

    /** Records that a paired phone just made a request, and how it reached the TV. */
    public synchronized void touchDevice(String id, String via, String ip) {
        JSONObject devices = obj("devices");
        JSONObject d = devices.optJSONObject(id);
        if (d == null) return;
        try { d.put("last_seen", System.currentTimeMillis()).put("last_via", via).put("last_ip", ip); } catch (JSONException ignored) { }
        prefs.edit().putString("devices", devices.toString()).apply();
    }

    /** Revokes a phone: its entry and any other entry with the same certificate go at once. */
    public synchronized boolean removeDevice(String id) {
        JSONObject devices = obj("devices");
        Object gone = devices.remove(id);
        if (gone == null) return false;
        if (gone instanceof JSONObject) removeFingerprint(devices, ((JSONObject) gone).optString("fp"));
        prefs.edit().putString("devices", devices.toString()).apply();
        return true;
    }

    public synchronized JSONArray devicesJson() {
        JSONArray out = new JSONArray();
        JSONObject devices = obj("devices");
        for (java.util.Iterator<String> it = devices.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONObject d = devices.optJSONObject(id);
            if (d == null || "pending".equals(d.optString("state"))) continue;
            try {
                long seen = d.optLong("last_seen");
                out.put(new JSONObject().put("id", id).put("name", d.optString("name"))
                        .put("paired_at", d.optLong("paired_at")).put("last_seen", seen)
                        .put("last_via", d.optString("last_via", "home"))
                        .put("state", d.optString("state", "active"))
                        .put("fingerprint", d.optString("fp"))
                        .put("active", System.currentTimeMillis() - seen < 120_000));
            } catch (JSONException ignored) { }
        }
        return out;
    }

    // ---------- activity log (blocked attempts, installs) ----------

    public synchronized void log(String type, String pkg) {
        log(type, pkg, "");
    }

    /** [by] names the phone (or "TV") that made the change, for the action log. */
    public synchronized void log(String type, String pkg, String by) {
        JSONArray log;
        try { log = new JSONArray(prefs.getString("log", "[]")); } catch (JSONException e) { log = new JSONArray(); }
        try {
            JSONObject e = new JSONObject().put("t", System.currentTimeMillis()).put("type", type).put("package", pkg);
            if (by != null && !by.isEmpty()) e.put("by", by);
            log.put(e);
        } catch (JSONException ignored) { }
        while (log.length() > 200) log.remove(0);
        prefs.edit().putString("log", log.toString()).apply();
    }

    public synchronized JSONArray logJson() {
        try { return new JSONArray(prefs.getString("log", "[]")); } catch (JSONException e) { return new JSONArray(); }
    }

    // ---------- internet control (off by default) ----------

    public synchronized boolean internetControl() { return prefs.getBoolean("internet_control", false); }

    public synchronized long internetSince() { return prefs.getLong("internet_since", 0); }

    public synchronized String internetBy() { return prefs.getString("internet_by", ""); }

    public synchronized void setInternetControl(boolean on, String by) {
        prefs.edit().putBoolean("internet_control", on).putLong("internet_since", on ? System.currentTimeMillis() : 0)
                .putString("internet_by", on ? by : "").apply();
    }

    // ---------- requests from kids ("Ask a parent") ----------

    public synchronized void addRequest(String profileId, String profileName, String pkg) {
        JSONArray a;
        try { a = new JSONArray(prefs.getString("requests", "[]")); } catch (JSONException e) { a = new JSONArray(); }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optString("package").equals(pkg) && o.optString("profile").equals(profileId) && "open".equals(o.optString("state"))) return;
        }
        try {
            a.put(new JSONObject().put("id", GuardCrypto.hex(GuardCrypto.random(6))).put("profile", profileId)
                    .put("profile_name", profileName).put("package", pkg).put("t", System.currentTimeMillis()).put("state", "open"));
        } catch (JSONException ignored) { }
        while (a.length() > 50) a.remove(0);
        prefs.edit().putString("requests", a.toString()).apply();
    }

    public synchronized JSONArray openRequests() {
        JSONArray a, out = new JSONArray();
        try { a = new JSONArray(prefs.getString("requests", "[]")); } catch (JSONException e) { return out; }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && "open".equals(o.optString("state"))) out.put(o);
        }
        return out;
    }

    /** Marks a request answered and returns it, or null. */
    public synchronized JSONObject answerRequest(String id, boolean allow) {
        JSONArray a;
        try { a = new JSONArray(prefs.getString("requests", "[]")); } catch (JSONException e) { return null; }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optString("id").equals(id)) {
                try { o.put("state", allow ? "allowed" : "declined"); } catch (JSONException ignored) { }
                prefs.edit().putString("requests", a.toString()).apply();
                return o;
            }
        }
        return null;
    }

    // ---------- helpers ----------

    private Set<String> set(String key) {
        return new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
    }

    private JSONObject obj(String key) {
        try { return new JSONObject(prefs.getString(key, "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    private void changed() {
        app.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(app.getPackageName()));
    }

    static Map<String, String> labels(Context ctx, List<String> pkgs) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, String> out = new LinkedHashMap<>();
        for (String p : pkgs) {
            try { out.put(p, pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()); }
            catch (PackageManager.NameNotFoundException e) { out.put(p, p); }
        }
        return out;
    }

    static SecureRandom rng() { return GuardCrypto.RNG; }
}
