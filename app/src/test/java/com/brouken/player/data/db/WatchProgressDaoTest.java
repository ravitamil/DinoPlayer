package com.brouken.player.data.db;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@Config(manifest = Config.NONE)
public class WatchProgressDaoTest {

    private AppDatabase database;
    private WatchProgressDao dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.watchProgressDao();
    }

    @After
    public void closeDb() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void testInsertAndGet() {
        String uri = "content://media/external/video/media/101";
        WatchProgressEntity entity = new WatchProgressEntity(uri, 5000L, 60000L, false, 123456789L);
        dao.upsert(entity);

        WatchProgressEntity loaded = dao.get(uri);
        assertNotNull(loaded);
        assertEquals(uri, loaded.mediaUri);
        assertEquals(5000L, loaded.positionMs);
        assertEquals(60000L, loaded.durationMs);
        assertFalse(loaded.completed);
        assertEquals(123456789L, loaded.updatedAt);
    }

    @Test
    public void testUpsertUpdatesExisting() {
        String uri = "content://media/external/video/media/102";
        WatchProgressEntity first = new WatchProgressEntity(uri, 1000L, 20000L, false, 100L);
        dao.upsert(first);

        WatchProgressEntity second = new WatchProgressEntity(uri, 15000L, 20000L, true, 200L);
        dao.upsert(second);

        WatchProgressEntity loaded = dao.get(uri);
        assertNotNull(loaded);
        assertEquals(15000L, loaded.positionMs);
        assertTrue(loaded.completed);
        assertEquals(200L, loaded.updatedAt);
    }

    @Test
    public void testGetAll() {
        dao.upsert(new WatchProgressEntity("uri1", 100L, 1000L, false, 10L));
        dao.upsert(new WatchProgressEntity("uri2", 200L, 1000L, true, 20L));
        dao.upsert(new WatchProgressEntity("uri3", 300L, 1000L, false, 30L));

        List<WatchProgressEntity> all = dao.getAll();
        assertNotNull(all);
        assertEquals(3, all.size());
    }

    @Test
    public void testDelete() {
        String uri = "content://media/external/video/media/103";
        dao.upsert(new WatchProgressEntity(uri, 4000L, 8000L, false, 50L));
        assertNotNull(dao.get(uri));

        dao.delete(uri);
        assertNull(dao.get(uri));
    }

    @Test
    public void testDeleteAll() {
        dao.upsert(new WatchProgressEntity("uri1", 100L, 1000L, false, 10L));
        dao.upsert(new WatchProgressEntity("uri2", 200L, 1000L, true, 20L));

        dao.deleteAll();
        List<WatchProgressEntity> all = dao.getAll();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    public void testCaptionJobDaoOperations() {
        CaptionJobDao captionDao = database.captionJobDao();
        String uri = "content://media/external/video/media/201";
        String packId = "whisper-tiny-en";

        CaptionJobEntity job = new CaptionJobEntity(uri, packId, "token-1", "QUEUED", 0, "", 100L);
        captionDao.upsert(job);

        CaptionJobEntity loaded = captionDao.get(uri, packId);
        assertNotNull(loaded);
        assertEquals("QUEUED", loaded.status);
        assertEquals("token-1", loaded.token);

        // Update progress
        job.status = "GENERATING";
        job.percent = 45;
        captionDao.upsert(job);

        loaded = captionDao.get(uri, packId);
        assertEquals("GENERATING", loaded.status);
        assertEquals(45, loaded.percent);

        // Test getByStatus
        List<CaptionJobEntity> generating = captionDao.getByStatus("GENERATING");
        assertEquals(1, generating.size());

        // Test delete
        captionDao.delete(uri, packId);
        assertNull(captionDao.get(uri, packId));
    }

    @Test
    public void testLegacyStorageMigration() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        // Reset migration flag for testing
        SharedPreferences migPrefs = context.getSharedPreferences("app_migration_prefs", Context.MODE_PRIVATE);
        migPrefs.edit().clear().apply();

        // 1. Create legacy positions binary file
        LinkedHashMap<String, Long> legacyPositions = new LinkedHashMap<>();
        legacyPositions.put("content://media/external/video/media/301", 12345L);
        try (FileOutputStream fos = context.openFileOutput("positions", Context.MODE_PRIVATE);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(legacyPositions);
        }
        File positionsFile = context.getFileStreamPath("positions");
        assertTrue(positionsFile.exists());

        // 2. Create legacy watch_history SharedPreferences
        SharedPreferences historyPrefs = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE);
        JSONObject json = new JSONObject();
        json.put("position", 5555L);
        json.put("duration", 60000L);
        json.put("completed", true);
        json.put("updated", 999999L);
        historyPrefs.edit().putString("content://media/external/video/media/302", json.toString()).apply();
        assertTrue(historyPrefs.contains("content://media/external/video/media/302"));

        // 3. Perform migration
        AppDatabase.migrateLegacyStorage(context, database);

        // 4. Assert legacy positions file is deleted
        assertFalse(context.getFileStreamPath("positions").exists());

        // 5. Assert legacy watch_history SharedPreferences is cleared
        assertTrue(historyPrefs.getAll().isEmpty());

        // 6. Assert Room DB contains migrated entries
        WatchProgressEntity item1 = dao.get("content://media/external/video/media/301");
        assertNotNull(item1);
        assertEquals(12345L, item1.positionMs);

        WatchProgressEntity item2 = dao.get("content://media/external/video/media/302");
        assertNotNull(item2);
        assertEquals(5555L, item2.positionMs);
        assertEquals(60000L, item2.durationMs);
        assertTrue(item2.completed);
        assertEquals(999999L, item2.updatedAt);
    }
}
