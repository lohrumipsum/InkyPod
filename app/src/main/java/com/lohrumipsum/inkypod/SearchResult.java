package com.lohrumipsum.inkypod;

// A simple Plain Old Java Object (POJO) to hold search result data.
public class SearchResult {
    public final String title;
    public final String author;
    public final String feedUrl;

    public SearchResult(String title, String author, String feedUrl) {
        this.title = title;
        this.author = author;
        this.feedUrl = feedUrl;
    }
}
