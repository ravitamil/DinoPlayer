package com.brouken.player;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FolderBrowserActivity extends AppCompatActivity implements FolderBrowserAdapter.OnItemClickListener {

    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final int REQUEST_CODE_SETTINGS = 1002;
    private static final String PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO";

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener captionListener=(p,k)-> {
        this.mainHandler.removeCallbacks(this.refreshCaptionStates);this.mainHandler.postDelayed(this.refreshCaptionStates,200);
    };
    private final Runnable refreshCaptionStates=()-> { if(this.adapter!=null)this.adapter.refreshCaptionStates(); };
    private Prefs mPrefs;
    private Toolbar toolbar;
    private TextView textBreadcrumb;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private View progressBar;
    private TextView headerTitle;
    private TextView headerSummary;
    private View layoutEmpty;
    private View layoutOnboarding;

    private FolderBrowserAdapter adapter;
    private MediaScanner.VideoFolder mCurrentFolder = null;
    private final List<MediaScanner.VideoFolder> mLoadedFolders = new ArrayList<>();
    private final List<FolderBrowserAdapter.BrowserItem> mCurrentVideoItems = new ArrayList<>();

    private MenuItem mSearchMenuItem;
    private SearchView mSearchView;
    private String mCurrentSearchQuery = "";
    private boolean mIsSearching = false;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ContentObserver mMediaStoreObserver;
    private Future<?> mScanTask;
    private int mScanGeneration;
    private String mRestoreFolderId;
    private Parcelable mRestoreScroll;
    private Parcelable mLibraryScroll;
    private String mRestoreQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_browser);

        CaptionWorkStore.initialize(this);
        CaptionAutoJobs.scheduleWatch(this);
        CaptionAutoJobs.kick(this);
        mPrefs = new Prefs(this);

        CaptionPackages.get(this).reconcile();

        initViews();
        if (savedInstanceState != null) {
            mRestoreFolderId = savedInstanceState.getString("folder");
            mRestoreQuery = savedInstanceState.getString("query", "");
            mRestoreScroll = savedInstanceState.getParcelable("scroll");
            mLibraryScroll = savedInstanceState.getParcelable("libraryScroll");
        }
        registerMediaStoreObserver();

        if (hasStoragePermission()) {
            loadRootFolders(false);
        } else {
            showOnboarding(true);
            requestStoragePermissions();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSharedPreferences("caption_work",0).registerOnSharedPreferenceChangeListener(captionListener);
        registerMediaStoreObserver();
        mPrefs = new Prefs(this);
        if (hasStoragePermission()) {
            adapter.refreshHistory();
            if (mScanTask == null) refreshCurrent(false);
        }
    }

    @Override protected void onPause() {
        unregisterMediaStoreObserver();
        getSharedPreferences("caption_work",0).unregisterOnSharedPreferenceChangeListener(captionListener);
        mainHandler.removeCallbacks(refreshCaptionStates);super.onPause();
    }

    @Override public void onVideoActions(FolderBrowserAdapter.BrowserItem item) {
        java.util.List<String> labels=new ArrayList<>();java.util.List<Runnable> actions=new ArrayList<>();
        CaptionWorkStore.Entry state=new CaptionWorkStore(this).read(item.uri,CaptionPackages.get(this).selected());
        boolean busy=state!=null && (state.status==CaptionWorkStore.Status.GENERATING || state.status==CaptionWorkStore.Status.QUEUED);
        if(busy) {
            labels.add("Cancel subtitle generation");actions.add(()->executorService.execute(()-> {
                CaptionAutoJobs.removeQueued(this,item.uri,null);
                try { SubtitleGenerator.cancelAndAwait(item.uri);
                    CaptionWorkStore store=new CaptionWorkStore(this);
                    for(CaptionPack pack:CaptionPack.ALL) { CaptionWorkStore.Entry current=store.read(item.uri,pack);
                        if(current!=null && current.status==CaptionWorkStore.Status.QUEUED)store.update(item.uri,pack,current.token,CaptionWorkStore.Status.CANCELLED,0,""); }
                }catch(Exception e) { mainHandler.post(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show()); }
            }));
        } else {
            labels.add("Generate / retry subtitles");actions.add(()->generateFromList(item,false));
            labels.add("Regenerate subtitles");actions.add(()->new AlertDialog.Builder(this).setMessage("Replace this video’s generated subtitles and saved edits after generation succeeds?")
                    .setPositiveButton("Regenerate",(d,w)->generateFromList(item,true)).setNegativeButton(android.R.string.cancel,null).show());
            labels.add("Edit / export subtitles");actions.add(()->startActivity(new Intent(this,SubtitleEditorActivity.class).setData(item.uri).putExtra("path",item.path)));
        }
        labels.add("Delete video");actions.add(()->confirmDelete(item));
        new AlertDialog.Builder(this).setTitle(item.name).setItems(labels.toArray(new String[0]),(d,w)->actions.get(w).run()).show();
    }
    private void generateFromList(FolderBrowserAdapter.BrowserItem item,boolean force) {
        if(!CaptionPackages.get(this).isInstalled(CaptionPackages.get(this).selected())) {
            new AlertDialog.Builder(this).setMessage("Install the selected offline subtitle pack in Settings first.")
                    .setPositiveButton("Settings",(d,w)->openSettings()).setNegativeButton(android.R.string.cancel,null).show();return;
        }
        // Watching this video streams captions as they arrive and provides a direct Cancel action.
        Intent intent=new Intent(this,PlayerActivity.class).setDataAndType(item.uri,"video/*").putExtra("caption_request",force?"regenerate":"generate");
        startActivity(intent);
    }
    private void confirmDelete(FolderBrowserAdapter.BrowserItem item) {
        android.widget.CheckBox include=new android.widget.CheckBox(this);include.setTextSize(14);
        include.setText("Also delete DinoPlayer’s generated subtitles and saved edits");include.setChecked(true);
        new AlertDialog.Builder(this).setTitle("Delete video permanently?")
                .setMessage(item.name+"\n\nSubtitle generation will stop before deletion. Separate SRT/VTT files next to the video and exported copies are kept.")
                .setView(include).setPositiveButton("Delete",(d,w)-> {
                    cancelPendingScan();
                    startActivityForResult(new Intent(this,VideoDeletionActivity.class).setData(item.uri).putExtra("path",item.path).putExtra("captions",include.isChecked()),4100);
                }).setNegativeButton(android.R.string.cancel,null).show();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        textBreadcrumb = findViewById(R.id.text_breadcrumb);
        headerTitle = findViewById(R.id.library_header_title);
        headerSummary = findViewById(R.id.library_header_summary);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        layoutEmpty = findViewById(R.id.layout_empty);
        layoutOnboarding = findViewById(R.id.layout_onboarding);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FolderBrowserAdapter(this, this);
        recyclerView.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.green);
        // Fixed: Swipe-to-refresh only shows its own spinner, NOT the center progressBar!
        swipeRefresh.setOnRefreshListener(() -> refreshCurrent(true));

        findViewById(R.id.button_select_folder).setOnClickListener(v -> requestStoragePermissions());
        findViewById(R.id.button_empty_change_folder).setOnClickListener(v -> openSettings());
    }

    private boolean hasStoragePermission() {
        for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (permission.isReadPermission() && permission.getUri().getPathSegments().contains("tree")) return true;
        }
        if (Build.VERSION.SDK_INT >= 33 && getApplicationInfo().targetSdkVersion >= 33) {
            return ContextCompat.checkSelfPermission(this, PERMISSION_READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermissions() {
        if (hasStoragePermission()) { loadRootFolders(false); return; }
        if (Build.VERSION.SDK_INT >= 33 && getApplicationInfo().targetSdkVersion >= 33) {
            ActivityCompat.requestPermissions(this,
                    new String[]{PERMISSION_READ_MEDIA_VIDEO},
                    REQUEST_CODE_PERMISSIONS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_PERMISSIONS);
        } else {
            loadRootFolders(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showOnboarding(false);
                loadRootFolders(false);
            } else {
                showPermissionRationaleDialog();
            }
        }
    }

    private void showPermissionRationaleDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage("Storage permission is required to index and play videos from your device folders. Please grant file access.")
                .setPositiveButton("Grant Access", (dialog, which) -> requestStoragePermissions())
                .setNeutralButton("Choose folders", (dialog, which) -> openSettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, REQUEST_CODE_SETTINGS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==4100 && resultCode==RESULT_OK) { cancelPendingScan();refreshCurrent(false);return; }
        if (requestCode == REQUEST_CODE_SETTINGS) {
            mPrefs = new Prefs(this);
            refreshCurrent(false);
        }
    }

    private void loadRootFolders(boolean isSwipeRefresh) {
        mCurrentFolder = null;
        scanLibrary(isSwipeRefresh);
    }

    private void scanLibrary(boolean isSwipeRefresh) {
        if (isDestroyed() || executorService.isShutdown()) return;
        int request = ++mScanGeneration;
        if (mScanTask != null) mScanTask.cancel(true);
        String folderId = mCurrentFolder != null ? mCurrentFolder.id : mRestoreFolderId;
        Prefs scanPrefs = new Prefs(this);
        if (!isSwipeRefresh && adapter.getItemCount() == 0) progressBar.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        layoutOnboarding.setVisibility(View.GONE);
        mScanTask = executorService.submit(() -> {
            try {
                MediaScanner.ScanResult result = MediaScanner.scan(getApplicationContext(), scanPrefs);
                CaptionAutoJobs.observe(getApplicationContext(),result.displayedFolders);
                new CaptionWorkStore(getApplicationContext()).reconcileReady();
                mainHandler.post(() -> {
                    if (request != mScanGeneration || isFinishing() || isDestroyed()) return;
                    mScanTask = null;
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    mLoadedFolders.clear();
                    mLoadedFolders.addAll(result.displayedFolders);
                    MediaScanner.VideoFolder selected = null;
                    for (MediaScanner.VideoFolder folder : mLoadedFolders) {
                        if (folder.id.equals(folderId)) { selected = folder; break; }
                    }
                    if (selected != null) showFolder(selected, false);
                    else { mCurrentFolder = null; restoreCurrentItems(); }
                    if (!mRestoreQuery.isEmpty() && mSearchMenuItem != null) {
                        mSearchMenuItem.expandActionView();
                        mSearchView.setQuery(mRestoreQuery, false);
                        mRestoreQuery = "";
                    } else if (mIsSearching) filterItems(mCurrentSearchQuery);
                    if (mRestoreScroll != null && recyclerView.getLayoutManager() != null) {
                        recyclerView.getLayoutManager().onRestoreInstanceState(mRestoreScroll);
                        mRestoreScroll = null;
                    }
                    mRestoreFolderId = null;
                });
            } catch (RuntimeException error) {
                mainHandler.post(() -> {
                    if (request != mScanGeneration || isFinishing() || isDestroyed()) return;
                    mScanTask = null;
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    showEmptyView(true, getString(R.string.library_scan_failed), getString(R.string.library_scan_retry));
                });
            }
        });
    }

    private void openFolder(MediaScanner.VideoFolder folder) {
        cancelPendingScan();
        RecyclerView.LayoutManager layout = recyclerView.getLayoutManager();
        if (mCurrentFolder == null && layout != null) {
            mLibraryScroll = layout.onSaveInstanceState();
        }
        recyclerView.stopScroll();
        showFolder(folder, true);
        if (layout instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layout).scrollToPositionWithOffset(0, 0);
        }
    }

    private void cancelPendingScan() {
        if (mScanTask != null) {
            mScanGeneration++;
            mScanTask.cancel(true);
            mScanTask = null;
        }
        progressBar.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    private void showFolder(MediaScanner.VideoFolder folder, boolean resetSearch) {
        if (resetSearch && mSearchMenuItem != null && mSearchMenuItem.isActionViewExpanded()) {
            mSearchMenuItem.collapseActionView();
        }
        mCurrentFolder = folder;
        updateToolbarFolder(folder);
        if (mSearchView != null) {
            mSearchView.setQueryHint(getString(R.string.search_hint_videos));
        }

        List<FolderBrowserAdapter.BrowserItem> videoItems = new ArrayList<>();
        long totalSize = 0;
        for (MediaScanner.VideoItem video : folder.videos) {
            totalSize += video.size;
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

        sortVideoItems(videoItems, mPrefs.sortMode);

        mCurrentVideoItems.clear();
        mCurrentVideoItems.addAll(videoItems);

        adapter.setHistoryScope(mCurrentVideoItems);
        adapter.setItems(mCurrentVideoItems);

        if (mCurrentVideoItems.isEmpty()) {
            showEmptyView(true, getString(R.string.no_videos_found), getString(R.string.no_videos_hint));
        } else {
            showEmptyView(false, null, null);
            String sizeStr = Formatter.formatFileSize(this, totalSize);
            toolbar.setSubtitle(mCurrentVideoItems.size() + (mCurrentVideoItems.size() == 1 ? " video" : " videos") + " • " + sizeStr);
        }
    }

    private void filterItems(String query) {
        mCurrentSearchQuery = (query != null ? query.trim().toLowerCase(Locale.ROOT) : "");
        if (mCurrentSearchQuery.isEmpty()) {
            restoreCurrentItems();
            return;
        }

        if (mCurrentFolder != null) {
            List<FolderBrowserAdapter.BrowserItem> filtered = new ArrayList<>();
            for (FolderBrowserAdapter.BrowserItem item : mCurrentVideoItems) {
                if (item.name != null && item.name.toLowerCase(Locale.ROOT).contains(mCurrentSearchQuery)) {
                    filtered.add(item);
                }
            }
            adapter.setItems(filtered);
            if (filtered.isEmpty()) {
                showEmptyView(true, getString(R.string.no_search_results), getString(R.string.no_search_results_hint));
            } else {
                showEmptyView(false, null, null);
            }
        } else {
            List<FolderBrowserAdapter.BrowserItem> filtered = new ArrayList<>();

            // 1. Folders matching the query
            for (MediaScanner.VideoFolder folder : mLoadedFolders) {
                if (folder.name != null && folder.name.toLowerCase(Locale.ROOT).contains(mCurrentSearchQuery)) {
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

            // 2. Videos across all loaded folders matching the query
            for (MediaScanner.VideoFolder folder : mLoadedFolders) {
                for (MediaScanner.VideoItem video : folder.videos) {
                    if (video.name != null && video.name.toLowerCase(Locale.ROOT).contains(mCurrentSearchQuery)) {
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

            adapter.setItems(filtered);
            if (filtered.isEmpty()) {
                showEmptyView(true, getString(R.string.no_search_results), getString(R.string.no_search_results_hint));
            } else {
                showEmptyView(false, null, null);
            }
        }
    }

    private void restoreCurrentItems() {
        if (mCurrentFolder != null) {
            adapter.setItems(mCurrentVideoItems);
            if (mCurrentVideoItems.isEmpty()) {
                showEmptyView(true, getString(R.string.no_videos_found), getString(R.string.no_videos_hint));
            } else {
                showEmptyView(false, null, null);
            }
        } else {
            List<FolderBrowserAdapter.BrowserItem> folderItems = new ArrayList<>();
            int totalVideos = 0;
            for (MediaScanner.VideoFolder folder : mLoadedFolders) {
                totalVideos += folder.getVideoCount();
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
            adapter.setHistoryScope(null);
            adapter.setItems(folderItems);
            if (folderItems.isEmpty()) {
                showEmptyView(true, getString(R.string.no_folders_found), getString(R.string.no_folders_hint));
            } else {
                showEmptyView(false, null, null);
                updateToolbarRoot(mLoadedFolders.size(), totalVideos);
            }
        }
    }

    private void updateToolbarRoot(int folderCount, int videoCount) {
        toolbar.setTitle(R.string.app_name);
        headerTitle.setText(R.string.your_videos);
        toolbar.setNavigationIcon(null);
        textBreadcrumb.setVisibility(View.GONE);

        StringBuilder sb = new StringBuilder();
        sb.append(folderCount).append(folderCount == 1 ? " folder" : " folders");
        sb.append(" • ");
        sb.append(videoCount).append(videoCount == 1 ? " video" : " videos");
        toolbar.setSubtitle(null);
        headerSummary.setText(sb.toString());
    }

    private void updateToolbarFolder(MediaScanner.VideoFolder folder) {
        toolbar.setTitle(R.string.app_name);
        headerTitle.setText(folder.name);
        headerSummary.setText(getString(R.string.library_private));
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        textBreadcrumb.setVisibility(View.GONE);
    }

    private void refreshCurrent(boolean isSwipeRefresh) {
        scanLibrary(isSwipeRefresh);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if (mCurrentFolder != null) state.putString("folder", mCurrentFolder.id);
        state.putString("query", mCurrentSearchQuery);
        state.putParcelable("libraryScroll", mLibraryScroll);
        if (recyclerView.getLayoutManager() != null) {
            state.putParcelable("scroll", recyclerView.getLayoutManager().onSaveInstanceState());
        }
    }

    private void showOnboarding(boolean show) {
        layoutOnboarding.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
    }

    private void showEmptyView(boolean show, String title, String hint) {
        layoutEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) {
            if (title != null) {
                ((TextView) findViewById(R.id.text_empty_title)).setText(title);
            }
            if (hint != null) {
                ((TextView) findViewById(R.id.text_empty_desc)).setText(hint);
            }
            TextView btn = findViewById(R.id.button_empty_change_folder);
            if (btn != null) {
                btn.setText("OPEN SETTINGS");
            }
        }
    }

    @Override
    public void onFolderClick(FolderBrowserAdapter.BrowserItem folderItem) {
        if (folderItem != null && folderItem.folder != null) {
            openFolder(folderItem.folder);
        }
    }

    @Override
    public void onVideoClick(FolderBrowserAdapter.BrowserItem videoItem) {
        if (videoItem != null && videoItem.uri != null) {
            List<Uri> playlist = new ArrayList<>();
            for (FolderBrowserAdapter.BrowserItem item : adapter.getItems()) {
                if (item.type == FolderBrowserAdapter.BrowserItem.TYPE_VIDEO && item.uri != null) {
                    playlist.add(item.uri);
                }
            }
            PlaylistManager.setPlaylist(this, playlist, videoItem.uri);

            Intent intent = new Intent(this, PlayerActivity.class);
            intent.setDataAndType(videoItem.uri, "video/*");
            if (videoItem.path != null) {
                intent.putExtra("video_path", videoItem.path);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        if (mSearchMenuItem != null && mSearchMenuItem.isActionViewExpanded()) {
            mSearchMenuItem.collapseActionView();
            return;
        }
        if (mCurrentFolder != null) {
            recyclerView.stopScroll();
            cancelPendingScan();
            mCurrentFolder = null;
            restoreCurrentItems();
            RecyclerView.LayoutManager layout = recyclerView.getLayoutManager();
            if (layout != null && mLibraryScroll != null) layout.onRestoreInstanceState(mLibraryScroll);
            else if (layout != null) layout.scrollToPosition(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_folder_browser, menu);

        mSearchMenuItem = menu.findItem(R.id.action_search);
        if (mSearchMenuItem != null) {
            mSearchView = (SearchView) mSearchMenuItem.getActionView();
            if (mSearchView != null) {
                mSearchView.setQueryHint(mCurrentFolder != null ? getString(R.string.search_hint_videos) : getString(R.string.search_hint));
                mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        filterItems(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        filterItems(newText);
                        return true;
                    }
                });
            }

            mSearchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    mIsSearching = true;
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    mIsSearching = false;
                    mCurrentSearchQuery = "";
                    restoreCurrentItems();
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.action_refresh) {
            refreshCurrent(false);
            return true;
        } else if (id == R.id.action_settings) {
            openSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sortVideoItems(List<FolderBrowserAdapter.BrowserItem> videos, Prefs.SortMode sortMode) {
        if (sortMode == null) {
            sortMode = Prefs.SortMode.DATE_DESC;
        }
        switch (sortMode) {
            case NAME_ASC:
                Collections.sort(videos, (a, b) -> a.name.compareToIgnoreCase(b.name));
                break;
            case NAME_DESC:
                Collections.sort(videos, (a, b) -> b.name.compareToIgnoreCase(a.name));
                break;
            case DATE_DESC:
                Collections.sort(videos, (a, b) -> Long.compare(b.lastModified, a.lastModified));
                break;
            case DATE_ASC:
                Collections.sort(videos, (a, b) -> Long.compare(a.lastModified, b.lastModified));
                break;
            case SIZE_DESC:
                Collections.sort(videos, (a, b) -> Long.compare(b.size, a.size));
                break;
            case SIZE_ASC:
                Collections.sort(videos, (a, b) -> Long.compare(a.size, b.size));
                break;
        }
    }

    private void showSortDialog() {
        final Prefs.SortMode[] modes = Prefs.SortMode.values();
        CharSequence[] items = new CharSequence[modes.length];
        int selected = 0;
        for (int i = 0; i < modes.length; i++) {
            items[i] = getString(modes[i].stringResId);
            if (modes[i] == mPrefs.sortMode) {
                selected = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_by)
                .setSingleChoiceItems(items, selected, (dialog, which) -> {
                    Prefs.SortMode chosen = modes[which];
                    mPrefs.updateSortMode(chosen);
                    dialog.dismiss();
                    if (mCurrentFolder != null) {
                        openFolder(mCurrentFolder);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private final Runnable mRefreshLibrary = () -> refreshCurrent(false);

    private void registerMediaStoreObserver() {
        if (mMediaStoreObserver != null) return;
        mMediaStoreObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                mainHandler.removeCallbacks(mRefreshLibrary);
                mainHandler.postDelayed(mRefreshLibrary, 350);
            }
        };
        try {
            getContentResolver().registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    mMediaStoreObserver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unregisterMediaStoreObserver() {
        if (mMediaStoreObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(mMediaStoreObserver);
            } catch (Exception ignored) {}
            mMediaStoreObserver = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterMediaStoreObserver();
        mScanGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        if (mScanTask != null) mScanTask.cancel(true);
        executorService.shutdownNow();
    }
}
