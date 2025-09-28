package com.lohrumipsum.inkypod;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "subscriptions")
public class Subscription {

    @PrimaryKey
    @NonNull
    public String feedUrl;
    public String title;
    public String description;

    public Subscription(@NonNull String feedUrl, String title, String description) {
        this.feedUrl = feedUrl;
        this.title = title;
        this.description = description;
    }
}

