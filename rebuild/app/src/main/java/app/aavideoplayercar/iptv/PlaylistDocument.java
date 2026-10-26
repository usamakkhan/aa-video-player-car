package app.aavideoplayercar.iptv;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.CancellationSignal;
import app.aavideoplayercar.network.HttpDocument;
import java.io.*;

/** Reads only a granted document, without copying it into shared storage. */
public final class PlaylistDocument {
    private final CancellationSignal cancellation = new CancellationSignal();
    private volatile InputStream input;
    public byte[] read(ContentResolver resolver, Uri uri) throws IOException {
        if (!"content".equalsIgnoreCase(uri.getScheme())) throw new IOException("Choose a document");
        cancellation.throwIfCanceled();
        try (var descriptor = resolver.openAssetFileDescriptor(uri, "r", cancellation)) {
            if (descriptor == null) throw new FileNotFoundException("Document unavailable");
            if (descriptor.getDeclaredLength() > HttpDocument.MAX_BYTES) throw new IOException("Playlist too large");
            try (InputStream stream = descriptor.createInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                input = stream; cancellation.throwIfCanceled();
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    cancellation.throwIfCanceled();
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                    if (bytes.size() + count > HttpDocument.MAX_BYTES) throw new IOException("Playlist too large");
                    bytes.write(buffer, 0, count);
                }
                return bytes.toByteArray();
            } finally { input = null; }
        }
    }
    public void cancel() {
        cancellation.cancel(); InputStream stream = input;
        if (stream != null) try { stream.close(); } catch (IOException ignored) { }
    }
}
