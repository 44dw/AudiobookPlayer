package com.a44dw.audiobookplayer;

import android.media.MediaMetadataRetriever;

import java.io.File;

public class Chapter {

    private File file;

    private String author;
    private String title;
    private long duration;
    private long progress;
    private boolean done;

    public Chapter(File f) {
        file = f;
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        metadata.setDataSource(f.toString());
        author = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
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

}
