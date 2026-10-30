package com.brouken.player.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "watch_progress")
public class WatchProgressEntity {
    @PrimaryKey
    @NonNull
    public String mediaUri;

    public long positionMs;
    public long durationMs;
    public boolean completed;
    public long updatedAt;

    public WatchProgressEntity() {
        this.mediaUri = "";
    }

    @Ignore
    public WatchProgressEntity(@NonNull String mediaUri, long positionMs, long durationMs, boolean completed, long updatedAt) {
        this.mediaUri = mediaUri;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.completed = completed;
        this.updatedAt = updatedAt;
    }
}
