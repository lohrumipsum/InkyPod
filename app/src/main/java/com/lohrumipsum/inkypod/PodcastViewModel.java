package com.lohrumipsum.inkypod;

import android.app.Application;
import android.content.ComponentName;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.work.WorkInfo;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class PodcastViewModel extends AndroidViewModel {

    private static final String TAG = "InkyPodDebug";
    private final PodcastRepository mRepository;
    private final LiveData<List<Episode>> mAllEpisodesWithProgress;
    private MediaController mediaController;

    public static final MutableLiveData<String> currentlyPlayingGuid = new MutableLiveData<>();
    public static final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);

    public PodcastViewModel(@NonNull Application application) {
        super(application);
        mRepository = new PodcastRepository(application);

        SessionToken sessionToken = new SessionToken(application, new ComponentName(application, PlaybackService.class));
        ListenableFuture<MediaController> controllerFuture = new MediaController.Builder(application, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error getting MediaController", e);
            }
        }, application.getMainExecutor());

        // LiveData combination logic
        MediatorLiveData<List<Episode>> mediator = new MediatorLiveData<>();
        LiveData<List<Episode>> episodesFromDb = mRepository.getAllEpisodes();
        LiveData<List<WorkInfo>> workInfo = mRepository.getWorkInfoForTag("download");

        mediator.addSource(episodesFromDb, episodes -> mediator.setValue(combineEpisodeAndWorkInfo(episodes, workInfo.getValue())));
        mediator.addSource(workInfo, workStatus -> mediator.setValue(combineEpisodeAndWorkInfo(episodesFromDb.getValue(), workStatus)));
        mAllEpisodesWithProgress = mediator;
    }

    private List<Episode> combineEpisodeAndWorkInfo(List<Episode> episodes, List<WorkInfo> workInfos) {
        if (episodes == null) {
            return new ArrayList<>();
        }

        Map<String, Integer> progressMap = new HashMap<>();
        if (workInfos != null) {
            for (WorkInfo info : workInfos) {
                if (info.getState() == WorkInfo.State.RUNNING) {
                    for (String tag : info.getTags()) {
                        progressMap.put(tag, info.getProgress().getInt(DownloadWorker.PROGRESS, 0));
                    }
                }
            }
        }

        for (Episode episode : episodes) {
            episode.downloadProgress = progressMap.getOrDefault(episode.guid, -1);
        }
        return episodes;
    }

    public LiveData<String> getCurrentlyPlayingGuid() {
        return currentlyPlayingGuid;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public LiveData<List<Episode>> getAllEpisodesWithProgress() {
        return mAllEpisodesWithProgress;
    }

    public LiveData<List<Episode>> getQueue() {
        return mRepository.getQueue();
    }

    public LiveData<List<Subscription>> getAllSubscriptions() {
        return mRepository.getAllSubscriptions();
    }

    public void playEpisode(Episode episode) {
        if (mediaController == null || episode.localFilePath == null) return;
        Log.d(TAG, "playEpisode called for: " + episode.title);

        String currentlyPlayingId = mediaController.getCurrentMediaItem() != null ? mediaController.getCurrentMediaItem().mediaId : null;
        if (episode.guid.equals(currentlyPlayingId)) {
            if (mediaController.isPlaying()) {
                mediaController.pause();
            } else {
                mediaController.play();
            }
        } else {
            // Before playing a new item, save the position of the old one.
            if (mediaController.getCurrentMediaItem() != null) {
                mRepository.savePlaybackPosition(mediaController.getCurrentMediaItem().mediaId, mediaController.getCurrentPosition(), mediaController.getDuration());
            }

            MediaItem mediaItem = new MediaItem.Builder()
                    .setMediaId(episode.guid)
                    .setUri(Uri.fromFile(new File(episode.localFilePath)))
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(episode.title).build())
                    .build();
            mediaController.setMediaItem(mediaItem, episode.playbackPosition);
            mediaController.prepare();
            mediaController.play();
        }
    }

    public void subscribe(String url) {
        mRepository.subscribeToFeed(url);
    }

    public void downloadEpisode(Episode episode) {
        mRepository.downloadEpisode(episode);
    }

    public void deleteDownload(Episode episode) {
        mRepository.deleteDownload(episode);
    }

    public void toggleQueueStatus(Episode episode) {
        episode.isInQueue = !episode.isInQueue;
        if (episode.isInQueue) {
            episode.queueTimestamp = System.currentTimeMillis();
            // If the episode is being added to the queue and is not downloaded, start the download.
            if (!episode.isDownloaded) {
                downloadEpisode(episode);
            }
        } else {
            episode.queueTimestamp = 0; 
        }
        mRepository.updateEpisode(episode);
    }

    public void togglePlayedStatus(Episode episode) {
        episode.isPlayed = !episode.isPlayed;
        if (!episode.isPlayed) {
            episode.playbackPosition = 0;
        }
        mRepository.updateEpisode(episode);
    }

    public LiveData<List<Episode>> getEpisodesForSubscription(String feedUrl) {
        return mRepository.getEpisodesForSubscription(feedUrl);
    }

    public void unsubscribe(Subscription subscription) {
        mRepository.unsubscribe(subscription);
    }

    public LiveData<List<SearchResult>> getSearchResults() {
        return mRepository.getSearchResults();
    }

    public void searchPodcasts(String term) {
        mRepository.searchPodcasts(term);
    }

    public void clearSearchResults() {
        mRepository.clearSearchResults();
    }

    public void refreshAllFeeds() {
        mRepository.refreshAllFeeds();
    }
}

