package com.lohrumipsum.inkypod;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.rometools.fetcher.impl.HttpURLFeedFetcher;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PodcastRepository {

    private static final String TAG = "InkyPodDebug";
    private final PodcastDao mPodcastDao;
    private final Application mApplication;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<List<SearchResult>> searchResults = new MutableLiveData<>();


    public PodcastRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        mPodcastDao = db.podcastDao();
        mApplication = application;
    }
    
    // **THE FIX:** Expose the DAO for synchronous operations in the service.
    public PodcastDao getDao() {
        return mPodcastDao;
    }

    public LiveData<List<Episode>> getAllEpisodes() {
        return mPodcastDao.getAllEpisodes();
    }

    public LiveData<List<Episode>> getQueue() {
        return mPodcastDao.getQueue();
    }

    public List<Episode> getQueueSync() {
        return mPodcastDao.getQueueSync();
    }

    public LiveData<List<Subscription>> getAllSubscriptions() {
        return mPodcastDao.getAllSubscriptions();
    }
    
    public LiveData<List<Episode>> getEpisodesForSubscription(String feedUrl) {
        return mPodcastDao.getEpisodesByFeedUrl(feedUrl);
    }
    
    public LiveData<List<WorkInfo>> getWorkInfoForTag(String tag) {
        return WorkManager.getInstance(mApplication).getWorkInfosByTagLiveData(tag);
    }

    public Episode getEpisodeByGuidSync(String guid) {
        return mPodcastDao.getEpisodeByGuidSync(guid);
    }

    public void downloadEpisode(Episode episode) {
        Data inputData = new Data.Builder()
                .putString("url", episode.audioUrl)
                .putString("guid", episode.guid)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest downloadWorkRequest = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(episode.guid)
                .build();
        
        Log.d(TAG, "Repo: Enqueuing download for GUID: " + episode.guid);
        WorkManager.getInstance(mApplication).enqueueUniqueWork(episode.guid, ExistingWorkPolicy.REPLACE, downloadWorkRequest);
    }

    public void deleteDownload(Episode episode) {
        executor.execute(() -> {
            if (episode.localFilePath != null && !episode.localFilePath.isEmpty()) {
                File file = new File(episode.localFilePath);
                if (file.exists()) {
                    file.delete();
                }
            }
            episode.isDownloaded = false;
            episode.localFilePath = null;
            episode.playbackPosition = 0;
            mPodcastDao.updateEpisode(episode);
        });
    }

    public void unsubscribe(Subscription subscription) {
        executor.execute(() -> {
            mPodcastDao.deleteEpisodesByFeedUrl(subscription.feedUrl);
            mPodcastDao.deleteSubscription(subscription.feedUrl);
        });
    }

    public void subscribeToFeed(String url) {
        executor.execute(() -> {
            try {
                HttpURLFeedFetcher fetcher = new HttpURLFeedFetcher();
                SyndFeed feed = fetcher.retrieveFeed(new URL(url));
                
                Subscription subscription = new Subscription(
                        url,
                        feed.getTitle(),
                        feed.getDescription()
                );
                mPodcastDao.insertSubscription(subscription);
                Log.d(TAG, "New subscription inserted: " + feed.getTitle());

                List<Episode> episodes = new ArrayList<>();
                for (SyndEntry entry : feed.getEntries()) {
                    Date pubDate = entry.getPublishedDate() != null ? entry.getPublishedDate() : new Date();
                    if (entry.getEnclosures() != null && !entry.getEnclosures().isEmpty()) {
                        Episode episode = new Episode(
                                entry.getUri(),
                                url, 
                                entry.getTitle(),
                                entry.getDescription() != null ? entry.getDescription().getValue() : "",
                                entry.getEnclosures().get(0).getUrl(),
                                pubDate.getTime(),
                                null
                        );
                        episodes.add(episode);
                    }
                }
                mPodcastDao.insertEpisodes(episodes);
                Log.d(TAG, "Processed " + episodes.size() + " episodes for " + feed.getTitle());

            } catch (Exception e) {
                Log.e(TAG, "Error subscribing to feed: " + url, e);
            }
        });
    }


    public void refreshAllFeeds() {
        executor.execute(() -> {
            List<Subscription> subscriptions = mPodcastDao.getAllSubscriptionsSync();
            Log.d(TAG, "Found " + subscriptions.size() + " subscriptions to refresh.");
            for (Subscription subscription : subscriptions) {
                // **THE FIX:** Use the robust feed fetcher for refreshing to handle redirects.
                try {
                    Log.d(TAG, "Refreshing feed: " + subscription.title);
                    HttpURLFeedFetcher fetcher = new HttpURLFeedFetcher();
                    SyndFeed feed = fetcher.retrieveFeed(new URL(subscription.feedUrl));

                    List<Episode> episodes = new ArrayList<>();
                    for (SyndEntry entry : feed.getEntries()) {
                        Date pubDate = entry.getPublishedDate() != null ? entry.getPublishedDate() : new Date();
                        if (entry.getEnclosures() != null && !entry.getEnclosures().isEmpty()) {
                            Episode episode = new Episode(
                                    entry.getUri(),
                                    subscription.feedUrl,
                                    entry.getTitle(),
                                    entry.getDescription() != null ? entry.getDescription().getValue() : "",
                                    entry.getEnclosures().get(0).getUrl(),
                                    pubDate.getTime(),
                                    null
                            );
                            episodes.add(episode);
                        }
                    }
                    mPodcastDao.insertEpisodes(episodes);
                    Log.d(TAG, "Successfully refreshed " + episodes.size() + " episodes for " + subscription.title);
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing feed: " + subscription.title, e);
                }
            }
        });
    }
    

    public void clearSearchResults() {
        searchResults.postValue(null);
    }

    public void updateEpisode(Episode episode) {
        executor.execute(() -> mPodcastDao.updateEpisode(episode));
    }

    public void savePlaybackPosition(String guid, long position, long duration) {
        executor.execute(() -> {
            if (guid == null) return;
            Episode episode = mPodcastDao.getEpisodeByGuidSync(guid);
            if (episode != null) {
                if (duration > 0 && position >= duration - 5000) {
                    episode.playbackPosition = 0;
                    episode.isPlayed = true;
                } else {
                    episode.playbackPosition = position;
                }
                mPodcastDao.updateEpisode(episode);
            }
        });
    }

    public LiveData<List<SearchResult>> getSearchResults() {
        return searchResults;
    }

    public void searchPodcasts(final String term) {
        executor.execute(() -> {
            try {
                String apiKey = "KLEMMZFYPNMWNE2UWRKN";
                String apiSecret = "NF5F2ThN5p62sncDnum#dwz^TnvwK4t8BGsb5s#j";

                long apiHeaderTime = new Date().getTime() / 1000;
                String dataToHash = apiKey + apiSecret + apiHeaderTime;
                
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                byte[] hash = digest.digest(dataToHash.getBytes("UTF-8"));
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                String apiHash = hexString.toString();

                String searchUrl = "https://api.podcastindex.org/api/1.0/search/byterm?q=" + java.net.URLEncoder.encode(term, "UTF-8");
                URL url = new URL(searchUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "InkyPod/1.0");
                connection.setRequestProperty("X-Auth-Date", "" + apiHeaderTime);
                connection.setRequestProperty("X-Auth-Key", apiKey);
                connection.setRequestProperty("Authorization", apiHash);

                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                connection.disconnect();

                JSONObject jsonResponse = new JSONObject(content.toString());
                JSONArray feeds = jsonResponse.getJSONArray("feeds");
                List<SearchResult> results = new ArrayList<>();
                for (int i = 0; i < feeds.length(); i++) {
                    JSONObject feed = feeds.getJSONObject(i);
                    String description = feed.has("description") ? feed.getString("description") : "";
                    
                    String feedUrl = feed.getString("originalUrl");

                    results.add(new SearchResult(
                            feed.getString("title"),
                            feedUrl,
                            description,
                            feed.getString("author")
                    ));
                }
                searchResults.postValue(results);

            } catch (Exception e) {
                Log.e(TAG, "Error searching podcasts", e);
                searchResults.postValue(new ArrayList<>());
            }
        });
    }
}

