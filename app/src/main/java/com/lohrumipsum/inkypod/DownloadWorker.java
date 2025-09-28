package com.lohrumipsum.inkypod;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadWorker extends Worker {

    public static final String PROGRESS = "PROGRESS";
    private static final String TAG = "DownloadWorker";

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String urlString = getInputData().getString("url");
        String guid = getInputData().getString("guid");

        if (urlString == null || guid == null) {
            Log.e(TAG, "doWork: FAILED - URL or GUID is null.");
            return Result.failure();
        }

        // Sanitize the GUID to create a valid filename
        String fileName = guid.replaceAll("[^a-zA-Z0-9.-]", "_") + ".mp3";
        File outputFile = new File(getApplicationContext().getFilesDir(), fileName);

        Log.d(TAG, "doWork: STARTING - Downloading " + urlString + " to " + outputFile.getAbsolutePath());

        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            // **THE FIX:** Use the standard, robust way to handle redirects automatically.
            connection.setInstanceFollowRedirects(true);
            connection.connect();

            int responseCode = connection.getResponseCode();

            // Check if the connection is successful (after redirects).
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "doWork: FAILED - Server returned HTTP " + responseCode + " " + connection.getResponseMessage());
                return Result.failure();
            }

            int fileLength = connection.getContentLength();
            InputStream input = connection.getInputStream();
            FileOutputStream output = new FileOutputStream(outputFile);

            byte[] data = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                // Publish progress
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    Data progressData = new Data.Builder().putInt(PROGRESS, progress).build();
                    setProgressAsync(progressData);
                }
                output.write(data, 0, count);
            }

            output.close();
            input.close();

            Log.d(TAG, "doWork: SUCCESS - Download complete for " + guid);

            // Update the database
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            PodcastDao podcastDao = db.podcastDao();
            Episode episode = podcastDao.getEpisodeByGuidSync(guid);
            if (episode != null) {
                episode.isDownloaded = true;
                episode.localFilePath = outputFile.getAbsolutePath();
                podcastDao.updateEpisode(episode);
                Log.d(TAG, "doWork: SUCCESS - Database updated for " + guid);
            } else {
                 Log.e(TAG, "doWork: FAILED - Could not find episode in database to update.");
                 return Result.failure();
            }

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork: FAILED - An exception occurred during download.", e);
            return Result.failure();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}

