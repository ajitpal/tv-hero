package com.ajit.tvhero;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import com.ajit.tvhero.protocol.Bytes;
import com.ajit.tvhero.protocol.Net;
import com.ajit.tvhero.protocol.Pairing;
import com.ajit.tvhero.protocol.Protocol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The phone app's API, protocol 2 (docs/PROTOCOL.md, docs/SECURITY.md).
 *
 * <ul>
 *   <li>Pairing port (plain HTTP, home network only): info and pairing. Nothing here controls the TV.</li>
 *   <li>Secure port (mutual TLS): every command. The caller is the paired phone whose certificate
 *       fingerprint the TLS handshake proved; there are no passwords or secrets in headers.</li>
 *   <li>From outside the home network (Tailscale) only while internet control is on, which can only
 *       be switched on with a confirmation on the TV.</li>
 *   <li>Changes that loosen protection also need the parent password.</li>
 * </ul>
 */
final class Api implements ControlServer.Handler {
    static final int API_VERSION = Protocol.API_VERSION;
    private static final long PAIR_TTL_MS = 3 * 60_000;

    private final Context ctx;
    private final GuardStore store;
    private final Profiles profiles;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Name of the phone making the current request, for the action log. One request per worker thread. */
    private final ThreadLocal<String> who = new ThreadLocal<>();

    // One pairing at a time.
    private String pairId, pairPhone, pairState = Protocol.PairState.NONE, pairCode;
    private byte[] pairPhoneCert, pairTvNonce;
    private long pairExpires;

    /** Internet control waiting for Allow on the TV. */
    private volatile boolean internetPending;

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
            boolean home = Net.classify(req.remote) == Net.Via.HOME;
            if (req.peer == null) {   // the plain pairing port
                switch (key) {
                    case "GET /v1/info": return info();
                    case "POST /v1/pair/start": return home ? pairStart(body(req)) : error(403, "pairing only works on the home network");
                    case "POST /v1/pair/reveal": return home ? pairReveal(body(req)) : error(403, "pairing only works on the home network");
                    case "GET /v1/pair/status": return pairStatus(req.param("pair_id"));
                    default: return error(403, "use the encrypted channel (port " + Protocol.SECURE_PORT + ")");
                }
            }

            String id = store.deviceByFingerprint(req.peer);
            if (id == null) return error(401, "this phone isn't paired");
            String name = store.deviceName(id);
            if (!home && !store.internetControl()) return error(403, "internet control is off: turn it on at the TV (Parent Menu)");
            store.touchDevice(id, home ? "home" : "internet", req.remote);
            if (!home && GuardService.instance != null) GuardService.instance.remoteNotice(name);
            who.set(name);
            JSONObject b = body(req);
            if (store.devicePending(id)) {
                return "POST /v1/pair/complete".equals(key) ? pairComplete(id, name, b) : error(401, "finish pairing first");
            }
            switch (key) {
                case "GET /v1/info": return info();
                case "GET /v1/status": return status();
                case "POST /v1/power": return power(b);
                case "GET /v1/volume": return ok(volumeJson());
                case "POST /v1/volume": return volume(b);
                case "GET /v1/apps": return apps();
                case "GET /v1/apps/icon": return icon(req.param("package"));
                case "POST /v1/apps/block": return block(b);
                case "POST /v1/apps/unblock":
                case "POST /v1/apps/approve": return allow(b);
                case "GET /v1/packages/state": return packagesState(req.param("packages"));
                case "GET /v1/settings": return settings();
                case "POST /v1/settings": return setSettings(b);
                case "GET /v1/home": return home();
                case "POST /v1/home": return setHome(b);
                case "POST /v1/launch": return launch(b);
                case "POST /v1/key": return key(b);
                case "POST /v1/password": return changePassword(b);
                case "GET /v1/devices": return ok(new JSONObject().put("devices", store.devicesJson()).put("you", id));
                case "POST /v1/devices/remove": return removeDevice(id, b);
                case "GET /v1/internet": return internet();
                case "POST /v1/internet": return setInternet(b, home);
                case "GET /v1/log": return ok(new JSONObject().put("log", store.logJson()));
                case "GET /v1/now": return ok(actions.now());
                case "GET /v1/profiles": return ok(profiles.json());
                case "POST /v1/profiles/save": return saveProfile(b);
                case "POST /v1/profiles/delete": return deleteProfile(b);
                case "POST /v1/profiles/activate": return activate(b);
                case "POST /v1/profiles/extra": return extra(b);
                case "POST /v1/profiles/end": return endToday(b);
                case "POST /v1/profiles/switches": return switchLimit(b);
                case "POST /v1/profiles/on_wake": return onWake(b);
                case "POST /v1/profiles/common": return setCommon(b);
                case "GET /v1/requests": return requests();
                case "POST /v1/requests/answer": return answerRequest(b);
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
        } finally {
            who.remove();
        }
    }

    private void log(String type, String detail) {
        store.log(type, detail, who.get());
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
                .put("secure_port", Protocol.SECURE_PORT)
                .put("addresses", addresses())
                .put("tailscale_ip", tailscaleIp()));
    }

    // ---------- pairing (numeric comparison, protocol.Pairing) ----------

    /** Step 1: the phone sends its certificate; the TV answers with its own and a commitment to its nonce. */
    private synchronized ControlServer.Response pairStart(JSONObject b) throws JSONException {
        long now = System.currentTimeMillis();
        if (pairId != null && now < pairExpires && !Protocol.PairState.DENIED.equals(pairState)
                && !Protocol.PairState.ALLOWED.equals(pairState)) {
            return error(409, "another phone is pairing: try again in a few minutes");
        }
        byte[] cert = Bytes.unb64(b.optString("cert"));
        if (!validPhoneCert(cert)) return error(400, "the phone's certificate is missing or not an EC P-256 certificate");
        if (store.deviceByFingerprint(Pairing.fingerprint(cert)) != null && !store.devicePending(store.deviceByFingerprint(Pairing.fingerprint(cert)))) {
            return error(409, "this phone is already paired");
        }
        TlsIdentity me;
        try { me = TlsIdentity.get(); } catch (Exception e) { return error(503, "the TV's key isn't available"); }
        byte[] tvCert;
        try { tvCert = me.cert.getEncoded(); } catch (Exception e) { return error(503, "the TV's certificate isn't available"); }
        pairId = GuardCrypto.hex(GuardCrypto.random(8));
        pairPhone = clean(b.optString("device_name", "Phone"));
        if (pairPhone.isEmpty()) pairPhone = "Phone";
        pairPhoneCert = cert;
        pairTvNonce = Pairing.nonce();
        pairCode = null;
        pairState = "started";
        pairExpires = now + PAIR_TTL_MS;
        return ok(new JSONObject()
                .put("pair_id", pairId)
                .put("tv_cert", Bytes.b64(tvCert))
                .put("commitment", Bytes.b64(Pairing.commitment(tvCert, cert, pairTvNonce)))
                .put("password_set", store.passwordSet())
                .put("expires_in", PAIR_TTL_MS / 1000));
    }

    /** Step 2: the phone reveals its nonce; the TV reveals its own and asks the person to compare codes. */
    private synchronized ControlServer.Response pairReveal(JSONObject b) throws JSONException {
        if (pairId == null || !pairId.equals(b.optString("pair_id")) || System.currentTimeMillis() > pairExpires) {
            return error(409, "no pairing in progress: start again");
        }
        if (!"started".equals(pairState)) return error(409, "already revealed: start again");
        byte[] np = Bytes.unb64(b.optString("nonce"));
        if (np == null || np.length != Pairing.NONCE_BYTES) return error(400, "bad nonce");
        byte[] tvCert;
        try { tvCert = TlsIdentity.get().cert.getEncoded(); } catch (Exception e) { return error(503, "the TV's certificate isn't available"); }
        pairCode = Pairing.code(pairPhoneCert, tvCert, np, pairTvNonce);
        pairState = Protocol.PairState.WAITING_TV;
        final String id = pairId, phone = pairPhone, code = pairCode;
        final byte[] cert = pairPhoneCert;
        GuardService g = GuardService.instance;
        if (g == null) {
            pairState = Protocol.PairState.DENIED;
            return error(503, "the TV's guard isn't running: open tv-hero on the TV");
        }
        g.confirmPairing(phone, code, allowed -> onPairAnswer(id, phone, cert, allowed));
        return ok(new JSONObject().put("nonce", Bytes.b64(pairTvNonce)));
    }

    private synchronized void onPairAnswer(String id, String phone, byte[] cert, boolean allowed) {
        if (!id.equals(pairId) || System.currentTimeMillis() > pairExpires) return;
        if (allowed) {
            store.addPendingDevice(phone, Bytes.b64(cert), Pairing.fingerprint(cert));
            pairState = Protocol.PairState.ALLOWED;
            store.log("pair_allowed", phone, "TV");
        } else {
            pairState = Protocol.PairState.DENIED;
            store.log("pair_denied", phone, "TV");
        }
    }

    private synchronized ControlServer.Response pairStatus(String id) throws JSONException {
        String state;
        if (pairId == null || !pairId.equals(id)) state = Protocol.PairState.NONE;
        else if (System.currentTimeMillis() > pairExpires && !Protocol.PairState.ALLOWED.equals(pairState)) state = Protocol.PairState.EXPIRED;
        else state = "started".equals(pairState) ? Protocol.PairState.WAITING_TV : pairState;
        return ok(new JSONObject().put("state", state));
    }

    /** Step 3, over the encrypted channel: the parent password (or the first one) makes the phone a full member. */
    private ControlServer.Response pairComplete(String id, String name, JSONObject b) throws JSONException {
        String password = b.optString("password", "");
        if (store.passwordSet()) {
            GuardStore.PasswordResult r = store.checkPassword(password);
            if (r == GuardStore.PasswordResult.LOCKED) return error(423, "too many wrong passwords: wait 5 minutes");
            if (r != GuardStore.PasswordResult.OK) return error(403, "wrong password");
        } else {
            if (password.length() < 6) return error(400, "choose a parent password of at least 6 characters");
            store.setPassword(password);
        }
        store.activateDevice(id);
        synchronized (this) { pairId = null; pairState = Protocol.PairState.NONE; }
        if (GuardService.instance != null) GuardService.instance.endPairingPrompt();
        store.log("paired", name, "TV");
        return ok(new JSONObject().put("device_id", id).put("name", name));
    }

    private static boolean validPhoneCert(byte[] der) {
        if (der == null || der.length > 4096) return false;
        try {
            X509Certificate c = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
            if (!(c.getPublicKey() instanceof ECPublicKey)) return false;
            return ((ECPublicKey) c.getPublicKey()).getParams().getCurve().getField().getFieldSize() == 256;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- Phase 1: status, power, volume ----------

    private ControlServer.Response status() throws JSONException {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return ok(new JSONObject()
                .put("power", pm != null && pm.isInteractive() ? "on" : "standby")
                .put("volume", volumeJson())
                .put("now", actions.now())
                .put("profile", profiles.activeId())
                .put("internet_control", store.internetControl())
                .put("version", BuildConfigInfo.versionName(ctx)));
    }

    /** sleep: standby now. wake: only reachable while the TV is awake enough to answer (Wi-Fi is off in deep standby). */
    private ControlServer.Response power(JSONObject b) throws JSONException {
        String action = b.optString("action", "sleep");
        switch (action) {
            case "sleep":
                log("power_sleep", "");
                actions.globalAction("sleep");
                return ok(new JSONObject().put("power", "standby"));
            case "wake": {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null && pm.isInteractive()) return ok(new JSONObject().put("power", "on"));
                actions.globalAction("wake");
                return ok(new JSONObject().put("power", "waking"));
            }
            default: return error(400, "action must be sleep or wake");
        }
    }

    private JSONObject volumeJson() throws JSONException {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cap = profiles.quietCapNow();
        return new JSONObject()
                .put("level", am.getStreamVolume(AudioManager.STREAM_MUSIC))
                .put("max", max)
                .put("muted", am.isStreamMute(AudioManager.STREAM_MUSIC))
                .put("cap", cap < 0 ? max : Math.max(1, max * cap / 100));
    }

    /** {"level": n} | {"step": +1/-1} | {"mute": true/false}. The night volume cap still applies. */
    private ControlServer.Response volume(JSONObject b) throws JSONException {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cap = profiles.quietCapNow();
        int limit = cap < 0 ? max : Math.max(1, max * cap / 100);
        if (b.has("mute")) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, b.getBoolean("mute") ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        }
        int level = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (b.has("level")) level = b.getInt("level");
        else if (b.has("step")) level += Math.max(-5, Math.min(5, b.getInt("step")));
        if (b.has("level") || b.has("step")) {
            level = Math.max(0, Math.min(limit, level));
            am.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI);
        }
        return ok(volumeJson());
    }

    // ---------- internet control ----------

    private ControlServer.Response internet() throws JSONException {
        return ok(new JSONObject()
                .put("enabled", store.internetControl())
                .put("since", store.internetSince())
                .put("by", store.internetBy())
                .put("pending", internetPending)
                .put("tailscale_ip", tailscaleIp()));
    }

    /**
     * Off: immediately, from any paired phone, anywhere. On: needs the parent password, a phone on the
     * home network, and Allow on the TV, so it can't be switched on by accident or from afar.
     */
    private ControlServer.Response setInternet(JSONObject b, boolean home) throws JSONException {
        if (!b.optBoolean("enabled", false)) {
            store.setInternetControl(false, who.get());
            log("internet_off", "");
            return internet();
        }
        if (store.internetControl()) return internet();
        if (!home) return error(403, "internet control can only be turned on from the home network");
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        GuardService g = GuardService.instance;
        if (g == null) return error(503, "the TV's guard isn't running");
        final String by = who.get();
        internetPending = true;
        g.confirmInternet(by, allowed -> {
            internetPending = false;
            if (allowed) {
                store.setInternetControl(true, by);
                store.log("internet_on", "", by + " (allowed on the TV)");
            } else {
                store.log("internet_denied", "", "TV");
            }
        });
        ControlServer.Response r = internet();
        return new ControlServer.Response(202, r.type, r.body);
    }

    // ---------- kids asking for an app ----------

    private ControlServer.Response requests() throws JSONException {
        JSONArray open = store.openRequests();
        java.util.List<String> pkgs = new java.util.ArrayList<>();
        for (int i = 0; i < open.length(); i++) pkgs.add(open.getJSONObject(i).optString("package"));
        Map<String, String> labels = GuardStore.labels(ctx, pkgs);
        for (int i = 0; i < open.length(); i++) {
            JSONObject o = open.getJSONObject(i);
            String what = o.optString("package");
            if (what.startsWith("time:")) o.put("kind", "time").put("label", what.substring(5) + " more minutes today");
            else if (what.startsWith("bedtime:")) o.put("kind", "bedtime").put("label", "Stay up " + what.substring(8) + " minutes later");
            else if (what.startsWith("switch:")) {
                Profiles.Kid to = profiles.kid(what.substring(7));
                o.put("kind", "switch").put("label", "Switch to " + (to == null ? "another profile" : to.name));
            }
            else o.put("kind", "app").put("label", labels.get(what));
        }
        return ok(new JSONObject().put("requests", open));
    }

    /** Allowing adds the app to that kid's list (needs the password); declining needs nothing. */
    private ControlServer.Response answerRequest(JSONObject b) throws JSONException {
        boolean allow = b.optBoolean("allow", false);
        if (allow) {
            ControlServer.Response denied = needPassword(b);
            if (denied != null) return denied;
        }
        JSONObject r = store.answerRequest(b.optString("id"), allow);
        if (r == null) return error(404, "no such request");
        if (allow) {
            Profiles.Kid k = profiles.kid(r.optString("profile"));
            String what = r.optString("package");
            if (k != null && what.startsWith("time:")) {
                profiles.addExtra(k.id, Math.max(5, Math.min(240, parseMinutes(what.substring(5)))));
            } else if (what.startsWith("switch:")) {
                Profiles.Kid to = profiles.kid(what.substring(7));
                if (to != null) {
                    profiles.setActive(to.id);
                    actions.globalAction("picker_done");
                    log("profile", to.name);
                }
            } else if (k != null && what.startsWith("bedtime:")) {
                profiles.snoozeBedtime(Math.max(5, Math.min(180, parseMinutes(what.substring(8)))));
            } else if (k != null) {
                k.allowed.add(what);
                profiles.save(k.json());
            }
        }
        log(allow ? "request_allowed" : "request_declined", r.optString("package"));
        actions.refresh();
        return requests();
    }

    // ---------- Privacy & Debloat dashboard (read-only; changes go through ADB from the phone) ----------

    private ControlServer.Response packagesState(String list) throws JSONException {
        if (list == null || list.isEmpty()) return error(400, "packages missing");
        PackageManager pm = ctx.getPackageManager();
        JSONArray out = new JSONArray();
        String[] pkgs = list.split(",");
        for (int i = 0; i < Math.min(pkgs.length, 200); i++) {
            String p = pkgs[i].trim();
            if (p.isEmpty()) continue;
            JSONObject o = new JSONObject().put("package", p);
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, PackageManager.MATCH_DISABLED_COMPONENTS);
                o.put("installed", true).put("enabled", ai.enabled).put("label", String.valueOf(pm.getApplicationLabel(ai)));
            } catch (PackageManager.NameNotFoundException e) {
                o.put("installed", false).put("enabled", false);
            }
            out.put(o);
        }
        return ok(new JSONObject().put("packages", out));
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
        log("blocked", pkg);
        actions.refresh();
        return ok(new JSONObject().put("package", pkg).put("state", "blocked"));
    }

    private ControlServer.Response allow(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        String pkg = b.optString("package");
        store.allow(pkg);
        log("allowed", pkg);
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
        if (np.length() < 6) return error(400, "the password must have at least 6 characters");
        store.setPassword(np);
        return ok(new JSONObject().put("changed", true));
    }

    /** A phone may always remove itself; removing another phone (a lost one) needs the parent password. */
    private ControlServer.Response removeDevice(String self, JSONObject b) throws JSONException {
        String target = b.optString("device_id", self);
        if (!target.equals(self)) {
            ControlServer.Response denied = needPassword(b);
            if (denied != null) return denied;
        }
        String name = store.deviceName(target);
        boolean removed = store.removeDevice(target);
        if (removed) log("device_removed", name);
        return ok(new JSONObject().put("removed", removed).put("devices", store.devicesJson()));
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
        if (kid != null && (!kid.all.contains(pkg) || Profiles.KID_NEVER.contains(pkg)))
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
        log("profile_saved", k.name);
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
        if (Profiles.GUEST.equals(id)) {
            profiles.setActive(Profiles.GUEST);
            actions.globalAction("picker_done");
            log("profile", "Guest");
            actions.refresh();
            return ok(profiles.json());
        }
        if (Profiles.PARENT.equals(id)) {
            ControlServer.Response denied = needPassword(b);
            if (denied != null) return denied;
        } else if (profiles.kid(id) == null) {
            return error(404, "no such profile");
        }
        profiles.setActive(id);
        actions.globalAction("picker_done");
        log("profile", Profiles.PARENT.equals(id) ? "Parent" : profiles.kid(id).name);
        actions.refresh();
        return ok(profiles.json());
    }

    /** Adds minutes for today (password), or takes them away (no password: it only tightens). */
    private ControlServer.Response extra(JSONObject b) throws JSONException {
        if (b.optInt("minutes", 15) > 0) {
            ControlServer.Response denied = needPassword(b);
            if (denied != null) return denied;
        }
        Profiles.Kid k = profiles.kid(b.optString("id"));
        if (k == null) return error(404, "no such profile");
        int minutes = Math.max(-240, Math.min(240, b.optInt("minutes", 15)));
        profiles.addExtra(k.id, minutes);
        log("extra_time", k.name + " " + minutes);
        actions.refresh();
        return ok(profiles.json());
    }

    /** Ends a profile's TV time for today now: Time's up at once if it's watching. No password (tightens). */
    private ControlServer.Response endToday(JSONObject b) throws JSONException {
        Profiles.Kid k = profiles.kid(b.optString("id"));
        if (k == null) return error(404, "no such profile");
        profiles.endToday(k);
        log("time_ended", k.name);
        actions.refresh();
        return ok(profiles.json());
    }

    /** Free profile switches a day on the TV (-1 = no limit). Fewer needs nothing; more needs the password. */
    private ControlServer.Response switchLimit(JSONObject b) throws JSONException {
        int now = profiles.switchLimit(), want = Math.max(-1, Math.min(10, b.optInt("limit", now)));
        boolean loosens = want < 0 ? now >= 0 : now >= 0 && want > now;
        if (loosens) {
            ControlServer.Response denied = needPassword(b);
            if (denied != null) return denied;
        }
        profiles.setSwitchLimit(want);
        log("switch_limit", String.valueOf(want));
        return ok(profiles.json());
    }

    /** Apps for everyone: on in every profile on top of its own apps. Needs the password (it can loosen rules). */
    private ControlServer.Response setCommon(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        JSONArray a = b.optJSONArray("apps");
        if (a == null) return error(400, "apps missing");
        java.util.Set<String> apps = new java.util.HashSet<>();
        for (int i = 0; i < a.length() && i < 64; i++) {
            String p = a.optString(i, "").trim();
            if (p.matches("[A-Za-z0-9_.]+")) apps.add(p);
        }
        profiles.setCommonApps(apps);
        log("common_apps", apps.size() + " apps");
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
        log(on ? "locked" : "unlocked", "");
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
        log(b.optBoolean("enabled", false) ? "adb_on" : "adb_off", "");
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

    private static int parseMinutes(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 30; }
    }

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
