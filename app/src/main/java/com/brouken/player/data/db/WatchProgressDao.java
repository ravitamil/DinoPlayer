package com.brouken.player.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WatchProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(WatchProgressEntity entity);

    @Query("SELECT * FROM watch_progress WHERE mediaUri = :uri LIMIT 1")
    WatchProgressEntity get(String uri);

    @Query("SELECT * FROM watch_progress")
    List<WatchProgressEntity> getAll();

    @Query("DELETE FROM watch_progress WHERE mediaUri = :uri")
    void delete(String uri);

    @Query("DELETE FROM watch_progress")
    void deleteAll();
}
