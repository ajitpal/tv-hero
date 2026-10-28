package com.ajit.tvhero.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Real handshakes on the JVM with EC P-256 self-signed certificates (made with keytool, as the
 * Android Keystore makes them on the devices): pinned peers connect, anyone else is refused, and
 * removing a pin takes effect on the next connection.
 */
public class TlsTest {
    static PrivateKey tvKey, phoneKey, strangerKey;
    static X509Certificate tvCert, phoneCert, strangerCert;

    @BeforeClass public static void keys() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("tls").toFile();
        Object[] tv = make(dir, "tv"), phone = make(dir, "phone"), stranger = make(dir, "stranger");
        tvKey = (PrivateKey) tv[0]; tvCert = (X509Certificate) tv[1];
        phoneKey = (PrivateKey) phone[0]; phoneCert = (X509Certificate) phone[1];
        strangerKey = (PrivateKey) stranger[0]; strangerCert = (X509Certificate) stranger[1];
    }

    private static Object[] make(File dir, String name) throws Exception {
        File ks = new File(dir, name + ".p12");
        String keytool = System.getProperty("java.home") + "/bin/keytool";
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", name, "-keyalg", "EC", "-groupname", "secp256r1",
                "-sigalg", "SHA256withECDSA", "-dname", "CN=" + name, "-validity", "3650", "-storetype", "PKCS12",
                "-keystore", ks.getPath(), "-storepass", "testpass", "-keypass", "testpass").redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertEquals(0, p.waitFor());
        KeyStore k = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(ks)) { k.load(in, "testpass".toCharArray()); }
        return new Object[]{k.getKey(name, "testpass".toCharArray()), k.getCertificate(name)};
    }

    /** Starts a one-shot TLS server like the TV's; returns the port. Records the client fingerprint it saw. */
    private static int server(Set<String> pinnedPhones, AtomicReference<String> seen) throws Exception {
        SSLContext ctx = Tls.context(tvKey, tvCert, pinnedPhones::contains);
        SSLServerSocket ss = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(0, 5, InetAddress.getLoopbackAddress());
        ss.setNeedClientAuth(true);
        Thread t = new Thread(() -> {
            try (SSLServerSocket s = ss; SSLSocket c = (SSLSocket) s.accept()) {
                Tls.restrict(c);
                c.startHandshake();
                seen.set(Tls.fingerprint((X509Certificate) c.getSession().getPeerCertificates()[0]));
                c.getOutputStream().write("ok".getBytes());
                c.getOutputStream().flush();
            } catch (Exception e) {
                seen.set("refused");
            }
        });
        t.setDaemon(true);
        t.start();
        return ss.getLocalPort();
    }

    private static String client(int port, PrivateKey key, X509Certificate cert, String pinnedTv) throws Exception {
        SSLContext ctx = Tls.context(key, cert, fp -> fp.equals(pinnedTv));
        try (SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket(InetAddress.getLoopbackAddress(), port)) {
            Tls.restrict(s);
            s.setSoTimeout(5000);
            s.startHandshake();
            InputStream in = s.getInputStream();
            OutputStream ignored = s.getOutputStream();
            return new String(in.readNBytes(2)) + " " + s.getSession().getProtocol();
        }
    }

    @Test public void pairedPhoneConnects() throws Exception {
        Set<String> pins = ConcurrentHashMap.newKeySet();
        pins.add(Tls.fingerprint(phoneCert));
        AtomicReference<String> seen = new AtomicReference<>();
        String r = client(server(pins, seen), phoneKey, phoneCert, Tls.fingerprint(tvCert));
        assertTrue(r, r.startsWith("ok TLSv1."));
        Thread.sleep(100);
        assertEquals(Tls.fingerprint(phoneCert), seen.get());
    }

    @Test public void strangerIsRefused() throws Exception {
        Set<String> pins = ConcurrentHashMap.newKeySet();
        pins.add(Tls.fingerprint(phoneCert));
        AtomicReference<String> seen = new AtomicReference<>();
        int port = server(pins, seen);
        try {
            client(port, strangerKey, strangerCert, Tls.fingerprint(tvCert));
            fail("a phone that never paired must not get in");
        } catch (Exception expected) { }
    }

    @Test public void removedPhoneIsRefusedNextTime() throws Exception {
        Set<String> pins = ConcurrentHashMap.newKeySet();
        pins.add(Tls.fingerprint(phoneCert));
        client(server(pins, new AtomicReference<>()), phoneKey, phoneCert, Tls.fingerprint(tvCert));
        pins.remove(Tls.fingerprint(phoneCert));
        try {
            client(server(pins, new AtomicReference<>()), phoneKey, phoneCert, Tls.fingerprint(tvCert));
            fail("removed phone must be refused");
        } catch (Exception expected) { }
    }

    @Test public void phoneRefusesAFakeTv() throws Exception {
        Set<String> pins = ConcurrentHashMap.newKeySet();
        pins.add(Tls.fingerprint(phoneCert));
        int port = server(pins, new AtomicReference<>());
        try {
            client(port, phoneKey, phoneCert, Tls.fingerprint(strangerCert));   // phone pinned a different TV
            fail("the phone must refuse a TV it didn't pair with");
        } catch (Exception expected) { }
    }
}
