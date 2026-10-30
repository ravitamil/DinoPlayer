package com.brouken.player;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.checkbox.MaterialCheckBox;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.ImageButton;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FolderBrowserActivity extends AppCompatActivity implements FolderBrowserAdapter.OnItemClickListener {

    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final int REQUEST_CODE_SETTINGS = 1002;
    private static final String PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO";

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener captionListener = (p, k) -> {
        this.mainHandler.removeCallbacks(this.refreshCaptionStates);
        this.mainHandler.postDelayed(this.refreshCaptionStates, 200);
    };
    private final Runnable refreshCaptionStates = () -> {
        if (this.adapter != null) this.adapter.refreshCaptionStates();
    };

    private Prefs mPrefs;
    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private View progressBar;
    private View layoutEmpty;
    private View layoutOnboarding;

    private FolderBrowserAdapter adapter;
    private FolderBrowserViewModel mViewModel;

    private MediaScanner.VideoFolder mCurrentFolder = null;
    private final List<MediaScanner.VideoFolder> mLoadedFolders = new ArrayList<>();

    private androidx.appcompat.view.ActionMode mActionMode;

    private final androidx.appcompat.view.ActionMode.Callback mActionModeCallback = new androidx.appcompat.view.ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
            menu.add(Menu.NONE, 1000, Menu.NONE, R.string.action_play_selected)
                    .setIcon(R.drawable.ic_play_arrow_24dp)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(Menu.NONE, 1001, Menu.NONE, R.string.action_select_all)
                    .setIcon(R.drawable.ic_select_all_24dp)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(Menu.NONE, 1002, Menu.NONE, R.string.action_delete_selected)
                    .setIcon(R.drawable.ic_delete_24dp_)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
            MenuItem selectAll = menu.findItem(1001);
            if (selectAll != null) {
                boolean allSelected = adapter.areAllVideosSelected();
                selectAll.setTitle(allSelected ? R.string.action_deselect_all : R.string.action_select_all);
                selectAll.setIcon(allSelected ? R.drawable.ic_close_24dp : R.drawable.ic_select_all_24dp);
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(androidx.appcompat.view.ActionMode mode, MenuItem item) {
            if (item.getItemId() == 1000) {
                playSelectedVideos();
                return true;
            } else if (item.getItemId() == 1001) {
                adapter.toggleSelectAllVideos();
                mode.invalidate();
                return true;
            } else if (item.getItemId() == 1002) {
                confirmDeleteSelectedVideos();
                return true;
            }
            return false;
        }


        @Override
        public void onDestroyActionMode(androidx.appcompat.view.ActionMode mode) {
            mActionMode = null;
            adapter.setSelectionMode(false);
        }
    };

    private MenuItem mSearchMenuItem;
    private SearchView mSearchView;
    private String mCurrentSearchQuery = "";
    private boolean mIsSearching = false;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ContentObserver mMediaStoreObserver;
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
        getSharedPreferences("caption_work", 0).registerOnSharedPreferenceChangeListener(captionListener);
        registerMediaStoreObserver();
        mPrefs = new Prefs(this);
        if (hasStoragePermission()) {
            adapter.refreshHistory();
            mViewModel.refreshWatchHistory();
            if (mLoadedFolders.isEmpty()) {
                refreshCurrent(false);
            }
        }
    }

    @Override
    protected void onPause() {
        unregisterMediaStoreObserver();
        getSharedPreferences("caption_work", 0).unregisterOnSharedPreferenceChangeListener(captionListener);
        mainHandler.removeCallbacks(refreshCaptionStates);
        super.onPause();
    }

    @Override
    public void onVideoActions(FolderBrowserAdapter.BrowserItem item) {
        if (item == null) return;
        BottomSheetDialog sheetDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_App_BottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_video_actions, null);
        sheetDialog.setContentView(sheet);

        TextView textTitle = sheet.findViewById(R.id.text_sheet_title);
        TextView textSubtitle = sheet.findViewById(R.id.text_sheet_subtitle);
        ImageView imageThumb = sheet.findViewById(R.id.image_sheet_thumbnail);

        textTitle.setText(item.name);

        StringBuilder meta = new StringBuilder();
        if (item.duration > 0) {
            meta.append(Utils.formatMilis(item.duration));
        }
        if (item.size > 0) {
            if (meta.length() > 0) meta.append(" • ");
            meta.append(Formatter.formatFileSize(this, item.size));
        }
        textSubtitle.setText(meta.toString());

        if (item.uri != null) {
            ThumbnailLoader.getInstance().load(this, item.uri, imageThumb, null, null, null, FolderBrowserAdapter.thumbnailVersion(item));
        }

        // Action: Play / Resume
        View actionPlay = sheet.findViewById(R.id.action_play);
        TextView textPlayTitle = sheet.findViewById(R.id.text_action_play_title);
        TextView textPlaySubtitle = sheet.findViewById(R.id.text_action_play_subtitle);

        WatchHistory watchHistory = new WatchHistory(this);
        Map<String, WatchHistory.Entry> histMap = watchHistory.snapshot();
        WatchHistory.Entry historyEntry = item.uri != null ? histMap.get(item.uri.toString()) : null;
        if (historyEntry != null && historyEntry.position > 1000) {
            textPlayTitle.setText("Resume playback");
            textPlaySubtitle.setText("Continue from " + Utils.formatMilis(historyEntry.position));
        } else {
            textPlayTitle.setText("Play video");
            textPlaySubtitle.setText("Start playback in player");
        }
        actionPlay.setOnClickListener(v -> {
            sheetDialog.dismiss();
            onVideoClick(item);
        });

        // Action: Share
        View actionShare = sheet.findViewById(R.id.action_share);
        actionShare.setOnClickListener(v -> {
            sheetDialog.dismiss();
            shareVideo(item);
        });

        // Action: Details
        View actionDetails = sheet.findViewById(R.id.action_details);
        actionDetails.setOnClickListener(v -> {
            sheetDialog.dismiss();
            showVideoDetailsSheet(item);
        });

        // Action: Subtitles & Captions
        View actionSubtitles = sheet.findViewById(R.id.action_subtitles);
        TextView textSubTitle = sheet.findViewById(R.id.text_action_subtitle_title);
        TextView textSubSubtitle = sheet.findViewById(R.id.text_action_subtitle_subtitle);

        CaptionWorkStore.Entry state = new CaptionWorkStore(this).read(item.uri, CaptionPackages.get(this).selected());
        boolean busy = state != null && (state.status == CaptionWorkStore.Status.GENERATING || state.status == CaptionWorkStore.Status.QUEUED);
        if (busy) {
            textSubTitle.setText("Cancel subtitle generation");
            textSubSubtitle.setText("Generation is currently in progress");
            actionSubtitles.setOnClickListener(v -> {
                sheetDialog.dismiss();
                cancelSubtitleGeneration(item);
            });
        } else {
            actionSubtitles.setOnClickListener(v -> {
                sheetDialog.dismiss();
                showSubtitleOptions(item);
            });
        }

        // Action: Reset watch progress
        View actionResetProgress = sheet.findViewById(R.id.action_reset_progress);
        if (historyEntry != null && historyEntry.position > 0) {
            actionResetProgress.setVisibility(View.VISIBLE);
            actionResetProgress.setOnClickListener(v -> {
                sheetDialog.dismiss();
                watchHistory.forget(item.uri);
                adapter.refreshHistory();
                Toast.makeText(this, "Watch progress cleared", Toast.LENGTH_SHORT).show();
            });
        } else {
            actionResetProgress.setVisibility(View.GONE);
        }

        // Action: Delete
        View actionDelete = sheet.findViewById(R.id.action_delete);
        actionDelete.setOnClickListener(v -> {
            sheetDialog.dismiss();
            confirmDelete(item);
        });

        sheetDialog.show();
    }

    private void shareVideo(FolderBrowserAdapter.BrowserItem item) {
        if (item == null || item.uri == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_STREAM, item.uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share " + item.name));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to share video", Toast.LENGTH_SHORT).show();
        }
    }

    private void showVideoDetailsSheet(FolderBrowserAdapter.BrowserItem item) {
        BottomSheetDialog detailsDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_App_BottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_video_details, null);
        detailsDialog.setContentView(sheet);

        TextView textName = sheet.findViewById(R.id.text_detail_name);
        TextView textPath = sheet.findViewById(R.id.text_detail_path);
        TextView textSize = sheet.findViewById(R.id.text_detail_size);
        TextView textDuration = sheet.findViewById(R.id.text_detail_duration);
        TextView textResolution = sheet.findViewById(R.id.text_detail_resolution);
        TextView textDate = sheet.findViewById(R.id.text_detail_date);
        ImageButton buttonClose = sheet.findViewById(R.id.button_details_close);

        textName.setText(item.name);
        textPath.setText(item.path != null ? item.path : (item.uri != null ? item.uri.toString() : "Unknown"));
        textSize.setText(Formatter.formatFileSize(this, item.size));
        textDuration.setText(item.duration > 0 ? Utils.formatMilis(item.duration) : "--:--");
        textResolution.setText("Video");
        if (item.lastModified > 0) {
            textDate.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.lastModified)));
        } else {
            textDate.setText("--");
        }

        buttonClose.setOnClickListener(v -> detailsDialog.dismiss());
        detailsDialog.show();
    }

    private void showSubtitleOptions(FolderBrowserAdapter.BrowserItem item) {
        String[] options = new String[]{
                "Generate on-device subtitles",
                "Regenerate subtitles (overwrite)",
                "Edit / export subtitles"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("Subtitles & Captions")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        generateFromList(item, false);
                    } else if (which == 1) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Regenerate Subtitles")
                                .setMessage("Replace this video’s generated subtitles and saved edits?")
                                .setPositiveButton("Regenerate", (d, w) -> generateFromList(item, true))
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    } else if (which == 2) {
                        startActivity(new Intent(this, SubtitleEditorActivity.class)
                                .setData(item.uri)
                                .putExtra("path", item.path));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void cancelSubtitleGeneration(FolderBrowserAdapter.BrowserItem item) {
        executorService.execute(() -> {
            CaptionAutoJobs.removeQueued(this, item.uri, null);
            try {
                SubtitleGenerator.cancelAndAwait(item.uri);
                CaptionWorkStore store = new CaptionWorkStore(this);
                for (CaptionPack pack : CaptionPack.ALL) {
                    CaptionWorkStore.Entry current = store.read(item.uri, pack);
                    if (current != null && current.status == CaptionWorkStore.Status.QUEUED)
                        store.update(item.uri, pack, current.token, CaptionWorkStore.Status.CANCELLED, 0, "");
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void generateFromList(FolderBrowserAdapter.BrowserItem item, boolean force) {
        if (!CaptionPackages.get(this).isInstalled(CaptionPackages.get(this).selected())) {
            new MaterialAlertDialogBuilder(this).setMessage("Install the selected offline subtitle pack in Settings first.")
                    .setPositiveButton("Settings", (d, w) -> openSettings()).setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        Intent intent = new Intent(this, PlayerActivity.class).setDataAndType(item.uri, "video/*").putExtra("caption_request", force ? "regenerate" : "generate");
        startActivity(intent);
    }

    private void confirmDelete(FolderBrowserAdapter.BrowserItem item) {
        MaterialCheckBox include = new MaterialCheckBox(this);
        include.setTextSize(14);
        include.setText("Also delete DinoPlayer’s generated subtitles and saved edits");
        include.setChecked(true);
        int pad = Utils.dpToPx(20);
        include.setPadding(pad, pad / 2, pad, pad / 2);

        new MaterialAlertDialogBuilder(this).setTitle("Delete video permanently?")
                .setMessage(item.name + "\n\nSubtitle generation will stop before deletion. Separate SRT/VTT files next to the video and exported copies are kept.")
                .setView(include).setPositiveButton("Delete", (d, w) -> {
                    mViewModel.cancelScan();
                    startActivityForResult(new Intent(this, VideoDeletionActivity.class).setData(item.uri).putExtra("path", item.path).putExtra("captions", include.isChecked()), 4100);
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        swipeRefresh = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        layoutEmpty = findViewById(R.id.layout_empty);
        layoutOnboarding = findViewById(R.id.layout_onboarding);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FolderBrowserAdapter(this, this);
        recyclerView.setAdapter(adapter);
        adapter.setSelectionListener(count -> {
            if (mActionMode != null) {
                if (count == 0) {
                    mActionMode.finish();
                } else {
                    mActionMode.setTitle(getString(R.string.selected_count, count));
                    mActionMode.invalidate();
                }
            }
        });

        mViewModel = new ViewModelProvider(this).get(FolderBrowserViewModel.class);

        mViewModel.getDisplayedItems().observe(this, items -> {
            adapter.setHistoryScope(items);
            adapter.setItems(items);
            if (mRestoreScroll != null && recyclerView.getLayoutManager() != null) {
                recyclerView.getLayoutManager().onRestoreInstanceState(mRestoreScroll);
                mRestoreScroll = null;
            }
        });

        mViewModel.getIsLoading().observe(this, loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        });

        mViewModel.getIsSwipeRefreshing().observe(this, refreshing -> {
            swipeRefresh.setRefreshing(refreshing);
        });

        mViewModel.getIsEmpty().observe(this, empty -> {
            layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        mViewModel.getEmptyTitle().observe(this, title -> {
            if (title != null) {
                TextView t = findViewById(R.id.text_empty_title);
                if (t != null) t.setText(title);
            }
        });

        mViewModel.getEmptyHint().observe(this, hint -> {
            if (hint != null) {
                TextView d = findViewById(R.id.text_empty_desc);
                if (d != null) d.setText(hint);
            }
        });

        mViewModel.getCurrentFolder().observe(this, folder -> {
            mCurrentFolder = folder;
            if (folder != null) {
                updateToolbarFolder(folder);
            } else {
                List<MediaScanner.VideoFolder> list = mViewModel.getFolders().getValue();
                updateToolbarRoot(list != null ? list.size() : 0, countTotalVideos(list));
            }
        });

        mViewModel.getFolders().observe(this, folders -> {
            mLoadedFolders.clear();
            if (folders != null) mLoadedFolders.addAll(folders);
            if (mViewModel.getCurrentFolder().getValue() == null) {
                updateToolbarRoot(mLoadedFolders.size(), countTotalVideos(mLoadedFolders));
            }
        });

        mViewModel.getWatchHistory().observe(this, history -> {
            adapter.refreshHistory();
        });

        swipeRefresh.setColorSchemeResources(R.color.green);
        swipeRefresh.setOnRefreshListener(() -> refreshCurrent(true));

        findViewById(R.id.button_select_folder).setOnClickListener(v -> requestStoragePermissions());
        findViewById(R.id.button_empty_change_folder).setOnClickListener(v -> openSettings());
    }

    private int countTotalVideos(List<MediaScanner.VideoFolder> folders) {
        if (folders == null) return 0;
        int total = 0;
        for (MediaScanner.VideoFolder f : folders) {
            total += f.getVideoCount();
        }
        return total;
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
        if (requestCode == 4100 && resultCode == RESULT_OK) {
            mViewModel.cancelScan();
            refreshCurrent(false);
            return;
        }
        if (requestCode == REQUEST_CODE_SETTINGS) {
            mPrefs = new Prefs(this);
            refreshCurrent(false);
        }
    }

    private void loadRootFolders(boolean isSwipeRefresh) {
        mViewModel.scanLibrary(mPrefs, isSwipeRefresh, mRestoreFolderId);
        mRestoreFolderId = null;
    }

    private void refreshCurrent(boolean isSwipeRefresh) {
        mViewModel.scanLibrary(mPrefs, isSwipeRefresh, null);
    }

    private void openFolder(MediaScanner.VideoFolder folder) {
        if (mActionMode != null) {
            mActionMode.finish();
        }
        RecyclerView.LayoutManager layout = recyclerView.getLayoutManager();
        if (mCurrentFolder == null && layout != null) {
            mLibraryScroll = layout.onSaveInstanceState();
        }
        recyclerView.stopScroll();
        mViewModel.openFolder(folder, mPrefs.sortMode);
        if (layout instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layout).scrollToPositionWithOffset(0, 0);
        }
    }

    private void filterItems(String query) {
        if (mActionMode != null) {
            mActionMode.finish();
        }
        mCurrentSearchQuery = (query != null ? query.trim() : "");
        mViewModel.setSearchQuery(mCurrentSearchQuery);
    }

    private void updateToolbarRoot(int folderCount, int videoCount) {
        toolbar.setTitle(R.string.your_videos);
        toolbar.setNavigationIcon(null);
        if (mSearchView != null) {
            mSearchView.setQueryHint(getString(R.string.search_hint));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(folderCount).append(folderCount == 1 ? " folder" : " folders");
        sb.append(" • ");
        sb.append(videoCount).append(videoCount == 1 ? " video" : " videos");
        toolbar.setSubtitle(sb.length() > 0 ? sb.toString() : null);
    }

    private void updateToolbarFolder(MediaScanner.VideoFolder folder) {
        toolbar.setTitle(folder.name);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        if (mSearchView != null) {
            mSearchView.setQueryHint(getString(R.string.search_hint_videos));
        }

        if (folder.videos != null && !folder.videos.isEmpty()) {
            long totalSize = 0;
            for (MediaScanner.VideoItem video : folder.videos) {
                totalSize += video.size;
            }
            String sizeStr = Formatter.formatFileSize(this, totalSize);
            toolbar.setSubtitle(folder.videos.size() + (folder.videos.size() == 1 ? " video" : " videos") + " • " + sizeStr);
        } else {
            toolbar.setSubtitle(null);
        }
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

    private void playSelectedVideos() {
        List<FolderBrowserAdapter.BrowserItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) return;
        List<Uri> playlist = new ArrayList<>();
        for (FolderBrowserAdapter.BrowserItem item : selected) {
            Uri u = item.uri != null ? item.uri : (item.file != null ? item.file.getUri() : null);
            if (u != null) playlist.add(u);
        }
        if (!playlist.isEmpty()) {
            if (mActionMode != null) {
                mActionMode.finish();
            }
            PlaylistManager.setPlaylist(this, playlist, playlist.get(0));
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.setDataAndType(playlist.get(0), "video/*");
            if (selected.get(0).path != null) {
                intent.putExtra("video_path", selected.get(0).path);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        }
    }

    @Override
    public void onFolderClick(FolderBrowserAdapter.BrowserItem folderItem) {
        if (adapter.isSelectionMode()) {
            if (mActionMode != null) {
                mActionMode.finish();
            }
            return;
        }
        if (folderItem != null && folderItem.folder != null) {
            openFolder(folderItem.folder);
        }
    }

    @Override
    public void onFolderLongClick(FolderBrowserAdapter.BrowserItem folderItem) {
        if (folderItem == null || folderItem.folder == null) return;
        MediaScanner.VideoFolder folder = folderItem.folder;

        BottomSheetDialog sheetDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_App_BottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_folder_actions, null);
        sheetDialog.setContentView(sheet);

        TextView textName = sheet.findViewById(R.id.text_folder_sheet_name);
        TextView textInfo = sheet.findViewById(R.id.text_folder_sheet_info);
        textName.setText(folder.name);
        int videoCount = folder.getVideoCount();
        textInfo.setText(videoCount + (videoCount == 1 ? " video" : " videos") + " • " + folder.path);

        // Action: Play all
        View actionPlayAll = sheet.findViewById(R.id.action_folder_play_all);
        TextView textPlayTitle = sheet.findViewById(R.id.text_folder_action_play_title);
        textPlayTitle.setText("Play all (" + videoCount + (videoCount == 1 ? " video" : " videos") + ")");
        actionPlayAll.setOnClickListener(v -> {
            sheetDialog.dismiss();
            List<Uri> uris = new ArrayList<>();
            for (MediaScanner.VideoItem vi : folder.videos) {
                if (vi.uri != null) uris.add(vi.uri);
            }
            if (!uris.isEmpty()) {
                PlaylistManager.setPlaylist(this, uris, uris.get(0));
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.setDataAndType(uris.get(0), "video/*");
                if (folder.videos.get(0).path != null) {
                    intent.putExtra("video_path", folder.videos.get(0).path);
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        });

        // Action: Exclude
        View actionExclude = sheet.findViewById(R.id.action_folder_exclude);
        actionExclude.setOnClickListener(v -> {
            sheetDialog.dismiss();
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Exclude Folder?")
                    .setMessage("Hide \"" + folder.name + "\" and its videos from the player library?")
                    .setPositiveButton("Exclude", (dialog, which) -> {
                        java.util.Set<String> updated = new java.util.HashSet<>(mPrefs.excludedFolders);
                        updated.add(folder.path);
                        mPrefs.updateExcludedFolders(updated);
                        Toast.makeText(this, "Folder excluded: " + folder.name, Toast.LENGTH_SHORT).show();
                        refreshCurrent(false);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        // Action: Details
        View actionDetails = sheet.findViewById(R.id.action_folder_details);
        actionDetails.setOnClickListener(v -> {
            sheetDialog.dismiss();
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Folder Details")
                    .setMessage("Name: " + folder.name + "\n\nPath: " + folder.path + "\n\nVideos: " + videoCount)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

        sheetDialog.show();
    }

    @Override
    public void onVideoLongClick(FolderBrowserAdapter.BrowserItem videoItem) {
        if (videoItem == null) return;
        Uri videoUri = videoItem.uri != null ? videoItem.uri : (videoItem.file != null ? videoItem.file.getUri() : null);
        if (videoUri == null) return;

        if (mActionMode == null) {
            adapter.setSelectionMode(true);
            adapter.toggleSelection(videoUri);
            mActionMode = startSupportActionMode(mActionModeCallback);
            if (mActionMode != null) {
                mActionMode.setTitle(getString(R.string.selected_count, adapter.getSelectedCount()));
            }
        } else {
            adapter.toggleSelection(videoUri);
        }
    }

    private void confirmDeleteSelectedVideos() {
        List<FolderBrowserAdapter.BrowserItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) return;

        int count = selected.size();
        MaterialCheckBox include = new MaterialCheckBox(this);
        include.setText("Include associated subtitles");
        include.setChecked(true);
        int pad = Utils.dpToPx(20);
        include.setPadding(pad, pad / 2, pad, pad / 2);

        new MaterialAlertDialogBuilder(this)
                .setTitle(count == 1 ? "Delete video?" : ("Delete " + count + " videos?"))
                .setMessage("Are you sure you want to permanently delete " + (count == 1 ? "this video" : (count + " videos")) + " from your device?")
                .setView(include)
                .setPositiveButton("Delete", (d, w) -> {
                    if (mActionMode != null) {
                        mActionMode.finish();
                    }
                    mViewModel.cancelScan();
                    ArrayList<Uri> uris = new ArrayList<>();
                    ArrayList<String> paths = new ArrayList<>();
                    for (FolderBrowserAdapter.BrowserItem item : selected) {
                        if (item.uri != null) uris.add(item.uri);
                        if (item.path != null) paths.add(item.path);
                    }
                    Intent intent = new Intent(this, VideoDeletionActivity.class)
                            .putParcelableArrayListExtra("uris", uris)
                            .putStringArrayListExtra("paths", paths)
                            .putExtra("captions", include.isChecked());
                    startActivityForResult(intent, 4100);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onVideoClick(FolderBrowserAdapter.BrowserItem videoItem) {
        if (adapter.isSelectionMode()) {
            if (videoItem != null) {
                adapter.toggleSelection(videoItem.uri);
            }
            return;
        }
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
        if (mActionMode != null) {
            mActionMode.finish();
            return;
        }
        if (mSearchMenuItem != null && mSearchMenuItem.isActionViewExpanded()) {
            mSearchMenuItem.collapseActionView();
            return;
        }
        if (mViewModel.navigateUp()) {
            recyclerView.stopScroll();
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
                    mViewModel.setIsSearching(true);
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    mIsSearching = false;
                    mCurrentSearchQuery = "";
                    mViewModel.setIsSearching(false);
                    return true;
                }
            });

            if (!mRestoreQuery.isEmpty()) {
                mSearchMenuItem.expandActionView();
                mSearchView.setQuery(mRestoreQuery, false);
                mRestoreQuery = "";
            }
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

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sort_by)
                .setSingleChoiceItems(items, selected, (dialog, which) -> {
                    Prefs.SortMode chosen = modes[which];
                    mPrefs.updateSortMode(chosen);
                    mViewModel.setSortMode(chosen);
                    dialog.dismiss();
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
        mainHandler.removeCallbacksAndMessages(null);
        if (mViewModel != null) mViewModel.cancelScan();
        executorService.shutdownNow();
    }
}
