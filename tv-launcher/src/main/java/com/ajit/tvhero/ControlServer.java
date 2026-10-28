package com.ajit.tvhero;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A deliberately tiny HTTP/1.1 server for the phone app on the home network. One request per
 * connection, bodies up to 64 KB, two worker threads: enough for a remote, cheap on a 1 GB TV.
 * Security is not HTTP's job here: every request except /v1/info and pairing is signed (Api).
 */
final class ControlServer {
    static final String TAG = "TvHeroServer";
    private static final int MAX_BODY = 64 * 1024;

    interface Handler {
        Response handle(Request req);
    }

    static final class Request {
        String method, path, query = "", remote;
        /** SHA-256 of the client's TLS certificate, or null on the plain pairing port. */
        String peer;
        final Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }

        String param(String name) {
            for (String kv : query.split("&")) {
                int i = kv.indexOf('=');
                if (i > 0 && kv.substring(0, i).equals(name)) {
                    try { return java.net.URLDecoder.decode(kv.substring(i + 1), "UTF-8"); }
                    catch (Exception e) { return null; }
                }
            }
            return null;
        }
    }

    static final class Response {
        final int status;
        final String type;
        final byte[] body;

        Response(int status, String type, byte[] body) {
            this.status = status;
            this.type = type;
            this.body = body;
        }

        static Response json(int status, String json) {
            return new Response(status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private final Handler handler;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private ServerSocket server;
    private Thread acceptor;

    private final int port;
    private final javax.net.ssl.SSLContext tls;

    /** Plain HTTP on [port] (pairing only), or mutual TLS when [tls] is given. */
    ControlServer(Handler handler, int port, javax.net.ssl.SSLContext tls) {
        this.handler = handler;
        this.port = port;
        this.tls = tls;
    }

    synchronized void start() {
        if (acceptor != null) return;
        acceptor = new Thread(this::acceptLoop, "tvhero-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    synchronized void stop() {
        Thread t = acceptor;
        acceptor = null;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        if (t != null) t.interrupt();
        pool.shutdownNow();
    }

    private void acceptLoop() {
        try {
            if (tls != null) {
                javax.net.ssl.SSLServerSocket ss = (javax.net.ssl.SSLServerSocket) tls.getServerSocketFactory().createServerSocket();
                ss.setNeedClientAuth(true);
                server = ss;
            } else {
                server = new ServerSocket();
            }
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            Log.i(TAG, "listening on " + port + (tls != null ? " (TLS)" : ""));
            while (!Thread.currentThread().isInterrupted()) {
                Socket s = server.accept();
                s.setSoTimeout(10_000);
                pool.execute(() -> serve(s));
            }
        } catch (IOException e) {
            if (acceptor != null) Log.w(TAG, "server stopped: " + e.getMessage());
        }
    }

    private void serve(Socket s) {
        try (Socket sock = s) {
            InputStream in = new BufferedInputStream(sock.getInputStream());
            Request req = new Request();
            req.remote = sock.getInetAddress().getHostAddress();
            if (sock instanceof javax.net.ssl.SSLSocket) {
                javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket) sock;
                com.ajit.tvhero.protocol.Tls.restrict(ssl);
                ssl.startHandshake();   // fails for any phone that isn't paired: the connection just closes
                java.security.cert.Certificate[] peer = ssl.getSession().getPeerCertificates();
                req.peer = com.ajit.tvhero.protocol.Tls.fingerprint((java.security.cert.X509Certificate) peer[0]);
            }
            String line = readLine(in);
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) { write(sock.getOutputStream(), Response.json(400, "{\"error\":\"bad request\"}")); return; }
            req.method = parts[0].toUpperCase(Locale.ROOT);
            String target = parts[1];
            int q = target.indexOf('?');
            req.path = q >= 0 ? target.substring(0, q) : target;
            if (q >= 0) req.query = target.substring(q + 1);
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                int c = h.indexOf(':');
                if (c > 0) req.headers.put(h.substring(0, c).trim().toLowerCase(Locale.ROOT), h.substring(c + 1).trim());
            }
            int len = 0;
            try { len = Integer.parseInt(req.headers.getOrDefault("content-length", "0")); } catch (NumberFormatException ignored) { }
            if (len < 0 || len > MAX_BODY) { write(sock.getOutputStream(), Response.json(413, "{\"error\":\"too large\"}")); return; }
            byte[] body = new byte[len];
            int n = 0, r;
            while (n < len && (r = in.read(body, n, len - n)) > 0) n += r;
            req.body = body;
            Response resp;
            try {
                resp = handler.handle(req);
            } catch (RuntimeException e) {
                Log.w(TAG, "handler failed for " + req.path, e);
                resp = Response.json(500, "{\"error\":\"internal\"}");
            }
            write(sock.getOutputStream(), resp);
        } catch (IOException e) {
            Log.d(TAG, "connection: " + e.getMessage());
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
            if (buf.size() > 8192) throw new IOException("line too long");
        }
        if (b < 0 && buf.size() == 0) return null;
        return buf.toString("UTF-8");
    }

    private static void write(OutputStream out, Response r) throws IOException {
        String head = "HTTP/1.1 " + r.status + " " + reason(r.status) + "\r\n"
                + "Content-Type: " + r.type + "\r\n"
                + "Content-Length: " + r.body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(r.body);
        out.flush();
    }

    private static String reason(int s) {
        switch (s) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 413: return "Payload Too Large";
            case 423: return "Locked";
            case 429: return "Too Many Requests";
            default: return s >= 500 ? "Server Error" : "Status";
        }
    }
}
