package com.lohrumipsum.inkypod;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "episodes")
public class Episode {
    @PrimaryKey
    @NonNull
    public String guid;

    public String feedUrl;
    public String title;
    public String description;
    public String audioUrl;
    public long publicationDate;
    public String localFilePath;
    public boolean isDownloaded;
    public boolean isInQueue;
    public boolean isPlayed;
    public long playbackPosition; // in milliseconds

    @Ignore
    public int downloadProgress = -1; // -1 means not downloading

    @Ignore
    public Episode(@NonNull String guid, String feedUrl, String title, String description, String audioUrl, long publicationDate, String localFilePath) {
        this.guid = guid;
        this.feedUrl = feedUrl;
        this.title = title;
        this.description = description;
        this.audioUrl = audioUrl;
        this.publicationDate = publicationDate;
        this.localFilePath = localFilePath;
        this.isDownloaded = false;
        this.isInQueue = false;
        this.isPlayed = false;
        this.playbackPosition = 0;
    }

    public Episode() {
    }
}

