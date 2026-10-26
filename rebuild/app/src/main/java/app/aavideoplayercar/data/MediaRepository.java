package app.aavideoplayercar.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.room.Room;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

/** Database and document-provider operations run on this bounded serial worker, never the UI thread. */
public final class MediaRepository {
    private static volatile MediaRepository instance;
    public final ListeningExecutorService worker = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    public final MediaDao dao;
    public final AppDatabase database;
    private final Context context;

    private MediaRepository(Context context) {
        this.context = context.getApplicationContext();
        database = Room.databaseBuilder(this.context, AppDatabase.class, "aa-player.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14).build();
        dao = database.media();
    }
    public static MediaRepository get(Context context) {
        if (instance == null) synchronized (MediaRepository.class) {
            if (instance == null) instance = new MediaRepository(context);
        }
        return instance;
    }
    /** Caller holds a readable document grant; throws if the provider revoked it or the file disappeared. */
    public MediaEntry importDocument(Uri uri, String fallbackMime) throws java.io.IOException {
        String mime = context.getContentResolver().getType(uri);
        if (mime == null) mime = fallbackMime;
        if (mime == null || (!mime.startsWith("audio/") && !mime.startsWith("video/")))
            throw new IllegalArgumentException("Choose an audio or video file.");
        try (android.os.ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) throw new java.io.FileNotFoundException("File cannot be opened");
        }
        String title = uri.getLastPathSegment();
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) title = cursor.getString(0);
        }
        MediaEntry entry = new MediaEntry();
        entry.id = UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8)).toString();
        entry.uri = uri.toString(); entry.title = title == null ? "Selected media" : title;
        entry.mime = mime; entry.addedAt = System.currentTimeMillis();
        dao.insert(entry);
        return dao.get(entry.id);
    }
}
