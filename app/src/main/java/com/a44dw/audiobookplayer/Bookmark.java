package com.a44dw.audiobookplayer;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.PrimaryKey;

import static android.arch.persistence.room.ForeignKey.CASCADE;

@Entity(foreignKeys =
        {@ForeignKey(entity = Chapter.class,
                parentColumns = "chapterId",
                childColumns = "chId",
                onDelete = CASCADE),

        @ForeignKey(entity = Book.class,
                parentColumns = "bookId",
                childColumns = "bId",
                onDelete = CASCADE)
        }
)

public class Bookmark {
    @PrimaryKey(autoGenerate = true)
    public long bookmarkId;
    public long chId;
    public long bId;

    public String pathToFile;
    public String name;
    public long time;

    public Bookmark() {}

    public Bookmark(Chapter ch, long bookId, long t) {
        this.name = "Закладка";
        this.pathToFile = ch.filepath;
        this.chId = ch.chapterId;
        this.bId = bookId;
        this.time = t;
    }
}