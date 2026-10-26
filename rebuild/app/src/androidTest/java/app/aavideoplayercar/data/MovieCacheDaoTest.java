package app.aavideoplayercar.data;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MovieCacheDaoTest {
    @Test public void prunesOldEntriesRejectsOversizeAndCascadesWithAddon() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            MovieAddon addon = new MovieAddon(); addon.id = UUID.randomUUID().toString(); addon.endpoint = "encrypted-fixture"; addon.providerId = "fixture"; addon.title = "Fixture"; addon.version = "1"; addon.updatedAt = 1;
            assertTrue(db.movieAddons().save(addon, true));
            for (int i=0; i<33; i++) {
                MovieCacheEntry entry = new MovieCacheEntry(); entry.id = String.format(java.util.Locale.ROOT, "%064x", i); entry.addonId = addon.id; entry.payload = "result"; entry.bytes = 6; entry.updatedAt = i;
                db.movieCache().save(entry);
            }
            assertNull(db.movieCache().get(String.format(java.util.Locale.ROOT, "%064x", 0), addon.id));
            assertNotNull(db.movieCache().get(String.format(java.util.Locale.ROOT, "%064x", 32), addon.id));
            MovieCacheEntry tooLarge = new MovieCacheEntry(); tooLarge.id = String.format(java.util.Locale.ROOT, "%064x", 33); tooLarge.addonId = addon.id; tooLarge.payload = "x".repeat(1048577); tooLarge.bytes = 1048577; tooLarge.updatedAt = 34;
            db.movieCache().save(tooLarge); assertNull(db.movieCache().get(tooLarge.id, addon.id));
            db.movieAddons().delete(addon.id);
            assertNull(db.movieCache().get(String.format(java.util.Locale.ROOT, "%064x", 32), addon.id));
        } finally { db.close(); }
    }
}
