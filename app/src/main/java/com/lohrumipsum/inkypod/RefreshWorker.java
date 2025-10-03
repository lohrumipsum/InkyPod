package com.lohrumipsum.inkypod;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class RefreshWorker extends Worker {

    private static final String TAG = "InkyPodDebug_Refresh";

    public RefreshWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Periodic refresh worker started.");
        try {
            // Get an instance of the repository to access the refresh logic
            PodcastRepository repository = new PodcastRepository((android.app.Application) getApplicationContext());
            repository.refreshAllFeeds();
            Log.d(TAG, "Periodic refresh worker finished successfully.");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Periodic refresh worker failed.", e);
            return Result.failure();
        }
    }
}
