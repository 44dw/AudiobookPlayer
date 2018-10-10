package com.a44dw.audiobookplayer;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.os.Environment;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

public class AudiobookViewModel extends ViewModel {

    private static int playerStatus = PlaybackStateCompat.STATE_STOPPED;

    static FileManagerHandler fileManagerHandler;

    private static MutableLiveData<Chapter> nowPlayingFile;
    private static MutableLiveData<ArrayList<Chapter>> playlist;

    public static LiveData<Chapter> getNowPlayingFile() {
        if(nowPlayingFile == null) nowPlayingFile = new MutableLiveData<>();
        return nowPlayingFile;
    }

    public static void updateNowPlayingFile(Chapter newChapter) {
        nowPlayingFile.setValue(newChapter);
    }

    public static void updatePlaylist(ArrayList<Chapter> newNowPlayingBook) {
        playlist.setValue(newNowPlayingBook);
    }

    public static LiveData<ArrayList<Chapter>> getPlaylist() {
        if(playlist == null) playlist = new MutableLiveData<>();
        return playlist;
    }

    public static int getPlayerStatus() {
        return playerStatus;
    }

    public static void setPlayerStatus(int playerStatus) {
        AudiobookViewModel.playerStatus = playerStatus;
    }
}
