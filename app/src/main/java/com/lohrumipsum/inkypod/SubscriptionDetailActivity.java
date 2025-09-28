package com.lohrumipsum.inkypod;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SubscriptionDetailActivity extends AppCompatActivity {

    public static final String EXTRA_FEED_URL = "com.lohrumipsum.inkypod.EXTRA_FEED_URL";
    public static final String EXTRA_FEED_TITLE = "com.lohrumipsum.inkypod.EXTRA_FEED_TITLE";

    private PodcastViewModel podcastViewModel;
    private EpisodeAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscription_detail);

        podcastViewModel = new ViewModelProvider(this).get(PodcastViewModel.class);

        String feedUrl = getIntent().getStringExtra(EXTRA_FEED_URL);
        String podcastTitle = getIntent().getStringExtra(EXTRA_FEED_TITLE);

        TextView titleTextView = findViewById(R.id.podcast_title_detail);
        titleTextView.setText(podcastTitle);

        RecyclerView recyclerView = findViewById(R.id.episode_recycler_view_detail);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        adapter = new EpisodeAdapter(new EpisodeAdapter.OnItemClickListener() {
            @Override
            public void onPlayClick(Episode episode) {
                podcastViewModel.playEpisode(episode);
            }
            @Override
            public void onDownloadClick(Episode episode) {
                podcastViewModel.downloadEpisode(episode);
            }
            @Override
            public void onQueueClick(Episode episode) {
                podcastViewModel.toggleQueueStatus(episode);
            }
            @Override
            public void onDeleteClick(Episode episode) {
                podcastViewModel.deleteDownload(episode);
            }
            @Override
            public void onMarkPlayedClick(Episode episode) {
                podcastViewModel.togglePlayedStatus(episode);
            }
        });
        recyclerView.setAdapter(adapter);

        podcastViewModel.getEpisodesForSubscription(feedUrl).observe(this, adapter::submitList);
        podcastViewModel.getCurrentlyPlayingGuid().observe(this, guid ->
                adapter.setPlaybackState(guid, podcastViewModel.getIsPlaying().getValue()));
        podcastViewModel.getIsPlaying().observe(this, isPlaying ->
                adapter.setPlaybackState(podcastViewModel.getCurrentlyPlayingGuid().getValue(), isPlaying));

        Button backButton = findViewById(R.id.back_button_detail);
        backButton.setOnClickListener(v -> finish());
        
        Button unsubscribeButton = findViewById(R.id.unsubscribe_button);
        unsubscribeButton.setOnClickListener(v -> {
            podcastViewModel.unsubscribe(new Subscription(feedUrl, podcastTitle, ""));
            finish();
        });
    }
}

