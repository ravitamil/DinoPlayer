package com.brouken.player;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;

class Prefs {

    private static final String PREF_KEY_MEDIA_URI = "mediaUri";
    private static final String PREF_KEY_MEDIA_TYPE = "mediaType";
    private static final String PREF_KEY_BRIGHTNESS = "brightness";
    private static final String PREF_KEY_FIRST_RUN = "firstRun";
    private static final String PREF_KEY_SUBTITLE_URI = "subtitleUri";
    private static final String PREF_KEY_AUDIO_TRACK = "audioTrack";
    private static final String PREF_KEY_AUDIO_TRACK_FFMPEG = "audioTrackFfmpeg";
    private static final String PREF_KEY_SUBTITLE_TRACK = "subtitleTrack";
    private static final String PREF_KEY_RESIZE_MODE = "resizeMode";
    private static final String PREF_KEY_ORIENTATION = "orientation";
    private static final String PREF_KEY_SCALE = "scale";
    private static final String PREF_KEY_SCOPE_URI = "scopeUri";
    private static final String PREF_KEY_ASK_SCOPE = "askScope";
    private static final String PREF_KEY_AUTO_PIP = "autoPiP";
    private static final String PREF_KEY_SHOW_HIDDEN = "showHidden";
    private static final String PREF_KEY_SORT_MODE = "sortMode";
    private static final String PREF_KEY_AUTOPLAY_NEXT = "autoPlayNext";
    private static final String PREF_KEY_EXCLUDED_FOLDERS = "excludedFolders";
    private static final String PREF_KEY_INCLUDED_FOLDERS = "includedFolders";
    private static final String PREF_KEY_SCAN_ONLY_INCLUDED = "scanOnlyIncluded";
    private static final String PREF_KEY_LIVE_CAPTIONS = "liveCaptions";

    public enum SortMode {
        NAME_ASC(0, R.string.sort_name_asc),
        NAME_DESC(1, R.string.sort_name_desc),
        DATE_DESC(2, R.string.sort_date_desc),
        DATE_ASC(3, R.string.sort_date_asc),
        SIZE_DESC(4, R.string.sort_size_desc),
        SIZE_ASC(5, R.string.sort_size_asc);

        public final int value;
        public final int stringResId;

        SortMode(int value, int stringResId) {
            this.value = value;
            this.stringResId = stringResId;
        }

        public static SortMode fromValue(int val) {
            for (SortMode mode : values()) {
                if (mode.value == val) return mode;
            }
            return DATE_DESC;
        }
    }

    final Context mContext;
    final SharedPreferences mSharedPreferences;

    public Uri mediaUri;
    public Uri subtitleUri;
    public Uri scopeUri;
    public String mediaType;
    public int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    public Utils.Orientation orientation = Utils.Orientation.VIDEO;
    public float scale = 1.f;

    public int subtitleTrack = -1;
    public int audioTrack = -1;
    public int audioTrackFfmpeg = -1;

    public int brightness = -1;
    public boolean firstRun = true;
    public boolean askScope = true;
    public boolean autoPiP = true;
    public boolean showHidden = false;
    public SortMode sortMode = SortMode.DATE_DESC;
    public boolean autoPlayNext = true;
    public Set<String> excludedFolders = new HashSet<>();
    public Set<String> includedFolders = new HashSet<>();
    public boolean scanOnlyIncluded = false;
    public boolean liveCaptions = true;

    private LinkedHashMap positions;

    public Prefs(Context context) {
        mContext = context;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        loadSavedPreferences();
        loadPositions();
    }

    private void loadSavedPreferences() {
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_URI))
            mediaUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_MEDIA_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_TYPE))
            mediaType = mSharedPreferences.getString(PREF_KEY_MEDIA_TYPE, null);
        brightness = mSharedPreferences.getInt(PREF_KEY_BRIGHTNESS, brightness);
        firstRun = mSharedPreferences.getBoolean(PREF_KEY_FIRST_RUN, firstRun);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_URI))
            subtitleUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_SUBTITLE_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_AUDIO_TRACK))
            audioTrack = mSharedPreferences.getInt(PREF_KEY_AUDIO_TRACK, audioTrack);
        if (mSharedPreferences.contains(PREF_KEY_AUDIO_TRACK_FFMPEG))
            audioTrackFfmpeg = mSharedPreferences.getInt(PREF_KEY_AUDIO_TRACK_FFMPEG, audioTrackFfmpeg);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_TRACK))
            subtitleTrack = mSharedPreferences.getInt(PREF_KEY_SUBTITLE_TRACK, subtitleTrack);
        if (mSharedPreferences.contains(PREF_KEY_RESIZE_MODE))
            resizeMode = mSharedPreferences.getInt(PREF_KEY_RESIZE_MODE, resizeMode);
        orientation = Utils.Orientation.values()[mSharedPreferences.getInt(PREF_KEY_ORIENTATION, 0)];
        scale = mSharedPreferences.getFloat(PREF_KEY_SCALE, scale);
        if (mSharedPreferences.contains(PREF_KEY_SCOPE_URI))
            scopeUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_SCOPE_URI, null));
        askScope = mSharedPreferences.getBoolean(PREF_KEY_ASK_SCOPE, askScope);
        autoPiP = mSharedPreferences.getBoolean(PREF_KEY_AUTO_PIP, autoPiP);
        showHidden = mSharedPreferences.getBoolean(PREF_KEY_SHOW_HIDDEN, showHidden);
        sortMode = SortMode.fromValue(mSharedPreferences.getInt(PREF_KEY_SORT_MODE, SortMode.DATE_DESC.value));
        autoPlayNext = mSharedPreferences.getBoolean(PREF_KEY_AUTOPLAY_NEXT, autoPlayNext);
        excludedFolders = new HashSet<>(mSharedPreferences.getStringSet(PREF_KEY_EXCLUDED_FOLDERS, Collections.emptySet()));
        includedFolders = new HashSet<>(mSharedPreferences.getStringSet(PREF_KEY_INCLUDED_FOLDERS, Collections.emptySet()));
        scanOnlyIncluded = mSharedPreferences.getBoolean(PREF_KEY_SCAN_ONLY_INCLUDED, scanOnlyIncluded);
        liveCaptions = mSharedPreferences.getBoolean(PREF_KEY_LIVE_CAPTIONS, liveCaptions);
    }

    public void updateMedia(final Context context, final Uri uri, final String type) {
        mediaUri = uri;
        mediaType = type;
        updateSubtitle(null);
        updateMeta(-1, -1, -1, AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.f);

        if (mediaType != null && mediaType.endsWith("/*")) {
            mediaType = null;
        }

        if (mediaType == null) {
            if (ContentResolver.SCHEME_CONTENT.equals(mediaUri.getScheme())) {
                mediaType = context.getContentResolver().getType(mediaUri);
            }
        }

        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        if (mediaUri == null)
            sharedPreferencesEditor.remove(PREF_KEY_MEDIA_URI);
        else
            sharedPreferencesEditor.putString(PREF_KEY_MEDIA_URI, mediaUri.toString());
        if (mediaType == null)
            sharedPreferencesEditor.remove(PREF_KEY_MEDIA_TYPE);
        else
            sharedPreferencesEditor.putString(PREF_KEY_MEDIA_TYPE, mediaType);
        sharedPreferencesEditor.commit();
    }

    public void updateSubtitle(final Uri uri) {
        subtitleUri = uri;
        subtitleTrack = -1;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        if (uri == null)
            sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_URI);
        else
            sharedPreferencesEditor.putString(PREF_KEY_SUBTITLE_URI, uri.toString());
        sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK);
        sharedPreferencesEditor.commit();
    }

    public void updatePosition(final long position) {
        if (mediaUri == null)
            return;

        while (positions.size() > 100)
            positions.remove(positions.keySet().toArray()[0]);

        positions.put(mediaUri.toString(), position);
        savePositions();
        mSharedPreferences.edit().putLong("resumeWritten:" + mediaUri, System.currentTimeMillis()).apply();
    }

    public void updateBrightness(final int brightness) {
        if (brightness >= -1) {
            this.brightness = brightness;
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            sharedPreferencesEditor.putInt(PREF_KEY_BRIGHTNESS, brightness);
            sharedPreferencesEditor.commit();
        }
    }

    public void markFirstRun() {
        this.firstRun = false;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_FIRST_RUN, false);
        sharedPreferencesEditor.commit();
    }

    public void markScopeAsked() {
        this.askScope = false;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_ASK_SCOPE, false);
        sharedPreferencesEditor.commit();
    }

    private void savePositions() {
        try {
            FileOutputStream fos = mContext.openFileOutput("positions", Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(positions);
            os.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        try {
            FileInputStream fis = mContext.openFileInput("positions");
            ObjectInputStream is = new ObjectInputStream(fis);
            positions = (LinkedHashMap) is.readObject();
            is.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
            positions = new LinkedHashMap(10);
        }
    }

    public long getPosition() {
        if (mediaUri == null) return 0L;
        WatchHistory.Entry checkpoint = new WatchHistory(mContext).latest(mediaUri);
        if (checkpoint != null && checkpoint.updated > mSharedPreferences.getLong("resumeWritten:" + mediaUri, 0)) {
            return WatchProgress.resumePosition(checkpoint.position, checkpoint.duration);
        }
        Object val = positions.get(mediaUri.toString());
        if (val instanceof Long)
            return checkpoint != null ? WatchProgress.resumePosition((long) val, checkpoint.duration) : (long) val;

        // Return position for uri from limited scope (loaded after using Next action)
        if (ContentResolver.SCHEME_CONTENT.equals(mediaUri.getScheme())) {
            final String searchPath = SubtitleUtils.getTrailPathFromUri(mediaUri);
            if (searchPath == null || searchPath.length() < 1)
                return 0L;
            final Set<String> keySet = positions.keySet();
            final Object[] keys = keySet.toArray();
            for (int i = keys.length; i > 0; i--) {
                final String key = (String) keys[i - 1];
                final Uri uri = Uri.parse(key);
                if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                    final String keyPath = SubtitleUtils.getTrailPathFromUri(uri);
                    if (searchPath.equals(keyPath)) {
                        return (long) positions.get(key);
                    }
                }
            }
        }

        return 0L;
    }

    public void updateOrientation() {
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(PREF_KEY_ORIENTATION, orientation.value);
        sharedPreferencesEditor.commit();
    }

    public void updateMeta(final int audioTrack, final int audioTrackFfmpeg, final int subtitleTrack, final int resizeMode, final float scale) {
        this.audioTrack = audioTrack;
        this.audioTrackFfmpeg = audioTrackFfmpeg;
        this.subtitleTrack = subtitleTrack;
        this.resizeMode = resizeMode;
        this.scale = scale;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        if (audioTrack == -1)
            sharedPreferencesEditor.remove(PREF_KEY_AUDIO_TRACK);
        else
            sharedPreferencesEditor.putInt(PREF_KEY_AUDIO_TRACK, audioTrack);
        if (audioTrackFfmpeg == -1)
            sharedPreferencesEditor.remove(PREF_KEY_AUDIO_TRACK_FFMPEG);
        else
            sharedPreferencesEditor.putInt(PREF_KEY_AUDIO_TRACK_FFMPEG, audioTrackFfmpeg);
        if (subtitleTrack == -1)
            sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK);
        else
            sharedPreferencesEditor.putInt(PREF_KEY_SUBTITLE_TRACK, subtitleTrack);
        sharedPreferencesEditor.putInt(PREF_KEY_RESIZE_MODE, resizeMode);
        sharedPreferencesEditor.putFloat(PREF_KEY_SCALE, scale);
        sharedPreferencesEditor.commit();
    }

    public void updateSubtitleTrack(final int subtitleTrack) {
        this.subtitleTrack = subtitleTrack;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        if (subtitleTrack == -1)
            sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK);
        else
            sharedPreferencesEditor.putInt(PREF_KEY_SUBTITLE_TRACK, subtitleTrack);
        sharedPreferencesEditor.commit();
    }

    public void updateScope(final Uri uri) {
        scopeUri = uri;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        if (uri == null)
            sharedPreferencesEditor.remove(PREF_KEY_SCOPE_URI);
        else
            sharedPreferencesEditor.putString(PREF_KEY_SCOPE_URI, uri.toString());
        sharedPreferencesEditor.commit();
    }

    public boolean toggleAutoPiP() {
        autoPiP = !autoPiP;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_AUTO_PIP, autoPiP);
        sharedPreferencesEditor.commit();
        return autoPiP;
    }

    public void updateShowHidden(final boolean showHidden) {
        this.showHidden = showHidden;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_SHOW_HIDDEN, showHidden);
        sharedPreferencesEditor.commit();
    }

    public void updateSortMode(final SortMode sortMode) {
        this.sortMode = sortMode;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(PREF_KEY_SORT_MODE, sortMode.value);
        sharedPreferencesEditor.commit();
    }

    public void updateAutoPlayNext(final boolean autoPlayNext) {
        this.autoPlayNext = autoPlayNext;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_AUTOPLAY_NEXT, autoPlayNext);
        sharedPreferencesEditor.commit();
    }

    public void updateExcludedFolders(Set<String> set) {
        this.excludedFolders = new HashSet<>(set);
        final SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putStringSet(PREF_KEY_EXCLUDED_FOLDERS, this.excludedFolders);
        editor.commit();
    }

    public void updateIncludedFolders(Set<String> set) {
        this.includedFolders = new HashSet<>(set);
        final SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putStringSet(PREF_KEY_INCLUDED_FOLDERS, this.includedFolders);
        editor.commit();
    }

    public void updateScanOnlyIncluded(boolean scanOnlyIncluded) {
        this.scanOnlyIncluded = scanOnlyIncluded;
        final SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(PREF_KEY_SCAN_ONLY_INCLUDED, scanOnlyIncluded);
        editor.commit();
    }

    public void updateLiveCaptions(boolean liveCaptions) {
        this.liveCaptions = liveCaptions;
        final SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(PREF_KEY_LIVE_CAPTIONS, liveCaptions);
        editor.commit();
    }

    public static String normalizeFolderPath(String rawPath) {
        return FolderPaths.normalize(rawPath);
    }

    public static boolean isPathUnderFolder(String childPath, String parentPath) {
        return FolderPaths.isUnder(childPath, parentPath);
    }

    public boolean isFolderExcluded(String folderPath, String folderName) {
        if (folderPath == null && folderName == null) return false;
        String normPath = normalizeFolderPath(folderPath);
        String normName = (folderName != null ? folderName.trim().toLowerCase(Locale.ROOT) : "");

        for (String excRaw : excludedFolders) {
            String exc = normalizeFolderPath(excRaw);
            // 1. Exact match or subfolder/child of excluded folder
            if (!normPath.isEmpty() && isPathUnderFolder(normPath, exc)) {
                return true;
            }
            // 2. Name-only match if exc has no slashes
            if (!exc.contains("/") && !normName.isEmpty() && normName.equals(exc)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFolderIncluded(String folderPath, String folderName) {
        if (includedFolders.isEmpty()) return false;
        String normPath = normalizeFolderPath(folderPath);
        String normName = (folderName != null ? folderName.trim().toLowerCase(Locale.ROOT) : "");

        for (String incRaw : includedFolders) {
            String inc = normalizeFolderPath(incRaw);
            // 1. Exact match or subfolder/child of included folder
            if (!normPath.isEmpty() && isPathUnderFolder(normPath, inc)) {
                return true;
            }
            // 2. Name-only match if inc has no slashes
            if (!inc.contains("/") && !normName.isEmpty() && normName.equals(inc)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFolderAllowed(String folderPath, String folderName) {
        if (scanOnlyIncluded) {
            return isFolderIncluded(folderPath, folderName);
        } else {
            return !isFolderExcluded(folderPath, folderName);
        }
    }

    public boolean isFolderTraversalAllowed(String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) return true;
        String normPath = normalizeFolderPath(folderPath);

        if (scanOnlyIncluded) {
            if (includedFolders.isEmpty()) return false;
            for (String incRaw : includedFolders) {
                String inc = normalizeFolderPath(incRaw);
                // Exact match or inside included folder
                if (normPath.equals(inc) || normPath.startsWith(inc + "/")) return true;
                // Ancestor of included folder (e.g. /storage/emulated/0/download is ancestor of .../.hidden)
                if (inc.startsWith(normPath + "/")) return true;
                // If inc has no slashes, allow traversal
                if (!inc.contains("/")) return true;
            }
            return false;
        } else {
            // Exclude mode: if this folder or an ancestor was excluded, stop traversal completely!
            if (isFolderExcluded(folderPath, null)) {
                return false;
            }
            return true;
        }
    }
}
