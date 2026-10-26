package app.aavideoplayercar;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.movies.*;
import app.aavideoplayercar.network.HttpDocument;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AddonRegistrationTest {
    private static final String MANIFEST = "{\"id\":\"org.fixture\",\"name\":\"Fixture\",\"version\":\"1.0.0\",\"types\":[\"movie\"],\"resources\":[\"stream\"],\"catalogs\":[]}";
    @Test public void encryptedRegistrationReopensDeduplicatesRotatesAndRejectsSwappedCiphertext() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext(); String name = "addon-" + UUID.randomUUID() + ".db";
        try (var server = new LocalHttpFixture()) {
            server.add("/private-token/manifest.json", MANIFEST.getBytes(StandardCharsets.UTF_8), "application/json");
            server.add("/rotated-token/manifest.json", MANIFEST.replace("Fixture", "Renamed").getBytes(StandardCharsets.UTF_8), "application/json");
            var manifest = AddonClient.manifest(new HttpDocument(), server.url("/private-token/manifest.json"));
            String id;
            { var db = Room.databaseBuilder(context, AppDatabase.class, name).build(); try {
                id = AddonRegistration.save(db.movieAddons(), manifest, null);
                assertEquals(id, AddonRegistration.save(db.movieAddons(), manifest, null)); assertEquals(1, db.movieAddons().count());
                var stored = db.movieAddons().get(id); assertFalse(stored.endpoint.contains("private-token")); assertFalse(stored.toString().contains("private-token"));
                assertEquals(manifest.url, AddonRegistration.endpoint(stored)); stored.id = UUID.randomUUID().toString();
                assertThrows(java.io.IOException.class, () -> AddonRegistration.endpoint(stored));
            } finally { db.close(); } }
            { var db = Room.databaseBuilder(context, AppDatabase.class, name).build(); try {
                assertEquals(manifest.url, AddonRegistration.endpoint(db.movieAddons().get(id)));
                var rotated = AddonClient.manifest(new HttpDocument(), server.url("/rotated-token/manifest.json"));
                assertEquals(id, AddonRegistration.save(db.movieAddons(), rotated, id)); assertEquals("Renamed", db.movieAddons().get(id).title);
                assertEquals(rotated.url, AddonRegistration.endpoint(db.movieAddons().get(id))); assertEquals(1, db.movieAddons().count());
                db.movieAddons().delete(id); assertNull(AddonRegistration.save(db.movieAddons(), rotated, id)); assertEquals(0, db.movieAddons().count());
            } finally { db.close(); } }
        } finally { context.deleteDatabase(name); }
    }
    @Test public void registrationBoundsAndInvalidRefreshPreserveExistingRows() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build(); try {
            MovieAddon kept = null;
            for (int i = 0; i < MovieAddonDao.MAX_ADDONS; i++) {
                var a = new MovieAddon(); a.id = UUID.randomUUID().toString(); a.endpoint = "cipher-fixture"; a.providerId = "org.fixture"; a.title = "Addon " + i; a.version = "1.0.0";
                assertTrue(db.movieAddons().save(a, true)); kept = a;
            }
            var extra = new MovieAddon(); extra.id = UUID.randomUUID().toString(); extra.endpoint = "cipher"; extra.providerId = "org.extra"; extra.title = "Extra"; extra.version = "1";
            assertThrows(IllegalArgumentException.class, () -> db.movieAddons().save(extra, true));
            assertEquals(50, db.movieAddons().all().size()); String id = kept.id; kept.title = "Updated"; assertTrue(db.movieAddons().save(kept, false));
            kept.title = "x".repeat(501); final MovieAddon invalid = kept;
            assertThrows(IllegalArgumentException.class, () -> db.movieAddons().save(invalid, false)); assertEquals("Updated", db.movieAddons().get(id).title);
        } finally { db.close(); }
    }
}
