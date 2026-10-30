package com.ajit.tvhero.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProbeTest {
    static final String XIAOMI = String.join("\n",
            "@@props",
            "[ro.build.version.sdk]: [28]",
            "[ro.build.version.release]: [9]",
            "[ro.product.brand]: [Xiaomi]",
            "[ro.product.manufacturer]: [Xiaomi]",
            "[ro.product.model]: [MiTV-AXSO0]",
            "[ro.config.low_ram]: [true]",
            "[ro.product.cpu.abi]: [armeabi-v7a]",
            "@@home",
            "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false",
            "com.ajit.tvhero/.MainActivity",
            "@@gtv",
            "@@tvhero",
            "    versionCode=14 minSdk=26 targetSdk=34",
            "    versionName=2.4.1",
            "      android.permission.WRITE_SECURE_SETTINGS: granted=true",
            "@@a11y",
            "com.ajit.tvhero/.GuardService",
            "@@saver",
            "com.ajit.tvhero/com.ajit.tvhero.ClockDream",
            "@@idle",
            "300000",
            "@@adb",
            "1",
            "@@mem",
            "MemTotal:         997744 kB",
            "@@disk",
            "/dev/block/dm-1  5045632 2934960   2094288  59% /data",
            "");

    @Test public void readsTheHomeTv() {
        Probe.Result r = Probe.parse(XIAOMI);
        assertEquals(28, r.sdk);
        assertEquals("MiTV-AXSO0", r.model);
        assertTrue(r.tvHeroIsHome());
        assertEquals("2.4.1", r.tvHeroVersion);
        assertEquals(14, r.tvHeroVersionCode);
        assertTrue(r.secureSettingsGranted);
        assertTrue(r.guardEnabled);
        assertEquals(5, r.idleMinutes);
        assertTrue(r.adbEnabled);
        assertTrue(r.lowRam);
        assertEquals(974, r.ramMb);
        assertEquals(2045, r.freeStorageMb);
        assertFalse(r.googleTv);
        assertEquals(Probe.Level.FULL, r.level());
    }

    @Test public void readsUsageAccess() {
        assertFalse(Probe.parse(XIAOMI).usageAccess);   // no @@usage section: not granted
        assertTrue(Probe.parse(XIAOMI + "@@usage\nGET_USAGE_STATS: allow\n").usageAccess);
        assertTrue(Probe.parse(XIAOMI + "@@usage\nGET_USAGE_STATS: allow; time=+3m2s ago\n").usageAccess);
        assertFalse(Probe.parse(XIAOMI + "@@usage\nGET_USAGE_STATS: default\n").usageAccess);
        assertFalse(Probe.parse(XIAOMI + "@@usage\nGET_USAGE_STATS: ignore\n").usageAccess);
    }

    @Test public void googleTvWithItsOwnHomeStartsAtGuardOnly() {
        String gtv = "@@props\n[ro.build.version.sdk]: [31]\n@@home\ncom.google.android.apps.tv.launcherx/.home.HomeActivity\n"
                + "@@gtv\npackage:com.google.android.apps.tv.launcherx\n@@tvhero\n@@a11y\nnull\n";
        Probe.Result r = Probe.parse(gtv);
        assertTrue(r.googleTv);
        assertEquals("", r.tvHeroVersion);
        assertFalse(r.guardEnabled);
        assertEquals(Probe.Level.GUARD_ONLY, r.level());
    }

    @Test public void oldAndroidIsUnsupported() {
        assertEquals(Probe.Level.UNSUPPORTED, Probe.parse("@@props\n[ro.build.version.sdk]: [25]\n").level());
    }

    @Test public void commandIsOneLineOfReadOnlySteps() {
        String c = Probe.command();
        assertFalse(c.contains("\n"));
        for (String bad : new String[]{"settings put", "pm disable", "pm uninstall", "rm ", "reboot", "setprop"}) assertFalse(bad, c.contains(bad));
    }
}
