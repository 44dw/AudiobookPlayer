package com.a44dw.audiobookplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LifecycleService;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.a44dw.audiobookplayer.MainActivity.model;

public class AudiobookService extends LifecycleService {

    private MediaSessionCompat mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private PlayerHandler playerHandler;

    public static final int POSITION_REFRESH_INTERVAL = 1000;
    private static final int NOTIFICATION_ID = 700;
    private final String NOTIFICATION_CHANNEL_ID = "audio_book";

    private boolean audioFocusRequested = false;
    private boolean serviceIsStarted = false;
    public static LiveData<File> nowPlayingFile;

    //формирует метадату треков
    private final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

    private final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackStateCompat.ACTION_REWIND
                    | PlaybackStateCompat.ACTION_FAST_FORWARD
    );


    @Override
    public void onCreate() {
        Log.d(MainActivity.TAG, "Service -> onCreate()");
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(MainActivity.TAG, "Service -> onCreate() -> >= Build.VERSION_CODES.O");
            String channelName = getString(R.string.audiobook_notification_channel);

            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                                                                              channelName,
                                                                              NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setAcceptsDelayedFocusGain(false)
                    //.setWillPauseWhenDucked(true)
                    .setAudioAttributes(audioAttributes)
                    .build();
        }

        mediaSession = new MediaSessionCompat(this, "AudiobookService");
        mediaSession.setCallback(mediaSessionCallback);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                              MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        Context appCtx = getApplicationContext();

        mediaSession.setSessionActivity(PendingIntent.getActivity(appCtx,0,
                                                                  new Intent(appCtx, MainActivity.class), 0));

        //API >= 21 если наш MediaSession неактивен (setActive(false)), его пробудят бродкастом.
        //И для того, чтобы этот механизм работал, надо сообщить MediaSession, в какой ресивер отправлять бродкасты.
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, appCtx, MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(appCtx, 0, mediaButtonIntent, 0));

        playerHandler = new PlayerHandler();

        nowPlayingFile = AudiobookViewModel.getNowPlayingFile();
        nowPlayingFile.observe(this, new Observer<File>() {
            @Override
            public void onChanged(@Nullable File file) {
                Log.d(MainActivity.TAG, "Service -> onChanged nowPlayingFile: " + file.toString());
                mediaSessionCallback.onPlay();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(MainActivity.TAG, "Service -> onStartCommand()");
        serviceIsStarted = true;
        //ловит события (нажатия на кнопки) с внешних источников и передаёт их в MediaSessionCompat
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(MainActivity.TAG, "Service -> onDestroy()");
        super.onDestroy();
        mediaSession.release();
        playerHandler.player.release();
    }

    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {

        @Override
        public void onPlay() {
            Log.d(MainActivity.TAG, "Service -> onPlay()");
            //нужна проверка, стартовал ли уже сервис? Пока что вызывается при каждом новом файле
            if (!serviceIsStarted) startService(new Intent(getApplicationContext(), AudiobookService.class));

            playerHandler.play();

            //если файл не подходит по формату...
            if(!playerHandler.isPlaying()) {
                Log.d(MainActivity.TAG, "Service -> onPlay() -> !playerHandler.isPlaying(): now status is " + AudiobookViewModel.getPlayerStatus());
                switch (AudiobookViewModel.getPlayerStatus()) {
                    case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT: {
                        onSkipToNext();
                        break;
                    }
                    case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS: {
                        onSkipToPrevious();
                        break;
                    }
                }
            }

            if(!audioFocusRequested) {
                Log.d(MainActivity.TAG, "Service -> onPlay() -> request audio focus");
                int audioFocusResult;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusResult = audioManager.requestAudioFocus(audioFocusRequest);
                } else {
                    audioFocusResult = audioManager.requestAudioFocus(
                            audioFocusChangeListener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN);
                }
                if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d(MainActivity.TAG, "Service -> onPlay() -> request audio focus: unsuccessful");
                    return;
                }
                audioFocusRequested = true;
            }
            mediaSession.setActive(true);
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                                                                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                                                                1).build());
            playerHandler.startUpdatingCallbackWithPosition();

            doNotificetionOperation(PlaybackStateCompat.STATE_PLAYING);
        }

        @Override
        public void onPause() {
            playerHandler.pause();
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                                                                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                                                                1).build());
            doNotificetionOperation(PlaybackStateCompat.STATE_PAUSED);
        }

        @Override
        public void onSkipToNext() {
            playerHandler.stop();
            //меняем статус
            //если предыдущий файл не прочитался, статус меняется ещё раз на STATE_SKIPPING_TO_NEXT, зато в
            //МА -> onPlaybackStateChanged запускаются нужные методы
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1).build());

        }

        @Override
        public void onSkipToPrevious() {
            playerHandler.stop();
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1).build());
        }

        @Override
        public void onFastForward() {
            playerHandler.rewindForward();
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_REWINDING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1).build());
        }

        @Override
        public void onRewind() {
            playerHandler.rewindBack();
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_REWINDING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1).build());
        }

        @Override
        public void onStop() {
            super.onStop();

            if(audioFocusRequested) audioFocusRequested = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
            mediaSession.setActive(false);
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED,
                                          PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                                          1).build());
            //currentState = PlaybackStateCompat.STATE_STOPPED;
            doNotificetionOperation(PlaybackStateCompat.STATE_STOPPED);
            serviceIsStarted = false;
            stopSelf();
        }

        @Override
        public void onSeekTo(long newPosition) {
            playerHandler.seekTo(newPosition);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        Log.d(MainActivity.TAG, "Service -> onBind()");
        return new AudiobookServiceBinder();
    }

    public class AudiobookServiceBinder extends Binder {
        public MediaSessionCompat.Token getMediaSessionToken() {
            Log.d(MainActivity.TAG, "Service -> onBind() -> AudiobookServiceBinder: getMediaSessionToken()");
            return mediaSession.getSessionToken();
        }
    }

    //для запроса аудио-фокуса на старых платформах
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        //если какое-то приложение запрашивает фокус
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(MainActivity.TAG, "Service -> OnAudioFocusChangeListener -> onAudioFocusChange");
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    mediaSessionCallback.onPlay();
                    break;
                default:
                    mediaSessionCallback.onPause();
                    break;
            }
        }
    };

    private void doNotificetionOperation(int playbackState) {
        Log.d(MainActivity.TAG, "Service -> doNotificetionOperation");
        switch (playbackState) {
            case PlaybackStateCompat.STATE_PLAYING: {
                Notification audiobookNotif = getNotification(playbackState);
                startForeground(NOTIFICATION_ID, audiobookNotif);
                break;
            }
            case PlaybackStateCompat.STATE_PAUSED: {
                NotificationManagerCompat.from(AudiobookService.this).notify(NOTIFICATION_ID, getNotification(playbackState));
                stopForeground(false);
                break;
            }
            default: {
                stopForeground(true);
                break;
            }
        }
    }

    private Notification getNotification(int playbackState) {
        Log.d(MainActivity.TAG, "Service -> getNotification");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        builder.addAction(new NotificationCompat.Action(R.drawable.ic_fast_rewind_black_24dp,
                getString(R.string.rewind_back),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND)
        ));

        builder.addAction(new NotificationCompat.Action(R.drawable.ic_skip_previous_black_24dp,
                getString(R.string.skip_prev),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        ));

        if(playbackState == PlaybackStateCompat.STATE_PLAYING)
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_pause_black_24dp,
                    getString(R.string.pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)
            ));
        else
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_play_arrow_black_24dp,
                    getString(R.string.play),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)
            ));

        builder.addAction(new NotificationCompat.Action(R.drawable.ic_skip_next_black_24dp,
                getString(R.string.skip_next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        ));

        builder.addAction(new NotificationCompat.Action(R.drawable.ic_fast_forward_black_24dp,
                getString(R.string.rewind_forward),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_FAST_FORWARD)
        ));
        builder.setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)));
        builder.setSmallIcon(R.mipmap.ic_launcher);
        //builder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        builder.setShowWhen(false);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setOnlyAlertOnce(true);

        return builder.build();
    }

    private class PlayerHandler {

        private MediaPlayer player;
        //для обновления SeekBar в audioPlayerFragment
        private ScheduledExecutorService mExecutor;
        private Runnable mSeekbarPositionUpdateTask;

        public void play() {
            Log.d(MainActivity.TAG, "PlayerHandler -> play()");
            if(AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PAUSED) resume();
            else loadMediaAndStartPlayer(nowPlayingFile.getValue());
        }

        private void loadMediaAndStartPlayer(File media) {
            Log.d(MainActivity.TAG, "PlayerHandler -> loadMediaAndStartPlayer: load file");
            loadMedia(media);
            player.start();
            if(isPlaying()) AudiobookViewModel.updateNowPlayingMediaDuration(player.getDuration());
        }

        private void loadMedia(File media) {
            if(player == null)initializeMediaPlayer();
            try {
                player.setDataSource(media.toString());
                Log.d(MainActivity.TAG, "PlayerHandler -> loadMedia(): loaded media name " + media);
                player.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void initializeMediaPlayer() {
            Log.d(MainActivity.TAG, "PlayerHandler -> initializeMediaPlayer()");
            player = new MediaPlayer();
        }

        public Boolean isPlaying() {
            return player != null && player.isPlaying();
        }

        public int getCurrentPosition() {
            Log.d(MainActivity.TAG, "PlayerHandler -> getCurrentPosition()");
            int currentPosition = 0;
            if(isPlaying()) currentPosition = player.getCurrentPosition();
            return currentPosition;
        }

        private void pause() {
            Log.d(MainActivity.TAG, "PlayerHandler -> pause()");
            stopUpdatingCallbackWithPosition();
            player.pause();
        }

        private void resume() {
            Log.d(MainActivity.TAG, "PlayerHandler -> resume()");
            player.start();
        }

        private void stop() {
            Log.d(MainActivity.TAG, "PlayerHandler -> stop()");
            stopUpdatingCallbackWithPosition();
            if(isPlaying()) player.stop();
            player.reset();
        }

        public void rewindBack() {
            Log.d(MainActivity.TAG, "PlayerHandler -> rewindBack()");
            int playerPosition = player.getCurrentPosition();
            int newPosition = playerPosition - 5*1000;
            player.seekTo(newPosition > 0 ? newPosition : 0);
        }

        public void rewindForward() {
            Log.d(MainActivity.TAG, "PlayerHandler -> rewindForward()");
            int playerPosition = player.getCurrentPosition();
            int newPosition = playerPosition + 5*1000;
            if(newPosition > player.getDuration()) mediaSessionCallback.onSkipToNext();
            else player.seekTo(newPosition);
        }

        public void seekTo(long newPosition) {
            Log.d(MainActivity.TAG, "PlayerHandler -> seekTo()");
            if(player != null) player.seekTo((int)newPosition);
        }

        //Обновление seekBar в AudioFragment
        private void startUpdatingCallbackWithPosition() {
            Log.d(MainActivity.TAG, "PlayerHandler -> startUpdatingCallbackWithPosition()");
            if(mExecutor == null) mExecutor = Executors.newSingleThreadScheduledExecutor();
            if(mSeekbarPositionUpdateTask == null) mSeekbarPositionUpdateTask = new Runnable() {
                @Override
                public void run() {
                    updateProgressCallbackTask();
                }
            };
            mExecutor.scheduleAtFixedRate(mSeekbarPositionUpdateTask, 0, POSITION_REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
        }

        private void stopUpdatingCallbackWithPosition() {
            if(mExecutor != null) {
                mExecutor.shutdownNow();
                mExecutor = null;
                mSeekbarPositionUpdateTask = null;
            }
        }

        private void updateProgressCallbackTask() {
            if(playerHandler.isPlaying()) {
                Log.d(MainActivity.TAG, "AudioPlayerHandler -> updateProgressCallbackTask()");
                Intent broadcastIntent = new Intent();
                broadcastIntent.putExtra(MainActivity.playbackStatus, playerHandler.getCurrentPosition());
                broadcastIntent.setAction(MainActivity.seekBarBroadcastName);
                sendBroadcast(broadcastIntent);
            }
        }
    }
}
