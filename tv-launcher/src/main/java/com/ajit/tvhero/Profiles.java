package com.ajit.tvhero;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Family profiles kept on the TV: Parent (no limits) plus up to 10 kids, each with the apps they
 * may open, a daily screen-time limit and a bedtime. Also the TV-wide state the guard enforces:
 * which profile is watching, a parent lock, a sleep timer, extra minutes granted today, the night
 * volume cap, and a short history of what was watched.
 */
public final class Profiles {
    static final int MAX_KIDS = 10;
    static final String PARENT = "parent";
    /** Guest mode: the household's allowed apps, no history, ends at standby. */
    static final String GUEST = "guest";
    /** Never allowed in a kid profile: from these a child could switch protection off or install apps. */
    static final Set<String> KID_NEVER = Set.of(
            "com.android.tv.settings", "com.droidlogic.tv.settings", "com.android.vending",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.droidlogic.tv.soundeffectsettings", "com.xiaomi.android.TV.audio");
    /** Always fine in a kid profile: system UI, keyboard, the home screen itself. */
    static final Set<String> KID_ALWAYS = Set.of(
            "com.ajit.tvhero", "android", "com.android.systemui", "com.google.android.inputmethod.latin",
            "com.mitv.dream", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher");

    private static Profiles instance;
    private final SharedPreferences prefs;
    private final Context app;

    private Profiles(Context ctx) {
        app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences("profiles", Context.MODE_PRIVATE);
    }

    public static synchronized Profiles get(Context ctx) {
        if (instance == null) instance = new Profiles(ctx);
        return instance;
    }

    // ---------- model ----------

    static final class Kid {
        String id, name;
        int color;
        /** This profile's own apps. */
        Set<String> allowed = new HashSet<>();
        /** What the profile may open: its own apps plus the apps for everyone. Filled when read. */
        Set<String> all = new HashSet<>();
        int dailyLimit;          // minutes, 0 = no limit
        boolean bedtimeOn;
        int bedStart = 21 * 60, bedEnd = 7 * 60;   // minutes after midnight; may wrap past midnight
        /** Earn Time: questions on the Time's up screen, each right answer adds a few minutes. */
        boolean quizOn = true;
        int quizMinutes = 5, quizCap = 15, quizLevel = 2;
        Set<String> quizTopics = new HashSet<>(Quiz.TOPICS);

        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("color", color)
                    .put("allowed", new JSONArray(allowed)).put("daily_limit", dailyLimit)
                    .put("bedtime_on", bedtimeOn).put("bedtime_start", bedStart).put("bedtime_end", bedEnd)
                    .put("quiz", quizJson());
        }

        JSONObject quizJson() throws JSONException {
            return new JSONObject().put("enabled", quizOn).put("minutes", quizMinutes).put("daily_max", quizCap)
                    .put("level", quizLevel).put("topics", new JSONArray(quizTopics));
        }

        void readQuiz(JSONObject q) {
            if (q == null) return;
            quizOn = q.optBoolean("enabled", quizOn);
            quizMinutes = Math.max(1, Math.min(30, q.optInt("minutes", quizMinutes)));
            quizCap = Math.max(0, Math.min(120, q.optInt("daily_max", quizCap)));
            quizLevel = Math.max(1, Math.min(3, q.optInt("level", quizLevel)));
            JSONArray t = q.optJSONArray("topics");
            if (t != null) {
                Set<String> picked = new HashSet<>();
                for (int i = 0; i < t.length(); i++) if (Quiz.TOPICS.contains(t.optString(i))) picked.add(t.optString(i));
                if (!picked.isEmpty()) quizTopics = picked;
            }
        }

        static Kid from(JSONObject o) {
            Kid k = new Kid();
            k.id = o.optString("id");
            k.name = o.optString("name", "Kid");
            k.color = o.optInt("color", 0);
            JSONArray a = o.optJSONArray("allowed");
            if (a != null) for (int i = 0; i < a.length(); i++) k.allowed.add(a.optString(i));
            k.dailyLimit = Math.max(0, Math.min(24 * 60, o.optInt("daily_limit", 0)));
            k.bedtimeOn = o.optBoolean("bedtime_on", false);
            k.bedStart = clampMinute(o.optInt("bedtime_start", 21 * 60));
            k.bedEnd = clampMinute(o.optInt("bedtime_end", 7 * 60));
            k.readQuiz(o.optJSONObject("quiz"));
            return k;
        }

        boolean inBedtime(int minuteOfDay) {
            if (!bedtimeOn || bedStart == bedEnd) return false;
            return bedStart < bedEnd ? minuteOfDay >= bedStart && minuteOfDay < bedEnd
                    : minuteOfDay >= bedStart || minuteOfDay < bedEnd;
        }

        /** Minutes until bedtime starts, or -1 when off or already bedtime. */
        int minutesToBedtime(int minuteOfDay) {
            if (!bedtimeOn || inBedtime(minuteOfDay)) return -1;
            int d = bedStart - minuteOfDay;
            return d < 0 ? d + 24 * 60 : d;
        }
    }

    static int clampMinute(int m) {
        return Math.max(0, Math.min(24 * 60 - 1, m));
    }

    // ---------- kids ----------

    public synchronized List<Kid> kids() {
        List<Kid> out = new ArrayList<>();
        JSONArray a = arr("kids");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(withCommon(Kid.from(o)));
        }
        return out;
    }

    private Kid withCommon(Kid k) {
        k.all = new HashSet<>(k.allowed);
        k.all.addAll(commonApps());
        k.all.removeAll(KID_NEVER);
        return k;
    }

    // ---------- apps for everyone ----------

    /** Out of the box: the web browser, WhatsApp and YouTube in Brave (Ajit's choice, Oct 2026). */
    static final Set<String> DEFAULT_COMMON = Set.of("com.phlox.tvwebbrowser", "com.whatsapp", "com.brave.browser");

    /** Apps every profile gets on top of its own (not Parent: Parent has everything). */
    public synchronized Set<String> commonApps() {
        if (!prefs.contains("common_apps")) return new HashSet<>(DEFAULT_COMMON);
        Set<String> out = new HashSet<>();
        JSONArray a = arr("common_apps");
        for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    public synchronized void setCommonApps(Set<String> apps) {
        Set<String> clean = new HashSet<>(apps);
        clean.removeAll(KID_NEVER);
        prefs.edit().putString("common_apps", new JSONArray(clean).toString()).apply();
    }

    /** A kid profile by id; "guest" gives Guest (its daily limit is the only thing it keeps). */
    public synchronized Kid kid(String id) {
        if (GUEST.equals(id)) return guest();
        for (Kid k : kids()) if (k.id.equals(id)) return k;
        return null;
    }

    /** Creates or updates; returns null when the 10-kid limit is reached. */
    public synchronized Kid save(JSONObject in) {
        if (GUEST.equals(in.optString("id"))) {
            setGuestLimit(in.optInt("daily_limit", guestLimit()));
            return guest();
        }
        Kid k = Kid.from(in);
        k.name = k.name.trim().isEmpty() ? "Kid" : (k.name.length() > 24 ? k.name.substring(0, 24) : k.name.trim());
        List<Kid> list = kids();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(k.id)) {
                if (!in.has("quiz")) {   // an older phone app doesn't know Earn Time: keep what's set
                    Kid old = list.get(i);
                    k.quizOn = old.quizOn; k.quizMinutes = old.quizMinutes; k.quizCap = old.quizCap;
                    k.quizLevel = old.quizLevel; k.quizTopics = old.quizTopics;
                }
                list.set(i, k);
                found = true;
            }
        }
        if (!found) {
            if (list.size() >= MAX_KIDS) return null;
            k.id = "k" + GuardCrypto.hex(GuardCrypto.random(4));
            list.add(k);
        }
        writeKids(list);
        return k;
    }

    public synchronized boolean delete(String id) {
        List<Kid> list = kids();
        boolean removed = list.removeIf(k -> k.id.equals(id));
        if (removed) {
            writeKids(list);
            if (id.equals(activeId())) setActive(PARENT);
        }
        return removed;
    }

    private void writeKids(List<Kid> list) {
        JSONArray a = new JSONArray();
        for (Kid k : list) {
            try { a.put(k.json()); } catch (JSONException ignored) { }
        }
        prefs.edit().putString("kids", a.toString()).apply();
    }

    // ---------- who is watching ----------

    public synchronized String activeId() {
        String id = prefs.getString("active", PARENT);
        return PARENT.equals(id) || GUEST.equals(id) || kid(id) != null ? id : PARENT;
    }

    public synchronized Kid activeKid() {
        String id = activeId();
        if (GUEST.equals(id)) return guest();
        return PARENT.equals(id) ? null : kid(id);
    }

    /** Guest: every app allowed on this TV except the parents-only ones; a daily limit if set, no bedtime. */
    Kid guest() {
        Kid g = new Kid();
        g.id = GUEST;
        g.name = "Guest";
        g.color = -1;
        g.dailyLimit = guestLimit();
        g.quizOn = false;
        g.allowed = new HashSet<>(GuardStore.get(app).launchablePackages());
        g.allowed.removeAll(KID_NEVER);
        g.all = g.allowed;
        return g;
    }

    /** Minutes a day for Guest, shared by every guest that day; 0 = no limit. */
    public synchronized int guestLimit() { return prefs.getInt("guest_limit", 0); }

    public synchronized void setGuestLimit(int minutes) {
        prefs.edit().putInt("guest_limit", Math.max(0, Math.min(24 * 60, minutes))).apply();
    }

    public synchronized boolean isGuest() { return GUEST.equals(activeId()); }

    public synchronized void setActive(String id) {
        prefs.edit().putString("active", id == null ? PARENT : id).apply();
    }

    /** True the first time it's asked after each boot (Settings.Global.BOOT_COUNT), false after that. */
    public synchronized boolean firstStartThisBoot() {
        int boot = android.provider.Settings.Global.getInt(app.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT, -1);
        if (boot < 0) return android.os.SystemClock.elapsedRealtime() < 5 * 60_000;   // no counter: only right after boot
        if (prefs.getInt("startup_applied_boot", -1) == boot) return false;
        prefs.edit().putInt("startup_applied_boot", boot).apply();
        return true;
    }

    /** "ask" shows Who's watching when the TV wakes; a profile id starts as that profile. */
    public synchronized String onWake() {
        String v = prefs.getString("on_wake", "ask");
        return "ask".equals(v) || PARENT.equals(v) || kid(v) != null ? v : "ask";
    }

    public synchronized void setOnWake(String v) {
        prefs.edit().putString("on_wake", v).apply();
    }

    // ---------- parent PIN (for the TV's own picker) ----------

    public synchronized boolean pinSet() { return prefs.contains("pin_hash"); }

    public synchronized void setPin(String pin) {
        byte[] salt = GuardCrypto.random(16);
        prefs.edit().putString("pin_salt", GuardCrypto.b64(salt))
                .putString("pin_hash", GuardCrypto.b64(GuardCrypto.pbkdf2(pin, salt))).putInt("pin_fail", 0).apply();
    }

    public synchronized void clearPin() {
        prefs.edit().remove("pin_hash").remove("pin_salt").apply();
    }

    /** 5 wrong PINs lock the picker for 5 minutes. */
    public synchronized boolean checkPin(String pin) {
        long now = System.currentTimeMillis();
        if (now < prefs.getLong("pin_lock_until", 0) || !pinSet()) return false;
        byte[] salt = GuardCrypto.unb64(prefs.getString("pin_salt", ""));
        boolean ok = GuardCrypto.equal(GuardCrypto.unb64(prefs.getString("pin_hash", "")), GuardCrypto.pbkdf2(pin, salt));
        int fails = ok ? 0 : prefs.getInt("pin_fail", 0) + 1;
        SharedPreferences.Editor e = prefs.edit().putInt("pin_fail", fails >= 5 ? 0 : fails);
        if (fails >= 5) e.putLong("pin_lock_until", now + 5 * 60_000L);
        e.apply();
        return ok;
    }

    // ---------- screen time ----------

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    static int minuteOfDay() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** Adds seconds of screen time for a profile today. */
    public synchronized void addUsage(String profile, int seconds) {
        JSONObject u = usageToday();
        try { u.put(profile, u.optInt(profile, 0) + seconds); } catch (JSONException ignored) { }
        prefs.edit().putString("usage", u.toString()).putString("usage_day", today()).apply();
    }

    public synchronized int usedMinutes(String profile) {
        return usageToday().optInt(profile, 0) / 60;
    }

    public synchronized JSONObject usageMinutesJson() {
        JSONObject u = usageToday(), out = new JSONObject();
        for (Iterator<String> it = u.keys(); it.hasNext(); ) {
            String k = it.next();
            try { out.put(k, u.optInt(k) / 60); } catch (JSONException ignored) { }
        }
        return out;
    }

    private JSONObject usageToday() {
        if (!today().equals(prefs.getString("usage_day", ""))) return new JSONObject();
        try { return new JSONObject(prefs.getString("usage", "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    public synchronized void addExtra(String profile, int minutes) {
        JSONObject x = extraToday();
        try { x.put(profile, x.optInt(profile, 0) + minutes); } catch (JSONException ignored) { }
        prefs.edit().putString("extra", x.toString()).putString("extra_day", today()).apply();
    }

    public synchronized int extraMinutes(String profile) {
        return extraToday().optInt(profile, 0);
    }

    private JSONObject extraToday() {
        if (!today().equals(prefs.getString("extra_day", ""))) return new JSONObject();
        try { return new JSONObject(prefs.getString("extra", "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    /** Minutes left today for a kid, or -1 for no limit. Extra time counts. */
    public synchronized int minutesLeft(Kid k) {
        if (k == null || k.dailyLimit <= 0) return -1;
        return Math.max(0, k.dailyLimit + extraMinutes(k.id) - usedMinutes(k.id));
    }

    // ---------- earn time (quiz) ----------

    private JSONObject quizToday(String profile) {
        JSONObject all;
        if (!today().equals(prefs.getString("quiz_day", ""))) all = new JSONObject();
        else try { all = new JSONObject(prefs.getString("quiz", "{}")); } catch (JSONException e) { all = new JSONObject(); }
        JSONObject o = all.optJSONObject(profile);
        return o == null ? new JSONObject() : o;
    }

    private void putQuiz(String profile, JSONObject o) {
        JSONObject all;
        if (!today().equals(prefs.getString("quiz_day", ""))) all = new JSONObject();
        else try { all = new JSONObject(prefs.getString("quiz", "{}")); } catch (JSONException e) { all = new JSONObject(); }
        try { all.put(profile, o); } catch (JSONException ignored) { }
        prefs.edit().putString("quiz", all.toString()).putString("quiz_day", today()).apply();
    }

    /** Minutes this profile earned with questions today. */
    public synchronized int quizEarned(String profile) { return quizToday(profile).optInt("earned", 0); }

    public synchronized int quizRight(String profile) { return quizToday(profile).optInt("right", 0); }

    /** While a break is on after too many wrong answers: when it ends (ms); 0 when there's none. */
    public synchronized long quizBreakUntil(String profile) {
        long t = quizToday(profile).optLong("break_until", 0);
        return t > System.currentTimeMillis() ? t : 0;
    }

    /**
     * Records an answer; returns the minutes it earned (added to today's extra time). A right answer
     * earns the profile's minutes up to its daily maximum. [Quiz.WRONG_IN_A_ROW] wrong answers in a
     * row start a [Quiz.BREAK_MINUTES]-minute break, so guessing doesn't pay.
     */
    public synchronized int quizAnswer(Kid k, boolean right) {
        JSONObject o = quizToday(k.id);
        int got = 0;
        try {
            if (right) {
                got = Quiz.award(o.optInt("earned", 0), k.quizMinutes, k.quizCap);
                o.put("earned", o.optInt("earned", 0) + got).put("right", o.optInt("right", 0) + 1).put("wrong_row", 0);
            } else {
                int row = o.optInt("wrong_row", 0) + 1;
                o.put("wrong", o.optInt("wrong", 0) + 1);
                if (row >= Quiz.WRONG_IN_A_ROW) {
                    o.put("wrong_row", 0).put("break_until", System.currentTimeMillis() + Quiz.BREAK_MINUTES * 60_000L);
                } else {
                    o.put("wrong_row", row);
                }
            }
        } catch (JSONException ignored) { }
        putQuiz(k.id, o);
        if (got > 0) addExtra(k.id, got);
        return got;
    }

    // ---------- parent lock and sleep timer ----------

    public synchronized boolean locked() { return prefs.getBoolean("locked", false); }

    public synchronized void setLocked(boolean on) { prefs.edit().putBoolean("locked", on).apply(); }

    /** Bedtime pause: "allow tonight" until this time. */
    public synchronized long bedtimeSnoozeUntil() { return prefs.getLong("bed_snooze", 0); }

    public synchronized void snoozeBedtime(int minutes) {
        prefs.edit().putLong("bed_snooze", System.currentTimeMillis() + minutes * 60_000L).apply();
    }

    public synchronized long sleepAt() { return prefs.getLong("sleep_at", 0); }

    public synchronized void setSleepAt(long t) { prefs.edit().putLong("sleep_at", t).apply(); }

    // ---------- night volume cap ----------

    public synchronized JSONObject quiet() {
        try {
            return new JSONObject().put("enabled", prefs.getBoolean("quiet_on", false))
                    .put("cap_percent", prefs.getInt("quiet_cap", 40))
                    .put("start", prefs.getInt("quiet_start", 21 * 60))
                    .put("end", prefs.getInt("quiet_end", 7 * 60));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public synchronized void setQuiet(JSONObject b) {
        SharedPreferences.Editor e = prefs.edit();
        if (b.has("enabled")) e.putBoolean("quiet_on", b.optBoolean("enabled"));
        if (b.has("cap_percent")) e.putInt("quiet_cap", Math.max(5, Math.min(100, b.optInt("cap_percent"))));
        if (b.has("start")) e.putInt("quiet_start", clampMinute(b.optInt("start")));
        if (b.has("end")) e.putInt("quiet_end", clampMinute(b.optInt("end")));
        e.apply();
    }

    /** Volume cap in percent now, or -1 when there is none. */
    public synchronized int quietCapNow() {
        if (!prefs.getBoolean("quiet_on", false)) return -1;
        int s = prefs.getInt("quiet_start", 21 * 60), en = prefs.getInt("quiet_end", 7 * 60), m = minuteOfDay();
        boolean in = s == en || (s < en ? m >= s && m < en : m >= s || m < en);
        return in ? prefs.getInt("quiet_cap", 40) : -1;
    }

    // ---------- watch history ----------

    /** Records that [profile] watched [pkg] (title optional) from [start] to [end]. Keeps 7 days, 500 entries. */
    public synchronized void addSession(String profile, String pkg, String title, long start, long end) {
        if (end - start < 15_000 || GUEST.equals(profile)) return;   // guests leave no history
        JSONArray a = arr("sessions");
        try {
            JSONObject last = a.length() > 0 ? a.getJSONObject(a.length() - 1) : null;
            if (last != null && last.optString("package").equals(pkg) && last.optString("profile").equals(profile)
                    && last.optString("title").equals(title == null ? "" : title) && start - last.optLong("end") < 60_000) {
                last.put("end", end);
            } else {
                a.put(new JSONObject().put("profile", profile).put("package", pkg)
                        .put("title", title == null ? "" : title).put("start", start).put("end", end));
            }
        } catch (JSONException ignored) { }
        long cutoff = System.currentTimeMillis() - 7L * 24 * 3600_000;
        JSONArray kept = new JSONArray();
        for (int i = Math.max(0, a.length() - 500); i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optLong("end") >= cutoff) kept.put(o);
        }
        prefs.edit().putString("sessions", kept.toString()).apply();
    }

    public synchronized JSONArray sessions(long since) {
        JSONArray a = arr("sessions"), out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optLong("end") >= since) out.put(o);
        }
        return out;
    }

    // ---------- json ----------

    public synchronized JSONObject json() throws JSONException {
        JSONArray kids = new JSONArray();
        for (Kid k : kids()) {
            kids.put(k.json().put("used_today", usedMinutes(k.id)).put("extra_today", extraMinutes(k.id))
                    .put("quiz_earned_today", quizEarned(k.id)).put("quiz_right_today", quizRight(k.id))
                    .put("minutes_left", minutesLeft(k)));
        }
        Kid g = guest();
        JSONObject guest = new JSONObject().put("daily_limit", g.dailyLimit).put("used_today", usedMinutes(GUEST))
                .put("extra_today", extraMinutes(GUEST)).put("minutes_left", minutesLeft(g));
        return new JSONObject().put("kids", kids).put("guest", guest).put("active", activeId()).put("on_wake", onWake())
                .put("common_apps", new JSONArray(commonApps()))
                .put("pin_set", pinSet()).put("locked", locked()).put("max_kids", MAX_KIDS)
                .put("parent_used_today", usedMinutes(PARENT)).put("sleep_at", sleepAt());
    }

    private JSONArray arr(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); } catch (JSONException e) { return new JSONArray(); }
    }
}
