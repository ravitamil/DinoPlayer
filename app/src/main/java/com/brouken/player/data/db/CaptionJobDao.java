package com.brouken.player.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CaptionJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CaptionJobEntity job);

    @Query("SELECT * FROM caption_jobs WHERE mediaUri = :mediaUri AND packId = :packId LIMIT 1")
    CaptionJobEntity get(String mediaUri, String packId);

    @Query("SELECT * FROM caption_jobs")
    List<CaptionJobEntity> getAll();

    @Query("DELETE FROM caption_jobs WHERE mediaUri = :mediaUri AND packId = :packId")
    void delete(String mediaUri, String packId);

    @Query("DELETE FROM caption_jobs WHERE mediaUri = :mediaUri")
    void deleteByMedia(String mediaUri);

    @Query("DELETE FROM caption_jobs")
    void deleteAll();

    @Query("SELECT * FROM caption_jobs WHERE status = :status ORDER BY updatedAt ASC")
    List<CaptionJobEntity> getByStatus(String status);
}
