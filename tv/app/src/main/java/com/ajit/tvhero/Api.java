package com.ajit.tvhero;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The phone app's API (docs/PROTOCOL.md). Open: GET /v1/info, POST /v1/pair/start, POST /v1/pair/finish.
 * Everything else must carry X-Device-Id, X-Timestamp and X-Signature (HMAC-SHA256 with the
 * secret handed over at pairing). Changes that loosen protection also need the parent password.
 */
final class Api implements ControlServer.Handler {
    static final int API_VERSION = 1;
    private static final long SKEW_MS = 120_000;

    private final Context ctx;
    private final GuardStore store;
    private final Profiles profiles;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> seenSignatures = new ArrayDeque<>();
    private final Set<String> seenSet = new HashSet<>();

    // Pairing: one code at a time, valid 2 minutes, 5 tries.
    private String pairCode;
    private long pairExpires;
    private int pairTries;
    private byte[] pairSalt;

    interface Actions {
        boolean globalAction(String action);
        /** Re-check every rule now (a profile, lock or block changed). */
        void refresh();
        /** What's on the TV right now. */
        JSONObject now();
    }

    private final Actions actions;

    Api(Context ctx, Actions actions) {
        this.ctx = ctx.getApplicationContext();
        this.store = GuardStore.get(ctx);
        this.profiles = Profiles.get(ctx);
        this.actions = actions;
    }

    @Override
    public ControlServer.Response handle(ControlServer.Request req) {
        try {
            String key = req.method + " " + req.path;
            switch (key) {
                case "GET /v1/info": return info();
                case "POST /v1/pair/start": return pairStart(body(req));
                case "POST /v1/pair/finish": return pairFinish(body(req));
                default: break;
            }
            String authError = verify(req);
            if (authError != null) return error(401, authError);
            JSONObject b = body(req);
            switch (key) {
                case "GET /v1/apps": return apps();
                case "GET /v1/apps/icon": return icon(req.param("package"));
                case "POST /v1/apps/block": return block(b);
                case "POST /v1/apps/unblock":
                case "POST /v1/apps/approve": return allow(b);
                case "GET /v1/settings": return settings();
                case "POST /v1/settings": return setSettings(b);
                case "GET /v1/home": return home();
                case "POST /v1/home": return setHome(b);
                case "POST /v1/launch": return launch(b);
                case "POST /v1/key": return key(b);
                case "POST /v1/password": return changePassword(b);
                case "GET /v1/devices": return ok(new JSONObject().put("devices", store.devicesJson()));
                case "POST /v1/devices/remove": return removeDevice(b);
                case "GET /v1/log": return ok(new JSONObject().put("log", store.logJson()));
                case "GET /v1/now": return ok(actions.now());
                case "GET /v1/profiles": return ok(profiles.json());
                case "POST /v1/profiles/save": return saveProfile(b);
                case "POST /v1/profiles/delete": return deleteProfile(b);
                case "POST /v1/profiles/activate": return activate(b);
                case "POST /v1/profiles/extra": return extra(b);
                case "POST /v1/profiles/on_wake": return onWake(b);
                case "POST /v1/pin": return setPin(b);
                case "POST /v1/lock": return lock(b);
                case "POST /v1/sleep": return sleepTimer(b);
                case "GET /v1/quiet": return ok(profiles.quiet());
                case "POST /v1/quiet": profiles.setQuiet(b); actions.refresh(); return ok(profiles.quiet());
                case "GET /v1/activity": return activity(req.param("since"));
                case "GET /v1/remote": return remote();
                case "POST /v1/adb": return adb(b);
                default: return error(404, "no such endpoint");
            }
        } catch (JSONException e) {
            return error(400, "bad json");
        }
    }

    // ---------- open endpoints ----------

    private ControlServer.Response info() throws JSONException {
        return ok(new JSONObject()
                .put("app", "tv-hero")
                .put("api", API_VERSION)
                .put("version", BuildConfigInfo.versionName(ctx))
                .put("model", Build.MODEL)
                .put("android", Build.VERSION.RELEASE)
                .put("password_set", store.passwordSet())
                .put("addresses", addresses())
                .put("tailscale_ip", tailscaleIp()));
    }

    private synchronized ControlServer.Response pairStart(JSONObject b) throws JSONException {
        String name = clean(b.optString("device_name", "Phone"));
        pairCode = String.format(Locale.ROOT, "%06d", GuardCrypto.RNG.nextInt(1_000_000));
        pairExpires = System.currentTimeMillis() + 120_000;
        pairTries = 0;
        pairSalt = GuardCrypto.random(16);
        final String code = pairCode;
        main.post(() -> PairingActivity.show(ctx, code, name));
        return ok(new JSONObject()
                .put("salt", GuardCrypto.b64(pairSalt))
                .put("password_set", store.passwordSet())
                .put("expires_in", 120));
    }

    private synchronized ControlServer.Response pairFinish(JSONObject b) throws JSONException {
        if (pairCode == null || System.currentTimeMillis() > pairExpires) return error(409, "no pairing in progress: start again");
        String code = b.optString("code", "");
        String password = b.optString("password", "");
        String name = clean(b.optString("device_name", "Phone"));
        if (!pairCode.equals(code)) {
            if (++pairTries >= 5) endPairing();
            return error(403, "wrong code");
        }
        if (store.passwordSet()) {
            GuardStore.PasswordResult r = store.checkPassword(password);
            if (r == GuardStore.PasswordResult.LOCKED) return error(423, "too many wrong passwords: wait 5 minutes");
            if (r != GuardStore.PasswordResult.OK) return error(403, "wrong password");
        } else {
            if (password.length() < 4) return error(400, "password must have at least 4 characters");
            store.setPassword(password);
        }
        byte[] secret = GuardCrypto.random(32);
        String id = store.addDevice(name, secret);
        byte[] key = GuardCrypto.pbkdf2(code + ":" + password, pairSalt);
        String box = GuardCrypto.b64(GuardCrypto.seal(key, secret));
        endPairing();
        store.log("paired", name);
        return ok(new JSONObject().put("device_id", id).put("box", box));
    }

    private void endPairing() {
        pairCode = null;
        main.post(() -> PairingActivity.dismiss(ctx));
    }

    // ---------- signature check ----------

    /** Returns null when the request is signed by a paired phone, else the reason. */
    private synchronized String verify(ControlServer.Request req) {
        String id = req.header("X-Device-Id");
        String ts = req.header("X-Timestamp");
        String sig = req.header("X-Signature");
        if (id == null || ts == null || sig == null) return "not signed: pair this phone first";
        byte[] secret = store.deviceSecret(id);
        if (secret == null) return "unknown device: pair again";
        long t;
        try { t = Long.parseLong(ts); } catch (NumberFormatException e) { return "bad timestamp"; }
        if (Math.abs(System.currentTimeMillis() - t) > SKEW_MS) return "clock difference too large: check the time on the phone and TV";
        String target = req.path + (req.query.isEmpty() ? "" : "?" + req.query);
        String msg = req.method + "\n" + target + "\n" + ts + "\n" + GuardCrypto.sha256Hex(req.body);
        String want = GuardCrypto.b64(GuardCrypto.hmac(secret, msg));
        if (!GuardCrypto.equal(want.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) return "bad signature";
        if (!seenSet.add(sig)) return "replayed request";
        seenSignatures.addLast(sig);
        while (seenSignatures.size() > 500) seenSet.remove(seenSignatures.removeFirst());
        return null;
    }

    // ---------- apps ----------

    private ControlServer.Response apps() throws JSONException {
        PackageManager pm = ctx.getPackageManager();
        List<String> pkgs = store.launchablePackages();
        Set<String> pending = store.pending();
        for (String p : pending) if (!pkgs.contains(p)) pkgs.add(p);
        Map<String, String> labels = GuardStore.labels(ctx, pkgs);
        JSONArray arr = new JSONArray();
        for (String p : pkgs) {
            boolean system = false;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            arr.put(new JSONObject()
                    .put("package", p)
                    .put("label", labels.get(p))
                    .put("state", store.state(p).name().toLowerCase(Locale.ROOT))
                    .put("system", system)
                    .put("protected", GuardStore.NEVER_BLOCK.contains(p)));
        }
        return ok(new JSONObject().put("apps", arr).put("pending_count", pending.size())
                .put("block_new_installs", store.blockNewInstalls()));
    }

    private ControlServer.Response icon(String pkg) {
        if (pkg == null) return error(400, "package missing");
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            int size = 96;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            return new ControlServer.Response(200, "image/png", out.toByteArray());
        } catch (PackageManager.NameNotFoundException e) {
            return error(404, "no such app");
        }
    }

    private ControlServer.Response block(JSONObject b) throws JSONException {
        String pkg = b.optString("package");
        if (!store.block(pkg)) return error(400, "this app can't be blocked");
        store.log("blocked", pkg);
        actions.refresh();
        return ok(new JSONObject().put("package", pkg).put("state", "blocked"));
    }

    private ControlServer.Response allow(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        String pkg = b.optString("package");
        store.allow(pkg);
        store.log("allowed", pkg);
        actions.refresh();
        return ok(new JSONObject().put("package", pkg).put("state", "allowed"));
    }

    // ---------- settings ----------

    private ControlServer.Response settings() throws JSONException {
        return ok(new JSONObject().put("block_new_installs", store.blockNewInstalls()));
    }

    private ControlServer.Response setSettings(JSONObject b) throws JSONException {
        if (b.has("block_new_installs")) {
            boolean on = b.getBoolean("block_new_installs");
            if (!on) {   // turning protection off needs the password
                ControlServer.Response denied = needPassword(b);
                if (denied != null) return denied;
            }
            store.setBlockNewInstalls(on);
        }
        return settings();
    }

    private ControlServer.Response changePassword(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        String np = b.optString("new_password", "");
        if (np.length() < 4) return error(400, "password must have at least 4 characters");
        store.setPassword(np);
        return ok(new JSONObject().put("changed", true));
    }

    private ControlServer.Response removeDevice(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        return ok(new JSONObject().put("removed", store.removeDevice(b.optString("device_id"))));
    }

    private ControlServer.Response needPassword(JSONObject b) {
        GuardStore.PasswordResult r = store.checkPassword(b.optString("password", null));
        if (r == GuardStore.PasswordResult.OK) return null;
        if (r == GuardStore.PasswordResult.LOCKED) return error(423, "too many wrong passwords: wait 5 minutes");
        return error(403, "wrong password");
    }

    // ---------- home screen ----------

    private ControlServer.Response home() throws JSONException {
        Resources res = ctx.getResources();
        JSONObject overlay = readOverlay();
        JSONObject out = new JSONObject();
        out.put("dock_apps", overlay.has("dock_apps") ? overlay.getJSONArray("dock_apps")
                : new JSONArray(Arrays.asList(res.getStringArray(R.array.dock_apps))));
        out.put("hidden_apps", overlay.has("hidden_apps") ? overlay.getJSONArray("hidden_apps")
                : new JSONArray(Arrays.asList(res.getStringArray(R.array.hidden_apps))));
        out.put("columns", overlay.optInt("columns", res.getInteger(R.integer.columns)));
        out.put("greeting_name", overlay.optString("greeting_name", res.getString(R.string.greeting_name)));
        out.put("show_greeting", overlay.optBoolean("show_greeting", res.getBoolean(R.bool.show_greeting)));
        return ok(out);
    }

    private ControlServer.Response setHome(JSONObject b) throws JSONException {
        JSONObject overlay = readOverlay();
        for (String k : new String[]{"dock_apps", "hidden_apps"}) {
            if (!b.has(k)) continue;
            JSONArray in = b.getJSONArray(k), clean = new JSONArray();
            for (int i = 0; i < in.length() && i < 64; i++) {
                String p = in.optString(i, "").trim();
                if (p.matches("[A-Za-z0-9_.]+")) clean.put(p);
            }
            overlay.put(k, clean);
        }
        if (b.has("columns")) overlay.put("columns", Math.max(4, Math.min(7, b.getInt("columns"))));
        if (b.has("greeting_name")) overlay.put("greeting_name", clean(b.getString("greeting_name")));
        if (b.has("show_greeting")) overlay.put("show_greeting", b.getBoolean("show_greeting"));
        File dir = Config.dir(ctx);
        if (dir == null) return error(500, "storage unavailable");
        try (FileOutputStream out = new FileOutputStream(new File(dir, Config.FILE_NAME))) {
            out.write(overlay.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return error(500, "could not save: " + e.getMessage());
        }
        ctx.sendBroadcast(new Intent(GuardStore.ACTION_CHANGED).setPackage(ctx.getPackageName()));
        return home();
    }

    private JSONObject readOverlay() {
        File dir = Config.dir(ctx);
        File f = dir == null ? null : new File(dir, Config.FILE_NAME);
        if (f == null || !f.isFile() || f.length() > 64 * 1024) return new JSONObject();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return new JSONObject();
        }
    }

    // ---------- quick controls ----------

    private ControlServer.Response launch(JSONObject b) throws JSONException {
        String pkg = b.optString("package");
        if (store.isRestricted(pkg)) return error(403, "this app is blocked");
        Profiles.Kid kid = profiles.activeKid();
        if (kid != null && (!kid.allowed.contains(pkg) || Profiles.KID_NEVER.contains(pkg)))
            return error(403, "this app isn't on " + kid.name + "'s list");
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return error(404, "no such app");
        final Intent start = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        main.post(() -> ctx.startActivity(start));
        return ok(new JSONObject().put("launched", pkg));
    }

    private ControlServer.Response key(JSONObject b) throws JSONException {
        String action = b.optString("action");
        if (!actions.globalAction(action)) return error(400, "unknown or unavailable action");
        return ok(new JSONObject().put("done", action));
    }

    // ---------- profiles ----------

    private ControlServer.Response saveProfile(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        JSONObject p = b.optJSONObject("profile");
        if (p == null) return error(400, "profile missing");
        Profiles.Kid k = profiles.save(p);
        if (k == null) return error(409, "up to " + Profiles.MAX_KIDS + " kid profiles");
        store.log("profile_saved", k.name);
        actions.refresh();
        return ok(new JSONObject().put("profile", k.json()).put("profiles", profiles.json()));
    }

    private ControlServer.Response deleteProfile(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        boolean removed = profiles.delete(b.optString("id"));
        actions.refresh();
        return ok(new JSONObject().put("removed", removed).put("profiles", profiles.json()));
    }

    /** Switching to a kid needs nothing; switching to Parent needs the password. */
    private ControlServer.Response activate(JSONObject b) throws JSONException {
        String id = b.optString("id", Profiles.PARENT);
        if (Profiles.PARENT.equals(id)) {
            ControlServer.Response denied = needPassword(b);
            if (denied != null) return denied;
        } else if (profiles.kid(id) == null) {
            return error(404, "no such profile");
        }
        profiles.setActive(id);
        actions.globalAction("picker_done");
        store.log("profile", Profiles.PARENT.equals(id) ? "Parent" : profiles.kid(id).name);
        actions.refresh();
        return ok(profiles.json());
    }

    private ControlServer.Response extra(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        Profiles.Kid k = profiles.kid(b.optString("id"));
        if (k == null) return error(404, "no such profile");
        int minutes = Math.max(-240, Math.min(240, b.optInt("minutes", 15)));
        profiles.addExtra(k.id, minutes);
        store.log("extra_time", k.name + " " + minutes);
        actions.refresh();
        return ok(profiles.json());
    }

    private ControlServer.Response onWake(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        profiles.setOnWake(b.optString("on_wake", "ask"));
        return ok(profiles.json());
    }

    private ControlServer.Response setPin(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        String pin = b.optString("pin", "");
        if (pin.isEmpty()) { profiles.clearPin(); return ok(profiles.json()); }
        if (!pin.matches("[0-9]{4,6}")) return error(400, "the PIN must be 4 to 6 digits");
        profiles.setPin(pin);
        return ok(profiles.json());
    }

    /** Pausing needs nothing (any paired phone may pause); resuming needs the password. */
    private ControlServer.Response lock(JSONObject b) throws JSONException {
        boolean on = b.optBoolean("locked", true);
        if (!on) {
            ControlServer.Response denied = needPassword(b);
            if (denied != null) return denied;
        }
        profiles.setLocked(on);
        store.log(on ? "locked" : "unlocked", "phone");
        actions.refresh();
        return ok(profiles.json());
    }

    private ControlServer.Response sleepTimer(JSONObject b) throws JSONException {
        int minutes = b.optInt("minutes", 0);
        if (minutes < 0 || minutes > 600) return error(400, "0 to 600 minutes");
        profiles.setSleepAt(minutes == 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L);
        if (minutes == 0 && b.optBoolean("now", false)) actions.globalAction("sleep");
        return ok(new JSONObject().put("sleep_at", profiles.sleepAt()));
    }

    private ControlServer.Response activity(String since) throws JSONException {
        long s = 0;
        try { if (since != null) s = Long.parseLong(since); } catch (NumberFormatException ignored) { }
        if (s == 0) s = System.currentTimeMillis() - 24L * 3600_000;
        org.json.JSONArray sessions = profiles.sessions(s);
        java.util.Set<String> pkgs = new java.util.HashSet<>();
        for (int i = 0; i < sessions.length(); i++) pkgs.add(sessions.getJSONObject(i).optString("package"));
        JSONObject labels = new JSONObject();
        for (Map.Entry<String, String> e : GuardStore.labels(ctx, new java.util.ArrayList<>(pkgs)).entrySet()) labels.put(e.getKey(), e.getValue());
        return ok(new JSONObject().put("sessions", sessions).put("labels", labels)
                .put("usage_today", profiles.usageMinutesJson()));
    }

    // ---------- remote access ----------

    private ControlServer.Response remote() throws JSONException {
        String adbPort = "";
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            adbPort = (String) sp.getMethod("get", String.class, String.class).invoke(null, "service.adb.tcp.port", "");
        } catch (Exception ignored) { }
        boolean tailscale;
        try { ctx.getPackageManager().getPackageInfo("com.tailscale.ipn", 0); tailscale = true; }
        catch (PackageManager.NameNotFoundException e) { tailscale = false; }
        return ok(new JSONObject()
                .put("adb_enabled", android.provider.Settings.Global.getInt(ctx.getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, 0) == 1)
                .put("adb_port", adbPort)
                .put("addresses", addresses())
                .put("tailscale_installed", tailscale)
                .put("tailscale_ip", tailscaleIp()));
    }

    /** Network debugging on or off; needs the password, and WRITE_SECURE_SETTINGS (granted at install). */
    private ControlServer.Response adb(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        try {
            android.provider.Settings.Global.putInt(ctx.getContentResolver(), android.provider.Settings.Global.ADB_ENABLED,
                    b.optBoolean("enabled", false) ? 1 : 0);
        } catch (SecurityException e) {
            return error(403, "the TV app can't change debugging (permission not granted)");
        }
        store.log(b.optBoolean("enabled", false) ? "adb_on" : "adb_off", "phone");
        return remote();
    }

    static org.json.JSONArray addresses() {
        org.json.JSONArray out = new org.json.JSONArray();
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (a instanceof java.net.Inet4Address) out.put(a.getHostAddress());
                }
            }
        } catch (java.net.SocketException ignored) { }
        return out;
    }

    /** The TV's Tailscale address (100.64.0.0/10), or "" when Tailscale isn't connected. */
    static String tailscaleIp() {
        org.json.JSONArray a = addresses();
        for (int i = 0; i < a.length(); i++) {
            String ip = a.optString(i);
            String[] p = ip.split("\\.");
            if (p.length == 4 && p[0].equals("100")) {
                int second = Integer.parseInt(p[1]);
                if (second >= 64 && second <= 127) return ip;
            }
        }
        return "";
    }

    // ---------- helpers ----------

    private static JSONObject body(ControlServer.Request req) throws JSONException {
        String s = req.bodyText().trim();
        return s.isEmpty() ? new JSONObject() : new JSONObject(s);
    }

    private static String clean(String s) {
        s = s == null ? "" : s.replaceAll("[\\p{Cntrl}]", "").trim();
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static ControlServer.Response ok(JSONObject o) {
        return ControlServer.Response.json(200, o.toString());
    }

    private static ControlServer.Response error(int status, String message) {
        try {
            return ControlServer.Response.json(status, new JSONObject().put("error", message).toString());
        } catch (JSONException e) {
            return ControlServer.Response.json(status, "{}");
        }
    }

    /** Version name without depending on the generated BuildConfig (disabled by AGP 8 by default). */
    static final class BuildConfigInfo {
        static String versionName(Context ctx) {
            try { return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName; }
            catch (PackageManager.NameNotFoundException e) { return "?"; }
        }
    }
}
