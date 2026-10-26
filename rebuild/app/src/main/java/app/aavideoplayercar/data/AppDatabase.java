package app.aavideoplayercar.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {MediaEntry.class, MaintenanceRecord.class, DataImport.class, RadioStation.class, PodcastFeed.class, PodcastEpisode.class, IptvPlaylist.class, IptvChannel.class, EpgSource.class, EpgProgramme.class, XtreamAccount.class, IptvSeries.class, IptvSeriesEpisode.class, NewsSource.class, NewsArticle.class, MovieAddon.class, MovieVideo.class, MovieCacheEntry.class}, version = 14, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MediaDao media();
    public abstract RadioDao radio();
    public abstract PodcastDao podcasts();
    public abstract IptvDao iptv();
    public abstract EpgDao epg();
    public abstract XtreamDao xtream();
    public abstract NewsDao news();
    public abstract MovieAddonDao movieAddons();
    public abstract MovieVideoDao movieVideos();
    public abstract MovieCacheDao movieCache();
    public static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE movie_videos ADD COLUMN providerType TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE movie_videos ADD COLUMN providerVideoId TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE movie_videos ADD COLUMN streamFingerprint TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE movie_videos ADD COLUMN streamIndex INTEGER NOT NULL DEFAULT -1");
        }
    };
    public static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS movie_cache (id TEXT NOT NULL PRIMARY KEY, addonId TEXT NOT NULL, payload TEXT NOT NULL, bytes INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(addonId) REFERENCES movie_addons(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_movie_cache_addonId ON movie_cache(addonId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_movie_cache_updatedAt ON movie_cache(updatedAt)");
        }
    };
    public static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS movie_videos (id TEXT NOT NULL PRIMARY KEY, addonId TEXT NOT NULL, title TEXT NOT NULL, source TEXT NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, lastPlayedAt INTEGER NOT NULL, FOREIGN KEY(addonId) REFERENCES movie_addons(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_movie_videos_addonId ON movie_videos(addonId)");
        }
    };
    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS movie_addons (id TEXT NOT NULL PRIMARY KEY, endpoint TEXT NOT NULL, providerId TEXT NOT NULL, title TEXT NOT NULL, version TEXT NOT NULL, updatedAt INTEGER NOT NULL)");
        }
    };
    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS news_sources (id TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL, updatedAt INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS news_articles (id TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, url TEXT NOT NULL, publishedAt INTEGER NOT NULL, `read` INTEGER NOT NULL, FOREIGN KEY(sourceId) REFERENCES news_sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_news_articles_sourceId_publishedAt ON news_articles(sourceId,publishedAt)");
        }
    };
    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS iptv_series (id TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL, providerId TEXT NOT NULL, title TEXT NOT NULL, category TEXT NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(playlistId) REFERENCES iptv_playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_iptv_series_playlistId ON iptv_series(playlistId)");
            db.execSQL("CREATE TABLE IF NOT EXISTS iptv_series_episodes (channelId TEXT NOT NULL PRIMARY KEY, seriesId TEXT NOT NULL, season INTEGER NOT NULL, number INTEGER NOT NULL, FOREIGN KEY(seriesId) REFERENCES iptv_series(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(channelId) REFERENCES iptv_channels(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_iptv_series_episodes_seriesId_season_number ON iptv_series_episodes(seriesId,season,number)");
        }
    };
    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS xtream_accounts (playlistId TEXT NOT NULL PRIMARY KEY, server TEXT NOT NULL, credentials TEXT NOT NULL, FOREIGN KEY(playlistId) REFERENCES iptv_playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        }
    };
    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS epg_sources (playlistId TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(playlistId) REFERENCES iptv_playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE TABLE IF NOT EXISTS epg_programmes (id TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL, channelId TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER NOT NULL, FOREIGN KEY(playlistId) REFERENCES iptv_playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programmes_playlistId_channelId_startMs ON epg_programmes(playlistId,channelId,startMs)");
        }
    };
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE iptv_channels ADD COLUMN playbackMode INTEGER NOT NULL DEFAULT 0");
        }
    };
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS iptv_playlists (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, url TEXT NOT NULL, updatedAt INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS iptv_channels (id TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL, groupName TEXT NOT NULL, tvgId TEXT NOT NULL, live INTEGER NOT NULL, favorite INTEGER NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, lastPlayedAt INTEGER NOT NULL, FOREIGN KEY(playlistId) REFERENCES iptv_playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_iptv_channels_playlistId ON iptv_channels(playlistId)");
        }
    };
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS podcast_feeds (id TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL, updatedAt INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS podcast_episodes (id TEXT NOT NULL PRIMARY KEY, feedId TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL, mime TEXT NOT NULL, publishedAt INTEGER NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, lastPlayedAt INTEGER NOT NULL, FOREIGN KEY(feedId) REFERENCES podcast_feeds(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_podcast_episodes_feedId ON podcast_episodes(feedId)");
        }
    };
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS radio_stations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, url TEXT NOT NULL, country TEXT NOT NULL, codec TEXT NOT NULL, hls INTEGER NOT NULL, favorite INTEGER NOT NULL, updatedAt INTEGER NOT NULL)");
        }
    };
    public abstract MaintenanceDao maintenance();
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS maintenance_records (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, type TEXT NOT NULL, serviceDay INTEGER, serviceKm INTEGER, dueDay INTEGER, dueKm INTEGER, notes TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS data_imports (name TEXT NOT NULL PRIMARY KEY)");
        }
    };
}
