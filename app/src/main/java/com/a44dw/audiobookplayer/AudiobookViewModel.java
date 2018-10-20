package com.a44dw.audiobookplayer;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.os.Environment;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

public class AudiobookViewModel extends ViewModel {

    private static int playerStatus = PlaybackStateCompat.STATE_STOPPED;

    //константа для погрешности статуса "файл прослушан"
    public static final int GAP = 1000;

    static FileManagerHandler fileManagerHandler;

    private static MutableLiveData<Chapter> nowPlayingFile;
    private static MutableLiveData<String> playlistName;
    private static Book currentPlaylist;
    private static int nowPlayingPosition;
    private static int nowPlayingFileNumber;

    public static LiveData<Chapter> getNowPlayingFile() {
        if(nowPlayingFile == null) nowPlayingFile = new MutableLiveData<>();
        return nowPlayingFile;
    }

    public static LiveData<String> getPlaylistName() {
        if(playlistName == null) playlistName = new MutableLiveData<>();
        return playlistName;
    }

    public static void updateNowPlayingFile(Chapter newChapter) {
        Log.d(MainActivity.TAG, "AudiobookViewModel -> updateNowPlayingFile: before update old file newChapter progress is " + newChapter.getProgress());
        //перед обновлением файла обновляем данные о нём в playlist
        //TODO избежать ложного вызова обновления, когда nowPlayingFile не существует, но обновление внизу всё равно запускается
        Chapter oldChapter = nowPlayingFile.getValue();
        if(oldChapter != null) {
            //проверка на equals нужна, чтобы избежать сохранения, если старый файл равен новому (например, при загрузке из закладок)
            Log.d(MainActivity.TAG, "AudiobookViewModel -> updateNowPlayingFile: chapters is equals:  " + oldChapter.equals(newChapter));
            if(!oldChapter.equals(newChapter)) {
                updateNowPlayingChapterInPlaylist();
            }
        }
        //вручную меняем статус, чтобы избежать коллизий с некорректным воспроизведением после снятия с паузы
        if(playerStatus != PlaybackStateCompat.STATE_STOPPED) playerStatus = PlaybackStateCompat.STATE_BUFFERING;
        Log.d(MainActivity.TAG, "AudiobookViewModel -> updateNowPlayingFile: after update old file newChapter progress is " + newChapter.getProgress());
        nowPlayingFile.setValue(newChapter);
    }

    public static void updatePlaylistName(String newName) {
        Log.d(MainActivity.TAG, "AudiobookViewModel -> updatePlaylistName: " + newName);
        if(playlistName == null) playlistName = new MutableLiveData<>();
        playlistName.setValue(newName);
    }

    public static void updatePlaylist(Book newNowPlayingBook) {
        Log.d(MainActivity.TAG, "AudiobookViewModel -> updatePlaylist");
        currentPlaylist = newNowPlayingBook;
    }

    public static Book getPlaylist() {
        return currentPlaylist;
    }

    public static void updateNowPlayingChapterInPlaylist() {
        currentPlaylist.updateInPlaylist(nowPlayingFile.getValue());
        currentPlaylist.setLastPlayedChapterNum(AudiobookViewModel.getNowPlayingFileNumber());
    }

    public static int getPlayerStatus() {
        return playerStatus;
    }

    public static void setPlayerStatus(int playerStatus) {
        Log.d(MainActivity.TAG, "AudiobookViewModel -> setPlayerStatus(): " + playerStatus);
        AudiobookViewModel.playerStatus = playerStatus;
    }

    public static void addBookmark() {
        //два варианта добавления закладки - в nowPlayingFile или сразу в плейлист
        //в первом случае нужно ещё добавить код в updateInPlaylist
        Log.d(MainActivity.TAG, "AudiobookViewModel -> addBookmark(): " + playerStatus);
        currentPlaylist.getChapters().get(nowPlayingFileNumber).addBookmark(nowPlayingPosition);
    }

    public static int getNowPlayingPosition() {
        return nowPlayingPosition;
    }

    public static void updateNowPlayingPosition(int pos) {
        nowPlayingPosition = pos;
    }

    public static int getNowPlayingFileNumber() {
        return nowPlayingFileNumber;
    }

    public static void setNowPlayingFileNumber(Chapter ch) {
        AudiobookViewModel.nowPlayingFileNumber = currentPlaylist.findInChapters(ch);
    }

    @Override
    protected void onCleared() {
        //TODO очистить здесь всю используемую ViewModel дату
        super.onCleared();
    }

}
