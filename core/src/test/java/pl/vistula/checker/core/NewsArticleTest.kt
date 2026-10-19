package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NewsArticleTest {
    @Test
    fun testNewsArticleJsonSerialization() {
        val article = NewsArticle(
            "news_101",
            "Dni rektorskie w semestrze zimowym",
            "2026-10-31",
            "Rektorat",
            "Informujemy o wyznaczonych dniach wolnych od zajęć dydaktycznych.",
            "https://vistula.edu.pl/komunikaty/101"
        )

        val json = article.json()
        assertEquals("news_101", json.optString("id"))
        assertEquals("Dni rektorskie w semestrze zimowym", json.optString("title"))
        assertEquals("2026-10-31", json.optString("publicationDate"))
        assertEquals("Rektorat", json.optString("author"))
        assertEquals("Informujemy o wyznaczonych dniach wolnych od zajęć dydaktycznych.", json.optString("headline"))
        assertEquals("https://vistula.edu.pl/komunikaty/101", json.optString("url"))

        val restored = NewsArticle.from(json)
        assertEquals(article.id, restored.id)
        assertEquals(article.title, restored.title)
        assertEquals(article.publicationDate, restored.publicationDate)
        assertEquals(article.author, restored.author)
        assertEquals(article.headline, restored.headline)
        assertEquals(article.url, restored.url)
    }

    @Test
    fun testUsosParserNewsArticles() {
        val array = JSONArray()
        val item1 = JSONObject()
            .put("id", 42)
            .put("title", JSONObject().put("pl", "Wybory do Samorządu Studentów"))
            .put("publication_date", "2026-10-14 12:00:00")
            .put("author_name", "Komisja Wyborcza")
            .put("headline", JSONObject().put("pl", "Rozpoczyna się zgłaszanie kandydatur."))
            .put("url", "https://usosweb.vistula.edu.pl/news/42")

        array.put(item1)

        val articles = UsosParser.newsArticles(array)
        assertEquals(1, articles.size)
        val a = articles[0]
        assertEquals("42", a.id)
        assertEquals("Wybory do Samorządu Studentów", a.title)
        assertEquals("2026-10-14", a.publicationDate)
        assertEquals("Komisja Wyborcza", a.author)
        assertEquals("Rozpoczyna się zgłaszanie kandydatur.", a.headline)
        assertEquals("https://usosweb.vistula.edu.pl/news/42", a.url)
    }

    @Test
    fun testEmptyOrNullHandling() {
        val nullList = UsosParser.newsArticles(null)
        assertTrue(nullList.isEmpty())

        val emptyArticle = NewsArticle.from(JSONObject())
        assertEquals("", emptyArticle.id)
        assertEquals("", emptyArticle.title)
        assertEquals("", emptyArticle.publicationDate)
    }
}
