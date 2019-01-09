package com.a44dw.audiobookplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.arch.lifecycle.LifecycleService;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.preference.PreferenceManager;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AudiobookService extends LifecycleService {

    private MediaSessionCompat mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private PlayerHandler playerHandler;

    private LiveData<Chapter> currentChapter;

    private AudiobookNotificationManager audiobookNotificationManager;

    private BookRepository repository = BookRepository.getInstance();

    public static int currentState = PlaybackStateCompat.STATE_NONE;

    private static final int POSITION_REFRESH_INTERVAL = 500;
    private static final int NOTIFICATION_ID = 700;
    private static final int RETURN_PROGRESS = 5000;
    private final String NOTIFICATION_CHANNEL_ID = "audio_book";
    private boolean audioFocusRequested = false;
    private boolean serviceIsStarted = false;
    private boolean autopause = false;
    private boolean registeredBecomingNoisy = false;
    public static float nowSpeed = 1.0f;

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
        super.onCreate();

        audiobookNotificationManager = new AudiobookNotificationManager();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            audiobookNotificationManager.getNotificationChannel();

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setAcceptsDelayedFocusGain(false)
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
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, appCtx, MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(appCtx, 0, mediaButtonIntent, 0));

        playerHandler = new PlayerHandler();

        currentChapter = repository.getCurrentChapter();
        currentChapter.observe(this, new Observer<Chapter>() {
            @Override
            public void onChanged(@Nullable Chapter chapter) {
                if(chapter != null) {
                    //если программа только запущена, воспроизведение паузится
                    if(currentState == PlaybackStateCompat.STATE_NONE) {
                        mediaSessionCallback.onPlay();
                        mediaSessionCallback.onPause();
                    } else {
                        mediaSessionCallback.onPlay();
                    }
                } else {
                    if((currentState != PlaybackStateCompat.STATE_NONE)&&
                       (currentState != PlaybackStateCompat.STATE_STOPPED)){
                        mediaSessionCallback.onStop();
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.end_of_dir),
                                Toast.LENGTH_SHORT)
                                .show();
                    }
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceIsStarted = true;
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        repository.saveCurrentBookAndChapter();
        mediaSession.release();
        if(playerHandler.player != null) playerHandler.player.release();
    }

    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {

            if (!serviceIsStarted) startService(new Intent(getApplicationContext(), AudiobookService.class));

            if(currentState == PlaybackStateCompat.STATE_PAUSED) {
                if(!autopause) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    String autorewindValue = prefs.getString(Preferences.KEY_REWIND_AUTO, "2");

                    playerHandler.rewindBack(autorewindValue);
                } else {
                    autopause = false;
                }
                playerHandler.resume();
            } else playerHandler.play();

            switch (playerHandler.isPlaying() ? 1 : 0) {
                case(1): {
                    if(!audioFocusRequested) {
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
                            playerHandler.pause();
                            return;
                        }
                        audioFocusRequested = true;
                    }
                    mediaSession.setActive(true);
                    registerReceiver(becomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                    registeredBecomingNoisy = true;

                    currentState = PlaybackStateCompat.STATE_PLAYING;
                    mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                            1).build());
                    playerHandler.startUpdatingCallbackWithPosition();

                    audiobookNotificationManager.doNotificetionOperation(PlaybackStateCompat.STATE_PLAYING);
                    break;
                }
                case(0): {
                    switch (currentState) {
                        case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT: {
                            onSkipToNext();
                            break;
                        }
                        case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS: {
                            onSkipToPrevious();
                            break;
                        }
                        default: {
                            Toast.makeText(getApplicationContext(),
                                    getString(R.string.wrong_file),
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                    break;
                }

            }
        }

        @Override
        public void onPause() {
            playerHandler.pause();
            if(registeredBecomingNoisy) {
                unregisterReceiver(becomingNoisyReceiver);
                registeredBecomingNoisy = false;
            }
            currentState = PlaybackStateCompat.STATE_PAUSED;
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                                                                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                                                                1).build());
            audiobookNotificationManager.doNotificetionOperation(PlaybackStateCompat.STATE_PAUSED);
        }

        @Override
        public void onSkipToNext() {
            if((repository.getBook() == null)||
            (repository.getCurrentChapter().getValue().bId != repository.getBook().bookId)) return;

            currentState = PlaybackStateCompat.STATE_SKIPPING_TO_NEXT;
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1).build());

            repository.skipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            if((repository.getBook() == null)||
            (repository.getCurrentChapter().getValue().bId != repository.getBook().bookId)) return;

            if(playerHandler.getCurrentPosition() > RETURN_PROGRESS) {
                playerHandler.startFromBeginning();
            } else {
                currentState = PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS;
                mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                            1).build());

                repository.skipToPrevious();
            }
        }

        @Override
        public void onFastForward() {
            String sec = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                    .getString(Preferences.KEY_REWIND_RIGHT, "10");
            playerHandler.rewindForward(sec);
        }

        @Override
        public void onRewind() {

            String sec = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                    .getString(Preferences.KEY_REWIND_LEFT, "10");
            playerHandler.rewindBack(sec);
        }

        @Override
        public void onStop() {
            super.onStop();
            playerHandler.stop();
            if(registeredBecomingNoisy) {
                unregisterReceiver(becomingNoisyReceiver);
                registeredBecomingNoisy = false;
            }
            if(audioFocusRequested) audioFocusRequested = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
            mediaSession.setActive(false);
            currentState = PlaybackStateCompat.STATE_STOPPED;
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED,
                                          PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                                          1).build());
            audiobookNotificationManager.doNotificetionOperation(PlaybackStateCompat.STATE_STOPPED);

            serviceIsStarted = false;
            stopSelf();
        }

        @Override
        public void onSeekTo(long newPosition) {
            repository.updateNowPlayingPosition((int)newPosition);
            playerHandler.seekTo(newPosition);
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            if(action.equals(BookmarkFragment.BOOKMARK_TIME_TAG)) {
                long time = extras.getLong(BookmarkFragment.BOOKMARK_TIME_TAG);
                playerHandler.seekTo(time);
            }
            if(action.equals(AudioPlayerFragment.SPEED_CHANGE_TAG)) {
                float speed = extras.getFloat(AudioPlayerFragment.SPEED_CHANGE_TAG);
                nowSpeed = speed;
                playerHandler.setSpeedTo(speed);
            }
        }
    };

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        //если какое-то приложение запрашивает фокус
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    if(autopause) mediaSessionCallback.onPlay();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    mediaSessionCallback.onPause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    autopause = true;
                    mediaSessionCallback.onPause();
                    break;
                default:
                    if(currentState == PlaybackStateCompat.STATE_PLAYING) {
                        audioFocusRequested = false;
                        mediaSessionCallback.onPause();
                    }
                    break;
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return new AudiobookServiceBinder();
    }

    public class AudiobookServiceBinder extends Binder {
        public MediaSessionCompat.Token getMediaSessionToken() {
            return mediaSession.getSessionToken();
        }
    }

    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                mediaSessionCallback.onPause();
            }
        }
    };

    private class AudiobookNotificationManager {

        void getNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelName = getString(R.string.audiobook_notification_channel);

                NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                        channelName,
                        NotificationManager.IMPORTANCE_LOW);
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }

        public void doNotificetionOperation(int playbackState) {
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

        Notification getNotification(int playbackState) {

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID);
            Map<String, Object> metadata = playerHandler.getMetadata();

            builder.addAction(new NotificationCompat.Action(R.drawable.ic_fast_rewind_black_24dp,
                    getString(R.string.rewind_back),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(getApplicationContext(), PlaybackStateCompat.ACTION_REWIND)
            ));

            builder.addAction(new NotificationCompat.Action(R.drawable.ic_skip_previous_black_24dp,
                    getString(R.string.skip_prev),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(getApplicationContext(), PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            ));

            if(playbackState == PlaybackStateCompat.STATE_PLAYING)
                builder.addAction(new NotificationCompat.Action(R.drawable.ic_pause_black_24dp,
                        getString(R.string.pause),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(getApplicationContext(), PlaybackStateCompat.ACTION_PAUSE)
                ));
            else
                builder.addAction(new NotificationCompat.Action(R.drawable.ic_play_arrow_black_24dp,
                        getString(R.string.play),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(getApplicationContext(), PlaybackStateCompat.ACTION_PLAY)
                ));

            builder.addAction(new NotificationCompat.Action(R.drawable.ic_skip_next_black_24dp,
                    getString(R.string.skip_next),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(getApplicationContext(), PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            ));

            builder.addAction(new NotificationCompat.Action(R.drawable.ic_fast_forward_black_24dp,
                    getString(R.string.rewind_forward),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(getApplicationContext(), PlaybackStateCompat.ACTION_FAST_FORWARD)
            ));
            builder.setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(1)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(getApplicationContext(), PlaybackStateCompat.ACTION_STOP)));

            Bitmap coverBitmap = (Bitmap) metadata.get("cover");
            builder.setSmallIcon(R.mipmap.ic_launcher);
            builder.setLargeIcon(coverBitmap != null ? coverBitmap :
                    BitmapFactory.decodeResource(getResources(), R.drawable.ic_for_notif));
            builder.setContentTitle((String)metadata.get("author"));
            builder.setContentText((String)metadata.get("title"));
            builder.setShowWhen(false);
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
            builder.setOnlyAlertOnce(true);

            return builder.build();
        }

    }

    //класс плеера
    private class PlayerHandler {

        private MediaPlayer player;
        private ScheduledExecutorService mExecutor;
        private Runnable mSeekbarPositionUpdateTask;

        public void play() {
            boolean statusNotPlayed = (currentState == PlaybackStateCompat.STATE_STOPPED ||
                    currentState == PlaybackStateCompat.STATE_NONE);
            if(!statusNotPlayed) stop();
            Chapter ch = repository.getCurrentChapter().getValue();
            int position = (ch.done ? 0 : (int)ch.progress);
            loadMediaAndStartPlayer(new File(ch.filepath), position);
        }

        private void loadMediaAndStartPlayer(File media, int position) {
            loadMedia(media);
            if(position > 0) player.seekTo(position);
            if(player != null) player.start();
        }

        private void loadMedia(File media) {
            if(player == null)initializeMediaPlayer();
            try {
                player.setDataSource(media.toString());
                player.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void initializeMediaPlayer() {
            player = new MediaPlayer();
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stop();
                    boolean toChapterEnd = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                            .getBoolean(Preferences.TO_CHAPTER_END, false);

                    if(!toChapterEnd) mediaSessionCallback.onSkipToNext();
                }
            });
        }

        public Boolean isPlaying() {
            return player != null && player.isPlaying();
        }

        public int getCurrentPosition() {
            int currentPosition = 0;
            if(isPlaying()) currentPosition = player.getCurrentPosition();
            return currentPosition;
        }

        public int getDuration() {
            if(player != null) return player.getDuration();
            return 0;
        }

        private void pause() {
            stopUpdatingCallbackWithPosition();
            if(player != null) player.pause();
        }

        public void resume() {
            if(player != null) player.start();
        }

        private void stop() {
            stopUpdatingCallbackWithPosition();
            if(player != null) {
                if(isPlaying()) player.stop();
                player.reset();
            }
        }

        public void rewindBack(String secs) {
            if(player != null) {
                int playerPosition = player.getCurrentPosition();
                int newPosition = playerPosition - Integer.parseInt(secs) * 1000;
                player.seekTo(newPosition > 0 ? newPosition : 0);
            }
        }

        public void startFromBeginning() {
            player.seekTo(0);
        }

        public void rewindForward(String secs) {
            if(player != null) {
                int playerPosition = player.getCurrentPosition();
                int newPosition = playerPosition + Integer.parseInt(secs) * 1000;
                if(newPosition > getDuration()) mediaSessionCallback.onSkipToNext();
                else player.seekTo(newPosition);
            }
        }

        public void seekTo(long newPosition) {
            if(player != null) player.seekTo((int)newPosition);
        }

        public Map<String, Object> getMetadata() {
            MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
            metadataRetriever.setDataSource(new File(currentChapter.getValue().filepath).toString());
            String author = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String title = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if((title == null)||(title.length() == 0))
                title = new File(currentChapter.getValue().filepath).getName();
            byte[] art = metadataRetriever.getEmbeddedPicture();
            Bitmap coverBitmap = null;
            if(art != null) {
                coverBitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
            }
            Map<String, Object> metaMap = new HashMap<>();
            metaMap.put("author", author);
            metaMap.put("title", title);
            metaMap.put("cover", coverBitmap);

            metadataRetriever.release();

            return metaMap;
        }

        //Обновление seekBar в AudioFragment
        private void startUpdatingCallbackWithPosition() {
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
                int position = playerHandler.getCurrentPosition();
                repository.updateNowPlayingPosition(position);
                Intent broadcastIntent = new Intent();
                broadcastIntent.putExtra(MainActivity.PLAYBACK_STATUS, position);
                broadcastIntent.setAction(MainActivity.SEEK_BAR_BROADCAST_NAME);
                sendBroadcast(broadcastIntent);
            }
        }

        public void setSpeedTo(float newSpeed) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(newSpeed));
                nowSpeed = newSpeed;
            }
        }
    }
}
