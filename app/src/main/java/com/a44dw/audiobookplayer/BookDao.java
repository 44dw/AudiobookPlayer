package com.a44dw.audiobookplayer;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface BookDao {
    @Query("SELECT * FROM book")
    List<Book> getAll();

    @Query("SELECT * FROM book WHERE bookid = :bookid")
    Book getById(long bookid);

    @Query("SELECT * FROM book WHERE filepath = :filepath")
    Book getBookByPath(String filepath);

    @Query("SELECT COUNT(*) FROM book")
    Long getCount();

    @Insert
    long insert(Book book);

    @Update
    void update(Book book);

    @Delete
    void delete(Book book);
}
