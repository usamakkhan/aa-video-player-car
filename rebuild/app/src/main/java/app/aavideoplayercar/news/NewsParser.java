package app.aavideoplayercar.news;

import android.text.Html;
import android.util.Xml;
import app.aavideoplayercar.network.HttpDocument;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** RSS 2.0 / Atom articles. Feed markup is reduced to text, never executed or fetched. */
public final class NewsParser {
    private static final String ATOM = "http://www.w3.org/2005/Atom", XML = "http://www.w3.org/XML/1998/namespace";
    private static final String CONTENT = "http://purl.org/rss/1.0/modules/content/";
    public static final class Article {
        public String id = "", title = "", body = "", url = "";
        public long publishedAt;
    }
    public static final class Result {
        public final String id, url, title;
        public final List<Article> articles;
        public final int skipped;
        Result(String id, String url, String title, List<Article> articles, int skipped) {
            this.id = id; this.url = url; this.title = title; this.articles = Collections.unmodifiableList(articles); this.skipped = skipped;
        }
    }
    private static String identity(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString(); }
    private static String link(URI base, String value) {
        try { return value == null || value.trim().isEmpty() ? "" : HttpDocument.url(base.resolve(value.trim()).toString()); }
        catch (Exception invalid) { return ""; }
    }
    public static Result parse(byte[] bytes, String source, String documentUrl) throws IOException {
        if (bytes.length > HttpDocument.MAX_BYTES) throw new IOException("News feed exceeds 4 MiB");
        String url = HttpDocument.url(source), feedId = identity(url), title = "";
        List<Article> articles = new ArrayList<>(); Set<String> seen = new HashSet<>();
        ArrayDeque<URI> bases = new ArrayDeque<>(); bases.push(URI.create(HttpDocument.url(documentUrl)));
        try {
            var parser = Xml.newPullParser(); parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
            parser.setInput(new ByteArrayInputStream(bytes), null);
            boolean atom = false, root = false, closed = false, channel = false, channelSeen = false;
            Article article = null; String guid = "", summary = "", content = ""; int itemDepth = -1, count = 0, skipped = 0;
            for (int token = parser.nextToken(); token != XmlPullParser.END_DOCUMENT; token = parser.nextToken()) {
                check(parser, token);
                if (token == XmlPullParser.START_TAG) {
                    int depth = parser.getDepth(); String name = parser.getName(), ns = parser.getNamespace();
                    String base = parser.getAttributeValue(XML, "base"); bases.push(base == null ? bases.peek() : bases.peek().resolve(base));
                    if (depth == 1) {
                        if (root) throw new IOException("Multiple feed roots");
                        atom = name.equals("feed") && ns.equals(ATOM); root = atom || (name.equals("rss") && ns.isEmpty());
                        if (!root) throw new IOException("Not an RSS or Atom feed");
                    }
                    boolean standard = atom ? ns.equals(ATOM) : ns.isEmpty();
                    if (!atom && depth == 2 && standard && name.equals("channel")) {
                        if (channelSeen) throw new IOException("Multiple RSS channels"); channelSeen = true; channel = true;
                    }
                    if (standard && ((atom && depth == 2 && name.equals("entry")) || (!atom && channel && depth == 3 && name.equals("item")))) {
                        if (++count > 2000) throw new IOException("News feed exceeds 2,000 articles");
                        article = new Article(); itemDepth = depth; guid = ""; summary = ""; content = "";
                    } else if (article == null && standard && name.equals("title") && depth == (atom ? 2 : 3) && (atom || channel)) {
                        title = text(parser, true, 500); bases.pop();
                    } else if (article != null && depth == itemDepth + 1) {
                        if (standard && name.equals("title")) { article.title = text(parser, true, 500); bases.pop(); }
                        else if (standard && name.equals(atom ? "id" : "guid")) { guid = raw(parser, 8192); bases.pop(); }
                        else if (standard && (name.equals("pubDate") || name.equals("published") || (name.equals("updated") && article.publishedAt == 0))) { article.publishedAt = date(raw(parser, 200)); bases.pop(); }
                        else if (standard && name.equals("link")) {
                            if (atom) {
                                String rel = parser.getAttributeValue(null, "rel"), type = parser.getAttributeValue(null, "type");
                                if (article.url.isEmpty() && (rel == null || rel.equals("alternate")) && (type == null || type.equals("text/html") || type.equals("application/xhtml+xml"))) article.url = link(bases.peek(), parser.getAttributeValue(null, "href"));
                            } else { article.url = link(bases.peek(), raw(parser, 8192)); bases.pop(); }
                        } else if (standard && (name.equals("description") || name.equals("summary"))) { summary = text(parser, atom, 64000); bases.pop(); }
                        else if ((atom && standard && name.equals("content")) || (!atom && ns.equals(CONTENT) && name.equals("encoded"))) {
                            if (!atom || parser.getAttributeValue(null, "src") == null) { content = text(parser, atom, 64000); bases.pop(); }
                        }
                    }
                } else if (token == XmlPullParser.END_TAG) {
                    if (article != null && parser.getDepth() == itemDepth) {
                        article.body = content.isEmpty() ? summary : content;
                        if (article.title.isEmpty() && !article.body.isEmpty()) article.title = article.body.substring(0, Math.min(100, article.body.length()));
                        if (!article.title.isEmpty()) {
                            String key = !guid.isEmpty() ? guid : !article.url.isEmpty() ? article.url : article.title + "\n" + article.publishedAt + "\n" + article.body;
                            article.id = identity(feedId + "\n" + key);
                            if (seen.add(article.id)) articles.add(article); else skipped++;
                        } else skipped++;
                        article = null;
                    }
                    if (!atom && parser.getDepth() == 2 && parser.getName().equals("channel")) channel = false;
                    if (parser.getDepth() == 1) closed = true;
                    bases.pop();
                }
            }
            if (!root || !closed || bases.size() != 1 || (!atom && !channelSeen)) throw new IOException("Incomplete news feed");
            return new Result(feedId, url, title.isEmpty() ? URI.create(url).getHost() : title, articles, skipped);
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("Invalid news feed"); }
    }
    private static void check(XmlPullParser parser, int token) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("News parsing cancelled");
        if (token == XmlPullParser.DOCDECL || parser.getDepth() > 32 || (token == XmlPullParser.ENTITY_REF && parser.getText() == null)) throw new IOException("Unsupported news XML");
    }
    private static String text(XmlPullParser parser, boolean atom, int max) throws Exception {
        String type = parser.getAttributeValue(null, "type");
        boolean html = !atom || "html".equals(type) || "text/html".equals(type);
        boolean supported = !atom || type == null || type.equals("text") || type.equals("text/plain") || type.equals("xhtml") || type.equals("application/xhtml+xml") || html;
        String result = raw(parser, 64000);
        if (!supported) return "";
        if (html) {
            result = result.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>", "");
            result = Html.fromHtml(result, Html.FROM_HTML_MODE_COMPACT).toString();
        }
        result = result.trim(); return result.substring(0, Math.min(max, result.length()));
    }
    private static String raw(XmlPullParser parser, int max) throws Exception {
        int depth = parser.getDepth(), suppressed = -1; StringBuilder value = new StringBuilder();
        for (int token = parser.nextToken(); !(token == XmlPullParser.END_TAG && parser.getDepth() == depth); token = parser.nextToken()) {
            check(parser, token); if (token == XmlPullParser.END_DOCUMENT) throw new IOException("Incomplete article");
            if (token == XmlPullParser.START_TAG && (parser.getName().equalsIgnoreCase("script") || parser.getName().equalsIgnoreCase("style")) && suppressed < 0) suppressed = parser.getDepth();
            if (token == XmlPullParser.END_TAG && parser.getDepth() == suppressed) suppressed = -1;
            if (suppressed < 0) {
                if (token == XmlPullParser.TEXT || token == XmlPullParser.CDSECT || token == XmlPullParser.ENTITY_REF) value.append(parser.getText());
                else if (token == XmlPullParser.END_TAG && (parser.getName().equals("p") || parser.getName().equals("div") || parser.getName().equals("br"))) value.append('\n');
            }
            if (value.length() > max) throw new IOException("News field exceeds limit");
        }
        return value.toString().trim();
    }
    private static long date(String value) {
        try { return Instant.parse(value).toEpochMilli(); } catch (Exception ignored) { }
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); } catch (Exception ignored) { return 0; }
    }
}
