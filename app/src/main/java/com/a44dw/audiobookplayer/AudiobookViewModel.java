package com.a44dw.audiobookplayer;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;

public class AudiobookViewModel extends ViewModel {

    public static final int STATUS_STOP = 0;
    public static final int STATUS_PLAY = 1;
    public static final int STATUS_PAUSE = 2;
    public static final int STATUS_SKIP_TO_NEXT = 3;
    public static final int STATUS_SKIP_TO_PREVIOUS = 4;
    public static final int STATUS_END_OF_DIR = 5;
    public static int playerStatus = STATUS_STOP;

    private static MutableLiveData<File> nowPlayingFile;
    public static int nowPlayingMediaDuration = 0;
    private OnIterationWithActivityListener mActivityListener;

    static AudioPlayerHandler playerHandler;
    static FileManagerHandler fileManagerHandler;

    public void initializeListener(OnIterationWithActivityListener listener) {
        mActivityListener = listener;
    }

    public LiveData<File> getNowPlayingFile() {
        if(nowPlayingFile == null) nowPlayingFile = new MutableLiveData<>();
        return nowPlayingFile;
    }

    public void updateNowPlayingFile(File newFile) {
        nowPlayingFile.setValue(newFile);
    }

    //вызывается из playerHandler
    public void chooseFileToPlay() {
        mActivityListener.launchFileManagerFragment();
    }

}
