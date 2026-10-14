package pl.vistula.checker.core;

import org.json.*;

public final class NewsArticle {
    public final String id;
    public final String title;
    public final String publicationDate;
    public final String author;
    public final String headline;
    public final String url;

    public NewsArticle(String id, String title, String publicationDate, String author, String headline, String url) {
        this.id = id != null ? id : "";
        this.title = title != null ? title : "";
        this.publicationDate = publicationDate != null ? publicationDate : "";
        this.author = author != null ? author : "";
        this.headline = headline != null ? headline : "";
        this.url = url != null ? url : "";
    }

    public JSONObject json() throws JSONException {
        return new JSONObject()
            .put("id", id)
            .put("title", title)
            .put("publicationDate", publicationDate)
            .put("author", author)
            .put("headline", headline)
            .put("url", url);
    }

    public static NewsArticle from(JSONObject o) {
        if (o == null) return new NewsArticle("", "", "", "", "", "");
        return new NewsArticle(
            o.optString("id", ""),
            o.optString("title", ""),
            o.optString("publicationDate", ""),
            o.optString("author", ""),
            o.optString("headline", ""),
            o.optString("url", "")
        );
    }
}
