package com.a44dw.audiobookplayer;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;
import java.util.List;

@Dao
public interface ChapterDao {
    @Query("SELECT * FROM chapter")
    List<Chapter> getAll();

    @Query("SELECT * FROM chapter WHERE filepath = :filepath")
    Chapter getByFilepath(String filepath);

    @Query("SELECT * FROM chapter WHERE bId = :id")
    List<Chapter> getByBookId(Long id);

    @Query("SELECT * FROM chapter WHERE bId = :id")
    Chapter getFirstByBookId(Long id);

    @Query("SELECT * FROM chapter WHERE chapterId = :id")
    Chapter getById(Long id);

    @Insert
    long insert(Chapter chapter);

    @Update
    void update(Chapter chapter);

    @Delete
    void delete(Chapter chapter);
}
