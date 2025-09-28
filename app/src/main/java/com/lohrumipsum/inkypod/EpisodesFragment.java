package com.lohrumipsum.inkypod;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class EpisodesFragment extends Fragment {

    private PodcastViewModel podcastViewModel;
    private EpisodeAdapter adapter;
    private static final String TAG = "InkyPodDebug";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_episodes, container, false);

        podcastViewModel = new ViewModelProvider(requireActivity()).get(PodcastViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.episode_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
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

        // Observe changes
        podcastViewModel.getAllEpisodesWithProgress().observe(getViewLifecycleOwner(), adapter::submitList);
        
        podcastViewModel.getCurrentlyPlayingGuid().observe(getViewLifecycleOwner(), guid ->
                adapter.setPlaybackState(guid, podcastViewModel.getIsPlaying().getValue()));
        
        podcastViewModel.getIsPlaying().observe(getViewLifecycleOwner(), isPlaying ->
                adapter.setPlaybackState(podcastViewModel.getCurrentlyPlayingGuid().getValue(), isPlaying));

        return view;
    }
}

