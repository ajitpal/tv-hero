package com.ajit.tvhero.adb;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** One ADB packet: a 24-byte little-endian header and an optional payload (see AOSP adb/protocol.txt). */
final class AdbMessage {
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257, STLS = 0x534c5453;
    static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    /** Protocol version that lets either side skip the payload checksum; we still send it. */
    static final int VERSION = 0x01000001;
    /** Largest payload we accept from the TV. Newer adbd negotiates up to 1 MB; we keep it modest for phones. */
    static final int MAX_PAYLOAD = 256 * 1024;

    final int command, arg0, arg1;
    final byte[] data;

    AdbMessage(int command, int arg0, int arg1, byte[] data) {
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.data = data == null ? new byte[0] : data;
    }

    byte[] encode() {
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(checksum(data)).putInt(~command);
        b.put(data);
        return b.array();
    }

    void writeTo(OutputStream out) throws IOException {
        out.write(encode());
        out.flush();
    }

    static AdbMessage read(DataInputStream in) throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = b.getInt(), a0 = b.getInt(), a1 = b.getInt(), len = b.getInt(), sum = b.getInt(), magic = b.getInt();
        if (magic != ~cmd) throw new IOException("not an ADB packet (bad magic)");
        if (len < 0 || len > 1024 * 1024) throw new IOException("ADB packet too large: " + len);
        byte[] data = new byte[len];
        in.readFully(data);
        if (sum != 0 && sum != checksum(data)) throw new IOException("ADB packet checksum mismatch");
        return new AdbMessage(cmd, a0, a1, data);
    }

    static int checksum(byte[] data) {
        int s = 0;
        for (byte x : data) s += x & 0xff;
        return s;
    }

    static String name(int cmd) {
        switch (cmd) {
            case CNXN: return "CNXN";
            case AUTH: return "AUTH";
            case OPEN: return "OPEN";
            case OKAY: return "OKAY";
            case CLSE: return "CLSE";
            case WRTE: return "WRTE";
            case STLS: return "STLS";
            default: return String.format("0x%08x", cmd);
        }
    }
}
