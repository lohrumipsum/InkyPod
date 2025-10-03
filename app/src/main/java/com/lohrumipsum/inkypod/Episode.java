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
    public long playbackPosition = 0;
    public boolean isPlayed = false;
    public boolean isInQueue = false;
    public int downloadProgress = -1; // -1 means not downloading
    public long queueTimestamp = 0;
    public boolean isDownloaded = false;

    public Episode() {
        // Needed for Room
    }

    @Ignore
    public Episode(String guid, String feedUrl, String title, String description, String audioUrl, long publicationDate, String localFilePath) {
        this.guid = guid;
        this.feedUrl = feedUrl;
        this.title = title;
        this.description = description;
        this.audioUrl = audioUrl;
        this.publicationDate = publicationDate;
        this.localFilePath = localFilePath;
    }
}

