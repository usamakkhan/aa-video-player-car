package app.aavideoplayercar;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

/** Small real HTTP fixture, including byte ranges needed for media resume tests. */
final class LocalHttpFixture implements AutoCloseable {
    private final ServerSocket server;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Response> routes = new ConcurrentHashMap<>();
    final java.util.Queue<Integer> ranges = new ConcurrentLinkedQueue<>();
    private static final class Response {
        final byte[] bytes; final String type, extra; final int status; final long declaredLength; final int delay;
        Response(byte[] bytes, String type, String extra, int status) { this(bytes, type, extra, status, bytes.length, 0); }
        Response(byte[] bytes, String type, String extra, int status, long declaredLength, int delay) {
            this.bytes = bytes; this.type = type; this.extra = extra; this.status = status; this.declaredLength = declaredLength; this.delay = delay;
        }
    }
    LocalHttpFixture() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        worker.submit(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String request = reader.readLine(); if (request == null) continue;
                    String path = request.split(" ")[1]; int offset = 0; String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase(java.util.Locale.ROOT).startsWith("range: bytes=")) offset = Integer.parseInt(line.substring(line.indexOf('=') + 1).split("-")[0]);
                    }
                    Response response = routes.getOrDefault(path, new Response(new byte[0], "text/plain", "", 404));
                    ranges.add(offset);
                    int start = Math.min(offset, response.bytes.length), length = response.bytes.length - start;
                    int status = offset > 0 && response.status == 200 ? 206 : response.status;
                    String headers = "HTTP/1.1 " + status + " Fixture\r\nContent-Type: " + response.type + "\r\nContent-Length: " + (response.declaredLength - start)
                        + "\r\nAccept-Ranges: bytes\r\nConnection: close\r\n" + response.extra
                        + (status == 206 ? "Content-Range: bytes " + start + "-" + (response.bytes.length - 1) + "/" + response.bytes.length + "\r\n" : "") + "\r\n";
                    OutputStream out = socket.getOutputStream(); out.write(headers.getBytes(StandardCharsets.US_ASCII));
                    for (int position = start; position < response.bytes.length; position += 32768) {
                        out.write(response.bytes, position, Math.min(32768, response.bytes.length - position)); out.flush();
                        if (response.delay > 0) Thread.sleep(response.delay);
                    }
                } catch (Exception ignored) { /* Cancellation may close a socket while its response is being written. */ }
            }
        });
    }
    String url(String path) { return "http://127.0.0.1:" + server.getLocalPort() + path; }
    void add(String path, byte[] bytes, String type) { routes.put(path, new Response(bytes, type, "", 200)); }
    void error(String path, int status) { routes.put(path, new Response(new byte[0], "text/plain", "", status)); }
    void slow(String path, byte[] bytes) { routes.put(path, new Response(bytes, "audio/mpeg", "", 200, bytes.length, 20)); }
    void oversized(String path, long length) { routes.put(path, new Response(new byte[1], "audio/mpeg", "", 200, length, 0)); }
    void gzip(String path, byte[] bytes, String type) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(data)) { gzip.write(bytes); }
        routes.put(path, new Response(data.toByteArray(), type, "Content-Encoding: gzip\r\n", 200));
    }
    void redirect(String path, String target) { routes.put(path, new Response(new byte[0], "text/plain", "Location: " + target + "\r\n", 302)); }
    @Override public void close() throws IOException { server.close(); worker.shutdownNow(); }
}
