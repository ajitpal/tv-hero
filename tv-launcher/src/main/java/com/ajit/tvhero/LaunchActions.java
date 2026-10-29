package com.ajit.tvhero;

import java.util.Map;
import java.util.function.BiFunction;

/** Selects a configured, package-scoped action only when it resolves; otherwise keeps the normal launch. */
final class LaunchActions {
    private LaunchActions() {}

    /**
     * "url:https://…" as a launch action opens that page in the tile's own app (a browser) instead of an
     * activity action. The browser reuses one tab for it (Browser.EXTRA_APPLICATION_ID), so pressing the
     * tile again doesn't pile up tabs. Returns null for an ordinary action.
     */
    static android.content.Intent urlIntent(String pkg, String action, String appId) {
        if (action == null || !action.startsWith("url:")) return null;
        String url = action.substring(4).trim();
        if (!url.startsWith("https://")) return null;   // web pages only, never file:// or intents
        return new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .setPackage(pkg)
                .putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, appId + "." + pkg)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static <T> T select(String pkg, Map<String, String> actions, T fallback,
                        BiFunction<String, String, T> resolver) {
        String action = actions.get(pkg);
        if (action == null) return fallback;
        T custom = resolver.apply(pkg, action);
        return custom != null ? custom : fallback;
    }
}
