package com.lohrumipsum.inkypod;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommands;
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
            MediaSession.ConnectionResult connectionResult = MediaSession.Callback.super.onConnect(session, controller);
            SessionCommands.Builder availableCommandsBuilder = connectionResult.availableSessionCommands.buildUpon();
            
            availableCommandsBuilder.add(Player.COMMAND_PLAY_PAUSE);
            availableCommandsBuilder.add(Player.COMMAND_SEEK_BACK);
            availableCommandsBuilder.add(Player.COMMAND_SEEK_FORWARD);
            availableCommandsBuilder.add(Player.COMMAND_STOP);
            
            return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableCommandsBuilder.build())
                    .build();
        }

        @Override
        public com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(MediaSession mediaSession, MediaSession.ControllerInfo controller, java.util.List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
            exoPlayer.setMediaItems(mediaItems, startIndex, startPositionMs);
            exoPlayer.prepare();
            exoPlayer.play();
            startForeground(NOTIFICATION_ID, buildNotification());
            return MediaSession.Callback.super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs);
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
        intent.setComponent(new android.content.ComponentName(this, "androidx.media3.session.MediaButtonReceiver"));
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
        // **THE FIX:** Save position of the current item before the service is destroyed.
        if (exoPlayer.isPlaying() && exoPlayer.getCurrentMediaItem() != null) {
            repository.savePlaybackPosition(exoPlayer.getCurrentMediaItem().mediaId, exoPlayer.getCurrentPosition(), exoPlayer.getDuration());
        }
        mediaSession.release();
        exoPlayer.release();
        super.onDestroy();
    }
}

