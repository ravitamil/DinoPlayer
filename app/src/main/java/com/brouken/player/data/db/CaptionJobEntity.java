package com.brouken.player.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;

@Entity(tableName = "caption_jobs", primaryKeys = {"mediaUri", "packId"})
public class CaptionJobEntity {
    @NonNull
    public String mediaUri;

    @NonNull
    public String packId;

    public String token;
    public String status;
    public int percent;
    public String reason;
    public long updatedAt;

    public CaptionJobEntity() {
        this.mediaUri = "";
        this.packId = "";
    }

    @Ignore
    public CaptionJobEntity(@NonNull String mediaUri, @NonNull String packId, String token, String status, int percent, String reason, long updatedAt) {
        this.mediaUri = mediaUri;
        this.packId = packId;
        this.token = token;
        this.status = status;
        this.percent = percent;
        this.reason = reason;
        this.updatedAt = updatedAt;
    }
}
