package com.a44dw.audiobookplayer;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

@Database(entities = {Book.class, Bookmark.class, Chapter.class}, version = 1, exportSchema = false)
public abstract class BookplayerDatabase extends RoomDatabase {
    public abstract BookDao bookDao();
    public abstract ChapterDao chapterDao();
    public abstract BookmarkDao bookmarkDao();

    private static BookplayerDatabase instance;

    public static BookplayerDatabase getDatabase(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(), BookplayerDatabase.class, "BookplayerDatabase")
                            .build();
        }
        return instance;
    }
}
