package app.aavideoplayercar.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/** Cancellable bounded document fetch. Call on a network worker, never on the UI/database worker. */
public final class HttpDocument {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    public static final class Result {
        public final byte[] bytes;
        public final String url;
        Result(byte[] bytes, String url) { this.bytes = bytes; this.url = url; }
    }
    public static String url(String value) throws IOException {
        try {
            URI uri = new URI(value.trim()).normalize();
            if (value.length() > 8192 || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) throw new IOException("Use an HTTP or HTTPS feed URL without embedded credentials.");
            String result = uri.toASCIIString(); int fragment = result.indexOf('#');
            return fragment < 0 ? result : result.substring(0, fragment);
        } catch (java.net.URISyntaxException error) { throw new IOException("Invalid feed URL.", error); }
    }
    public void cancel() { cancelled = true; HttpURLConnection current = connection; if (current != null) current.disconnect(); }
    public Result get(String input) throws IOException {
        return get(input, "application/rss+xml, application/atom+xml, application/xml, text/xml");
    }
    public Result get(String input, String accept) throws IOException {
        return get(input, accept, true);
    }
    /** Account endpoints must not forward credential-bearing requests through redirects. */
    public Result getWithoutRedirects(String input, String accept) throws IOException { return get(input, accept, false); }
    private Result get(String input, String accept, boolean redirects) throws IOException {
        String target = url(input);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (cancelled || Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new IOException("Feed request cancelled or timed out.");
            HttpURLConnection active = (HttpURLConnection) URI.create(target).toURL().openConnection(); connection = active;
            try {
                if (cancelled) throw new IOException("Feed request cancelled.");
                active.setConnectTimeout(8000); active.setReadTimeout(8000); active.setInstanceFollowRedirects(false);
                active.setRequestProperty("User-Agent", "AA-Video-Player-Car/0.1");
                active.setRequestProperty("Accept", accept);
                active.setRequestProperty("Accept-Encoding", "gzip");
                int status = active.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    if (!redirects) throw new IOException("Account endpoint redirects are unsupported");
                    String location = active.getHeaderField("Location");
                    if (location == null) throw new IOException("Feed redirect is missing its URL.");
                    target = url(URI.create(target).resolve(location).toString()); continue;
                }
                if (status != 200) throw new IOException("Feed server returned HTTP " + status + ".");
                try (InputStream raw = active.getInputStream(); InputStream in = "gzip".equalsIgnoreCase(active.getContentEncoding()) ? new GZIPInputStream(raw) : raw;
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (cancelled || Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new IOException("Feed request cancelled or timed out.");
                        if (out.size() + read > MAX_BYTES) throw new IOException("Feed exceeds the 4 MiB limit.");
                        out.write(buffer, 0, read);
                    }
                    return new Result(out.toByteArray(), target);
                }
            } catch (IllegalArgumentException error) { throw new IOException("Invalid feed redirect.", error); }
            finally { active.disconnect(); connection = null; }
        }
        throw new IOException("Too many feed redirects.");
    }
}
