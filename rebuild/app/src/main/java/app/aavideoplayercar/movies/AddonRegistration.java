package app.aavideoplayercar.movies;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.iptv.AccountSecrets;
import java.io.IOException;
import java.util.UUID;

/** Repository-worker operations. The manifest must first be fetched on a network worker. */
public final class AddonRegistration {
    private AddonRegistration() {}
    private static String scope(String id) { return "movies-addon-url-v1:" + id; }
    public static String endpoint(MovieAddon addon) throws IOException {
        return AddonClient.manifestUrl(AccountSecrets.decrypt(scope(addon.id), addon.endpoint));
    }
    /** Add explicitly, or refresh/replace an existing ID. Registration of the same URL is idempotent. */
    public static String save(MovieAddonDao dao, AddonClient.Manifest manifest, String existingId) throws IOException {
        String id = existingId;
        if (id == null) {
            for (MovieAddon saved : dao.all()) {
                try { if (endpoint(saved).equals(manifest.url)) { id = saved.id; break; } }
                catch (IOException unavailable) { /* A lost key must not prevent managing other registrations. */ }
            }
            if (id == null) id = UUID.randomUUID().toString();
        } else if (dao.get(id) == null) return null;
        MovieAddon addon = new MovieAddon(); addon.id = id;
        addon.endpoint = AccountSecrets.encrypt(scope(id), manifest.url);
        addon.providerId = manifest.id; addon.title = manifest.name; addon.version = manifest.version; addon.updatedAt = System.currentTimeMillis();
        return dao.save(addon, existingId == null) ? id : null;
    }
}
