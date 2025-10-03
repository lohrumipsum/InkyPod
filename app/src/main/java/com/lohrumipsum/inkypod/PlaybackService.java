package com.lohrumipsum.inkypod;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommands;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;
    private ExoPlayer exoPlayer;
    private PodcastRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String CHANNEL_ID = "InkyPodPlaybackChannel";
    private static final int NOTIFICATION_ID = 123;
    private static final String TAG = "InkyPodDebug";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        exoPlayer = new ExoPlayer.Builder(this)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        mediaSession = new MediaSession.Builder(this, exoPlayer)
                .setCallback(new CustomMediaSessionCallback())
                .build();

        repository = new PodcastRepository(getApplication());

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                PodcastViewModel.isPlaying.postValue(isPlaying);
                if (!isPlaying && exoPlayer.getCurrentMediaItem() != null) {
                    repository.savePlaybackPosition(exoPlayer.getCurrentMediaItem().mediaId, exoPlayer.getCurrentPosition(), exoPlayer.getDuration());
                }
                updateNotification();
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (mediaItem != null) {
                    PodcastViewModel.currentlyPlayingGuid.postValue(mediaItem.mediaId);
                }
                updateNotification();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    MediaItem finishedItem = exoPlayer.getCurrentMediaItem();
                    if (finishedItem != null && finishedItem.mediaId != null) {
                        handleEpisodeCompletion(finishedItem.mediaId);
                    }
                }
            }
        });
    }

    private void handleEpisodeCompletion(String finishedGuid) {
        executor.execute(() -> {
            // **THE FIX:** Get the DAO to perform synchronous database operations.
            PodcastDao dao = repository.getDao();
            Episode finishedEpisode = dao.getEpisodeByGuidSync(finishedGuid);

            if (finishedEpisode != null) {
                Log.d(TAG, "Episode finished: " + finishedEpisode.title);

                String pathToDelete = finishedEpisode.localFilePath;
                boolean wasInQueue = finishedEpisode.isInQueue;

                finishedEpisode.isPlayed = true;
                finishedEpisode.playbackPosition = 0;
                if (wasInQueue) {
                    finishedEpisode.isInQueue = false;
                }
                // **THE FIX:** Perform a synchronous update to prevent race conditions.
                dao.updateEpisode(finishedEpisode);

                if (wasInQueue) {
                    // **THE FIX:** Get the fresh queue *after* the synchronous update.
                    List<Episode> nextQueue = dao.getQueueSync();
                    Episode nextEpisodeToPlay = null;

                    for (Episode next : nextQueue) {
                        if (next.isDownloaded && next.localFilePath != null && !next.localFilePath.isEmpty()) {
                            nextEpisodeToPlay = next;
                            break; 
                        }
                    }

                    if (nextEpisodeToPlay != null) {
                        final Episode finalNextEpisode = nextEpisodeToPlay;
                        Log.d(TAG, "Playing next in queue: " + finalNextEpisode.title);
                        
                        new Handler(Looper.getMainLooper()).post(() -> {
                            MediaItem mediaItem = new MediaItem.Builder()
                                    .setMediaId(finalNextEpisode.guid)
                                    .setUri(Uri.fromFile(new File(finalNextEpisode.localFilePath)))
                                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(finalNextEpisode.title).build())
                                    .build();
                            exoPlayer.setMediaItem(mediaItem, finalNextEpisode.playbackPosition);
                            exoPlayer.prepare();
                            exoPlayer.play();
                        });
                    } else {
                        Log.d(TAG, "No more downloaded episodes in queue.");
                    }
                }

                if (wasInQueue && pathToDelete != null && !pathToDelete.isEmpty()) {
                    File file = new File(pathToDelete);
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted downloaded file for completed queue item: " + pathToDelete);
                        } else {
                            Log.e(TAG, "Failed to delete file: " + pathToDelete);
                        }
                    }
                }
            }
        });
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    private class CustomMediaSessionCallback implements MediaSession.Callback {
        @Override
        public MediaSession.ConnectionResult onConnect(MediaSession session, MediaSession.ControllerInfo controller) {
            SessionCommands sessionCommands = new SessionCommands.Builder()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_BACK)
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .add(Player.COMMAND_STOP)
                    .build();
            return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build();
        }
    }

    private void updateNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        MediaItem currentItem = exoPlayer.getCurrentMediaItem();
        String title = (currentItem != null && currentItem.mediaMetadata.title != null) ? currentItem.mediaMetadata.title.toString() : "No Title";

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("InkyPod")
                .setSmallIcon(R.drawable.ic_queue)
                .setContentIntent(pendingIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionCompatToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setSilent(true);

        builder.addAction(R.drawable.ic_rewind, "Rewind", createMediaButtonPendingIntent(Player.COMMAND_SEEK_BACK));
        
        if (exoPlayer.isPlaying()) {
            builder.addAction(R.drawable.ic_episodes, "Pause", createMediaButtonPendingIntent(Player.COMMAND_PLAY_PAUSE));
        } else {
            builder.addAction(R.drawable.ic_episodes, "Play", createMediaButtonPendingIntent(Player.COMMAND_PLAY_PAUSE));
        }

        builder.addAction(R.drawable.ic_fast_forward, "Forward", createMediaButtonPendingIntent(Player.COMMAND_SEEK_FORWARD));
        
        return builder.build();
    }

    private PendingIntent createMediaButtonPendingIntent(int command) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.setComponent(new ComponentName(this, "androidx.media3.session.MediaButtonReceiver"));
        Bundle extras = new Bundle();
        extras.putInt("androidx.media3.session.command", command);
        intent.putExtras(extras);
        return PendingIntent.getBroadcast(this, command, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.playback_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (exoPlayer.isPlaying() && exoPlayer.getCurrentMediaItem() != null) {
            repository.savePlaybackPosition(exoPlayer.getCurrentMediaItem().mediaId, exoPlayer.getCurrentPosition(), exoPlayer.getDuration());
        }
        mediaSession.release();
        exoPlayer.release();
        super.onDestroy();
    }
}

