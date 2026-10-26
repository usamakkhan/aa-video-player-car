package app.aavideoplayercar;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import app.aavideoplayercar.news.NewsParser;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NewsParserTest {
    private NewsParser.Result parse(String xml) throws Exception { return NewsParser.parse(xml.getBytes(StandardCharsets.UTF_8), "https://example.com/subscription", "https://cdn.example.com/news/feed.xml"); }
    private String rss(String items) { return "<rss version='2.0' xmlns:content='http://purl.org/rss/1.0/modules/content/'><channel><title>Fixture news</title>" + items + "</channel></rss>"; }
    @Test public void rssExtractsPlainTextAndKeepsGuidIdentityAcrossUpdates() throws Exception {
        String item = "<item><guid>stable</guid><title>Headline</title><link>article</link><description>Summary</description><content:encoded><![CDATA[<p>Full <b>story</b> &amp; details</p><script>unwanted()</script><style>hidden</style>]]></content:encoded><pubDate>Wed, 21 Oct 2026 10:00:00 GMT</pubDate></item>";
        var first = parse(rss(item + item)); assertEquals("Fixture news", first.title); assertEquals(1, first.articles.size()); assertEquals(1, first.skipped);
        var article = first.articles.get(0); assertEquals("https://cdn.example.com/news/article", article.url); assertEquals("Full story & details", article.body); assertTrue(article.publishedAt > 0);
        var changed = parse(rss(item.replace("Headline", "Changed").replace("<link>article", "<link>new-article")));
        assertEquals(article.id, changed.articles.get(0).id); assertEquals(first.id, changed.id);
    }
    @Test public void atomHandlesTextHtmlXhtmlBaseAndIgnoresRemoteContent() throws Exception {
        var result = parse("<feed xmlns='http://www.w3.org/2005/Atom' xml:base='https://publisher.example/base/'><title type='text'>A &lt; B</title><entry xml:base='nested/'><id>one</id><title type='html'>A &amp;amp; B</title><link rel='enclosure' href='audio.mp3'/><link href='story'/><summary type='html'>&lt;p&gt;Summary&lt;/p&gt;</summary><content type='xhtml'><div xmlns='http://www.w3.org/1999/xhtml'><p>First</p><p>Second<script>hidden()</script></p></div></content><updated>2026-10-21T10:00:00Z</updated></entry><entry><id>two</id><title>Remote</title><summary>Fallback</summary><content src='https://remote.example/file' type='text'/></entry><entry><id>three</id><title>Literal</title><content type='text'>&lt;b&gt;literal&lt;/b&gt;</content></entry></feed>");
        assertEquals("A < B", result.title); assertEquals(3, result.articles.size());
        assertEquals("A & B", result.articles.get(0).title); assertEquals("https://publisher.example/base/nested/story", result.articles.get(0).url);
        assertEquals("First\nSecond", result.articles.get(0).body); assertTrue(result.articles.get(0).publishedAt > 0);
        assertEquals("Fallback", result.articles.get(1).body); assertEquals("<b>literal</b>", result.articles.get(2).body);
    }
    @Test public void supportsDescriptionOnlyArticlesAndRejectsUnsafeLinks() throws Exception {
        var result = parse(rss("<item><description>Text-only article</description><link>javascript:alert(1)</link></item><item><title>Bad link</title><link>https://user:secret@example.com/a</link><pubDate>invalid</pubDate></item><item/>"));
        assertEquals(2, result.articles.size()); assertEquals(1, result.skipped); assertEquals("Text-only article", result.articles.get(0).title);
        assertEquals("", result.articles.get(0).url); assertEquals("", result.articles.get(1).url); assertEquals(0, result.articles.get(1).publishedAt);
    }
    @Test public void boundsWorkRejectsMalformedXmlAndHonorsCancellation() throws Exception {
        for (String invalid : new String[]{"<html/>", "<rss><channel>", "<rss/>", "<rss><channel/><channel/></rss>", "<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///secret'>]>" + rss("<item><title>&x;</title></item>"), rss("<item><title>&unknown;</title></item>"), rss("<item><description>" + "x".repeat(64001) + "</description></item>"), rss("<item><title>X</title></item>".repeat(2001)), rss("<a>".repeat(33) + "</a>".repeat(33))}) {
            assertThrows(java.io.IOException.class, () -> parse(invalid));
        }
        assertThrows(java.io.IOException.class, () -> NewsParser.parse(new byte[4 * 1024 * 1024 + 1], "https://example.com", "https://example.com"));
        Thread.currentThread().interrupt(); try { assertThrows(java.io.IOException.class, () -> parse(rss(""))); } finally { Thread.interrupted(); }
    }
}
