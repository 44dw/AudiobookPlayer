package com.a44dw.audiobookplayer;

import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

public class Chapter {

    private File file;

    private String author;
    private String title;
    private String chapter;
    private long duration;
    private long progress;
    private boolean done;
    private ArrayList<Bookmark> bookmarks;

    public Chapter(File f) {
        file = f;
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        metadata.setDataSource(f.toString());
        author = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        chapter = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        progress = 0;
    }

    public File getFile() {
        return file;
    }

    public String getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getChapter() {
        return chapter;
    }

    public long getProgress() {
        return progress;
    }

    public void setProgress(long progress) {
        this.progress = progress;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public long getDuration() {
        return duration;
    }

    public void addBookmark(long time) {
        if(bookmarks == null) bookmarks = new ArrayList<>();
        bookmarks.add(new Bookmark(this, time));
    }

    public ArrayList<Bookmark> getBookmarks() {
        return bookmarks;
    }

    public Bookmark getBookmark(long time) {
        for(Bookmark b : bookmarks) {
            if (b.getTime() == time) return b;
        }
        return null;
    }
    public void updateBookmark(Bookmark newBookmark) {
        Bookmark b = getBookmark(newBookmark.getTime());
        if(b != null) b.setName(newBookmark.getName());
    }

    public void deleteBookmark(Bookmark delBookmark) {
        Bookmark b = getBookmark(delBookmark.getTime());
        bookmarks.remove(b);
        Log.d(MainActivity.TAG, "Chapter -> deleteBookmark(): length of bookmarks now is " + bookmarks.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chapter chapter = (Chapter) o;
        return Objects.equals(file, chapter.file);
    }

    @Override
    public int hashCode() {

        return Objects.hash(file);
    }
}
