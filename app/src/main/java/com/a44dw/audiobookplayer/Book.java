package com.a44dw.audiobookplayer;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.graphics.Bitmap;
import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

@Entity
public class Book {

    @PrimaryKey(autoGenerate = true)
    public long bookId;

    public String publicName;
    public String filepath;
    public int lastPlayedChapterNum;
    public int percent;
    public long bookDuration;
    public long bookProgress;

    @Ignore
    public ArrayList<Chapter> chapters;

    @Ignore
    public Bitmap cover;

    public Book() {}

    public Book(File directory) {
        filepath = generatePath(directory);
        publicName = generateName(directory);
        this.percent = 0;
        this.bookProgress = 0;
    }

    public boolean exists() {
        return (new File(filepath).exists());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return Objects.equals(filepath, book.filepath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filepath);
    }

    public void updateInPlaylist(Chapter oldChapter) {
        int num = BookRepository.getInstance().getNowPlayingChapterNumber();
        if(chapters.get(num).chapterId == oldChapter.chapterId) {
            chapters.set(num, oldChapter);
        } else {
            for(int i=0; i<chapters.size(); i++) {
                if(i == num) continue;
                if(chapters.get(i).chapterId == oldChapter.chapterId) {
                    chapters.set(i, oldChapter);
                    break;
                }
            }
        }
        recalcBookProgress();
        recalcPercent();
    }

    private void recalcBookProgress() {
        this.bookProgress = 0;
        for(Chapter ch : chapters) {
            long chapterProgress = ch.progress;
            if(chapterProgress == 0) continue;
            this.bookProgress += (ch.done ? ch.duration : chapterProgress);
        }
    }

    private void recalcPercent() {
        if(this.bookDuration == 0) this.bookDuration = calcDuration();
        float perc = (this.bookProgress * 100)/this.bookDuration;
        this.percent = (perc == 99 ? 100 : (int)perc);
    }

    private static String generatePath(File directory) {
        return directory.getAbsolutePath();
    }

    private static String generateName(File directory) {
        return directory.getName();
    }

    public long calcDuration() {
        long duration = 1;
        for(Chapter ch : chapters) duration += ch.duration;
        return duration;
    }

    public int findInChapters(Chapter chapter) {
        for(int i=0; i<chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            if(ch.equals(chapter)) {
                return i;
            }
        }
        return -1;
    }

    //используется в Bookmark Fragment
    public int findInChapters(String path) {
        for(int i=0; i<chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            if(ch.filepath.equals(path))
                return i;
        }
        return -1;
    }

    public void nullProgress() {
        if(chapters != null) {
            for (Chapter ch : chapters) {
                if(ch.progress > 0) {
                    ch.progress = 0;
                    if(ch.done) ch.done = false;
                }
            }
        }
        this.bookProgress = 0;
        this.percent = 0;
        this.lastPlayedChapterNum = 0;
    }
}
