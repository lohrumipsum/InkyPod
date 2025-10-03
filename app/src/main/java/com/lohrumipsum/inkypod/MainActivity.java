package com.lohrumipsum.inkypod;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private PodcastViewModel podcastViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        podcastViewModel = new ViewModelProvider(this).get(PodcastViewModel.class);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(navListener);

        // This is to prevent the fragment from being recreated on rotation.
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                    new SubscriptionsFragment()).commit();
            // **THE FIX:** Schedule the periodic refresh worker when the app starts.
            schedulePeriodicFeedRefresh();
        }
    }

    private final BottomNavigationView.OnItemSelectedListener navListener =
            item -> {
                Fragment selectedFragment = null;
                int itemId = item.getItemId();

                if (itemId == R.id.nav_subscriptions) {
                    selectedFragment = new SubscriptionsFragment();
                } else if (itemId == R.id.nav_episodes) {
                    selectedFragment = new EpisodesFragment();
                } else if (itemId == R.id.nav_queue) {
                    selectedFragment = new QueueFragment();
                }

                if (selectedFragment != null) {
                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                            selectedFragment).commit();
                }
                return true;
            };

    // **THE FIX:** New method to schedule the background work.
    private void schedulePeriodicFeedRefresh() {
        PeriodicWorkRequest refreshRequest =
                new PeriodicWorkRequest.Builder(RefreshWorker.class, 12, TimeUnit.HOURS)
                        .setConstraints(new androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build())
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "feedRefreshWork",
                ExistingPeriodicWorkPolicy.KEEP,
                refreshRequest
        );
    }
}

