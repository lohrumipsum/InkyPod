package com.lohrumipsum.inkypod;

public class SearchResult {
    public final String title;
    public final String feedUrl;
    public final String author;
    public final String description;

    public SearchResult(String title, String feedUrl, String author, String description) {
        this.title = title;
        this.feedUrl = feedUrl;
        this.author = author;
        this.description = description;
    }
}

