package com.a44dw.audiobookplayer;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.media.MediaPlayer;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_END_OF_DIR;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_PAUSE;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_PLAY;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_SKIP_TO_NEXT;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_SKIP_TO_PREVIOUS;
import static com.a44dw.audiobookplayer.AudiobookViewModel.STATUS_STOP;
import static com.a44dw.audiobookplayer.AudiobookViewModel.fileManagerHandler;
import static com.a44dw.audiobookplayer.AudiobookViewModel.playerStatus;

public class AudioPlayerHandler {

    private static MediaPlayer player;
    private OnAudioPlayerIterationWithFragmentListener fragmentListener;
    private ScheduledExecutorService mExecutor;
    private Runnable mSeekbarPositionUpdateTask;
    public static final int POSITION_REFRESH_INTERVAL = 1000;

    AudioPlayerHandler(OnAudioPlayerIterationWithFragmentListener listener) {
        fragmentListener = listener;
        AudiobookViewModel.playerStatus = STATUS_STOP;
    }

    public void play() {
        Log.d(MainActivity.TAG, "AudioPlayerHandler -> play()");
        switch (AudiobookViewModel.playerStatus) {
            case (STATUS_STOP): {
                Log.d(MainActivity.TAG, "AudioPlayerHandler -> play() -> STATUS_STOP");
                MainActivity.model.chooseFileToPlay();
                break;
            }
            case (STATUS_PLAY): {
                pause();
                break;
            }
            case (STATUS_PAUSE): {
                resume();
                break;
            }
        }
    }

    public int loadMediaAndStartPlayer(File media) {
        Log.d(MainActivity.TAG, "AudioPlayerHandler -> loadMediaAndStartPlayer: load file");
        if(media == null) {
            return STATUS_END_OF_DIR;
        }
        loadMedia(media);
        player.start();
        if(isPlaying()) AudiobookViewModel.playerStatus = STATUS_PLAY;
        startUpdatingCallbackWithPosition();
        return AudiobookViewModel.playerStatus;
    }

    private void loadMedia(File media) {
        if(player == null)initializeMediaPlayer();
        try {
            player.setDataSource(media.toString());
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> loadMedia(): loaded media name " + media);
            player.prepare();
            AudiobookViewModel.nowPlayingMediaDuration = player.getDuration();
            //fragmentListener.onPositionChanged(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeMediaPlayer() {
        Log.d(MainActivity.TAG, "AudioPlayerHandler -> initializeMediaPlayer()");
        player = new MediaPlayer();
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if(playerStatus != STATUS_PLAY) {
                    Log.d(MainActivity.TAG, "AudioPlayerHandler -> initializeMediaPlayer() -> onCompletion() !STATUS_PLAY");
                    playerStatus = STATUS_STOP;
                    stopUpdatingCallbackWithPosition();
                    fragmentListener.onStopPlayer();
                }
            }
        });
    }

    public Boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    private void pause() {
        Log.d(MainActivity.TAG, "AudioPlayerHandler -> pause()");
        player.pause();
        fragmentListener.onStopPlayer();
        AudiobookViewModel.playerStatus = STATUS_PAUSE;
    }

    private void resume() {
        Log.d(MainActivity.TAG, "AudioPlayerHandler -> resume()");
        player.start();
        fragmentListener.onLaunchPlayer();
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
        stopUpdatingCallbackWithPosition();
        if(isPlaying()) stop();
        AudiobookViewModel.playerStatus = STATUS_SKIP_TO_NEXT;
        File newFile = fileManagerHandler.getNextOrPrevFile();
        MainActivity.model.updateNowPlayingFile(newFile);
    }

    public void skipToPrevious() {
        int playerPosition = player.getCurrentPosition();
        stopUpdatingCallbackWithPosition();
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

    public void seekTo(int position) {
        if(player != null) player.seekTo(position);
    }

    private void startUpdatingCallbackWithPosition() {
        Log.d(MainActivity.TAG, "AudioPlayerHandler -> startUpdatingCallbackWithPosition()");
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
            fragmentListener.onPositionChanged(0);
        }
    }

    private void updateProgressCallbackTask() {
        if(isPlaying()) {
            Log.d(MainActivity.TAG, "AudioPlayerHandler -> updateProgressCallbackTask()");
            if(fragmentListener != null) fragmentListener.onPositionChanged(player.getCurrentPosition());
        }
    }

    public interface OnAudioPlayerIterationWithFragmentListener {
        void onLaunchPlayer();
        void onStopPlayer();
        void onDurationChanged(int duration);
        void onPositionChanged(int position);
    }
}
