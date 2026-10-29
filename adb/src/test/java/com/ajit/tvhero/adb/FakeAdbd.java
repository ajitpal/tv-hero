package com.ajit.tvhero.adb;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/** A tiny adbd on loopback for tests: RSA auth with an "Allow" switch, shell echo, and exec install. */
final class FakeAdbd implements AutoCloseable {
    final ServerSocket server;
    final Set<String> trustedKeys = new HashSet<>();
    volatile boolean allowNewKey = true;
    volatile boolean stls;
    volatile byte[] lastInstall;
    private final Thread thread;

    FakeAdbd() throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        thread = new Thread(this::serve, "fake-adbd");
        thread.setDaemon(true);
        thread.start();
    }

    int port() { return server.getLocalPort(); }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) { session(s); } catch (IOException ignored) { }
        }
    }

    private void session(Socket s) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        OutputStream out = s.getOutputStream();
        AdbMessage cnxn = AdbMessage.read(in);
        if (cnxn.command != AdbMessage.CNXN) return;
        if (stls) { new AdbMessage(AdbMessage.STLS, 1, 0, null).writeTo(out); return; }
        byte[] token = new byte[20];
        new java.util.Random().nextBytes(token);
        new AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token).writeTo(out);
        boolean ok = false;
        while (!ok) {
            AdbMessage m = AdbMessage.read(in);
            if (m.command != AdbMessage.AUTH) return;
            if (m.arg0 == AdbMessage.AUTH_SIGNATURE) {
                for (String k : trustedKeys) if (verify(k, token, m.data)) ok = true;
                if (!ok) new AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token).writeTo(out);
            } else if (m.arg0 == AdbMessage.AUTH_RSAPUBLICKEY) {
                if (!allowNewKey) return;   // "Don't allow": the TV just drops it
                trustedKeys.add(new String(m.data, StandardCharsets.US_ASCII).replace("\0", "").split(" ")[0]);
                ok = true;
            }
        }
        new AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, 4096, "device::ro.product.model=Fake;\0".getBytes(StandardCharsets.UTF_8)).writeTo(out);
        while (true) {
            AdbMessage open;
            try { open = AdbMessage.read(in); } catch (IOException e) { return; }
            if (open.command != AdbMessage.OPEN) continue;
            String svc = new String(open.data, StandardCharsets.UTF_8).replace("\0", "");
            int remote = open.arg0, local = 77;
            new AdbMessage(AdbMessage.OKAY, local, remote, null).writeTo(out);
            byte[] reply;
            if (svc.startsWith("shell:")) {
                reply = ("ran: " + svc.substring(6) + "\n").getBytes(StandardCharsets.UTF_8);
            } else if (svc.startsWith("exec:cmd package install -r -S ")) {
                int size = Integer.parseInt(svc.substring(svc.lastIndexOf(' ') + 1));
                java.io.ByteArrayOutputStream got = new java.io.ByteArrayOutputStream();
                while (got.size() < size) {
                    AdbMessage w = AdbMessage.read(in);
                    if (w.command == AdbMessage.WRTE) {
                        got.write(w.data, 0, w.data.length);
                        new AdbMessage(AdbMessage.OKAY, local, remote, null).writeTo(out);
                    }
                }
                lastInstall = got.toByteArray();
                reply = "Success\n".getBytes(StandardCharsets.UTF_8);
            } else {
                new AdbMessage(AdbMessage.CLSE, 0, remote, null).writeTo(out);
                continue;
            }
            new AdbMessage(AdbMessage.WRTE, local, remote, reply).writeTo(out);
            AdbMessage ack = AdbMessage.read(in);   // OKAY
            new AdbMessage(AdbMessage.CLSE, local, remote, null).writeTo(out);
            AdbMessage.read(in);   // their CLSE
        }
    }

    private static boolean verify(String b64Key, byte[] token, byte[] sig) {
        try {
            byte[] k = Base64.getDecoder().decode(b64Key);
            byte[] le = java.util.Arrays.copyOfRange(k, 8, 8 + 256);
            byte[] be = new byte[257];
            for (int i = 0; i < 256; i++) be[256 - i] = le[i];
            PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(new java.math.BigInteger(be), java.math.BigInteger.valueOf(65537)));
            Signature v = Signature.getInstance("NONEwithRSA");
            v.initVerify(pub);
            v.update(AdbKeys.toBeSigned(token));
            return v.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    @Override public void close() throws IOException { server.close(); }
}
