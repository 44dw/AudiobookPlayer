package com.a44dw.audiobookplayer;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.os.Environment;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.File;
import java.util.ArrayList;

public class AudiobookViewModel extends ViewModel {

    private static int playerStatus = PlaybackStateCompat.STATE_STOPPED;

    private static MutableLiveData<File> nowPlayingFile;
    private static MutableLiveData<Integer> nowPlayingMediaDuration;
    //private OnIterationWithActivityListener mActivityListener;

    static FileManagerHandler fileManagerHandler;

    public void initializeListener(OnIterationWithActivityListener listener) {
        //mActivityListener = listener;
    }

    public static LiveData<File> getNowPlayingFile() {
        if(nowPlayingFile == null) nowPlayingFile = new MutableLiveData<>();
        return nowPlayingFile;
    }

    public void updateNowPlayingFile(File newFile) {
        nowPlayingFile.setValue(newFile);
    }

    public static LiveData<Integer> getNowPlayingMediaDuration() {
        if(nowPlayingMediaDuration == null) nowPlayingMediaDuration = new MutableLiveData<>();
        return nowPlayingMediaDuration;
    }

    public static void updateNowPlayingMediaDuration(Integer duration) {
        nowPlayingMediaDuration.setValue(duration);
    }

    public static int getPlayerStatus() {
        return playerStatus;
    }

    public static void setPlayerStatus(int playerStatus) {
        AudiobookViewModel.playerStatus = playerStatus;
    }

    public File getNextOrPrevFile() {
        return fileManagerHandler.getNextOrPrevFile();
    }
}
