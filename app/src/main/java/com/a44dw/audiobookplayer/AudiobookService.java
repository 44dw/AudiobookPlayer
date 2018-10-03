package com.a44dw.audiobookplayer;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_END_OF_DIR;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_PAUSE;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_PLAY;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_SKIP_TO_NEXT;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_SKIP_TO_PREVIOUS;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_STOP;
import static com.a44dw.audiobookplayer.AudiobookViewModel.fileManagerHandler;
import static com.a44dw.audiobookplayer.AudiobookViewModel.playerStatus;

public class AudiobookService extends Service {

    private MediaSessionCompat mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean audioFocusRequested = false;
    private PlayerHandler playerHandler;

    //формирует метадату треков
    private final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

    private final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    );


    @Override
    public void onCreate() {
        Log.d(MainActivity.TAG, "Service -> onCreate()");
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(MainActivity.TAG, "Service -> onStartCommand()");
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

        int currentState = PlaybackStateCompat.STATE_STOPPED;

        @Override
        public void onPlay() {
            Log.d(MainActivity.TAG, "Service -> onPlay()");
            //нужна проверка, стартовал ли уже сервис?
            startService(new Intent(getApplicationContext(), AudiobookService.class));

            playerHandler.play();

            if(!audioFocusRequested) {
                Log.d(MainActivity.TAG, "Service -> onPlay() -> request audio focus");
                int audioFocusResult;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusResult = audioManager.requestAudioFocus(audioFocusRequest);
                } else {
                    audioFocusResult = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
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
            currentState = PlaybackStateCompat.STATE_PLAYING;
        }

        @Override
        public void onPause() {
            super.onPause();
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
        }

        @Override
        public void onStop() {
            super.onStop();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
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

    private class PlayerHandler {

        private MediaPlayer player;

        public void play() {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> play()");
            loadMediaAndStartPlayer(MainActivity.nowPlayingFile.getValue());
        }

        public void loadMediaAndStartPlayer(File media) {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> loadMediaAndStartPlayer: load file");
            loadMedia(media);
            player.start();
        }

        private void loadMedia(File media) {
            if(player == null)initializeMediaPlayer();
            try {
                player.setDataSource(media.toString());
                Log.d(MainActivity.TAG, "AudioPlayerHandler -> loadMedia(): loaded media name " + media);
                player.prepare();
                AudiobookViewModel.nowPlayingMediaDuration = player.getDuration();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void initializeMediaPlayer() {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> initializeMediaPlayer()");
            player = new MediaPlayer();
        }

        public Boolean isPlaying() {
            return player != null && player.isPlaying();
        }

        private void pause() {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> pause()");
            player.pause();
            AudiobookViewModel.playerStatus = STATUS_PAUSE;
        }

        private void resume() {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> resume()");
            player.start();
            AudiobookViewModel.playerStatus = STATUS_PLAY;
        }

        private void stop() {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> stop()");
            player.stop();
            player.reset();
            AudiobookViewModel.playerStatus = STATUS_STOP;
        }

        public void skipToNext() {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> skipToNext()");
            if(isPlaying()) stop();
            AudiobookViewModel.playerStatus = STATUS_SKIP_TO_NEXT;
            File newFile = fileManagerHandler.getNextOrPrevFile();
            MainActivity.model.updateNowPlayingFile(newFile);
        }

        public void skipToPrevious() {
            int playerPosition = player.getCurrentPosition();
            if(playerPosition > 5*1000) {
                Log.d(MainActivity.TAG, "AudioPlayerHandler -> skipToPrevious(): restarting player");
                player.pause();
                player.seekTo(0);
                player.start();
            } else {
                Log.d(MainActivity.TAG, "AudioPlayerHandler -> skipToPrevious(): start previous composition");
                stop();
                AudiobookViewModel.playerStatus = STATUS_SKIP_TO_PREVIOUS;
                File prevFile = fileManagerHandler.getNextOrPrevFile();
                MainActivity.model.updateNowPlayingFile(prevFile);
            }
        }

        public void rewindBack() {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> rewindBack()");
            int playerPosition = player.getCurrentPosition();
            int newPosition = playerPosition - 5*1000;
            player.seekTo(newPosition > 0 ? newPosition : 0);
        }

        public void rewindForward() {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> rewindForward()");
            int playerPosition = player.getCurrentPosition();
            int newPosition = playerPosition + 5*1000;
            if(newPosition > player.getDuration()) skipToNext();
            else player.seekTo(newPosition);
        }
    }
}
