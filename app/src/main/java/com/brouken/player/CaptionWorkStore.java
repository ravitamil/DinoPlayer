package com.brouken.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.brouken.player.data.db.AppDatabase;
import com.brouken.player.data.db.CaptionJobDao;
import com.brouken.player.data.db.CaptionJobEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Durable, token-guarded state backed by Room database. */
public final class CaptionWorkStore {
    public enum Status { QUEUED, GENERATING, READY, FAILED, CANCELLED }

    public static final class Entry {
        public Status status;
        public String token, reason;
        public int percent;

        public String label() {
            switch(status) {
                case GENERATING: return "Generating · " + percent + "%";
                case READY: return "Subtitles ready";
                case FAILED: return "Subtitles failed · Retry";
                case CANCELLED: return "Subtitles cancelled · Retry";
                default: return "Subtitles queued";
            }
        }
    }

    private final SharedPreferences prefs;
    private final Context context;
    private final CaptionJobDao dao;
    private static boolean recovered;

    public static synchronized void initialize(Context context) {
        if (recovered) return;
        recovered = true;
        new CaptionWorkStore(context).recoverInterrupted();
    }

    public CaptionWorkStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("caption_work", Context.MODE_PRIVATE);
        this.dao = AppDatabase.getInstance(this.context).captionJobDao();
    }

    public static String key(Uri media, CaptionPack pack) {
        return digest(media + "|" + pack.cacheIdentity());
    }

    static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            final char[] hex = "0123456789abcdef".toCharArray();
            for (byte b : bytes) {
                result.append(hex[(b & 255) >>> 4]);
                result.append(hex[b & 15]);
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void begin(Uri media, CaptionPack pack, String token) {
        synchronized (CaptionWorkStore.class) {
            write(media, pack, token, Status.QUEUED, 0, "");
        }
    }

    public void update(Uri media, CaptionPack pack, String token, Status state, int percent, String reason) {
        synchronized (CaptionWorkStore.class) {
            CaptionJobEntity current = dao.get(media.toString(), pack.id);
            if (current != null && token.equals(current.token)) {
                write(media, pack, token, state, percent, reason);
            }
        }
    }

    private void write(Uri media, CaptionPack pack, String token, Status state, int percent, String reason) {
        long now = System.currentTimeMillis();
        CaptionJobEntity entity = new CaptionJobEntity(
                media.toString(),
                pack.id,
                token,
                state.name(),
                percent,
                reason,
                now
        );
        dao.upsert(entity);
    }

    public Entry read(Uri media, CaptionPack pack) {
        if (media == null || pack == null) return null;
        CaptionJobEntity entity = dao.get(media.toString(), pack.id);
        if (entity == null) return null;
        try {
            Entry e = new Entry();
            e.token = entity.token;
            e.status = Status.valueOf(entity.status);
            e.percent = entity.percent;
            e.reason = entity.reason;
            return e;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Entry> snapshot(CaptionPack pack, java.util.List<FolderBrowserAdapter.BrowserItem> items) {
        Map<String, Entry> result = new HashMap<>();
        for (FolderBrowserAdapter.BrowserItem item : items) {
            if (item.uri != null) {
                Entry e = read(item.uri, pack);
                if (e != null) result.put(item.uri.toString(), e);
            }
        }
        return result;
    }

    public void reconcileReady() {
        synchronized (CaptionWorkStore.class) {
            List<CaptionJobEntity> all = dao.getAll();
            for (CaptionJobEntity entity : all) {
                if (!Status.READY.name().equals(entity.status)) continue;
                Uri media = Uri.parse(entity.mediaUri);
                CaptionPack pack = CaptionPack.find(entity.packId);
                if (pack == null) continue;
                String path = SubtitleGenerator.resolveRealPath(context, media);
                if (!CaptionLibrary.saved(context, media, pack).isFile() && !SubtitleGenerator.getCachedSubtitleFile(context, media, path, pack).isFile()) {
                    update(media, pack, entity.token, Status.CANCELLED, 0, "Cached subtitles expired; generate again");
                }
            }
        }
    }

    public void forget(Uri media) {
        if (media == null) return;
        dao.deleteByMedia(media.toString());
    }

    public void blockDeletion(Uri media, boolean blocked) {
        prefs.edit().putBoolean("deleting:" + media, blocked).apply();
    }

    public boolean isDeleting(Uri media) {
        return prefs.getBoolean("deleting:" + media, false);
    }

    public void recoverInterrupted() {
        prefs.edit().clear().apply();
        List<CaptionJobEntity> all = dao.getAll();
        for (CaptionJobEntity entity : all) {
            if (Status.GENERATING.name().equals(entity.status) || Status.QUEUED.name().equals(entity.status)) {
                entity.status = Status.CANCELLED.name();
                entity.reason = "App closed before completion";
                dao.upsert(entity);
            }
        }
    }
}
