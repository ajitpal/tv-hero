package com.ajit.tvhero.adb;

import com.ajit.tvhero.protocol.Net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * A small ADB client over TCP (port 5555): RSA authentication, shell commands and streamed installs,
 * one stream at a time. Enough to set up and update a TV from the phone; nothing else.
 *
 * Security:
 * - Connects only to addresses on the home network (private, link-local or loopback): never over the
 *   internet or Tailscale, because ADB's TCP transport is unencrypted and gives full control.
 * - The key never leaves its Signer (on the phone, the Android Keystore).
 * - The first time, the TV asks a person to Allow this key; its fingerprint is shown on both screens.
 * - Commands are fixed strings built in this module; anything else is refused.
 */
public final class AdbClient implements Closeable {

    public interface Listener {
        /** The TV is showing "Allow debugging?" for this key; a person must press Allow on the TV. */
        void waitingForAllow(String fingerprint);
    }

    public interface Progress {
        void sent(long bytes, long total);
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int maxPayload = AdbMessage.MAX_PAYLOAD;
    private int nextLocalId = 1;
    private String banner = "";

    private AdbClient(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 64 * 1024));
        this.out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);
    }

    /**
     * Connects and authenticates. [allowTimeoutMs] is how long to wait for a person to press Allow on the
     * TV the first time this key is used.
     */
    public static AdbClient connect(String host, int port, AdbKeys.Signer signer, String keyName,
                                    Listener listener, int allowTimeoutMs) throws IOException {
        InetAddress addr = InetAddress.getByName(host);
        if (Net.classify(addr.getHostAddress()) != Net.Via.HOME) {
            throw new AdbException("Debugging is only used on your home network (" + addr.getHostAddress() + " isn't).");
        }
        Socket s = new Socket();
        s.setTcpNoDelay(true);
        try {
            s.connect(new InetSocketAddress(addr, port), 5000);
        } catch (IOException e) {
            s.close();
            throw new AdbException("The TV didn't answer on port " + port + ". Is debugging on?", e);
        }
        AdbClient c = new AdbClient(s);
        try {
            c.handshake(signer, keyName, listener, allowTimeoutMs);
            return c;
        } catch (IOException | RuntimeException e) {
            c.close();
            throw e;
        }
    }

    private void handshake(AdbKeys.Signer signer, String keyName, Listener listener, int allowTimeoutMs) throws IOException {
        socket.setSoTimeout(10_000);
        send(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, AdbKeys.ascii("host::\0"));
        boolean signed = false, offeredKey = false;
        while (true) {
            AdbMessage m;
            try {
                m = AdbMessage.read(in);
            } catch (SocketTimeoutException e) {
                throw new AdbException(offeredKey ? "Nobody pressed Allow on the TV in time." : "The TV stopped answering.", e);
            }
            switch (m.command) {
                case AdbMessage.CNXN:
                    banner = new String(m.data, StandardCharsets.UTF_8).replace("\0", "");
                    maxPayload = Math.max(4096, Math.min(AdbMessage.MAX_PAYLOAD, m.arg1));
                    socket.setSoTimeout(30_000);
                    return;
                case AdbMessage.AUTH:
                    if (m.arg0 != AdbMessage.AUTH_TOKEN) throw new AdbException("Unexpected AUTH type " + m.arg0);
                    if (!signed) {
                        signed = true;
                        try {
                            send(AdbMessage.AUTH, AdbMessage.AUTH_SIGNATURE, 0, signer.sign(m.data));
                        } catch (GeneralSecurityException e) {
                            throw new AdbException("Couldn't sign with this phone's debugging key.", e);
                        }
                    } else if (!offeredKey) {
                        offeredKey = true;
                        send(AdbMessage.AUTH, AdbMessage.AUTH_RSAPUBLICKEY, 0,
                                AdbKeys.ascii(AdbKeys.publicKeyLine(signer.publicKey(), keyName) + "\0"));
                        if (listener != null) listener.waitingForAllow(AdbKeys.fingerprint(signer.publicKey()));
                        socket.setSoTimeout(allowTimeoutMs);
                    } else {
                        throw new AdbException("The TV refused this phone's debugging key.");
                    }
                    break;
                case AdbMessage.STLS:
                    throw new AdbException("This TV uses Wireless debugging with a pairing code, which isn't supported yet.");
                default:
                    break;   // anything else before CNXN is ignored
            }
        }
    }

    /** "device::ro.product.name=…;ro.product.model=…;…;features=…" as the TV announced itself. */
    public String banner() { return banner; }

    /** Runs a fixed shell command and returns its output (stdout and stderr together, at most 4 MB). */
    public String shell(String command) throws IOException {
        checkCommand(command);
        return new String(run("shell:" + command, null, 0, null), StandardCharsets.UTF_8);
    }

    /**
     * Installs or updates an APK streamed from [apk] ([length] bytes) with `cmd package install -S`.
     * Returns the installer's answer ("Success" or the failure line).
     */
    public String install(InputStream apk, long length, Progress progress) throws IOException {
        if (length <= 0 || length > 512L * 1024 * 1024) throw new AdbException("Bad APK size: " + length);
        String cmd = "exec:cmd package install -r -S " + length;
        return new String(run(cmd, apk, length, progress), StandardCharsets.UTF_8).trim();
    }

    private static void checkCommand(String c) {
        if (c == null || c.isEmpty() || c.indexOf('\n') >= 0 || c.indexOf('\r') >= 0 || c.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("bad command");
        }
    }

    private byte[] run(String service, InputStream input, long length, Progress progress) throws IOException {
        int local = nextLocalId++;
        send(AdbMessage.OPEN, local, 0, AdbKeys.ascii(service + "\0"));
        int remote = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // Wait for the stream to open.
        while (remote == 0) {
            AdbMessage m = AdbMessage.read(in);
            if (m.arg1 != local) continue;
            if (m.command == AdbMessage.OKAY) remote = m.arg0;
            else if (m.command == AdbMessage.CLSE) throw new AdbException("The TV refused: " + service.split(" ")[0]);
        }
        boolean closed = false;
        if (input != null) {
            byte[] buf = new byte[maxPayload];
            long sent = 0;
            while (sent < length && !closed) {
                int n = input.read(buf, 0, (int) Math.min(buf.length, length - sent));
                if (n < 0) throw new AdbException("The APK ended early.");
                byte[] chunk = n == buf.length ? buf.clone() : java.util.Arrays.copyOf(buf, n);
                send(AdbMessage.WRTE, local, remote, chunk);
                sent += n;
                if (progress != null) progress.sent(sent, length);
                // One write in flight: wait for the TV's OKAY, keeping any output it sends meanwhile.
                while (true) {
                    AdbMessage m = AdbMessage.read(in);
                    if (m.arg1 != local) continue;
                    if (m.command == AdbMessage.OKAY) break;
                    if (m.command == AdbMessage.WRTE) { append(output, m.data); send(AdbMessage.OKAY, local, remote, null); }
                    if (m.command == AdbMessage.CLSE) { closed = true; break; }
                }
            }
        }
        while (!closed) {
            AdbMessage m = AdbMessage.read(in);
            if (m.arg1 != local) continue;
            if (m.command == AdbMessage.WRTE) {
                append(output, m.data);
                send(AdbMessage.OKAY, local, remote, null);
            } else if (m.command == AdbMessage.CLSE) {
                closed = true;
            }
        }
        send(AdbMessage.CLSE, local, remote, null);
        return output.toByteArray();
    }

    private static void append(ByteArrayOutputStream out, byte[] data) throws AdbException {
        if (out.size() + data.length > 4 * 1024 * 1024) throw new AdbException("The TV's answer was too long.");
        out.write(data, 0, data.length);
    }

    private void send(int cmd, int a0, int a1, byte[] data) throws IOException {
        new AdbMessage(cmd, a0, a1, data).writeTo(out);
    }

    @Override
    public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }

    /** A failure the person can read. */
    public static final class AdbException extends IOException {
        public AdbException(String message) { super(message); }
        public AdbException(String message, Throwable cause) { super(message, cause); }
    }
}
