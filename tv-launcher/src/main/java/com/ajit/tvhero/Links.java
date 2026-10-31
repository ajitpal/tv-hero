package com.ajit.tvhero;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings for links and searches from the phone (2.10): the TV's default browser and each profile's search
 * engine. Every engine keeps safe search on (see LinkRules), so changing one never loosens a rule.
 */
final class Links {
    private final Context ctx;
    private final SharedPreferences prefs;

    private Links(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        prefs = this.ctx.getSharedPreferences("links", Context.MODE_PRIVATE);
    }

    static Links get(Context ctx) { return new Links(ctx); }

    /** Installed apps that open web links (YouTube excluded: it only opens its own links). */
    List<String> browsers() {
        PackageManager pm = ctx.getPackageManager();
        Set<String> out = new LinkedHashSet<>();
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"));
        List<ResolveInfo> found = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL);
        for (ResolveInfo r : found) {
            String p = r.activityInfo == null ? null : r.activityInfo.packageName;
            if (p == null || p.equals(LinkRules.YOUTUBE_TV) || p.equals(ctx.getPackageName())) continue;
            out.add(p);
        }
        return new ArrayList<>(out);
    }

    /** The browser links open in: the one a parent chose if it's still installed, else the first one found. */
    String browser() {
        List<String> all = browsers();
        String chosen = prefs.getString("default_browser", "");
        if (all.contains(chosen)) return chosen;
        if (all.contains("com.phlox.tvwebbrowser")) return "com.phlox.tvwebbrowser";
        return all.isEmpty() ? null : all.get(0);
    }

    boolean setBrowser(String pkg) {
        if (!browsers().contains(pkg)) return false;
        prefs.edit().putString("default_browser", pkg).apply();
        return true;
    }

    String engine(String profile) {
        String e = engines().optString(profile, LinkRules.DEFAULT_ENGINE);
        return LinkRules.knownEngine(e) ? e : LinkRules.DEFAULT_ENGINE;
    }

    boolean setEngine(String profile, String engine) {
        if (profile == null || profile.isEmpty() || !LinkRules.knownEngine(engine)) return false;
        JSONObject o = engines();
        try { o.put(profile, engine); } catch (JSONException e) { return false; }
        prefs.edit().putString("engines", o.toString()).apply();
        return true;
    }

    private JSONObject engines() {
        try { return new JSONObject(prefs.getString("engines", "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    boolean installed(String pkg) {
        try {
            ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    JSONObject json(Profiles profiles) throws JSONException {
        PackageManager pm = ctx.getPackageManager();
        JSONArray bs = new JSONArray();
        for (String p : browsers()) {
            String label = p;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString(); } catch (PackageManager.NameNotFoundException ignored) { }
            bs.put(new JSONObject().put("package", p).put("label", label));
        }
        JSONObject eng = new JSONObject().put(Profiles.PARENT, engine(Profiles.PARENT)).put(Profiles.GUEST, engine(Profiles.GUEST));
        for (Profiles.Kid k : profiles.kids()) eng.put(k.id, engine(k.id));
        JSONArray choices = new JSONArray();
        for (String e : LinkRules.ENGINES) choices.put(new JSONObject().put("id", e).put("label", LinkRules.label(e)));
        String b = browser();
        return new JSONObject().put("default_browser", b == null ? "" : b).put("browsers", bs)
                .put("youtube", installed(LinkRules.YOUTUBE_TV)).put("engines", eng).put("engine_choices", choices);
    }
}
