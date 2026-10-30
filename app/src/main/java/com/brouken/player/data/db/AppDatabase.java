package com.brouken.player.data.db;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Map;

@Database(entities = {WatchProgressEntity.class, CaptionJobEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "justplayer.db";
    private static final String PREF_MIGRATION = "app_migration_prefs";
    private static final String KEY_MIGRATED_ROOM_V1 = "migrated_to_room_v1";

    private static volatile AppDatabase INSTANCE;

    public abstract WatchProgressDao watchProgressDao();
    public abstract CaptionJobDao captionJobDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    Context appContext = context.getApplicationContext();
                    INSTANCE = Room.databaseBuilder(
                            appContext,
                            AppDatabase.class,
                            DB_NAME
                    )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build();

                    migrateLegacyStorage(appContext, INSTANCE);
                }
            }
        }
        return INSTANCE;
    }

    public static synchronized void migrateLegacyStorage(Context context, AppDatabase db) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_MIGRATION, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_MIGRATED_ROOM_V1, false)) {
            return;
        }

        WatchProgressDao watchDao = db.watchProgressDao();

        // 1. Migrate legacy "positions" binary file
        try {
            File positionsFile = context.getFileStreamPath("positions");
            if (positionsFile != null && positionsFile.exists()) {
                try (FileInputStream fis = context.openFileInput("positions");
                     ObjectInputStream ois = new ObjectInputStream(fis)) {
                    Object obj = ois.readObject();
                    if (obj instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) obj;
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getKey() instanceof String && entry.getValue() instanceof Long) {
                                String uri = (String) entry.getKey();
                                long pos = (Long) entry.getValue();
                                WatchProgressEntity existing = watchDao.get(uri);
                                if (existing == null) {
                                    watchDao.upsert(new WatchProgressEntity(uri, pos, 0, false, System.currentTimeMillis()));
                                } else if (existing.positionMs == 0 && pos > 0) {
                                    existing.positionMs = pos;
                                    watchDao.upsert(existing);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        context.deleteFile("positions");
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Migrate legacy "watch_history" SharedPreferences
        try {
            SharedPreferences historyPrefs = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE);
            Map<String, ?> historyEntries = historyPrefs.getAll();
            if (historyEntries != null && !historyEntries.isEmpty()) {
                for (Map.Entry<String, ?> item : historyEntries.entrySet()) {
                    if (item.getValue() instanceof String) {
                        try {
                            JSONObject json = new JSONObject((String) item.getValue());
                            String uri = item.getKey();
                            long pos = json.optLong("position");
                            long dur = json.optLong("duration");
                            long updated = json.optLong("updated", System.currentTimeMillis());
                            boolean completed = json.optBoolean("completed");

                            WatchProgressEntity existing = watchDao.get(uri);
                            if (existing != null) {
                                existing.durationMs = dur;
                                existing.completed = completed || existing.completed;
                                if (pos > 0) existing.positionMs = pos;
                                if (updated > existing.updatedAt) existing.updatedAt = updated;
                                watchDao.upsert(existing);
                            } else {
                                watchDao.upsert(new WatchProgressEntity(uri, pos, dur, completed, updated));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                historyPrefs.edit().clear().apply();
            }
        } catch (Exception ignored) {
        }

        // 3. Migrate legacy "caption_work" SharedPreferences
        try {
            SharedPreferences workPrefs = context.getSharedPreferences("caption_work", Context.MODE_PRIVATE);
            Map<String, ?> workEntries = workPrefs.getAll();
            if (workEntries != null && !workEntries.isEmpty()) {
                CaptionJobDao captionDao = db.captionJobDao();
                for (Map.Entry<String, ?> item : workEntries.entrySet()) {
                    if (item.getValue() instanceof String) {
                        try {
                            JSONObject json = new JSONObject((String) item.getValue());
                            String uri = json.optString("uri");
                            String pack = json.optString("pack");
                            if (!uri.isEmpty() && !pack.isEmpty()) {
                                CaptionJobEntity job = new CaptionJobEntity(
                                        uri,
                                        pack,
                                        json.optString("token"),
                                        json.optString("state"),
                                        json.optInt("percent"),
                                        json.optString("reason"),
                                        System.currentTimeMillis()
                                );
                                captionDao.upsert(job);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        prefs.edit().putBoolean(KEY_MIGRATED_ROOM_V1, true).apply();
    }
}
