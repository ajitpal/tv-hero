package com.ajit.tvhero.protocol;

/** Where a connection comes from, decided by the peer's address as the TV sees it. */
public final class Net {
    private Net() { }

    public enum Via { HOME, INTERNET }

    /**
     * HOME: private IPv4 ranges (RFC 1918), link-local, loopback, IPv6 link-local / unique-local.
     * INTERNET: everything else, including Tailscale's 100.64.0.0/10 (carrier-grade NAT range).
     */
    public static Via classify(String ip) {
        if (ip == null || ip.isEmpty()) return Via.INTERNET;
        String a = ip.toLowerCase(java.util.Locale.ROOT);
        if (a.startsWith("::ffff:")) a = a.substring(7);   // IPv4 seen through an IPv6 socket
        int pct = a.indexOf('%');
        if (pct >= 0) a = a.substring(0, pct);
        if (a.contains(":")) {
            if (a.equals("::1") || a.startsWith("fe8") || a.startsWith("fe9") || a.startsWith("fea") || a.startsWith("feb")
                    || a.startsWith("fc") || a.startsWith("fd")) return Via.HOME;
            return Via.INTERNET;
        }
        String[] p = a.split("\\.");
        if (p.length != 4) return Via.INTERNET;
        int o1, o2;
        try { o1 = Integer.parseInt(p[0]); o2 = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return Via.INTERNET; }
        if (o1 == 10 || o1 == 127) return Via.HOME;
        if (o1 == 192 && o2 == 168) return Via.HOME;
        if (o1 == 172 && o2 >= 16 && o2 <= 31) return Via.HOME;
        if (o1 == 169 && o2 == 254) return Via.HOME;
        return Via.INTERNET;
    }

    public static boolean isTailscale(String ip) {
        if (ip == null) return false;
        String a = ip.startsWith("::ffff:") ? ip.substring(7) : ip;
        String[] p = a.split("\\.");
        if (p.length != 4 || !p[0].equals("100")) return false;
        try {
            int o2 = Integer.parseInt(p[1]);
            return o2 >= 64 && o2 <= 127;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
