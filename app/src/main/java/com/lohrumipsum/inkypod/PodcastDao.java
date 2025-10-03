package com.lohrumipsum.inkypod;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PodcastDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertEpisodes(List<Episode> episodes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSubscription(Subscription subscription);

    @Update
    void updateEpisode(Episode episode);

    @Query("SELECT * FROM episodes WHERE isPlayed = 0 ORDER BY publicationDate DESC")
    LiveData<List<Episode>> getAllEpisodes();

    @Query("SELECT * FROM episodes WHERE isInQueue = 1 ORDER BY queueTimestamp ASC")
    LiveData<List<Episode>> getQueue();

    @Query("SELECT * FROM episodes WHERE isInQueue = 1 ORDER BY queueTimestamp ASC")
    List<Episode> getQueueSync();

    @Query("SELECT * FROM subscriptions ORDER BY title ASC")
    LiveData<List<Subscription>> getAllSubscriptions();
    
    @Query("SELECT * FROM subscriptions ORDER BY title ASC")
    List<Subscription> getAllSubscriptionsSync();

    @Query("SELECT * FROM episodes WHERE feedUrl = :feedUrl ORDER BY publicationDate DESC")
    LiveData<List<Episode>> getEpisodesByFeedUrl(String feedUrl);

    @Query("SELECT * FROM episodes WHERE guid = :guid LIMIT 1")
    Episode getEpisodeByGuidSync(String guid);

    @Query("DELETE FROM subscriptions WHERE feedUrl = :feedUrl")
    void deleteSubscription(String feedUrl);

    @Query("DELETE FROM episodes WHERE feedUrl = :feedUrl")
    void deleteEpisodesByFeedUrl(String feedUrl);
}

