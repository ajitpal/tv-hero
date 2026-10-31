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
            if (!home && PHONE_TO_TV.contains(key)) return error(403, "links, search, typing and photos work only on the home Wi-Fi");
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
                case "POST /v1/password/check": {   // 2.11: the phone checks it before saving it for the fingerprint
                    ControlServer.Response denied = needPassword(b);
                    return denied != null ? denied : ok(new JSONObject().put("ok", true));
                }
                case "GET /v1/devices": return ok(new JSONObject().put("devices", store.devicesJson()).put("you", id));
                case "POST /v1/devices/remove": return removeDevice(id, b);
                case "GET /v1/internet": return internet();
                case "POST /v1/internet": return setInternet(b, home);
                case "GET /v1/log": return ok(new JSONObject().put("log", store.logJson()));
                case "GET /v1/tools": return tools(null, null);
                case "POST /v1/tools/start": return tools(b, "start");
                case "POST /v1/tools/control": return tools(b, "control");
                case "GET /v1/now": return ok(actions.now());
                case "GET /v1/profiles": return ok(profiles.json());
                case "POST /v1/profiles/save": return saveProfile(b);
                case "POST /v1/profiles/delete": return deleteProfile(b);
                case "POST /v1/profiles/activate": return activate(b);
                case "POST /v1/profiles/extra": return extra(b);
                case "POST /v1/profiles/end": return endToday(b);
                case "POST /v1/profiles/quiz_reset": return quizReset(b);
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
                case "GET /v1/onscreen": return ok(OnScreen.get(ctx).json());
                case "POST /v1/onscreen": return setOnScreen(b);
                case "GET /v1/remote": return remote();
                case "POST /v1/adb": return adb(b);
                case "GET /v1/freetv": return ok(freeTvJson());
                case "POST /v1/freetv": return setFreeTv(b);
                case "POST /v1/freetv/ticks": return setFreeTvTicks(b);
                case "GET /v1/festivals": return ok(festivalsJson());
                case "POST /v1/festivals": {
                    if (b.has("on")) {
                        Festivals.setEnabled(ctx, b.optBoolean("on"));
                        log("festivals", b.optBoolean("on") ? "Festival line on" : "Festival line off");
                        sendChanged();
                    }
                    return ok(festivalsJson());
                }
                case "GET /v1/weather": return ok(WeatherStore.get(ctx).json());
                case "POST /v1/weather": return setWeather(b);
                case "GET /v1/links": return links();
                case "POST /v1/links": return setLinks(b);
                case "POST /v1/open": return openLink(b);
                case "POST /v1/search": return search(b);
                case "POST /v1/type/start": return typeStart(name);
                case "POST /v1/type": return type(b);
                case "POST /v1/type/stop": PhoneTyping.stop(ctx); return ok(new JSONObject().put("typing", false));
                case "POST /v1/photos/show": return photo(b);
                case "POST /v1/photos/stop": PhotoActivity.stop(); return ok(new JSONObject().put("showing", false));
                default: return error(404, "no such endpoint");
            }
        } catch (JSONException e) {
            return error(400, "bad json");
        } finally {
            who.remove();
        }
    }

    /** Phone to TV (2.10): only from the home Wi-Fi, never over Remote Access. */
    static final Set<String> PHONE_TO_TV = Set.of("GET /v1/links", "POST /v1/links", "POST /v1/open", "POST /v1/search",
            "POST /v1/type/start", "POST /v1/type", "POST /v1/type/stop", "POST /v1/photos/show", "POST /v1/photos/stop");

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

    // ---------- Free TV (2.11) ----------

    private JSONObject freeTvJson() throws JSONException {
        FreeTv f = FreeTv.get(ctx);
        JSONArray items = new JSONArray();
        for (FreeTv.Item i : f.items()) items.put(i.json());
        JSONArray players = new JSONArray();
        for (String p : f.players()) players.put(new JSONObject().put("package", p).put("label", appName(p)));
        String player = f.player();
        return new JSONObject().put("items", items).put("ticks", f.ticksJson()).put("players", players)
                .put("player", player == null ? "" : player).put("max", FreeTv.MAX_ITEMS);
    }

    /** {items: [...]} replaces the list (children still see only ticked items); {player} picks the video player. */
    private ControlServer.Response setFreeTv(JSONObject b) throws JSONException {
        FreeTv f = FreeTv.get(ctx);
        if (b.has("items")) {
            JSONArray a = b.optJSONArray("items");
            if (a == null || a.length() > FreeTv.MAX_ITEMS) return error(400, "at most " + FreeTv.MAX_ITEMS + " items");
            java.util.List<FreeTv.Item> list = new java.util.ArrayList<>();
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < a.length(); i++) {
                FreeTv.Item it = FreeTv.check(a.optJSONObject(i));
                if (it == null) return error(400, "item " + (i + 1) + " isn't a valid app, YouTube channel or web stream");
                if (ids.add(it.id)) list.add(it);
            }
            f.setItems(list);
            log("freetv_list", list.size() + " items");
        }
        if (b.has("player")) {
            if (!f.setPlayer(b.optString("player"))) return error(400, "that app can't play video streams");
            log("freetv_list", "Channels play in " + appName(b.optString("player")));
        }
        sendChanged();
        return ok(freeTvJson());
    }

    /** {profile, ids, password}: which items a child (or Guest) sees. Adding any needs the password. */
    private ControlServer.Response setFreeTvTicks(JSONObject b) throws JSONException {
        String prof = b.optString("profile");
        if (!Profiles.GUEST.equals(prof) && profiles.kid(prof) == null) return error(404, "no such child profile");
        FreeTv f = FreeTv.get(ctx);
        java.util.Set<String> known = new java.util.HashSet<>();
        for (FreeTv.Item i : f.items()) known.add(i.id);
        java.util.Set<String> want = new java.util.LinkedHashSet<>();
        JSONArray a = b.optJSONArray("ids");
        for (int i = 0; a != null && i < a.length(); i++) if (known.contains(a.optString(i))) want.add(a.optString(i));
        if (FreeTv.adds(f.ticks(prof), want)) {
            ControlServer.Response denied = needPassword(b);
            if (denied != null) return denied;
        }
        f.setTicks(prof, want);
        log("freetv_ticks", profileName(prof) + ": " + want.size() + " Free TV items");
        sendChanged();
        return ok(freeTvJson());
    }

    // ---------- festivals (2.11) ----------

    private JSONObject festivalsJson() throws JSONException {
        java.time.LocalDate today = java.time.LocalDate.now();
        return new JSONObject().put("on", Festivals.enabled(ctx)).put("today", Festivals.line(today))
                .put("covered_until", Festivals.coveredUntil().toString()).put("source", "Government of India gazetted holidays (Delhi)");
    }

    /** Home redraws with the new setting. */
    private void sendChanged() {
        ctx.sendBroadcast(new Intent(GuardStore.ACTION_CHANGED).setPackage(ctx.getPackageName()));
    }

    // ---------- weather (2.11) ----------

    /**
     * {city: {name, lat, lon}} sets the city and starts hourly fetches from Open-Meteo, the TV's one outside
     * connection; {on: false} stops them; {refresh: true} fetches now; {screensaver} shows it on the watch.
     */
    private ControlServer.Response setWeather(JSONObject b) throws JSONException {
        WeatherStore w = WeatherStore.get(ctx);
        JSONObject city = b.optJSONObject("city");
        if (city != null) {
            String name = clean(city.optString("name"));
            double lat = city.optDouble("lat", Double.NaN), lon = city.optDouble("lon", Double.NaN);
            if (name.isEmpty() || !Weather.validPosition(lat, lon)) return error(400, "a city needs a name, latitude and longitude");
            w.setCity(name, lat, lon);
            w.maybeFetch(true, true);
            log("weather", "Weather for " + name + " from " + Weather.HOST);
        }
        if (b.has("screensaver")) w.setScreensaver(b.optBoolean("screensaver", true));
        if (b.has("on") && !b.optBoolean("on", true)) {
            w.turnOff();
            log("weather", "Weather turned off");
        }
        if (b.optBoolean("refresh", false)) w.maybeFetch(true, true);
        return ok(w.json());
    }

    // ---------- phone to TV (2.10): links, search, typing, photos ----------

    private ControlServer.Response links() throws JSONException {
        return ok(Links.get(ctx).json(profiles));
    }

    /** The default browser and each profile's search engine. Every engine keeps safe search on: no password. */
    private ControlServer.Response setLinks(JSONObject b) throws JSONException {
        Links l = Links.get(ctx);
        if (b.has("default_browser")) {
            String p = b.optString("default_browser");
            if (!l.setBrowser(p)) return error(400, "that isn't a web browser on this TV");
            log("links", "Links open in " + appName(p));
        }
        JSONObject e = b.optJSONObject("engine");
        if (e != null) {
            String prof = e.optString("profile"), en = e.optString("engine");
            if (!Profiles.PARENT.equals(prof) && !Profiles.GUEST.equals(prof) && profiles.kid(prof) == null) return error(404, "no such profile");
            if (!l.setEngine(prof, en)) return error(400, "unknown search engine");
            log("links", profileName(prof) + " searches with " + LinkRules.label(en));
        }
        return links();
    }

    private String profileName(String id) {
        if (Profiles.PARENT.equals(id)) return "Parent";
        if (Profiles.GUEST.equals(id)) return "Guest";
        Profiles.Kid k = profiles.kid(id);
        return k == null ? id : k.name;
    }

    private String appName(String pkg) {
        PackageManager pm = ctx.getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (PackageManager.NameNotFoundException e) { return pkg; }
    }

    private ControlServer.Response openLink(JSONObject b) throws JSONException {
        String url = LinkRules.webLink(b.optString("url"));
        if (url == null) return error(400, "only web links (http or https) can be opened on the TV");
        return openOnTv(url, b.optString("app", ""), b, "link", url.length() > 120 ? url.substring(0, 120) + "\u2026" : url);
    }

    /** A search from the phone, in YouTube or on the web with the active profile's engine (safe search on). */
    private ControlServer.Response search(JSONObject b) throws JSONException {
        String q = LinkRules.trimQuery(b.optString("query"));
        if (q.isEmpty()) return error(400, "type something to search for");
        boolean yt = "youtube".equals(b.optString("where"));
        String url = yt ? LinkRules.youTubeSearchUrl(q)
                : LinkRules.searchUrl(Links.get(ctx).engine(profiles.activeId()), q, profiles.activeKid() != null);
        return openOnTv(url, yt ? LinkRules.YOUTUBE_TV : "", b, "search", (yt ? "YouTube: " : "Web: ") + q);
    }

    /**
     * Opens [url] in [app] (empty: YouTube for YouTube links, else the default browser). In a child's profile
     * the app must be on the child's list, or a parent allows this one link with the password: the guard then
     * lets that app through once, and no list changes.
     */
    private ControlServer.Response openOnTv(String url, String app, JSONObject b, String kind, String detail) throws JSONException {
        Links links = Links.get(ctx);
        String target;
        if (!app.isEmpty()) {
            if (!app.equals(LinkRules.YOUTUBE_TV) && !links.browsers().contains(app)) return error(400, "that app can't open web links");
            target = app;
        } else if (LinkRules.isYouTube(url) && links.installed(LinkRules.YOUTUBE_TV)) {
            target = LinkRules.YOUTUBE_TV;
        } else {
            target = links.browser();
        }
        if (target == null) return error(404, "there's no web browser on this TV");
        if (!links.installed(target)) return error(404, (target.equals(LinkRules.YOUTUBE_TV) ? "YouTube" : appName(target)) + " isn't on this TV");
        Intent i = viewIntent(url, target);
        if (ctx.getPackageManager().resolveActivity(i, 0) == null && app.isEmpty() && target.equals(LinkRules.YOUTUBE_TV)) {
            // This TV's YouTube doesn't take web links: the browser opens it instead.
            String browser = links.browser();
            if (browser != null) { target = browser; i = viewIntent(url, target); }
        }
        if (ctx.getPackageManager().resolveActivity(i, 0) == null) return error(404, appName(target) + " can't open this link");
        if (store.isRestricted(target)) return error(403, appName(target) + " is blocked on this TV");
        Profiles.Kid kid = profiles.activeKid();
        boolean once = false;
        if (kid != null && (!kid.all.contains(target) || Profiles.KID_NEVER.contains(target))) {
            if (!b.has("password")) {
                return ControlServer.Response.json(403, new JSONObject().put("error", appName(target) + " isn't on " + kid.name
                        + "'s list. A parent can allow this once.").put("allow_once", true).toString());
            }
            ControlServer.Response denied = needPassword(b);
            if (denied != null) return denied;
            once = true;
        }
        if (once) GuardService.allowOnce(target);
        PhotoActivity.stop();
        final Intent start = i;
        main.post(() -> {
            try { ctx.startActivity(start); } catch (RuntimeException e) { android.util.Log.w("TvHeroApi", "open: " + e.getMessage()); }
        });
        log(once ? kind + "_once" : kind, detail + " \u2192 " + appName(target));
        return ok(new JSONObject().put("opened", target).put("label", appName(target)).put("once", once));
    }

    /** Opens [url] in [pkg]; a browser reuses one tab for links from the phone. */
    private Intent viewIntent(String url, String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!pkg.equals(LinkRules.YOUTUBE_TV)) i.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, ctx.getPackageName() + ".phone");
        return i;
    }

    private ControlServer.Response typeStart(String phone) throws JSONException {
        boolean was = PhoneTyping.active();
        if (!PhoneTyping.start(ctx, phone)) return error(409, "the TV won't let tv-hero switch its keyboard: update the TV app from the phone");
        if (!was) log("typing", "Typing from the phone");
        return ok(new JSONObject().put("typing", true).put("ready", PhoneTyping.ready()));
    }

    /** {text, backspace, enter}: typed into the box that has focus on the TV. */
    private ControlServer.Response type(JSONObject b) throws JSONException {
        String text = b.optString("text", "");
        if (text.length() > 500) return error(400, "send at most 500 characters at a time");
        int back = Math.max(0, Math.min(500, b.optInt("backspace", 0)));
        Boolean r = PhoneTyping.send(text, back, b.optBoolean("enter", false));
        if (r == null) return ok(new JSONObject().put("typing", PhoneTyping.active()).put("ready", false));
        return ok(new JSONObject().put("typing", true).put("ready", true).put("done", r));
    }

    /** One photo of a slideshow the phone runs: {jpeg (base64), session, caption}. Shown, never saved. */
    private ControlServer.Response photo(JSONObject b) throws JSONException {
        PowerManager pm = ctx.getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) return error(409, "the TV is off");
        byte[] jpeg;
        try { jpeg = android.util.Base64.decode(b.optString("jpeg", ""), android.util.Base64.DEFAULT); }
        catch (IllegalArgumentException e) { return error(400, "bad photo"); }
        String session = b.optString("session", "");
        boolean first = !PhotoActivity.showing();
        switch (PhotoActivity.show(ctx, session, jpeg, clean(b.optString("caption", "")))) {
            case STOPPED_ON_TV: return error(410, "stopped on the TV");
            case BAD_IMAGE: return error(400, "that photo couldn't be shown");
            default:
                if (first) log("photos", "Photos from the phone");
                return ok(new JSONObject().put("showing", true));
        }
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

    /** Lets a profile earn its daily maximum with questions again today. Password: it loosens. */
    private ControlServer.Response quizReset(JSONObject b) throws JSONException {
        ControlServer.Response denied = needPassword(b);
        if (denied != null) return denied;
        Profiles.Kid k = profiles.kid(b.optString("id"));
        if (k == null) return error(404, "no such profile");
        profiles.quizReset(k.id);
        log("quiz_reset", k.name);
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

    // ---------- tools (Phase 3): timer, stopwatch, workout ----------

    /**
     * Starts or controls a tool on the TV and returns its state. No password: tools change no rule.
     * Runs on the main thread, where ToolsEngine lives.
     */
    private ControlServer.Response tools(JSONObject b, String what) throws JSONException {
        ToolsEngine e = ToolsEngine.get(ctx);
        final String[] err = {null};
        final JSONObject[] out = {null};
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        main.post(() -> {
            try {
                if ("start".equals(what)) {
                    String tool = b.optString("tool");
                    ToolsCore.Tool shown;
                    switch (tool) {
                        case "timer":
                            long ms = b.has("seconds") ? b.optLong("seconds") * 1000 : b.optLong("minutes", 5) * 60_000;
                            e.startTimer(ms, b.optString("label", "").trim());
                            shown = ToolsCore.Tool.TIMER;
                            break;
                        case "stopwatch":
                            e.startStopwatch();
                            shown = ToolsCore.Tool.STOPWATCH;
                            break;
                        case "workout": {
                            JSONObject spec = b.optJSONObject("workout");
                            e.startWorkout(spec != null ? ToolsEngine.specFrom(spec) : Workout.presets().get(0));
                            shown = ToolsCore.Tool.WORKOUT;
                            break;
                        }
                        case "clock":
                            actions.globalAction("screensaver");
                            shown = null;
                            break;
                        default:
                            err[0] = "unknown tool";
                            return;
                    }
                    if (shown != null) {
                        GuardService g = GuardService.instance;
                        if (g != null) g.showTool(shown == ToolsCore.Tool.TIMER ? TimerActivity.class
                                : shown == ToolsCore.Tool.STOPWATCH ? StopwatchActivity.class : WorkoutActivity.class);
                    }
                    log("tool_started", tool);
                } else if ("control".equals(what)) {
                    switch (b.optString("action")) {
                        case "pause": e.pause(); break;
                        case "resume": e.resume(); break;
                        case "toggle": e.toggle(); break;
                        case "stop": e.stop(); break;
                        case "add_minute": e.addMinute(); break;
                        case "lap": e.lap(); break;
                        case "skip": e.skip(); break;
                        default: err[0] = "unknown action"; return;
                    }
                }
                out[0] = e.json();
            } catch (JSONException x) {
                err[0] = "bad request";
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(3, java.util.concurrent.TimeUnit.SECONDS)) return error(503, "the TV is busy");
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
            return error(503, "interrupted");
        }
        if (err[0] != null) return error(400, err[0]);
        return ok(out[0]);
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
        JSONObject apps = profiles.appUsageJson();   // seconds per profile and app, today (2.9)
        for (java.util.Iterator<String> it = apps.keys(); it.hasNext(); ) {
            JSONObject mine = apps.optJSONObject(it.next());
            if (mine != null) for (java.util.Iterator<String> p = mine.keys(); p.hasNext(); ) pkgs.add(p.next());
        }
        JSONObject labels = new JSONObject();
        for (Map.Entry<String, String> e : GuardStore.labels(ctx, new java.util.ArrayList<>(pkgs)).entrySet()) labels.put(e.getKey(), e.getValue());
        GuardService g = GuardService.instance;
        return ok(new JSONObject().put("sessions", sessions).put("labels", labels)
                .put("usage_today", profiles.usageMinutesJson())
                .put("usage_apps_today", apps)
                .put("usage_source", g != null && g.usageAccess() ? "usage_stats" : "windows"));
    }

    /** On-screen notes (2.9). Notes only, never rules, so no password: see OnScreen. */
    private ControlServer.Response setOnScreen(JSONObject b) throws JSONException {
        OnScreen o = OnScreen.get(ctx);
        o.apply(b);
        StringBuilder what = new StringBuilder();
        for (String k : OnScreen.KEYS) {
            if (!b.has(k)) continue;
            if (what.length() > 0) what.append(", ");
            what.append(OnScreen.label(k)).append(o.on(k) ? " on" : " off");
        }
        if (what.length() > 0) log("onscreen", what.toString());
        actions.refresh();
        return ok(o.json());
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
