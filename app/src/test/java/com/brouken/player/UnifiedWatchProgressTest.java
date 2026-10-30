package com.brouken.player;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.brouken.player.data.db.AppDatabase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@Config(manifest = Config.NONE)
public class UnifiedWatchProgressTest {

    private Context context;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        AppDatabase.getInstance(context).watchProgressDao().deleteAll();
    }

    @Test
    public void testWatchHistoryAndPrefsPersistenceUnification() {
        Uri testUri = Uri.parse("content://media/external/video/media/501");

        // 1. Record watch progress via WatchHistory
        WatchHistory watchHistory = new WatchHistory(context);
        watchHistory.record(testUri, 30000L, 60000L, false);

        // 2. Read latest via WatchHistory
        WatchHistory.Entry entry = watchHistory.latest(testUri);
        assertNotNull(entry);
        assertEquals(30000L, entry.position);
        assertEquals(60000L, entry.duration);

        // 3. Read position via Prefs (using same mediaUri)
        Prefs prefs = new Prefs(context);
        prefs.mediaUri = testUri;
        long resumePos = prefs.getPosition();
        // Since duration is 60000 and position is 30000, WatchProgress.resumePosition returns 30000
        assertEquals(30000L, resumePos);

        // 4. Update position via Prefs
        prefs.updatePosition(45000L);

        // 5. Verify WatchHistory reflects the updated position from Room
        WatchHistory.Entry updatedEntry = watchHistory.latest(testUri);
        assertNotNull(updatedEntry);
        assertEquals(45000L, updatedEntry.position);

        // 6. Verify snapshot contains the entry
        Map<String, WatchHistory.Entry> snapshot = watchHistory.snapshot();
        assertTrue(snapshot.containsKey(testUri.toString()));

        // 7. Verify forget deletes the entry
        watchHistory.forget(testUri);
        assertNull(watchHistory.latest(testUri));
        assertEquals(0L, prefs.getPosition());
    }
}
