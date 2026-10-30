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
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
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

        SwitchMaterial switchCaptionTranslate = findViewById(R.id.switch_caption_translate);
        if (switchCaptionTranslate != null) {
            switchCaptionTranslate.setChecked(mPrefs.captionTranslate);
            switchCaptionTranslate.setOnCheckedChangeListener((btn, isChecked) -> {
                mPrefs.updateCaptionTranslate(isChecked);
                updateSummaries();
                setResult(RESULT_OK);
            });
        }
        View rowCaptionTranslate = findViewById(R.id.row_caption_translate);
        if (rowCaptionTranslate != null && switchCaptionTranslate != null) {
            rowCaptionTranslate.setOnClickListener(v -> switchCaptionTranslate.toggle());
        }

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
            Toast.makeText(this, isChecked ? "Subtitles while watching enabled" : "Subtitles while watching disabled", Toast.LENGTH_SHORT).show();
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
        hint.setText("Automatically generate subtitles for new videos in the background using on-device AI.");
        rows.addView(hint,1);
        toggle.setOnCheckedChangeListener((button,on) -> {
            CaptionAutoJobs.setEnabled(this,on);
            if(on && CaptionAutoJobs.canDiscover(this))executor.execute(() -> {
                try { CaptionAutoJobs.observe(getApplicationContext(),MediaScanner.scan(getApplicationContext(),new Prefs(this)).displayedFolders); }
                catch(RuntimeException ignored) { }
            });
        });
    }

    private static class LanguageOption {
        final String code;
        final String name;
        final String subtitle;
        final String badge;
        final boolean isAuto;

        LanguageOption(String code, String name, String subtitle, String badge, boolean isAuto) {
            this.code = code;
            this.name = name;
            this.subtitle = subtitle;
            this.badge = badge;
            this.isAuto = isAuto;
        }
    }

    private String getLanguageDisplayName(String code) {
        if (code == null || "auto".equalsIgnoreCase(code) || code.isEmpty()) {
            return "Auto-detect (99 languages)";
        }
        switch (code.toLowerCase()) {
            case "en": return "English";
            case "hi": return "Hindi (हिन्दी)";
            case "es": return "Spanish (Español)";
            case "fr": return "French (Français)";
            case "de": return "German (Deutsch)";
            case "ja": return "Japanese (日本語)";
            case "ko": return "Korean (한국어)";
            case "zh": return "Chinese (中文)";
            case "ar": return "Arabic (العربية)";
            case "pt": return "Portuguese (Português)";
            case "ru": return "Russian (Русский)";
            case "ta": return "Tamil (தமிழ்)";
            case "te": return "Telugu (తెలుగు)";
            case "bn": return "Bengali (বাংলা)";
            default: return code.toUpperCase();
        }
    }

    private void updateSummaries() {
        String langDisplay = getLanguageDisplayName(mPrefs.captionLanguage);
        if (mPrefs.captionTranslate) {
            langDisplay = langDisplay + " → English";
        }
        TextView textLang = findViewById(R.id.text_caption_language);
        if (textLang != null) {
            textLang.setText(langDisplay);
        }
        if (mPrefs.scanOnlyIncluded) {
            textScanModeSummary.setText("Selected folders only");
        } else {
            textScanModeSummary.setText("All device folders");
        }

        int incCount = mPrefs.includedFolders.size();
        if (incCount == 0) {
            textIncludedFoldersSummary.setText("No folders selected • Tap to add");
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
            textExcludedFoldersSummary.setText("None • All device folders scanned");
        } else {
            textExcludedFoldersSummary.setText(excCount + (excCount == 1 ? " folder excluded" : " folders excluded"));
        }
    }

    private void initCaptionPackages() {
        findViewById(R.id.row_caption_language).setOnClickListener(view -> {
            BottomSheetDialog sheetDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_App_BottomSheetDialog);
            View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_subtitle_language, null);
            sheetDialog.setContentView(sheet);
            sheetDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);

            SwitchMaterial sheetSwitchTranslate = sheet.findViewById(R.id.switch_sheet_translate);
            SwitchMaterial mainSwitchTranslate = findViewById(R.id.switch_caption_translate);
            if (sheetSwitchTranslate != null) {
                sheetSwitchTranslate.setChecked(mPrefs.captionTranslate);
                sheetSwitchTranslate.setOnCheckedChangeListener((btn, checked) -> {
                    mPrefs.updateCaptionTranslate(checked);
                    if (mainSwitchTranslate != null) mainSwitchTranslate.setChecked(checked);
                    updateSummaries();
                    setResult(RESULT_OK);
                });
                View rowSheetTranslate = sheet.findViewById(R.id.row_sheet_translate);
                if (rowSheetTranslate != null) {
                    rowSheetTranslate.setOnClickListener(v -> sheetSwitchTranslate.toggle());
                }
            }

            RecyclerView recycler = sheet.findViewById(R.id.recycler_sheet_languages);
            recycler.setLayoutManager(new LinearLayoutManager(this));

            List<LanguageOption> options = new ArrayList<>();
            options.add(new LanguageOption("auto", "Auto-detect language (Recommended)", "Automatically detects speech across 99 languages", "", true));
            options.add(new LanguageOption("en", "English", "English speech transcription", "EN", false));
            options.add(new LanguageOption("hi", "Hindi (हिन्दी)", "Hindi speech transcription", "HI", false));
            options.add(new LanguageOption("es", "Spanish (Español)", "Spanish speech transcription", "ES", false));
            options.add(new LanguageOption("fr", "French (Français)", "French speech transcription", "FR", false));
            options.add(new LanguageOption("de", "German (Deutsch)", "German speech transcription", "DE", false));
            options.add(new LanguageOption("ja", "Japanese (日本語)", "Japanese speech transcription", "JA", false));
            options.add(new LanguageOption("ko", "Korean (한국어)", "Korean speech transcription", "KO", false));
            options.add(new LanguageOption("zh", "Chinese (中文)", "Mandarin Chinese speech transcription", "ZH", false));
            options.add(new LanguageOption("ar", "Arabic (العربية)", "Arabic speech transcription", "AR", false));
            options.add(new LanguageOption("pt", "Portuguese (Português)", "Portuguese speech transcription", "PT", false));
            options.add(new LanguageOption("ru", "Russian (Русский)", "Russian speech transcription", "RU", false));
            options.add(new LanguageOption("ta", "Tamil (தமிழ்)", "Tamil speech transcription", "TA", false));
            options.add(new LanguageOption("te", "Telugu (తెలుగు)", "Telugu speech transcription", "TE", false));
            options.add(new LanguageOption("bn", "Bengali (বাংলা)", "Bengali speech transcription", "BN", false));

            recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sheet_language, parent, false);
                    return new RecyclerView.ViewHolder(v) {};
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    LanguageOption opt = options.get(position);
                    TextView textTitle = holder.itemView.findViewById(R.id.text_lang_title);
                    TextView textSub = holder.itemView.findViewById(R.id.text_lang_subtitle);
                    TextView textBadge = holder.itemView.findViewById(R.id.text_lang_badge);
                    ImageView imageAuto = holder.itemView.findViewById(R.id.image_lang_auto_icon);
                    MaterialRadioButton radio = holder.itemView.findViewById(R.id.radio_lang_selected);

                    textTitle.setText(opt.name);
                    textSub.setText(opt.subtitle);

                    if (opt.isAuto) {
                        textBadge.setVisibility(View.GONE);
                        imageAuto.setVisibility(View.VISIBLE);
                    } else {
                        textBadge.setVisibility(View.VISIBLE);
                        imageAuto.setVisibility(View.GONE);
                        textBadge.setText(opt.badge);
                    }

                    boolean isSelected = opt.code.equalsIgnoreCase(mPrefs.captionLanguage);
                    radio.setChecked(isSelected);

                    holder.itemView.setOnClickListener(v -> {
                        mPrefs.updateCaptionLanguage(opt.code);
                        updateSummaries();
                        setResult(RESULT_OK);
                        notifyDataSetChanged();
                        sheet.postDelayed(sheetDialog::dismiss, 120);
                    });
                }

                @Override
                public int getItemCount() {
                    return options.size();
                }
            });

            sheetDialog.show();
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
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Clear cache?")
                    .setMessage("Your videos, watch history, and installed subtitle packs will not be affected. Cached thumbnails and subtitles will be cleared.")
                    .setPositiveButton("Clear cache", (dialog, which) -> {
                        cacheClearing = true;
                        view.setEnabled(false);
                        ThumbnailLoader.getInstance().clearCache(this, () -> SubtitleGenerator.clearCache(this, () -> {
                            cacheClearing = false;
                            if (!isDestroyed()) {
                                view.setEnabled(true);
                                Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
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
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(pack.title).setMessage(state.installed
                    ? "Remove this offline pack? Your videos and watch history will stay." : "Cancel this download? You can download it again later.")
                    .setPositiveButton(action, (dialog, which) -> {
                        try { if (state.installed) manager.remove(pack); else manager.cancel(pack); }
                        catch (java.io.IOException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
                    }).setNegativeButton(android.R.string.cancel,null).show();
        } else {
            final boolean[] mobileData = {false};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Download " + pack.title + "?")
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
        BottomSheetDialog sheetDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_App_BottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_scan_mode, null);
        sheetDialog.setContentView(sheet);
        sheetDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);

        View optionAll = sheet.findViewById(R.id.option_scan_all);
        View optionIncluded = sheet.findViewById(R.id.option_scan_included);
        MaterialRadioButton radioAll = sheet.findViewById(R.id.radio_scan_all);
        MaterialRadioButton radioIncluded = sheet.findViewById(R.id.radio_scan_included);

        boolean isOnlyIncluded = mPrefs.scanOnlyIncluded;
        radioAll.setChecked(!isOnlyIncluded);
        radioIncluded.setChecked(isOnlyIncluded);

        optionAll.setOnClickListener(v -> {
            radioAll.setChecked(true);
            radioIncluded.setChecked(false);
            mPrefs.updateScanOnlyIncluded(false);
            updateSummaries();
            setResult(RESULT_OK);
            sheet.postDelayed(sheetDialog::dismiss, 150);
        });

        optionIncluded.setOnClickListener(v -> {
            radioAll.setChecked(false);
            radioIncluded.setChecked(true);
            mPrefs.updateScanOnlyIncluded(true);
            updateSummaries();
            setResult(RESULT_OK);
            sheet.postDelayed(sheetDialog::dismiss, 150);
        });

        sheetDialog.show();
    }

    private void showIncludedFoldersDialog() {
        List<String> allFolders = new ArrayList<>();
        for (String inc : mPrefs.includedFolders) {
            if (!allFolders.contains(inc)) {
                allFolders.add(inc);
            }
        }
        for (String disc : mDiscoveredFolders) {
            if (!allFolders.contains(disc)) {
                allFolders.add(disc);
            }
        }

        BottomSheetDialog sheetDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_App_BottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_folder_manager, null);
        sheetDialog.setContentView(sheet);
        sheetDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        sheetDialog.getBehavior().setSkipCollapsed(true);

        TextView textTitle = sheet.findViewById(R.id.text_sheet_folder_title);
        TextView textSubtitle = sheet.findViewById(R.id.text_sheet_folder_subtitle);
        View emptyState = sheet.findViewById(R.id.container_empty_state);
        RecyclerView recycler = sheet.findViewById(R.id.recycler_sheet_folders);
        MaterialButton buttonAdd = sheet.findViewById(R.id.button_add_folder);
        MaterialButton buttonApply = sheet.findViewById(R.id.button_apply_folders);

        textTitle.setText("Included Folders");
        textSubtitle.setText("Check folders to scan in your video library");

        recycler.setLayoutManager(new LinearLayoutManager(this));

        FolderFilterAdapter filterAdapter = new FolderFilterAdapter(allFolders, mPrefs.includedFolders, true, removedPath -> {
            Set<String> updated = new HashSet<>(mPrefs.includedFolders);
            updated.remove(removedPath);
            mPrefs.updateIncludedFolders(updated);
            updateSummaries();
            setResult(RESULT_OK);
            if (recycler.getAdapter() != null && recycler.getAdapter().getItemCount() == 0) {
                emptyState.setVisibility(View.VISIBLE);
                recycler.setVisibility(View.GONE);
            }
        });
        recycler.setAdapter(filterAdapter);

        if (allFolders.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }

        buttonAdd.setOnClickListener(v -> {
            mPrefs.updateIncludedFolders(filterAdapter.getSelectedPaths());
            sheetDialog.dismiss();
            launchAddFolderPicker();
        });

        buttonApply.setOnClickListener(v -> {
            mPrefs.updateIncludedFolders(filterAdapter.getSelectedPaths());
            updateSummaries();
            setResult(RESULT_OK);
            Toast.makeText(this, "Included folders updated", Toast.LENGTH_SHORT).show();
            sheetDialog.dismiss();
        });

        sheetDialog.show();
    }

    private void showExcludedFoldersDialog() {
        BottomSheetDialog sheetDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_App_BottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_folder_manager, null);
        sheetDialog.setContentView(sheet);
        sheetDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        sheetDialog.getBehavior().setSkipCollapsed(true);

        TextView textTitle = sheet.findViewById(R.id.text_sheet_folder_title);
        TextView textSubtitle = sheet.findViewById(R.id.text_sheet_folder_subtitle);
        View emptyState = sheet.findViewById(R.id.container_empty_state);
        RecyclerView recycler = sheet.findViewById(R.id.recycler_sheet_folders);
        MaterialButton buttonAdd = sheet.findViewById(R.id.button_add_folder);
        MaterialButton buttonApply = sheet.findViewById(R.id.button_apply_folders);

        textTitle.setText("Excluded Folders");
        textSubtitle.setText("Check folders you want to hide from scanning");
        buttonAdd.setVisibility(View.GONE);

        recycler.setLayoutManager(new LinearLayoutManager(this));

        FolderFilterAdapter filterAdapter = new FolderFilterAdapter(mDiscoveredFolders, mPrefs.excludedFolders);
        recycler.setAdapter(filterAdapter);

        if (mDiscoveredFolders.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }

        buttonApply.setOnClickListener(v -> {
            mPrefs.updateExcludedFolders(filterAdapter.getSelectedPaths());
            updateSummaries();
            setResult(RESULT_OK);
            Toast.makeText(this, "Excluded folders updated", Toast.LENGTH_SHORT).show();
            sheetDialog.dismiss();
        });

        sheetDialog.show();
    }

    private void updateStoragePermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasAllFiles = Environment.isExternalStorageManager();
            if (hasAllFiles) {
                textManageStorageStatus.setText("Storage access granted");
                textManageStorageStatus.setTextColor(0xFF81C784);
                textManageStorageAction.setText("ALLOWED");
                textManageStorageAction.setTextColor(0xFF81C784);
            } else {
                textManageStorageStatus.setText("Permission needed to scan all folders");
                textManageStorageStatus.setTextColor(0xFFFFA726);
                textManageStorageAction.setText("GRANT");
                textManageStorageAction.setTextColor(0xFF3DDC84);
            }
        } else {
            textManageStorageStatus.setText("Media storage access granted");
            textManageStorageStatus.setTextColor(0xFF81C784);
            textManageStorageAction.setText("OK");
            textManageStorageAction.setTextColor(0xFF81C784);
        }
    }

    private void showAllFilesAccessRationale() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Storage Access")
                .setMessage("To find videos in hidden and protected folders, please enable 'All files access' for Just Player in system settings.")
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
