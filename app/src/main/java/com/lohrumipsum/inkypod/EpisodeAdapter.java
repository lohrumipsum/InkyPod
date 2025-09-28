package com.lohrumipsum.inkypod;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class EpisodeAdapter extends ListAdapter<Episode, EpisodeAdapter.EpisodeViewHolder> {

    private final OnItemClickListener listener;
    private String currentlyPlayingGuid = null;
    private boolean isPlaying = false;
    private static final String TAG = "InkyPodDebug";

    public interface OnItemClickListener {
        void onDownloadClick(Episode episode);
        void onPlayClick(Episode episode);
        void onQueueClick(Episode episode);
        void onDeleteClick(Episode episode);
        void onMarkPlayedClick(Episode episode);
    }

    public EpisodeAdapter(OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    // **THE FIX:** New method to update the adapter's knowledge of the playback state.
    public void setPlaybackState(String guid, Boolean isPlaying) {
        this.currentlyPlayingGuid = guid;
        this.isPlaying = (isPlaying != null && isPlaying);
        notifyDataSetChanged(); // A simple way to refresh all visible items
    }

    private static final DiffUtil.ItemCallback<Episode> DIFF_CALLBACK = new DiffUtil.ItemCallback<Episode>() {
        @Override
        public boolean areItemsTheSame(@NonNull Episode oldItem, @NonNull Episode newItem) {
            return oldItem.guid.equals(newItem.guid);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Episode oldItem, @NonNull Episode newItem) {
            return oldItem.isDownloaded == newItem.isDownloaded &&
                   oldItem.isInQueue == newItem.isInQueue &&
                   oldItem.isPlayed == newItem.isPlayed &&
                   oldItem.downloadProgress == newItem.downloadProgress &&
                   oldItem.title.equals(newItem.title);
        }
    };

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_episode, parent, false);
        return new EpisodeViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        Episode current = getItem(position);
        holder.bind(current, listener, currentlyPlayingGuid, isPlaying);
        Log.d(TAG, "Adapter.onBindViewHolder: Binding episode '" + current.title + "'. Downloaded: " + current.isDownloaded + ", Progress: " + current.downloadProgress);
    }

    static class EpisodeViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final Button downloadButton;
        private final Button playButton;
        private final Button queueButton;
        private final Button deleteButton;
        private final Button markPlayedButton;
        private final ProgressBar downloadProgressBar;

        public EpisodeViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.episode_title);
            downloadButton = itemView.findViewById(R.id.download_button);
            playButton = itemView.findViewById(R.id.play_button);
            queueButton = itemView.findViewById(R.id.queue_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
            markPlayedButton = itemView.findViewById(R.id.mark_played_button);
            downloadProgressBar = itemView.findViewById(R.id.download_progress_bar);
        }

        public void bind(final Episode episode, final OnItemClickListener listener, String currentlyPlayingGuid, boolean isPlaying) {
            titleTextView.setText(episode.title);

            boolean isDownloading = episode.downloadProgress >= 0;
            
            downloadProgressBar.setVisibility(isDownloading ? View.VISIBLE : View.GONE);
            downloadButton.setVisibility(!episode.isDownloaded && !isDownloading ? View.VISIBLE : View.GONE);
            playButton.setVisibility(episode.isDownloaded ? View.VISIBLE : View.GONE);
            deleteButton.setVisibility(episode.isDownloaded ? View.VISIBLE : View.GONE);

            if (isDownloading) {
                downloadProgressBar.setProgress(episode.downloadProgress);
            }
            
            // **THE FIX:** More robust check for the "Pause" state.
            boolean isThisEpisodePlaying = currentlyPlayingGuid != null && currentlyPlayingGuid.equals(episode.guid) && isPlaying;
            playButton.setText(isThisEpisodePlaying ? "Pause" : "Play");

            queueButton.setText(episode.isInQueue ? "Remove Queue" : "Queue");
            markPlayedButton.setText(episode.isPlayed ? "Mark Unplayed" : "Mark Played");

            downloadButton.setOnClickListener(v -> listener.onDownloadClick(episode));
            playButton.setOnClickListener(v -> listener.onPlayClick(episode));
            queueButton.setOnClickListener(v -> listener.onQueueClick(episode));
            deleteButton.setOnClickListener(v -> listener.onDeleteClick(episode));
            markPlayedButton.setOnClickListener(v -> listener.onMarkPlayedClick(episode));
        }
    }
}

