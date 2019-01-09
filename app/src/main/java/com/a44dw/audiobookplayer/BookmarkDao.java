package com.a44dw.audiobookplayer;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;
import java.util.List;

@Dao
public interface BookmarkDao {
    @Query("SELECT * FROM bookmark")
    List<Bookmark> getAll();

    @Query("SELECT * FROM bookmark WHERE chId = :id")
    List<Bookmark> getAllByChapterId(Long id);

    @Query("SELECT * FROM bookmark WHERE bId = :id")
    List<Bookmark> getAllByBookId(Long id);

    @Query("SELECT * FROM bookmark WHERE bookmarkId = :id")
    Bookmark getById(Long id);

    @Query("DELETE FROM bookmark WHERE time = :time AND chId = :chId")
    void deleteByTime(Long time, Long chId);

    @Query("UPDATE bookmark SET name = :name WHERE time = :time AND chId = :chId")
    void updateNameByTime(String name, Long time, Long chId);

    @Insert
    long insert(Bookmark bookmark);

    @Update
    void update(Bookmark bookmark);

    @Delete
    void delete(Bookmark bookmark);
}
