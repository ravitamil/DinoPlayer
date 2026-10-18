package com.brouken.player;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_ADD_FOLDER = 2001;

    private Prefs mPrefs;
    private TextView textScanModeSummary;
    private TextView textIncludedFoldersSummary;
    private TextView textExcludedFoldersSummary;
    private SwitchMaterial switchShowHidden;
    private SwitchMaterial switchAutoPlayNext;
    private SwitchMaterial switchLiveCaptions;

    private View layoutManageStorage;
    private TextView textManageStorageStatus;
    private TextView textManageStorageAction;

    private final List<String> mDiscoveredFolders = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler packageHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Map<String, TextView> packageSummaries = new java.util.HashMap<>();
    private final java.util.Map<String, android.widget.ProgressBar> packageProgress = new java.util.HashMap<>();
    private boolean showingPackages;
    private boolean cacheClearing;
    private final Runnable refreshPackages = new Runnable() {
        @Override public void run() {
            if (!showingPackages) return;
            CaptionPackages manager = CaptionPackages.get(SettingsActivity.this);
            for (CaptionPack pack : CaptionPack.ALL) {
                CaptionPackages.State state = manager.state(pack);
                packageSummaries.get(pack.id).setText(state.text);
                ((View)packageSummaries.get(pack.id).getParent()).setContentDescription(pack.title + ", " + state.text);
                android.widget.ProgressBar progress = packageProgress.get(pack.id);
                progress.setVisibility(state.downloading ? View.VISIBLE : View.GONE);
                progress.setIndeterminate(state.verifying);
                progress.setProgress(state.percent);
            }
            packageHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mPrefs = new Prefs(this);

        initViews();
        initAutomaticCaptions();
        findViewById(R.id.row_subtitle_appearance).setOnClickListener(v->SubtitleAppearanceSettings.show(this,this::updateSubtitleAppearanceSummary));
        updateSubtitleAppearanceSummary();
        if(savedInstanceState==null && getIntent().getBooleanExtra("subtitle_appearance",false))
            SubtitleAppearanceSettings.show(this,this::updateSubtitleAppearanceSummary);
        loadDiscoveredFolders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSubtitleAppearanceSummary();
        updateStoragePermissionStatus();
        updateSummaries();
        showingPackages = true;
        packageHandler.removeCallbacks(refreshPackages);
        packageHandler.post(refreshPackages);
    }

    @Override protected void onPause() {
        showingPackages = false;
        packageHandler.removeCallbacks(refreshPackages);
        super.onPause();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        textScanModeSummary = findViewById(R.id.text_scan_mode_summary);
        textIncludedFoldersSummary = findViewById(R.id.text_included_folders_summary);
        textExcludedFoldersSummary = findViewById(R.id.text_excluded_folders_summary);
        switchShowHidden = findViewById(R.id.switch_show_hidden);
        switchAutoPlayNext = findViewById(R.id.switch_autoplay_next);
        switchLiveCaptions = findViewById(R.id.switch_live_captions);
        initCaptionPackages();

        layoutManageStorage = findViewById(R.id.layout_manage_storage_permission);
        textManageStorageStatus = findViewById(R.id.text_manage_storage_status);
        textManageStorageAction = findViewById(R.id.text_manage_storage_action);

        // Initial switch states
        switchShowHidden.setChecked(mPrefs.showHidden);
        switchAutoPlayNext.setChecked(mPrefs.autoPlayNext);
        switchLiveCaptions.setChecked(mPrefs.liveCaptions);

        updateSummaries();

        // Row click listeners
        findViewById(R.id.row_scan_mode).setOnClickListener(v -> showScanModeDialog());
        findViewById(R.id.row_included_folders).setOnClickListener(v -> showIncludedFoldersDialog());
        findViewById(R.id.row_excluded_folders).setOnClickListener(v -> showExcludedFoldersDialog());

        findViewById(R.id.row_show_hidden).setOnClickListener(v -> switchShowHidden.toggle());
        switchShowHidden.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.updateShowHidden(isChecked);
            setResult(RESULT_OK);
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    showAllFilesAccessRationale();
                }
            }
        });

        layoutManageStorage.setOnClickListener(v -> openAllFilesAccessSettings());

        findViewById(R.id.row_autoplay_next).setOnClickListener(v -> switchAutoPlayNext.toggle());
        switchAutoPlayNext.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.updateAutoPlayNext(isChecked);
        });

        findViewById(R.id.row_live_captions).setOnClickListener(v -> switchLiveCaptions.toggle());
        switchLiveCaptions.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.updateLiveCaptions(isChecked);
            Toast.makeText(this, isChecked ? "Live speech subtitles enabled" : "Live speech subtitles disabled", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.button_rescan_library).setOnClickListener(v -> {
            setResult(RESULT_OK);
            Toast.makeText(this, "Rescanning video library...", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void updateSubtitleAppearanceSummary() {
        SubtitleStyleOptions options=new SubtitleStylePreferences(this).read();
        TextView summary=findViewById(R.id.text_subtitle_appearance);
        if(options.useAndroid)summary.setText(R.string.subtitle_android_summary);
        else summary.setText(getString(R.string.subtitle_appearance_summary,
                getResources().getStringArray(R.array.subtitle_sizes)[options.size],
                getResources().getStringArray(R.array.subtitle_colors)[options.color]));
    }

    private void initAutomaticCaptions() {
        android.widget.LinearLayout rows=findViewById(R.id.caption_pack_rows);
        SwitchMaterial toggle=new SwitchMaterial(this);
        toggle.setText("Generate subtitles for new videos");toggle.setTextSize(16);
        toggle.setMinHeight(Utils.dpToPx(56));toggle.setChecked(CaptionAutoJobs.enabled(this));
        rows.addView(toggle,0);
        TextView hint=new TextView(this);hint.setTextSize(14);
        hint.setText("Off by default. Uses the selected offline pack. Existing videos are skipped. New videos queue in the background; playback takes priority. Android may defer background work.");
        rows.addView(hint,1);
        toggle.setOnCheckedChangeListener((button,on) -> {
            CaptionAutoJobs.setEnabled(this,on);
            if(on && CaptionAutoJobs.canDiscover(this))executor.execute(() -> {
                try { CaptionAutoJobs.observe(getApplicationContext(),MediaScanner.scan(getApplicationContext(),new Prefs(this)).displayedFolders); }
                catch(RuntimeException ignored) { }
            });
        });
    }

    private void updateSummaries() {
        CaptionPack pack = CaptionPackages.get(this).selected();
        ((TextView)findViewById(R.id.text_caption_language)).setText(pack == CaptionPack.HINDI
                ? "Hindi audio → English · Experimental" : "English audio → English");
        if (mPrefs.scanOnlyIncluded) {
            textScanModeSummary.setText("Scan only included folders");
        } else {
            textScanModeSummary.setText("Scan all device folders (except excluded)");
        }

        int incCount = mPrefs.includedFolders.size();
        if (incCount == 0) {
            textIncludedFoldersSummary.setText("None configured • Tap to add folders");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(incCount).append(incCount == 1 ? " folder included: " : " folders included: ");
            int i = 0;
            for (String f : mPrefs.includedFolders) {
                if (i > 0) sb.append(", ");
                sb.append(extractShortFolderName(f));
                i++;
                if (i >= 2 && incCount > 2) {
                    sb.append(" (+").append(incCount - 2).append(" more)");
                    break;
                }
            }
            textIncludedFoldersSummary.setText(sb.toString());
        }

        int excCount = mPrefs.excludedFolders.size();
        if (excCount == 0) {
            textExcludedFoldersSummary.setText("None • All device folders are scanned");
        } else {
            textExcludedFoldersSummary.setText(excCount + (excCount == 1 ? " folder excluded" : " folders excluded"));
        }
    }

    private void initCaptionPackages() {
        findViewById(R.id.row_caption_language).setOnClickListener(view -> {
            CaptionPackages manager = CaptionPackages.get(this);
            new AlertDialog.Builder(this).setTitle("Audio & captions")
                    .setSingleChoiceItems(new String[]{"English audio → English", "Hindi audio → English (experimental)"},
                            manager.selected() == CaptionPack.HINDI ? 1 : 0, (dialog, which) -> {
                                manager.select(which == 1 ? CaptionPack.HINDI : CaptionPack.ENGLISH);
                                updateSummaries();
                                setResult(RESULT_OK);
                                dialog.dismiss();
                            }).setNegativeButton(android.R.string.cancel, null).show();
        });
        android.widget.LinearLayout rows = findViewById(R.id.caption_pack_rows);
        int padding = Utils.dpToPx(16);
        for (CaptionPack pack : CaptionPack.ALL) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.VERTICAL);
            row.setPadding(padding, padding, padding, padding);
            row.setBackgroundResource(R.drawable.library_row_background);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = Utils.dpToPx(8);
            row.setLayoutParams(params);
            TextView title = new TextView(this);
            title.setText(pack.title + " · " + (pack.downloadBytes()/1000000) + " MB");
            title.setTextSize(16);
            title.setTextColor(getResources().getColor(R.color.app_text));
            row.addView(title);
            TextView summary = new TextView(this);
            summary.setTextColor(getResources().getColor(R.color.app_text_secondary));
            summary.setTextSize(13);
            summary.setPadding(0, Utils.dpToPx(6), 0, Utils.dpToPx(8));
            row.addView(summary);
            packageSummaries.put(pack.id, summary);
            android.widget.ProgressBar progress = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.player_accent)));
            row.addView(progress, new android.widget.LinearLayout.LayoutParams(-1, Utils.dpToPx(4)));
            packageProgress.put(pack.id, progress);
            row.setContentDescription(pack.title + ", manage offline package");
            row.setFocusable(true);
            row.setOnClickListener(view -> managePackage(pack));
            rows.addView(row);
        }
        findViewById(R.id.button_clear_cache).setOnClickListener(view -> {
            if (cacheClearing) return;
            new AlertDialog.Builder(this).setTitle("Clear preview and caption cache?")
                    .setMessage("Your videos, watch history and installed subtitle packs will stay. Captions will be generated again when needed.")
                    .setPositiveButton("Clear cache", (dialog, which) -> {
                        cacheClearing = true;
                        view.setEnabled(false);
                        ThumbnailLoader.getInstance().clearCache(this, () -> SubtitleGenerator.clearCache(this, () -> {
                            cacheClearing = false;
                            if (!isDestroyed()) {
                                view.setEnabled(true);
                                Toast.makeText(this, "Preview and caption cache cleared", Toast.LENGTH_SHORT).show();
                            }
                        }));
                    }).setNegativeButton(android.R.string.cancel, null).show();
        });
    }

    private void managePackage(CaptionPack pack) {
        CaptionPackages manager = CaptionPackages.get(this);
        CaptionPackages.State state = manager.state(pack);
        if (state.verifying) {
            Toast.makeText(this, "Please wait for verification to finish", Toast.LENGTH_SHORT).show();
        } else if (state.downloading || state.installed) {
            String action = state.installed ? "Remove package" : "Cancel download";
            new AlertDialog.Builder(this).setTitle(pack.title).setMessage(state.installed
                    ? "Remove this offline pack? Your videos and watch history will stay." : "Cancel this download? You can download it again later.")
                    .setPositiveButton(action, (dialog, which) -> {
                        try { if (state.installed) manager.remove(pack); else manager.cancel(pack); }
                        catch (java.io.IOException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
                    }).setNegativeButton(android.R.string.cancel,null).show();
        } else {
            final boolean[] mobileData = {false};
            new AlertDialog.Builder(this).setTitle("Download " + pack.title + "?")
                    .setMultiChoiceItems(new String[]{"Allow mobile data (" + pack.downloadBytes()/1000000 + " MB)"},
                            new boolean[]{false}, (dialog, which, checked) -> mobileData[0] = checked)
                    .setPositiveButton("Download", (dialog, which) -> {
                        try { manager.start(pack, mobileData[0]); }
                        catch (java.io.IOException e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
                    }).setNegativeButton(android.R.string.cancel,null).show();
        }
    }

    private String extractShortFolderName(String path) {
        if (path == null || path.isEmpty()) return "Folder";
        String clean = path;
        while (clean.endsWith("/") && clean.length() > 1) {
            clean = clean.substring(0, clean.length() - 1);
        }
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 1 < clean.length()) {
            return clean.substring(lastSlash + 1);
        }
        return clean;
    }

    private void showScanModeDialog() {
        String[] options = new String[]{
                "Scan all device folders (except excluded)",
                "Scan only included folders"
        };
        int selectedIndex = mPrefs.scanOnlyIncluded ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle("Scanning Mode")
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    boolean isIncluded = (which == 1);
                    mPrefs.updateScanOnlyIncluded(isIncluded);
                    updateSummaries();
                    setResult(RESULT_OK);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showIncludedFoldersDialog() {
        List<String> items = new ArrayList<>(mPrefs.includedFolders);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Included Folders");

        if (items.isEmpty()) {
            builder.setMessage("No folders in the inclusion list.\n\nTap 'Add Folder' to select a folder from storage.");
        } else {
            CharSequence[] displayNames = new CharSequence[items.size()];
            for (int i = 0; i < items.size(); i++) {
                displayNames[i] = items.get(i);
            }
            builder.setItems(displayNames, (dialog, which) -> {
                String folderToRemove = items.get(which);
                new AlertDialog.Builder(this)
                        .setTitle("Remove Folder")
                        .setMessage("Remove \"" + folderToRemove + "\" from included scan list?")
                        .setPositiveButton("Remove", (d, w) -> {
                            Set<String> updated = new HashSet<>(mPrefs.includedFolders);
                            updated.remove(folderToRemove);
                            mPrefs.updateIncludedFolders(updated);
                            updateSummaries();
                            setResult(RESULT_OK);
                            Toast.makeText(this, "Folder removed", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }

        builder.setPositiveButton("Add Folder", (dialog, which) -> launchAddFolderPicker());
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void showExcludedFoldersDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_folder_filters, null);
        dialogView.findViewById(R.id.radio_group_filter_mode).setVisibility(View.GONE);
        dialogView.findViewById(R.id.check_filter_show_hidden).setVisibility(View.GONE);

        TextView textSectionTitle = dialogView.findViewById(R.id.text_filter_section_title);
        textSectionTitle.setText("Check folders you want to exclude from scanning:");

        RecyclerView recyclerFolders = dialogView.findViewById(R.id.recycler_filter_folders);
        recyclerFolders.setLayoutManager(new LinearLayoutManager(this));

        FolderFilterAdapter filterAdapter = new FolderFilterAdapter(mDiscoveredFolders, mPrefs.excludedFolders);
        recyclerFolders.setAdapter(filterAdapter);

        new AlertDialog.Builder(this)
                .setTitle("Excluded Folders")
                .setView(dialogView)
                .setPositiveButton("Apply", (dialog, which) -> {
                    mPrefs.updateExcludedFolders(filterAdapter.getSelectedPaths());
                    updateSummaries();
                    setResult(RESULT_OK);
                    Toast.makeText(this, "Excluded folders updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateStoragePermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasAllFiles = Environment.isExternalStorageManager();
            if (hasAllFiles) {
                textManageStorageStatus.setText("Full access granted • Can scan .hidden and dot folders");
                textManageStorageStatus.setTextColor(0xFF81C784);
                textManageStorageAction.setText("ALLOWED");
                textManageStorageAction.setTextColor(0xFF81C784);
            } else {
                textManageStorageStatus.setText("Required on Android 11+ to scan .hidden folders");
                textManageStorageStatus.setTextColor(0xFFFFA726);
                textManageStorageAction.setText("GRANT");
                textManageStorageAction.setTextColor(0xFF3DDC84);
            }
        } else {
            textManageStorageStatus.setText("Granted via runtime storage permission");
            textManageStorageStatus.setTextColor(0xFF81C784);
            textManageStorageAction.setText("OK");
            textManageStorageAction.setTextColor(0xFF81C784);
        }
    }

    private void showAllFilesAccessRationale() {
        new AlertDialog.Builder(this)
                .setTitle("All Files Access Required")
                .setMessage("Android 11+ restricts access to .hidden directories and non-media folders. To index videos in .hidden folders, please enable 'All files access' for Just Player.")
                .setPositiveButton("Open Settings", (dialog, which) -> openAllFilesAccessSettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                } catch (Exception ex) {
                    Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "All storage access is already granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadDiscoveredFolders() {
        executor.execute(() -> {
            MediaScanner.ScanResult result = MediaScanner.scan(this, mPrefs);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                mDiscoveredFolders.clear();
                mDiscoveredFolders.addAll(result.allDiscoveredFolderPaths);
                for (String inc : mPrefs.includedFolders) {
                    if (!mDiscoveredFolders.contains(inc)) {
                        mDiscoveredFolders.add(inc);
                    }
                }
                for (String exc : mPrefs.excludedFolders) {
                    if (!mDiscoveredFolders.contains(exc)) {
                        mDiscoveredFolders.add(exc);
                    }
                }
            });
        });
    }

    private void launchAddFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_CODE_ADD_FOLDER);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_files_missing, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Add to scopeUri and to includedFolders
                mPrefs.updateScope(treeUri);

                DocumentFile doc = DocumentFile.fromTreeUri(this, treeUri);
                String folderName = doc != null ? doc.getName() : null;
                String normalized = Prefs.normalizeFolderPath(treeUri.toString());
                if (normalized.isEmpty() && folderName != null) {
                    normalized = folderName;
                }

                Set<String> newIncluded = new HashSet<>(mPrefs.includedFolders);
                if (!normalized.isEmpty()) {
                    newIncluded.add(normalized);
                } else if (folderName != null) {
                    newIncluded.add(folderName);
                }
                mPrefs.updateIncludedFolders(newIncluded);
                mPrefs.updateScanOnlyIncluded(true);

                boolean isSelectedHidden = (folderName != null && folderName.startsWith(".")) || normalized.contains("/.");
                if (isSelectedHidden && !mPrefs.showHidden) {
                    mPrefs.updateShowHidden(true);
                    switchShowHidden.setChecked(true);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        showAllFilesAccessRationale();
                    }
                }

                updateSummaries();
                setResult(RESULT_OK);
                Toast.makeText(this, "Added to included scan list: " + (folderName != null ? folderName : normalized), Toast.LENGTH_LONG).show();
                loadDiscoveredFolders();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
