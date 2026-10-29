package com.ajit.tvhero.adb;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Computer-side check of the ADB client against a real TV (never part of an app):
 *   java -jar tvhero-adb-cli.jar <tv-ip> <adbkey> probe
 *   java -jar tvhero-adb-cli.jar <tv-ip> <adbkey> install <apk>
 * Only read-only commands and installs; no free-form shell, by design.
 */
public final class AdbCli {
    public static void main(String[] a) throws Exception {
        if (a.length < 3) {
            System.err.println("usage: <tv-ip> <adbkey> probe | install <apk>");
            System.exit(2);
        }
        AdbKeys.Signer key = AdbKeys.fromPem(new String(Files.readAllBytes(new File(a[1]).toPath()), StandardCharsets.US_ASCII));
        System.out.println("key fingerprint " + AdbKeys.fingerprint(key.publicKey()));
        if (a[2].equals("pubkey")) {   // must equal the base64 part of adbkey.pub
            System.out.println(AdbKeys.publicKeyLine(key.publicKey(), "check"));
            return;
        }
        try (AdbClient adb = AdbClient.connect(a[0], 5555, key, "tv-hero-cli",
                fp -> System.out.println("Press Allow on the TV (fingerprint " + fp + ")"), 60_000)) {
            System.out.println("connected: " + adb.banner());
            switch (a[2]) {
                case "probe":
                    System.out.println(Probe.run(adb));
                    break;
                case "install": {
                    File apk = new File(a[3]);
                    long t0 = System.currentTimeMillis();
                    try (InputStream in = new FileInputStream(apk)) {
                        System.out.println(adb.install(in, apk.length(), null));
                    }
                    System.out.println("took " + (System.currentTimeMillis() - t0) + " ms");
                    System.out.println(Probe.run(adb));
                    break;
                }
                default:
                    System.err.println("unknown command");
                    System.exit(2);
            }
        }
    }
}
