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
import android.media.MediaMetadataRetriever;
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
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AudiobookService extends LifecycleService {

    //классы, ответственные за воспроизведение звука и управление плеером
    private MediaSessionCompat mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private PlayerHandler playerHandler;

    //классы LiveData
    public static LiveData<Chapter> nowPlayingFile;
    public static LiveData<ArrayList<Chapter>> playlist;
    //public static ArrayList<Chapter> updatablePlaylist;

    //класс, ответственный за нотификации
    private AudiobookNotificationManager audiobookNotificationManager;

    //константы и флаги
    public static final int POSITION_REFRESH_INTERVAL = 500;
    private static final int NOTIFICATION_ID = 700;
    private final String NOTIFICATION_CHANNEL_ID = "audio_book";
    private boolean audioFocusRequested = false;
    private boolean serviceIsStarted = false;

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

    //класс, ответственный за метадату треков
    private final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

    @Override
    public void onCreate() {
        Log.d(MainActivity.TAG, "Service -> onCreate()");
        super.onCreate();

        audiobookNotificationManager = new AudiobookNotificationManager();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(MainActivity.TAG, "Service -> onCreate() -> >= Build.VERSION_CODES.O");

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

        //API >= 21 если наш MediaSession неактивен (setActive(false)), его пробудят бродкастом.
        //И для того, чтобы этот механизм работал, надо сообщить MediaSession, в какой ресивер отправлять бродкасты.
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, appCtx, MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(appCtx, 0, mediaButtonIntent, 0));

        playerHandler = new PlayerHandler();

        nowPlayingFile = AudiobookViewModel.getNowPlayingFile();
        nowPlayingFile.observe(this, new Observer<Chapter>() {
            @Override
            public void onChanged(@Nullable Chapter chapter) {
                if(chapter != null) {
                    Log.d(MainActivity.TAG, "Service -> onChanged nowPlayingFile: " + chapter.getFile().toString());
                    mediaSessionCallback.onPlay();
                } else Toast.makeText(getApplicationContext(),
                        getString(R.string.end_of_dir),
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });
        playlist = AudiobookViewModel.getPlaylist();
        playlist.observe(this, new Observer<ArrayList<Chapter>>() {
            @Override
            public void onChanged(@Nullable ArrayList<Chapter> playlist) {
                Log.d(MainActivity.TAG, "Service -> onChanged playlist:");
                if(playlist != null) {
                    for(Chapter chapter : playlist) Log.d(MainActivity.TAG, chapter.getFile().toString());
                    //updatablePlaylist = (ArrayList<Chapter>) playlist.clone();
                }
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
            if (!serviceIsStarted) startService(new Intent(getApplicationContext(), AudiobookService.class));

            playerHandler.play();

            switch (playerHandler.isPlaying() ? 1 : 0) {
                //если файл подходит по формату...
                case(1): {
                    //AudiobookViewModel.updateNowPlayingMediaDuration(playerHandler.getDuration());

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
                    //диспатчим бродкаст обновления сикбара
                    playerHandler.startUpdatingCallbackWithPosition();

                    //собираем метадату (непонятно, как её потом использовать)
                    //mediaSession.setMetadata(playerHandler.collectMetadata());

                    audiobookNotificationManager.doNotificetionOperation(PlaybackStateCompat.STATE_PLAYING);
                    break;
                }
                //если не подходит...
                case(0): {
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
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                                                                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                                                                1).build());
            audiobookNotificationManager.doNotificetionOperation(PlaybackStateCompat.STATE_PAUSED);
        }

        @Override
        public void onSkipToNext() {
            int position = playerHandler.getCurrentPosition();
            //playerHandler.stop();
            File npfile = nowPlayingFile.getValue().getFile();
            ArrayList<Chapter> nplist = playlist.getValue();
            if(npfile == null) return;
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1).build());
            boolean find = false;
            for(int i=0; i<nplist.size(); i++) {
                Chapter ch = nplist.get(i);
                if(find) {
                    AudiobookViewModel.updateNowPlayingFile(ch);
                    return;
                }
                if(ch.getFile().equals(npfile)) {
                    //запоминаем позицию
                    Log.d(MainActivity.TAG, "PlayerHandler -> onSkipToNext: current position is " + position);
                    nplist.get(i).setProgress(position);

                    //не работает?
                    if(position == ch.getDuration()) nplist.get(i).setDone(true);

                    find = true;
                }
            }
            //если текущий файл - последний в каталоге
            Toast.makeText(getApplicationContext(),
                    getString(R.string.end_of_dir),
                    Toast.LENGTH_SHORT)
                    .show();
            onStop();
        }

        @Override
        public void onSkipToPrevious() {
            int position = playerHandler.getCurrentPosition();
            //playerHandler.stop();
            File npfile = nowPlayingFile.getValue().getFile();
            ArrayList<Chapter> nplist = playlist.getValue();
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1).build());
            boolean find = false;
            for(int i = nplist.size()-1; i>=0; i--) {
                Chapter ch = nplist.get(i);
                if(find) {
                    AudiobookViewModel.updateNowPlayingFile(nplist.get(i));
                    return;
                }
                if(ch.getFile().equals(npfile)) {
                    //запоминаем позицию
                    ch.setProgress(position);

                    find = true;
                }
            }
            //если текущий файл - первый в каталоге
            AudiobookViewModel.updateNowPlayingFile(nowPlayingFile.getValue());
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
            audiobookNotificationManager.doNotificetionOperation(PlaybackStateCompat.STATE_STOPPED);
            serviceIsStarted = false;
            stopSelf();
        }

        @Override
        public void onSeekTo(long newPosition) {
            playerHandler.seekTo(newPosition);
        }
    };

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

    //класс, ответственный за нотификации
    private class AudiobookNotificationManager {

        public void getNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelName = getString(R.string.audiobook_notification_channel);

                //выставил IMPORTANCE_LOW, чтобы убрать звук уведомления
                NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                        channelName,
                        NotificationManager.IMPORTANCE_LOW);
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }

        public void doNotificetionOperation(int playbackState) {
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

        public Notification getNotification(int playbackState) {
            Log.d(MainActivity.TAG, "Service -> getNotification");

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID);
            Map<String, String> metadata = playerHandler.getMetadata();

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
            builder.setSmallIcon(R.mipmap.ic_launcher);
            builder.setContentTitle(metadata.get("author"));
            builder.setContentText(metadata.get("title"));
            //builder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
            builder.setShowWhen(false);
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
            builder.setOnlyAlertOnce(true);

            return builder.build();
        }

    }

    //класс плеера
    private class PlayerHandler {

        private MediaPlayer player;
        //сбор метадаты
        private MediaMetadataRetriever metadataRetriever;
        //для обновления SeekBar в audioPlayerFragment
        private ScheduledExecutorService mExecutor;
        private Runnable mSeekbarPositionUpdateTask;

        public void play() {
            Log.d(MainActivity.TAG, "PlayerHandler -> play()");
            int status = AudiobookViewModel.getPlayerStatus();
            if(status == PlaybackStateCompat.STATE_PAUSED) resume();
            else {
                if(status == PlaybackStateCompat.STATE_PLAYING) stop();
                Chapter ch = nowPlayingFile.getValue();
                //если файл закончен, начинаем сначала, если нет - с текущей позиции
                int position = (ch.isDone() ? 0 : (int)ch.getProgress());
                loadMediaAndStartPlayer(ch.getFile(), position);
            }
        }

        private void loadMediaAndStartPlayer(File media, int position) {
            Log.d(MainActivity.TAG, "PlayerHandler -> loadMediaAndStartPlayer: load file, position is: " + position);
            loadMedia(media);
            if(position > 0) player.seekTo(position);
            player.start();
            //плохо, хорошо бы операции с ViewModel вывести из PlayerHandler
            /*if(isPlaying()) {
                AudiobookViewModel.updateNowPlayingMediaDuration(getDuration());
                AudiobookViewModel.updateNowPlayingMediaMetadata(getMetadata());
            }*/
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
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.d(MainActivity.TAG, "PlayerHandler -> initializeMediaPlayer() -> setOnCompletionListener()");
                    //плохо, хорошо бы операции с mediaSessionCallback вывести из PlayerHandler
                    mediaSessionCallback.onSkipToNext();
                }
            });
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

        public int getDuration() {
            Log.d(MainActivity.TAG, "PlayerHandler -> getDuration()");
            return player.getDuration();
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
            if(newPosition > getDuration()) mediaSessionCallback.onSkipToNext();
            else player.seekTo(newPosition);
        }

        public void seekTo(long newPosition) {
            Log.d(MainActivity.TAG, "PlayerHandler -> seekTo()");
            if(player != null) player.seekTo((int)newPosition);
        }

        //сбор метадаты
        public MediaMetadataCompat collectMetadata() {
            metadataRetriever = new MediaMetadataRetriever();
            metadataRetriever.setDataSource(nowPlayingFile.getValue().toString());
            String author = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String title = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String trackNum = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            String trackAmount = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
            String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

            return metadataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, author)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, Long.parseLong(trackNum))
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, Long.parseLong(trackAmount))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Long.parseLong(duration)).build();
        }

        public Map<String, String> getMetadata() {
            Log.d(MainActivity.TAG, "PlayerHandler -> getMetadata()");
            metadataRetriever = new MediaMetadataRetriever();
            metadataRetriever.setDataSource(nowPlayingFile.getValue().getFile().toString());
            String author = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String title = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            Map<String, String> metaMap = new HashMap<>();
            metaMap.put("author", author);
            metaMap.put("title", title);
            return metaMap;
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
