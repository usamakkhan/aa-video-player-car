package app.aavideoplayercar.data;
import androidx.room.*;
import java.util.*;

@Dao public abstract class NewsDao {
    public static final int MAX_SOURCES = 100, MAX_ARTICLES = 2000;
    public static final long MAX_TEXT = 8L * 1024 * 1024;
    @Query("SELECT * FROM news_sources ORDER BY title COLLATE NOCASE,id LIMIT :limit OFFSET :offset") public abstract List<NewsSource> sources(int limit, int offset);
    @Query("SELECT COUNT(*) FROM news_sources") public abstract int sourceCount();
    @Query("SELECT * FROM news_sources WHERE id=:id") public abstract NewsSource source(String id);
    @Query("SELECT * FROM news_articles WHERE id=:id") public abstract NewsArticle article(String id);
    @Query("SELECT * FROM news_articles WHERE sourceId=:source AND (:unread=0 OR `read`=0) AND instr(lower(title),lower(:query))>0 ORDER BY publishedAt DESC,id LIMIT :limit OFFSET :offset")
    public abstract List<NewsArticle> articles(String source, String query, boolean unread, int limit, int offset);
    @Query("SELECT COUNT(*) FROM news_articles WHERE sourceId=:source AND (:unread=0 OR `read`=0) AND instr(lower(title),lower(:query))>0")
    public abstract int count(String source, String query, boolean unread);
    @Query("UPDATE news_articles SET `read`=:read WHERE id=:id") public abstract void setRead(String id, boolean read);
    @Query("DELETE FROM news_sources WHERE id=:id") public abstract void delete(String id);
    @Query("DELETE FROM news_articles WHERE sourceId=:id") protected abstract void clear(String id);
    @Query("SELECT COALESCE(SUM(length(CAST(title AS BLOB))+length(CAST(body AS BLOB))+length(CAST(url AS BLOB))),0) FROM news_articles WHERE sourceId!=:excluded") protected abstract long otherText(String excluded);
    @Upsert protected abstract void saveSource(NewsSource source);
    @Insert protected abstract void insert(List<NewsArticle> articles);

    /** A bounded snapshot refresh preserves read flags for retained IDs and never resurrects removed sources. */
    @Transaction public boolean replace(NewsSource source, List<NewsArticle> rows, boolean adding) {
        NewsSource existing = source(source.id);
        if (existing == null && !adding) return false;
        if (source.id.isEmpty() || source.title.isEmpty() || source.title.length() > 500 || source.url.length() > 8192 || rows.size() > MAX_ARTICLES
            || (existing == null && sourceCount() >= MAX_SOURCES)) throw new IllegalArgumentException("News cache limit or invalid source");
        Map<String, NewsArticle> prior = new HashMap<>(); for (NewsArticle row : articles(source.id, "", false, MAX_ARTICLES, 0)) prior.put(row.id, row);
        Set<String> seen = new HashSet<>(); long text = otherText(source.id);
        for (NewsArticle row : rows) {
            if (!source.id.equals(row.sourceId) || row.id.isEmpty() || !seen.add(row.id) || row.title.isEmpty() || row.title.length() > 500 || row.body.length() > 64000 || row.url.length() > 8192) throw new IllegalArgumentException("Invalid news article");
            text += (long)row.title.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + row.body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + row.url.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (text > MAX_TEXT) throw new IllegalArgumentException("News text cache is full");
            NewsArticle old = prior.get(row.id); row.read = old != null && old.read;
        }
        saveSource(source); clear(source.id); insert(rows); return true;
    }
}
