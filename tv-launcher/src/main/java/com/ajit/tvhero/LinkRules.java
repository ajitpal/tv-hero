package com.ajit.tvhero;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links and searches sent from the phone (2.10 "Phone to TV"). Plain Java, so the rules are unit-tested:
 * only web links are opened, YouTube links go to YouTube, and every search keeps safe search on.
 */
final class LinkRules {
    static final String YOUTUBE_TV = "com.google.android.youtube.tv";

    /** Search engines a profile can use. Safe search is always on: strict for children and Guest, moderate for Parent. */
    static final String DUCKDUCKGO = "duckduckgo", GOOGLE = "google", BING = "bing";
    static final String[] ENGINES = {DUCKDUCKGO, GOOGLE, BING};
    static final String DEFAULT_ENGINE = DUCKDUCKGO;

    static final int MAX_URL = 2048, MAX_QUERY = 200;

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+", Pattern.CASE_INSENSITIVE);

    private LinkRules() { }

    /**
     * The web link in [text] (a shared message may wrap it in words), or null. Only http and https: an
     * intent:, file:, content: or javascript: link is never opened. Trailing punctuation is dropped.
     */
    static String webLink(String text) {
        if (text == null) return null;
        Matcher m = URL.matcher(text);
        if (!m.find()) return null;
        String u = m.group();
        while (!u.isEmpty() && ".,;:!?)]}'".indexOf(u.charAt(u.length() - 1)) >= 0) u = u.substring(0, u.length() - 1);
        if (u.length() > MAX_URL) return null;
        try {
            URI uri = new URI(u);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            if (uri.getHost() == null || uri.getHost().isEmpty()) return null;
        } catch (URISyntaxException e) {
            return null;
        }
        return u;
    }

    /** The host of a web link, lower case, without "www.", for the event log; "" if none. */
    static String host(String url) {
        try {
            String h = new URI(url).getHost();
            if (h == null) return "";
            h = h.toLowerCase(Locale.ROOT);
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (URISyntaxException e) {
            return "";
        }
    }

    static boolean isYouTube(String url) {
        String h = host(url);
        return h.equals("youtube.com") || h.equals("m.youtube.com") || h.equals("youtu.be") || h.equals("music.youtube.com");
    }

    static boolean knownEngine(String e) {
        for (String k : ENGINES) if (k.equals(e)) return true;
        return false;
    }

    /** A web search address with safe search on: [strict] for children and Guest. */
    static String searchUrl(String engine, String query, boolean strict) {
        String q = enc(trimQuery(query));
        switch (knownEngine(engine) ? engine : DEFAULT_ENGINE) {
            case GOOGLE: return "https://www.google.com/search?q=" + q + "&safe=active";
            case BING: return "https://www.bing.com/search?q=" + q + "&adlt=" + (strict ? "strict" : "moderate");
            default: return "https://duckduckgo.com/?q=" + q + "&kp=" + (strict ? "1" : "-1");
        }
    }

    static String youTubeSearchUrl(String query) {
        return "https://www.youtube.com/results?search_query=" + enc(trimQuery(query));
    }

    static String trimQuery(String q) {
        q = q == null ? "" : q.replaceAll("[\\p{Cntrl}]", " ").trim();
        return q.length() > MAX_QUERY ? q.substring(0, MAX_QUERY) : q;
    }

    static String label(String engine) {
        switch (knownEngine(engine) ? engine : DEFAULT_ENGINE) {
            case GOOGLE: return "Google (SafeSearch)";
            case BING: return "Bing (SafeSearch)";
            default: return "DuckDuckGo (safe search)";
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
