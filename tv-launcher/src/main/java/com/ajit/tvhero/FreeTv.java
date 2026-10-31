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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Free TV (2.11): official apps, YouTube channels and playlist channels a parent picks on the phone. tv-hero
 * ships no channel lists. A child sees only the items a parent ticked for them (ticking needs the password);
 * Parent and Guest's rules are as for apps. Streams play in a video player installed on the TV.
 */
final class FreeTv {
    static final int MAX_ITEMS = 120;
    static final String APP = "app", YOUTUBE = "youtube", STREAM = "stream";
    static final String VLC = "org.videolan.vlc";

    /** One thing on the Free TV screen. */
    static final class Item {
        final String id, kind, title, pkg, url, group;

        Item(String id, String kind, String title, String pkg, String url, String group) {
            this.id = id;
            this.kind = kind;
            this.title = title;
            this.pkg = pkg;
            this.url = url;
            this.group = group;
        }

        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("kind", kind).put("title", title).put("package", pkg)
                    .put("url", url).put("group", group);
        }
    }

    /**
     * A checked item from the phone, or null: a known kind, a title, http(s) only for streams, a YouTube
     * address for channels, a package name for apps. Titles and groups are trimmed and shortened.
     */
    static Item check(JSONObject o) {
        if (o == null) return null;
        String kind = o.optString("kind");
        String title = shorten(o.optString("title"), 60);
        String group = shorten(o.optString("group"), 40);
        String id = o.optString("id").replaceAll("[^A-Za-z0-9_-]", "");
        if (id.isEmpty() || id.length() > 40 || title.isEmpty()) return null;
        switch (kind) {
            case APP: {
                String pkg = o.optString("package");
                if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+") || pkg.length() > 120) return null;
                return new Item(id, kind, title, pkg, "", group);
            }
            case YOUTUBE: {
                String url = LinkRules.webLink(o.optString("url"));
                if (url == null || !LinkRules.isYouTube(url)) return null;
                return new Item(id, kind, title, "", url, group);
            }
            case STREAM: {
                String url = LinkRules.webLink(o.optString("url"));
                if (url == null) return null;
                return new Item(id, kind, title, "", url, group);
            }
            default:
                return null;
        }
    }

    private static String shorten(String s, int max) {
        s = s == null ? "" : s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** What [profile] sees: everything for Parent, the ticked items for a child or Guest. */
    static List<Item> visible(List<Item> all, String profile, Set<String> ticked) {
        if (Profiles.PARENT.equals(profile)) return all;
        List<Item> out = new ArrayList<>();
        for (Item i : all) if (ticked.contains(i.id)) out.add(i);
        return out;
    }

    /** Does going from [before] to [after] tick anything new (loosening a child's rule)? */
    static boolean adds(Set<String> before, Set<String> after) {
        for (String id : after) if (!before.contains(id)) return true;
        return false;
    }

    // ---------- storage ----------

    private final Context ctx;
    private final SharedPreferences prefs;

    private FreeTv(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        prefs = this.ctx.getSharedPreferences("freetv", Context.MODE_PRIVATE);
    }

    static FreeTv get(Context ctx) { return new FreeTv(ctx); }

    List<Item> items() {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                Item it = check(a.optJSONObject(i));
                if (it != null) out.add(it);
            }
        } catch (JSONException ignored) { }
        return out;
    }

    /** Replaces the list; ticks for items that are gone are dropped. */
    void setItems(List<Item> items) throws JSONException {
        JSONArray a = new JSONArray();
        Set<String> ids = new HashSet<>();
        for (Item i : items) { a.put(i.json()); ids.add(i.id); }
        JSONObject t = ticksJson(), kept = new JSONObject();
        for (java.util.Iterator<String> it = t.keys(); it.hasNext(); ) {
            String p = it.next();
            JSONArray old = t.optJSONArray(p), now = new JSONArray();
            for (int i = 0; old != null && i < old.length(); i++) if (ids.contains(old.optString(i))) now.put(old.optString(i));
            kept.put(p, now);
        }
        prefs.edit().putString("items", a.toString()).putString("ticks", kept.toString()).apply();
    }

    Set<String> ticks(String profile) {
        Set<String> out = new LinkedHashSet<>();
        JSONArray a = ticksJson().optJSONArray(profile);
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    void setTicks(String profile, Set<String> ids) throws JSONException {
        JSONObject t = ticksJson();
        t.put(profile, new JSONArray(ids));
        prefs.edit().putString("ticks", t.toString()).apply();
    }

    JSONObject ticksJson() {
        try { return new JSONObject(prefs.getString("ticks", "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    /** Apps that can play a web video stream (VLC first where installed). */
    List<String> players() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://example.com/live.m3u8"), "video/*");
        Set<String> out = new LinkedHashSet<>();
        for (ResolveInfo r : pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)) {
            if (r.activityInfo != null && !r.activityInfo.packageName.equals(ctx.getPackageName())) out.add(r.activityInfo.packageName);
        }
        List<String> list = new ArrayList<>(out);
        if (list.remove(VLC)) list.add(0, VLC);
        return list;
    }

    String player() {
        List<String> all = players();
        String chosen = prefs.getString("player", "");
        if (all.contains(chosen)) return chosen;
        return all.isEmpty() ? null : all.get(0);
    }

    boolean setPlayer(String pkg) {
        if (!players().contains(pkg)) return false;
        prefs.edit().putString("player", pkg).apply();
        return true;
    }

    /** The intent that opens [i], or null if nothing on the TV can (an app gone, no player). */
    Intent intent(Item i) {
        PackageManager pm = ctx.getPackageManager();
        Intent out;
        switch (i.kind) {
            case APP:
                out = pm.getLeanbackLaunchIntentForPackage(i.pkg);
                if (out == null) out = pm.getLaunchIntentForPackage(i.pkg);
                break;
            case YOUTUBE:
                out = new Intent(Intent.ACTION_VIEW, Uri.parse(i.url)).setPackage(LinkRules.YOUTUBE_TV);
                if (pm.resolveActivity(out, 0) == null) {
                    String b = Links.get(ctx).browser();
                    out = b == null ? null : new Intent(Intent.ACTION_VIEW, Uri.parse(i.url)).setPackage(b);
                }
                break;
            default: {
                String p = player();
                out = p == null ? null : new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(i.url), "video/*").setPackage(p);
            }
        }
        if (out == null || pm.resolveActivity(out, 0) == null) return null;
        return out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
