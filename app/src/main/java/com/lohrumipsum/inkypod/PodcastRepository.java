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

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

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

    // IMPORTANT: Replace with your actual API key and secret from podcastindex.org
    private static final String API_KEY = "LTENK6UZPXYXMET5QFWZ";
    private static final String API_SECRET = "FLhpptnZ7rtUFcxAbFfxxR9$UGTntBZEVW$CHSTL";

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
    
    public LiveData<List<SearchResult>> getSearchResults() {
        return searchResults;
    }

    public LiveData<List<Episode>> getAllEpisodes() {
        return mPodcastDao.getAllEpisodes();
    }

    public LiveData<List<Episode>> getQueue() {
        return mPodcastDao.getQueue();
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
    
    public void searchPodcasts(String term) {
        if (API_KEY.equals("YOUR_API_KEY_HERE") || API_SECRET.equals("YOUR_API_SECRET_HERE")) {
            Log.e(TAG, "API Key and Secret not set in PodcastRepository.java");
            // Optionally, post an empty list or an error state to the LiveData
            searchResults.postValue(new ArrayList<>());
            return;
        }

        executor.execute(() -> {
            try {
                // 1. Prepare for API Authentication
                long apiHeaderTime = new Date().getTime() / 1000;
                String data4Hash = API_KEY + API_SECRET + apiHeaderTime;
                
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                byte[] hash = digest.digest(data4Hash.getBytes("UTF-8"));
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                String authorization = hexString.toString();

                // 2. Make the HTTP Request
                URL url = new URL("https://api.podcastindex.org/api/1.0/search/byterm?q=" + term);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "InkyPod/1.0");
                connection.setRequestProperty("X-Auth-Date", "" + apiHeaderTime);
                connection.setRequestProperty("X-Auth-Key", API_KEY);
                connection.setRequestProperty("Authorization", authorization);
                
                // 3. Read the Response
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                connection.disconnect();

                // 4. Parse the JSON and update LiveData
                JSONObject jsonResponse = new JSONObject(content.toString());
                JSONArray feeds = jsonResponse.getJSONArray("feeds");
                List<SearchResult> results = new ArrayList<>();
                for (int i = 0; i < feeds.length(); i++) {
                    JSONObject feed = feeds.getJSONObject(i);
                    results.add(new SearchResult(
                        feed.getString("title"),
                        feed.getString("author"),
                        feed.getString("url")
                    ));
                }
                searchResults.postValue(results);

            } catch (Exception e) {
                Log.e(TAG, "Error searching podcasts", e);
                searchResults.postValue(new ArrayList<>()); // Post empty list on error
            }
        });
    }

    public void subscribeToFeed(String url) {
        executor.execute(() -> {
            try {
                URL feedUrl = new URL(url);
                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(new XmlReader(feedUrl));

                Subscription subscription = new Subscription(
                        url,
                        feed.getTitle(),
                        feed.getDescription()
                );
                mPodcastDao.insertSubscription(subscription);


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
            } catch (Exception e) {
                Log.e(TAG, "Error fetching or parsing RSS feed", e);
            }
        });
    }

    public void savePlaybackPosition(String guid, long position, long duration) {
        executor.execute(() -> {
            if (guid == null) return;
            Episode episode = mPodcastDao.getEpisodeByGuidSync(guid);
            if (episode != null) {
                if (duration > 0 && position > duration * 0.97) {
                    episode.isPlayed = true;
                    episode.playbackPosition = 0;
                } else {
                    episode.playbackPosition = position;
                }
                mPodcastDao.updateEpisode(episode);
                Log.d(TAG, "Repo: Saved position " + position + " for GUID " + guid);
            }
        });
    }

    public void updateEpisode(Episode episode) {
        executor.execute(() -> mPodcastDao.updateEpisode(episode));
    }
}

