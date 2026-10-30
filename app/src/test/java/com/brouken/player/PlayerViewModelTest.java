package com.brouken.player;

import android.net.Uri;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@Config(manifest = Config.NONE)
public class PlayerViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private PlayerViewModel viewModel;

    @Before
    public void setup() {
        viewModel = new PlayerViewModel();
    }

    @Test
    public void testInitialState() {
        assertNull(viewModel.getMediaUri().getValue());
        assertEquals("", viewModel.getVideoTitle().getValue());
        assertNull(viewModel.getMediaType().getValue());
        assertEquals(Float.valueOf(1.0f), viewModel.getPlaybackSpeed().getValue());
        assertEquals(Integer.valueOf(AspectRatioFrameLayout.RESIZE_MODE_FIT), viewModel.getResizeMode().getValue());
        assertFalse(Boolean.TRUE.equals(viewModel.isPlaying().getValue()));
        assertFalse(Boolean.TRUE.equals(viewModel.isLocked().getValue()));
        assertFalse(Boolean.TRUE.equals(viewModel.areSubtitlesEnabled().getValue()));
        assertEquals(Integer.valueOf(0), viewModel.getBoostLevel().getValue());
    }

    @Test
    public void testSetMediaItem() {
        Uri uri = Uri.parse("content://media/external/video/media/100");
        String title = "Sample Video.mp4";
        String mimeType = "video/mp4";

        viewModel.setMediaItem(uri, title, mimeType);

        assertEquals(uri, viewModel.getMediaUri().getValue());
        assertEquals(title, viewModel.getVideoTitle().getValue());
        assertEquals(mimeType, viewModel.getMediaType().getValue());
    }

    @Test
    public void testSpeedCycling() {
        // Starts at 1.0f -> next 1.25f -> 1.5f -> 1.75f -> 2.0f -> 0.5f -> 0.75f -> 1.0f
        assertEquals(1.25f, viewModel.cyclePlaybackSpeed(), 0.001f);
        assertEquals(1.5f, viewModel.cyclePlaybackSpeed(), 0.001f);
        assertEquals(1.75f, viewModel.cyclePlaybackSpeed(), 0.001f);
        assertEquals(2.0f, viewModel.cyclePlaybackSpeed(), 0.001f);
        assertEquals(0.5f, viewModel.cyclePlaybackSpeed(), 0.001f);
        assertEquals(0.75f, viewModel.cyclePlaybackSpeed(), 0.001f);
        assertEquals(1.0f, viewModel.cyclePlaybackSpeed(), 0.001f);
    }

    @Test
    public void testSpeedCyclingWithCustomSpeeds() {
        viewModel.setPlaybackSpeed(1.1f);
        assertEquals(1.25f, viewModel.cyclePlaybackSpeed(), 0.001f);

        viewModel.setPlaybackSpeed(2.5f);
        assertEquals(0.5f, viewModel.cyclePlaybackSpeed(), 0.001f);

        viewModel.setPlaybackSpeed(0.25f);
        assertEquals(0.5f, viewModel.cyclePlaybackSpeed(), 0.001f);
    }

    @Test
    public void testControllerVisibility() {
        assertTrue(Boolean.TRUE.equals(viewModel.isControllerVisible().getValue()));
        viewModel.setControllerVisible(false);
        assertFalse(Boolean.TRUE.equals(viewModel.isControllerVisible().getValue()));
    }

    @Test
    public void testSpeedClamping() {
        viewModel.setPlaybackSpeed(0.1f);
        assertEquals(Float.valueOf(0.25f), viewModel.getPlaybackSpeed().getValue());

        viewModel.setPlaybackSpeed(5.0f);
        assertEquals(Float.valueOf(4.0f), viewModel.getPlaybackSpeed().getValue());

        viewModel.setPlaybackSpeed(1.5f);
        assertEquals(Float.valueOf(1.5f), viewModel.getPlaybackSpeed().getValue());
    }

    @Test
    public void testResizeModeToggle() {
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, (int) viewModel.getResizeMode().getValue());

        int next = viewModel.toggleResizeMode();
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, next);
        assertEquals(Integer.valueOf(AspectRatioFrameLayout.RESIZE_MODE_ZOOM), viewModel.getResizeMode().getValue());

        next = viewModel.toggleResizeMode();
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, next);
        assertEquals(Integer.valueOf(AspectRatioFrameLayout.RESIZE_MODE_FIT), viewModel.getResizeMode().getValue());
    }

    @Test
    public void testOrientationLockToggle() {
        assertFalse(Boolean.TRUE.equals(viewModel.isOrientationLocked().getValue()));

        boolean locked = viewModel.toggleOrientationLock();
        assertTrue(locked);
        assertTrue(Boolean.TRUE.equals(viewModel.isOrientationLocked().getValue()));

        locked = viewModel.toggleOrientationLock();
        assertFalse(locked);
        assertFalse(Boolean.TRUE.equals(viewModel.isOrientationLocked().getValue()));
    }

    @Test
    public void testScreenLockToggle() {
        assertFalse(Boolean.TRUE.equals(viewModel.isLocked().getValue()));

        boolean locked = viewModel.toggleLock();
        assertTrue(locked);
        assertTrue(Boolean.TRUE.equals(viewModel.isLocked().getValue()));

        locked = viewModel.toggleLock();
        assertFalse(locked);
        assertFalse(Boolean.TRUE.equals(viewModel.isLocked().getValue()));
    }

    @Test
    public void testSubtitlesState() {
        assertFalse(Boolean.TRUE.equals(viewModel.areSubtitlesEnabled().getValue()));

        viewModel.setSubtitleTrack(0);
        assertTrue(Boolean.TRUE.equals(viewModel.areSubtitlesEnabled().getValue()));
        assertEquals(Integer.valueOf(0), viewModel.getSubtitleTrack().getValue());

        viewModel.setSubtitleTrack(-1);
        assertFalse(Boolean.TRUE.equals(viewModel.areSubtitlesEnabled().getValue()));

        Uri subUri = Uri.parse("content://media/external/subtitles/sub1.srt");
        viewModel.setSubtitleUri(subUri);
        assertTrue(Boolean.TRUE.equals(viewModel.areSubtitlesEnabled().getValue()));
        assertEquals(subUri, viewModel.getSubtitleUri().getValue());

        viewModel.setSubtitleUri(null);
        assertFalse(Boolean.TRUE.equals(viewModel.areSubtitlesEnabled().getValue()));
    }

    @Test
    public void testBoostLevelAndRemainingTime() {
        viewModel.setBoostLevel(12);
        assertEquals(Integer.valueOf(12), viewModel.getBoostLevel().getValue());

        assertFalse(Boolean.TRUE.equals(viewModel.getShowRemainingTime().getValue()));
        boolean remaining = viewModel.toggleShowRemainingTime();
        assertTrue(remaining);
        assertTrue(Boolean.TRUE.equals(viewModel.getShowRemainingTime().getValue()));
    }
}
