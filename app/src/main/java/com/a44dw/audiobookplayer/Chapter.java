package com.a44dw.audiobookplayer;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

import static android.arch.persistence.room.ForeignKey.CASCADE;

@Entity(foreignKeys = @ForeignKey(entity = Book.class, parentColumns = "bookId", childColumns = "bId", onDelete = CASCADE))
public class Chapter {

    @PrimaryKey(autoGenerate = true)
    public long chapterId;
    public long bId;

    public String filepath;
    public String author;
    public String title;
    public String chapter;
    public long duration;
    public long progress;
    public boolean done;

    @Ignore
    public ArrayList<Bookmark> bookmarks;

    public Chapter() {}

    public Chapter(File f) {
        filepath = f.getAbsolutePath();
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(f.toString());
            author = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            chapter = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if(chapter == null) chapter = f.getName();
            title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            progress = 0;
            bId = 0;
        } catch (Exception e) {
            e.printStackTrace();
            filepath = null;
        } finally {
            metadata.release();
        }
    }

    public void addBookmark(long time) {
        if(bookmarks == null) bookmarks = new ArrayList<>();
        bookmarks.add(new Bookmark(this,
                                       BookRepository.getInstance().getBook().bookId,
                                       time));
    }

    private Bookmark getBookmark(long time) {
        if(bookmarks != null) {
            for(Bookmark b : bookmarks) {
                if (b.time == time) return b;
            }
        }
        return null;
    }

    public void deleteBookmark(Bookmark delBookmark) {
        Bookmark b = getBookmark(delBookmark.time);
        if((bookmarks != null)&&(b != null))
            bookmarks.remove(b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chapter chapter = (Chapter) o;
        return Objects.equals(filepath, chapter.filepath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filepath);
    }

    public boolean exists() {
        return (filepath != null)&&(new File(filepath).exists());
    }
}
