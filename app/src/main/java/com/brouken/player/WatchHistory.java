package com.brouken.player;

import android.content.Context;
import android.net.Uri;

import com.brouken.player.data.db.AppDatabase;
import com.brouken.player.data.db.WatchProgressDao;
import com.brouken.player.data.db.WatchProgressEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local playback history backed by Room database. */
public final class WatchHistory {
    public static final class Entry {
        public long position, duration, updated;
        public boolean completed;
    }

    private final WatchProgressDao dao;

    public WatchHistory(Context context) {
        dao = AppDatabase.getInstance(context).watchProgressDao();
    }

    public Map<String, Entry> snapshot() {
        Map<String, Entry> result = new HashMap<>();
        List<WatchProgressEntity> all = dao.getAll();
        for (WatchProgressEntity entity : all) {
            Entry entry = new Entry();
            entry.position = entity.positionMs;
            entry.duration = entity.durationMs;
            entry.updated = entity.updatedAt;
            entry.completed = entity.completed;
            result.put(entity.mediaUri, entry);
        }
        return result;
    }

    public Entry latest(Uri uri) {
        if (uri == null) return null;
        WatchProgressEntity entity = dao.get(uri.toString());
        if (entity == null || entity.durationMs <= 0) return null;
        Entry entry = new Entry();
        entry.position = entity.positionMs;
        entry.duration = entity.durationMs;
        entry.updated = entity.updatedAt;
        entry.completed = entity.completed;
        return entry;
    }

    public void forget(Uri uri) {
        if (uri == null) return;
        dao.delete(uri.toString());
    }

    public void record(Uri uri, long position, long duration, boolean ended) {
        if (uri == null || duration <= 0 || position < 0 || (!ended && position == 0)) return;
        String key = uri.toString();
        WatchProgressEntity existing = dao.get(key);
        boolean completed = ended || (existing != null && existing.completed);
        long pos = Math.min(position, duration);
        long now = System.currentTimeMillis();
        WatchProgressEntity entity = new WatchProgressEntity(key, pos, duration, completed, now);
        dao.upsert(entity);
    }
}
