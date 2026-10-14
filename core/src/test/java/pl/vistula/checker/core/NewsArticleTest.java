package pl.vistula.checker.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NewsArticleTest {

    @Test
    public void testNewsArticleJsonSerialization() {
        NewsArticle article = new NewsArticle(
            "news_101",
            "Dni rektorskie w semestrze zimowym",
            "2026-10-31",
            "Rektorat",
            "Informujemy o wyznaczonych dniach wolnych od zajęć dydaktycznych.",
            "https://vistula.edu.pl/komunikaty/101"
        );

        JSONObject json = article.json();
        assertEquals("news_101", json.optString("id"));
        assertEquals("Dni rektorskie w semestrze zimowym", json.optString("title"));
        assertEquals("2026-10-31", json.optString("publicationDate"));
        assertEquals("Rektorat", json.optString("author"));
        assertEquals("Informujemy o wyznaczonych dniach wolnych od zajęć dydaktycznych.", json.optString("headline"));
        assertEquals("https://vistula.edu.pl/komunikaty/101", json.optString("url"));

        NewsArticle restored = NewsArticle.from(json);
        assertEquals(article.id, restored.id);
        assertEquals(article.title, restored.title);
        assertEquals(article.publicationDate, restored.publicationDate);
        assertEquals(article.author, restored.author);
        assertEquals(article.headline, restored.headline);
        assertEquals(article.url, restored.url);
    }

    @Test
    public void testUsosParserNewsArticles() {
        JSONArray array = new JSONArray();
        JSONObject item1 = new JSONObject()
            .put("id", 42)
            .put("title", new JSONObject().put("pl", "Wybory do Samorządu Studentów"))
            .put("publication_date", "2026-10-14 12:00:00")
            .put("author_name", "Komisja Wyborcza")
            .put("headline", new JSONObject().put("pl", "Rozpoczyna się zgłaszanie kandydatur."))
            .put("url", "https://usosweb.vistula.edu.pl/news/42");

        array.put(item1);

        List<NewsArticle> articles = UsosParser.newsArticles(array);
        assertEquals(1, articles.size());
        NewsArticle a = articles.get(0);
        assertEquals("42", a.id);
        assertEquals("Wybory do Samorządu Studentów", a.title);
        assertEquals("2026-10-14", a.publicationDate);
        assertEquals("Komisja Wyborcza", a.author);
        assertEquals("Rozpoczyna się zgłaszanie kandydatur.", a.headline);
        assertEquals("https://usosweb.vistula.edu.pl/news/42", a.url);
    }

    @Test
    public void testEmptyOrNullHandling() {
        List<NewsArticle> nullList = UsosParser.newsArticles(null);
        assertTrue(nullList.isEmpty());

        NewsArticle emptyArticle = NewsArticle.from(new JSONObject());
        assertEquals("", emptyArticle.id);
        assertEquals("", emptyArticle.title);
        assertEquals("", emptyArticle.publicationDate);
    }
}
