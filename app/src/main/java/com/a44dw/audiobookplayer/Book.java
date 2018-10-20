package com.a44dw.audiobookplayer;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.a44dw.audiobookplayer.AudiobookViewModel.GAP;

public class Book {

    private String publicName;
    private String fileName;
    //private String path;
    private ArrayList<Chapter> chapters;
    private int lastPlayedChapterNum;
    private int percent;
    private long bookDuration;
    private long bookProgress;

    public Book(ArrayList<Chapter> chapters) {
        this.chapters = chapters;
        publicName = generatePublicName(chapters.get(0).getFile());
        fileName = generateFileName(chapters.get(0).getFile().getParentFile());
        this.percent = 0;
        this.bookDuration = calcDuration();
        this.bookProgress = 0;
    }

    public static String generateFileName(File dir) {
        String path = dir.getAbsolutePath();
        return path.replace("/", "+");
    }

    public String getName() {
        return publicName;
    }

    public void setName(String name) {
        this.publicName = name;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return Objects.equals(fileName, book.fileName);
    }

    @Override
    public int hashCode() {

        return Objects.hash(fileName);
    }

    public ArrayList<Chapter> getChapters() {
        return chapters;
    }

    public Chapter getNext() {
        int nextNum = AudiobookViewModel.getNowPlayingFileNumber() + 1;
        return chapters.get(nextNum);
    }

    public Chapter getPrevious() {
        int prevNum = AudiobookViewModel.getNowPlayingFileNumber() - 1;
        return chapters.get(prevNum);
    }

    public void updateInPlaylist(Chapter oldChapter) {
        long nowPlayingPosition = AudiobookViewModel.getNowPlayingPosition();
        Log.d(MainActivity.TAG, "Book -> updateInPlaylist: getProgress() is " + nowPlayingPosition);
        Chapter updatedChapter = chapters.get(AudiobookViewModel.getNowPlayingFileNumber());
        Log.d(MainActivity.TAG, "Book -> updateInPlaylist: updatedChapter is " + updatedChapter.getChapter());
        updatedChapter.setProgress(nowPlayingPosition);
        if(nowPlayingPosition > oldChapter.getDuration() - GAP) updatedChapter.setDone(true);
        else updatedChapter.setDone(false);
        //пересчитываем прогресс и процент прослушанного
        recalcBookProgress();
        recalcPercent();
    }

    private void recalcBookProgress() {
        this.bookProgress = 0;
        for(Chapter ch : chapters) {
            long chapterProgress = ch.getProgress();
            if(chapterProgress == 0) continue;
            this.bookProgress += chapterProgress;
        }
        Log.d(MainActivity.TAG, "Book -> recalcBookProgress(): " + this.bookProgress);
    }

    public float getPercent() {
        return percent;
    }

    private void recalcPercent() {
        float perc = (this.bookProgress * 100)/this.bookDuration;
        Log.d(MainActivity.TAG, "Book -> recalcPercent(): " + perc);
        this.percent = (int)perc;
    }

    public long getDuration() {
        return bookDuration;
    }

    public long getProgress() {
        return bookProgress;
    }


    public int getLastPlayedChapterNum() {return lastPlayedChapterNum;}

    public void setLastPlayedChapterNum(int lastPlayedChapterNum) {this.lastPlayedChapterNum = lastPlayedChapterNum;}

    private String generatePublicName(File firstFileInPlaylist) {
        //возвращает имя директории
        return firstFileInPlaylist.getParentFile().getName();
    }

    private long calcDuration() {
        long duration = 0;
        for(Chapter ch : chapters) duration += ch.getDuration();
        Log.d(MainActivity.TAG, "Book -> calcDuration() is " + duration);
        return duration;
    }

    public int findInChapters(Chapter chapter) {
        for(int i=0; i<chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            if(ch.getFile().equals(chapter.getFile())) {
                Log.d(MainActivity.TAG, "Book -> findInChapters(): num of nowPlayingFile is " + i);
                return i;
            }
        }
        return -1;
    }

    //используется в Bookmark Fragment
    public int findInChapters(String path) {
        for(int i=0; i<chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            if(ch.getFile().toString().equals(path))
                return i;
        }
        return -1;
    }
}
