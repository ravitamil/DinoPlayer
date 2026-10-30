package com.brouken.player;

import android.app.Application;
import android.net.Uri;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@Config(manifest = Config.NONE)
public class FolderBrowserViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private FolderBrowserViewModel viewModel;
    private Application application;

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        viewModel = new FolderBrowserViewModel(application);
    }

    @Test
    public void testInitialState() {
        assertNotNull(viewModel.getFolders().getValue());
        assertTrue(viewModel.getFolders().getValue().isEmpty());
        assertNull(viewModel.getCurrentFolder().getValue());
        assertNotNull(viewModel.getDisplayedItems().getValue());
        assertFalse(Boolean.TRUE.equals(viewModel.getIsLoading().getValue()));
        assertFalse(Boolean.TRUE.equals(viewModel.getIsSwipeRefreshing().getValue()));
        assertEquals("", viewModel.getSearchQuery().getValue());
        assertFalse(Boolean.TRUE.equals(viewModel.getIsSearching().getValue()));
    }

    @Test
    public void testFolderNavigationAndSearchFilter() {
        MediaScanner.VideoFolder folder = new MediaScanner.VideoFolder("f1", "Movies", "/storage/emulated/0/Movies", false);

        MediaScanner.VideoItem video1 = new MediaScanner.VideoItem(
                Uri.parse("content://media/external/video/media/1"),
                "Interstellar.2014.mkv",
                "/storage/emulated/0/Movies/Interstellar.2014.mkv",
                1024L * 1024L * 800L,
                1600000000L,
                180 * 60 * 1000L,
                false
        );
        MediaScanner.VideoItem video2 = new MediaScanner.VideoItem(
                Uri.parse("content://media/external/video/media/2"),
                "Inception.2010.mp4",
                "/storage/emulated/0/Movies/Inception.2010.mp4",
                1024L * 1024L * 600L,
                1500000000L,
                148 * 60 * 1000L,
                false
        );
        MediaScanner.VideoItem video3 = new MediaScanner.VideoItem(
                Uri.parse("content://media/external/video/media/3"),
                "The.Matrix.1999.mp4",
                "/storage/emulated/0/Movies/The.Matrix.1999.mp4",
                1024L * 1024L * 700L,
                1400000000L,
                136 * 60 * 1000L,
                false
        );

        folder.addVideo(video1);
        folder.addVideo(video2);
        folder.addVideo(video3);

        // Open folder
        viewModel.openFolder(folder, Prefs.SortMode.NAME_ASC);

        assertEquals(folder, viewModel.getCurrentFolder().getValue());
        List<FolderBrowserAdapter.BrowserItem> items = viewModel.getDisplayedItems().getValue();
        assertNotNull(items);
        assertEquals(3, items.size());
        assertEquals("Inception.2010.mp4", items.get(0).name);
        assertEquals("Interstellar.2014.mkv", items.get(1).name);
        assertEquals("The.Matrix.1999.mp4", items.get(2).name);

        // Search query filtering
        viewModel.setSearchQuery("matrix");
        assertEquals("matrix", viewModel.getSearchQuery().getValue());
        List<FolderBrowserAdapter.BrowserItem> filtered = viewModel.getDisplayedItems().getValue();
        assertNotNull(filtered);
        assertEquals(1, filtered.size());
        assertEquals("The.Matrix.1999.mp4", filtered.get(0).name);

        // Filter with no match
        viewModel.setSearchQuery("nonexistent");
        List<FolderBrowserAdapter.BrowserItem> emptyMatch = viewModel.getDisplayedItems().getValue();
        assertNotNull(emptyMatch);
        assertTrue(emptyMatch.isEmpty());
        assertTrue(Boolean.TRUE.equals(viewModel.getIsEmpty().getValue()));

        // Clear query
        viewModel.setSearchQuery("");
        List<FolderBrowserAdapter.BrowserItem> restored = viewModel.getDisplayedItems().getValue();
        assertNotNull(restored);
        assertEquals(3, restored.size());

        // Navigate up
        boolean navigated = viewModel.navigateUp();
        assertTrue(navigated);
        assertNull(viewModel.getCurrentFolder().getValue());
    }

    @Test
    public void testSortModesInFolder() {
        MediaScanner.VideoFolder folder = new MediaScanner.VideoFolder("f2", "Clips", "/storage/emulated/0/Clips", false);

        folder.addVideo(new MediaScanner.VideoItem(
                Uri.parse("content://1"), "B_clip.mp4", "/storage/emulated/0/Clips/B_clip.mp4",
                100L, 2000L, 60000L, false
        ));
        folder.addVideo(new MediaScanner.VideoItem(
                Uri.parse("content://2"), "A_clip.mp4", "/storage/emulated/0/Clips/A_clip.mp4",
                500L, 1000L, 30000L, false
        ));
        folder.addVideo(new MediaScanner.VideoItem(
                Uri.parse("content://3"), "C_clip.mp4", "/storage/emulated/0/Clips/C_clip.mp4",
                300L, 3000L, 45000L, false
        ));

        // Sort by NAME_ASC
        viewModel.openFolder(folder, Prefs.SortMode.NAME_ASC);
        List<FolderBrowserAdapter.BrowserItem> items = viewModel.getDisplayedItems().getValue();
        assertNotNull(items);
        assertEquals("A_clip.mp4", items.get(0).name);
        assertEquals("B_clip.mp4", items.get(1).name);
        assertEquals("C_clip.mp4", items.get(2).name);

        // Sort by SIZE_DESC
        viewModel.setSortMode(Prefs.SortMode.SIZE_DESC);
        items = viewModel.getDisplayedItems().getValue();
        assertNotNull(items);
        assertEquals("A_clip.mp4", items.get(0).name); // 500L
        assertEquals("C_clip.mp4", items.get(1).name); // 300L
        assertEquals("B_clip.mp4", items.get(2).name); // 100L

        // Sort by DATE_DESC
        viewModel.setSortMode(Prefs.SortMode.DATE_DESC);
        items = viewModel.getDisplayedItems().getValue();
        assertNotNull(items);
        assertEquals("C_clip.mp4", items.get(0).name); // 3000L
        assertEquals("B_clip.mp4", items.get(1).name); // 2000L
        assertEquals("A_clip.mp4", items.get(2).name); // 1000L
    }

    @Test
    public void testSortVideoItemsNullSafety() {
        java.util.List<FolderBrowserAdapter.BrowserItem> list = new java.util.ArrayList<>();
        list.add(new FolderBrowserAdapter.BrowserItem(FolderBrowserAdapter.BrowserItem.TYPE_VIDEO, null, null, null, 100, 100, 100, false, 0, null));
        list.add(new FolderBrowserAdapter.BrowserItem(FolderBrowserAdapter.BrowserItem.TYPE_VIDEO, null, "Video.mp4", null, 200, 200, 200, false, 0, null));

        // Should not throw NullPointerException
        FolderBrowserViewModel.sortVideoItems(list, Prefs.SortMode.NAME_ASC);
        assertEquals(2, list.size());
        assertEquals("", list.get(0).name == null ? "" : list.get(0).name);

        FolderBrowserViewModel.sortVideoItems(list, Prefs.SortMode.NAME_DESC);
        assertEquals(2, list.size());
        assertEquals("Video.mp4", list.get(0).name);

        // Null list should not crash
        FolderBrowserViewModel.sortVideoItems(null, Prefs.SortMode.NAME_ASC);
    }

    @Test
    public void testSearchQueryWhitespaceAndTrimming() {
        MediaScanner.VideoFolder folder = new MediaScanner.VideoFolder("f3", "TestFolder", "/path", false);
        folder.addVideo(new MediaScanner.VideoItem(
                Uri.parse("content://1"), "Avatar 2009.mkv", "/path/Avatar 2009.mkv", 100, 100, 100, false
        ));
        viewModel.openFolder(folder, Prefs.SortMode.NAME_ASC);

        viewModel.setSearchQuery("   AVATAR   ");
        List<FolderBrowserAdapter.BrowserItem> items = viewModel.getDisplayedItems().getValue();
        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("Avatar 2009.mkv", items.get(0).name);

        viewModel.setSearchQuery(null);
        items = viewModel.getDisplayedItems().getValue();
        assertNotNull(items);
        assertEquals(1, items.size());
    }
}
