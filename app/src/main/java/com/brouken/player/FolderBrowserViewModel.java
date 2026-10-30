package com.brouken.player;

import android.app.Application;
import android.net.Uri;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * ViewModel managing asynchronous folder discovery, search query filtering,
 * folder navigation, and watch progress updates for the folder browser.
 */
public class FolderBrowserViewModel extends AndroidViewModel {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Future<?> mScanTask;
    private int mScanGeneration = 0;

    private final Object mDataLock = new Object();
    private volatile MediaScanner.VideoFolder mActiveFolder = null;
    private volatile String mCurrentSearchQuery = "";

    private final List<MediaScanner.VideoFolder> mLoadedFolders = new ArrayList<>();
    private final List<FolderBrowserAdapter.BrowserItem> mCurrentVideoItems = new ArrayList<>();

    // LiveData states
    private final MutableLiveData<List<MediaScanner.VideoFolder>> folders = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<MediaScanner.VideoFolder> currentFolder = new MutableLiveData<>(null);
    private final MutableLiveData<List<FolderBrowserAdapter.BrowserItem>> displayedItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isSwipeRefreshing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isEmpty = new MutableLiveData<>(false);
    private final MutableLiveData<String> emptyTitle = new MutableLiveData<>(null);
    private final MutableLiveData<String> emptyHint = new MutableLiveData<>(null);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isSearching = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, WatchHistory.Entry>> watchHistory = new MutableLiveData<>(null);
    private final MutableLiveData<String> scanError = new MutableLiveData<>(null);

    private Prefs.SortMode mSortMode = Prefs.SortMode.DATE_DESC;

    public FolderBrowserViewModel(@NonNull Application application) {
        super(application);
        refreshWatchHistory();
    }

    // --- Getters for LiveData ---

    @NonNull
    public LiveData<List<MediaScanner.VideoFolder>> getFolders() {
        return folders;
    }

    @NonNull
    public LiveData<MediaScanner.VideoFolder> getCurrentFolder() {
        return currentFolder;
    }

    @NonNull
    public LiveData<List<FolderBrowserAdapter.BrowserItem>> getDisplayedItems() {
        return displayedItems;
    }

    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    @NonNull
    public LiveData<Boolean> getIsSwipeRefreshing() {
        return isSwipeRefreshing;
    }

    @NonNull
    public LiveData<Boolean> getIsEmpty() {
        return isEmpty;
    }

    @NonNull
    public LiveData<String> getEmptyTitle() {
        return emptyTitle;
    }

    @NonNull
    public LiveData<String> getEmptyHint() {
        return emptyHint;
    }

    @NonNull
    public LiveData<String> getSearchQuery() {
        return searchQuery;
    }

    @NonNull
    public LiveData<Boolean> getIsSearching() {
        return isSearching;
    }

    @NonNull
    public LiveData<Map<String, WatchHistory.Entry>> getWatchHistory() {
        return watchHistory;
    }

    @NonNull
    public LiveData<String> getScanError() {
        return scanError;
    }

    // --- Actions & Business Logic ---

    public void refreshWatchHistory() {
        executorService.execute(() -> {
            WatchHistory history = new WatchHistory(getApplication());
            watchHistory.postValue(history.snapshot());
        });
    }

    public void setSortMode(Prefs.SortMode sortMode) {
        this.mSortMode = sortMode != null ? sortMode : Prefs.SortMode.DATE_DESC;
        if (mActiveFolder != null || currentFolder.getValue() != null) {
            synchronized (mDataLock) {
                sortVideoItems(mCurrentVideoItems, mSortMode);
            }
            applyFilter();
        }
    }

    public void scanLibrary(@NonNull Prefs prefs, boolean isSwipeRefresh, @Nullable String restoreFolderId) {
        int request = ++mScanGeneration;
        if (mScanTask != null) {
            mScanTask.cancel(true);
        }

        if (isSwipeRefresh) {
            isSwipeRefreshing.setValue(true);
        } else if (displayedItems.getValue() == null || displayedItems.getValue().isEmpty()) {
            isLoading.setValue(true);
        }
        isEmpty.setValue(false);
        scanError.setValue(null);

        final String folderIdToRestore = mActiveFolder != null ? mActiveFolder.id : (currentFolder.getValue() != null ? currentFolder.getValue().id : restoreFolderId);

        mScanTask = executorService.submit(() -> {
            try {
                MediaScanner.ScanResult result = MediaScanner.scan(getApplication().getApplicationContext(), prefs);
                CaptionAutoJobs.observe(getApplication().getApplicationContext(), result.displayedFolders);
                new CaptionWorkStore(getApplication().getApplicationContext()).reconcileReady();

                if (request != mScanGeneration) return;

                synchronized (mDataLock) {
                    mLoadedFolders.clear();
                    mLoadedFolders.addAll(result.displayedFolders);
                }
                folders.postValue(new ArrayList<>(result.displayedFolders));

                MediaScanner.VideoFolder selected = null;
                if (folderIdToRestore != null) {
                    for (MediaScanner.VideoFolder f : result.displayedFolders) {
                        if (f.id.equals(folderIdToRestore)) {
                            selected = f;
                            break;
                        }
                    }
                }

                final MediaScanner.VideoFolder folderToShow = selected;
                isLoading.postValue(false);
                isSwipeRefreshing.postValue(false);

                if (folderToShow != null) {
                    showFolderInternal(folderToShow, false);
                } else {
                    mActiveFolder = null;
                    currentFolder.postValue(null);
                    restoreRootItemsInternal(result.displayedFolders);
                }
            } catch (Exception e) {
                if (request != mScanGeneration) return;
                isLoading.postValue(false);
                isSwipeRefreshing.postValue(false);
                scanError.postValue(e.getMessage());
                showEmpty(true, "Could not load the library", "Check folder access, then pull down to retry.");
            }
        });
    }

    public void cancelScan() {
        if (mScanTask != null) {
            mScanGeneration++;
            mScanTask.cancel(true);
            mScanTask = null;
        }
        isLoading.setValue(false);
        isSwipeRefreshing.setValue(false);
    }

    public void openFolder(@NonNull MediaScanner.VideoFolder folder, @Nullable Prefs.SortMode sortMode) {
        cancelScan();
        if (sortMode != null) mSortMode = sortMode;
        showFolderInternal(folder, true);
    }

    public boolean navigateUp() {
        if (mActiveFolder != null || currentFolder.getValue() != null) {
            cancelScan();
            mActiveFolder = null;
            currentFolder.postValue(null);
            synchronized (mDataLock) {
                mCurrentVideoItems.clear();
            }
            restoreRootItemsInternal();
            return true;
        }
        return false;
    }

    public void setSearchQuery(@Nullable String query) {
        mCurrentSearchQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        searchQuery.setValue(mCurrentSearchQuery);
        applyFilter();
    }

    public void setIsSearching(boolean searching) {
        isSearching.setValue(searching);
        if (!searching) {
            mCurrentSearchQuery = "";
            searchQuery.setValue("");
            applyFilter();
        }
    }

    private void showFolderInternal(MediaScanner.VideoFolder folder, boolean resetSearch) {
        mActiveFolder = folder;
        currentFolder.postValue(folder);
        if (resetSearch) {
            mCurrentSearchQuery = "";
            searchQuery.postValue("");
            isSearching.postValue(false);
        }

        List<FolderBrowserAdapter.BrowserItem> videoItems = new ArrayList<>();
        if (folder != null && folder.videos != null) {
            for (MediaScanner.VideoItem video : folder.videos) {
                videoItems.add(new FolderBrowserAdapter.BrowserItem(
                        FolderBrowserAdapter.BrowserItem.TYPE_VIDEO,
                        video.uri,
                        video.name,
                        video.path,
                        video.size,
                        video.dateAdded,
                        video.duration,
                        video.isHidden,
                        0,
                        null
                ));
            }
        }

        sortVideoItems(videoItems, mSortMode);
        synchronized (mDataLock) {
            mCurrentVideoItems.clear();
            mCurrentVideoItems.addAll(videoItems);
        }

        applyFilter();
    }

    private void restoreRootItemsInternal() {
        List<MediaScanner.VideoFolder> loadedFolders;
        synchronized (mDataLock) {
            loadedFolders = new ArrayList<>(mLoadedFolders);
        }
        restoreRootItemsInternal(loadedFolders);
    }

    private void restoreRootItemsInternal(List<MediaScanner.VideoFolder> loadedFolders) {
        List<FolderBrowserAdapter.BrowserItem> folderItems = new ArrayList<>();
        if (loadedFolders != null) {
            for (MediaScanner.VideoFolder folder : loadedFolders) {
                folderItems.add(new FolderBrowserAdapter.BrowserItem(
                        FolderBrowserAdapter.BrowserItem.TYPE_FOLDER,
                        folder.posterUri,
                        folder.name,
                        folder.path,
                        0, 0, 0,
                        folder.isHidden,
                        folder.getVideoCount(),
                        folder
                ));
            }
        }

        if (folderItems.isEmpty()) {
            displayedItems.postValue(new ArrayList<>());
            showEmpty(true, "No video folders found", "No videos found. Check storage access or enable \"Show hidden folders\" in the menu.");
        } else {
            showEmpty(false, null, null);
            displayedItems.postValue(folderItems);
        }
    }

    private void applyFilter() {
        String query = mCurrentSearchQuery;
        if (query == null) query = "";
        query = query.trim().toLowerCase(Locale.ROOT);

        MediaScanner.VideoFolder active = mActiveFolder;
        List<FolderBrowserAdapter.BrowserItem> currentVideos;
        List<MediaScanner.VideoFolder> loadedFolders;
        synchronized (mDataLock) {
            currentVideos = new ArrayList<>(mCurrentVideoItems);
            loadedFolders = new ArrayList<>(mLoadedFolders);
        }

        if (query.isEmpty()) {
            if (active != null) {
                displayedItems.postValue(new ArrayList<>(currentVideos));
                if (currentVideos.isEmpty()) {
                    showEmpty(true, "No videos or subfolders found", "Try choosing a different folder or enable \"Show hidden folders\" in the menu.");
                } else {
                    showEmpty(false, null, null);
                }
            } else {
                restoreRootItemsInternal(loadedFolders);
            }
            return;
        }

        List<FolderBrowserAdapter.BrowserItem> filtered = new ArrayList<>();
        if (active != null) {
            for (FolderBrowserAdapter.BrowserItem item : currentVideos) {
                if (item != null && item.name != null && item.name.toLowerCase(Locale.ROOT).contains(query)) {
                    filtered.add(item);
                }
            }
        } else {
            // Root mode: match folders and match videos across folders
            for (MediaScanner.VideoFolder folder : loadedFolders) {
                if (folder != null && folder.name != null && folder.name.toLowerCase(Locale.ROOT).contains(query)) {
                    filtered.add(new FolderBrowserAdapter.BrowserItem(
                            FolderBrowserAdapter.BrowserItem.TYPE_FOLDER,
                            folder.posterUri,
                            folder.name,
                            folder.path,
                            0, 0, 0,
                            folder.isHidden,
                            folder.getVideoCount(),
                            folder
                    ));
                }
            }

            for (MediaScanner.VideoFolder folder : loadedFolders) {
                if (folder != null && folder.videos != null) {
                    for (MediaScanner.VideoItem video : folder.videos) {
                        if (video != null && video.name != null && video.name.toLowerCase(Locale.ROOT).contains(query)) {
                            filtered.add(new FolderBrowserAdapter.BrowserItem(
                                    FolderBrowserAdapter.BrowserItem.TYPE_VIDEO,
                                    video.uri,
                                    video.name,
                                    video.path,
                                    video.size,
                                    video.dateAdded,
                                    video.duration,
                                    video.isHidden,
                                    0,
                                    null
                            ));
                        }
                    }
                }
            }
        }

        displayedItems.postValue(filtered);
        if (filtered.isEmpty()) {
            showEmpty(true, "No matching results found", "Try searching with a different name");
        } else {
            showEmpty(false, null, null);
        }
    }

    private void showEmpty(boolean empty, @Nullable String title, @Nullable String hint) {
        isEmpty.postValue(empty);
        emptyTitle.postValue(title);
        emptyHint.postValue(hint);
    }

    public static void sortVideoItems(List<FolderBrowserAdapter.BrowserItem> videos, Prefs.SortMode sortMode) {
        if (videos == null) return;
        if (sortMode == null) sortMode = Prefs.SortMode.DATE_DESC;
        switch (sortMode) {
            case NAME_ASC:
                Collections.sort(videos, (a, b) -> {
                    String nameA = (a != null && a.name != null) ? a.name : "";
                    String nameB = (b != null && b.name != null) ? b.name : "";
                    return nameA.compareToIgnoreCase(nameB);
                });
                break;
            case NAME_DESC:
                Collections.sort(videos, (a, b) -> {
                    String nameA = (a != null && a.name != null) ? a.name : "";
                    String nameB = (b != null && b.name != null) ? b.name : "";
                    return nameB.compareToIgnoreCase(nameA);
                });
                break;
            case DATE_DESC:
                Collections.sort(videos, (a, b) -> Long.compare(b != null ? b.lastModified : 0, a != null ? a.lastModified : 0));
                break;
            case DATE_ASC:
                Collections.sort(videos, (a, b) -> Long.compare(a != null ? a.lastModified : 0, b != null ? b.lastModified : 0));
                break;
            case SIZE_DESC:
                Collections.sort(videos, (a, b) -> Long.compare(b != null ? b.size : 0, a != null ? a.size : 0));
                break;
            case SIZE_ASC:
                Collections.sort(videos, (a, b) -> Long.compare(a != null ? a.size : 0, b != null ? b.size : 0));
                break;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelScan();
        executorService.shutdownNow();
    }
}
