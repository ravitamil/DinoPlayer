package com.brouken.player;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MediaScanner {

    public static class VideoItem {
        public final Uri uri;
        public final String name;
        public final String path;
        public final long size;
        public final long dateAdded;
        public final long duration;
        public final boolean isHidden;

        public VideoItem(Uri uri, String name, String path, long size, long dateAdded, long duration, boolean isHidden) {
            this.uri = uri;
            this.name = name;
            this.path = path;
            this.size = size;
            this.dateAdded = dateAdded;
            this.duration = duration;
            this.isHidden = isHidden;
        }
    }

    public static class VideoFolder {
        public final String id;
        public final String name;
        public final String path;
        public final boolean isHidden;
        public Uri posterUri;
        public final List<VideoItem> videos = new ArrayList<>();
        private final Set<String> mVideoKeys = new HashSet<>();

        public VideoFolder(String id, String name, String path, boolean isHidden) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.isHidden = isHidden;
        }

        public synchronized boolean addVideo(VideoItem item) {
            if (item == null) return false;
            String key = getVideoKey(item);
            if (mVideoKeys.add(key)) {
                videos.add(item);
                if (posterUri == null && item.uri != null) {
                    posterUri = item.uri;
                }
                return true;
            }
            return false;
        }

        public static String getVideoKey(VideoItem item) {
            if (item.path != null && !item.path.isEmpty()) {
                return Prefs.normalizeFolderPath(item.path);
            }
            if (item.uri != null) {
                String uriStr = item.uri.toString();
                String norm = Prefs.normalizeFolderPath(uriStr);
                if (!norm.isEmpty()) return norm;
                return uriStr;
            }
            return (item.name != null ? item.name : "") + "_" + item.size;
        }

        public int getVideoCount() {
            return videos.size();
        }
    }

    public static class ScanResult {
        public final List<VideoFolder> displayedFolders;
        public final List<String> allDiscoveredFolderPaths;

        public ScanResult(List<VideoFolder> displayedFolders, List<String> allDiscoveredFolderPaths) {
            this.displayedFolders = displayedFolders;
            this.allDiscoveredFolderPaths = allDiscoveredFolderPaths;
        }
    }

    public static String getCanonicalFolderKey(String folderPath, String fallbackId) {
        if (folderPath != null && !folderPath.isEmpty()) {
            String norm = Prefs.normalizeFolderPath(folderPath);
            if (!norm.isEmpty()) {
                return norm;
            }
        }
        return fallbackId != null ? fallbackId.trim().toLowerCase(Locale.ROOT) : "unknown_folder";
    }

    public static String resolveFolderName(String name, String folderPath) {
        if (name != null && !name.isEmpty()
                && !name.equalsIgnoreCase("Videos")
                && !name.equalsIgnoreCase("primary")
                && !name.equalsIgnoreCase("0")) {
            return name;
        }
        if (folderPath != null && !folderPath.isEmpty()) {
            String clean = folderPath;
            while (clean.endsWith("/") && clean.length() > 1) {
                clean = clean.substring(0, clean.length() - 1);
            }
            int lastSlash = clean.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash + 1 < clean.length()) {
                String seg = clean.substring(lastSlash + 1);
                if (!seg.isEmpty()) return seg;
            }
        }
        return (name != null && !name.isEmpty()) ? name : "Videos";
    }

    public static boolean isHiddenItem(String path, String name) {
        if (name != null && name.startsWith(".")) {
            return true;
        }
        if (path != null) {
            String norm = Prefs.normalizeFolderPath(path);
            if (norm.contains("/.")) {
                return true;
            }
        }
        return false;
    }

    private static VideoFolder getOrCreateFolder(Map<String, VideoFolder> folderMap, String canonicalKey, String displayName, String folderPath, boolean isHidden) {
        VideoFolder folder = folderMap.get(canonicalKey);
        if (folder == null) {
            String resolvedName = resolveFolderName(displayName, folderPath);
            folder = new VideoFolder(canonicalKey, resolvedName, folderPath, isHidden);
            folderMap.put(canonicalKey, folder);
        }
        return folder;
    }

    public static ScanResult scan(Context context, Prefs prefs) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        Map<String, VideoFolder> folderMap = new LinkedHashMap<>();
        Set<String> allFolderPathsSet = new LinkedHashSet<>();
        Set<String> scannedDirPaths = new HashSet<>();

        if (prefs.scanOnlyIncluded) {
            // =========================================================================
            // Mode 1: Scan ONLY INCLUDED Folders
            // Scope is strictly restricted to user's selected folders and sub-folders.
            // Show hidden files toggle applies ONLY within these selected folders.
            // =========================================================================

            // 1. Direct scan of explicitly included folders (and sub-folders)
            if (prefs.includedFolders != null) {
                for (String inc : prefs.includedFolders) {
                    String norm = Prefs.normalizeFolderPath(inc);
                    if (norm.startsWith("/")) {
                        try {
                            File dir = new File(norm);
                            if (dir.exists() && dir.isDirectory()) {
                                scanDirRecursive(dir, prefs, folderMap, allFolderPathsSet, scannedDirPaths, 0);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            // Read every persisted folder grant; selecting another folder must not discard earlier ones.
            Set<Uri> scopes = new LinkedHashSet<>();
            if (prefs.scopeUri != null) scopes.add(prefs.scopeUri);
            for (android.content.UriPermission grant : context.getContentResolver().getPersistedUriPermissions()) {
                List<String> segments = grant.getUri().getPathSegments();
                if (grant.isReadPermission() && segments.size() >= 2 && "tree".equals(segments.get(0))) scopes.add(grant.getUri());
            }
            for (Uri scope : scopes) {
                try {
                    DocumentFile scopeDoc = DocumentFile.fromTreeUri(context, scope);
                    if (scopeDoc != null && scopeDoc.exists()) {
                        scanSafScopeRecursive(scopeDoc, prefs, folderMap, allFolderPathsSet, scannedDirPaths, 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 3. Scan MediaStore for any videos located inside the included folders
            scanMediaStore(context, prefs, folderMap, allFolderPathsSet);

        } else {
            // =========================================================================
            // Mode 2: Scan ALL Folders (except excluded)
            // By default scans all media files.
            // Excluded folders (and any hidden sub-folders inside them) are completely excluded.
            // If showHidden is true, scans all hidden files/folders across storage.
            // =========================================================================

            // 1. MediaStore scan for all device media files (excluding any excluded folders)
            scanMediaStore(context, prefs, folderMap, allFolderPathsSet);

            // 2. If showHidden is enabled, scan storage for hidden files/folders (respecting exclusions)
            if (prefs.showHidden) {
                try {
                    File storageDir = Environment.getExternalStorageDirectory();
                    if (storageDir != null && storageDir.exists() && storageDir.canRead()) {
                        scanDirRecursive(storageDir, prefs, folderMap, allFolderPathsSet, scannedDirPaths, 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        List<VideoFolder> resultList = new ArrayList<>();
        for (VideoFolder folder : folderMap.values()) {
            if (folder.getVideoCount() > 0) {
                resultList.add(folder);
            }
        }
        Collections.sort(resultList, (a, b) -> a.name.compareToIgnoreCase(b.name));

        List<String> allFolderPaths = new ArrayList<>(allFolderPathsSet);
        Collections.sort(allFolderPaths, String.CASE_INSENSITIVE_ORDER);

        return new ScanResult(resultList, allFolderPaths);
    }

    private static void scanMediaStore(Context context, Prefs prefs, Map<String, VideoFolder> folderMap, Set<String> allFolderPathsSet) {
        final String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATA
        };

        final String sortOrder = MediaStore.Video.Media.DATE_MODIFIED + " DESC";

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder)) {

            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
                int durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID);
                int bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);

                while (cursor.moveToNext()) {
                    if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    long size = cursor.getLong(sizeCol);
                    long dateModified = cursor.getLong(dateCol) * 1000L;
                    long duration = cursor.getLong(durCol);
                    String bucketId = cursor.getString(bucketIdCol);
                    String bucketName = cursor.getString(bucketNameCol);
                    String dataPath = cursor.getString(dataCol);

                    if (bucketId == null) {
                        bucketId = "unknown";
                    }
                    if (bucketName == null || bucketName.isEmpty()) {
                        bucketName = "Videos";
                    }

                    String folderPath = "";
                    if (dataPath != null && !dataPath.isEmpty()) {
                        File f = new File(dataPath);
                        File parent = f.getParentFile();
                        if (parent != null) {
                            folderPath = parent.getAbsolutePath();
                        }
                    }
                    if (folderPath.isEmpty()) {
                        folderPath = bucketName;
                    }

                    allFolderPathsSet.add(folderPath);

                    boolean isHidden = isHiddenItem(folderPath, bucketName) || isHiddenItem(dataPath, name);
                    if (isHidden && !prefs.showHidden) {
                        continue;
                    }

                    // Check if folder is allowed by user's filter (include or exclude mode)
                    if (!prefs.isFolderAllowed(folderPath, bucketName)) {
                        continue;
                    }

                    String canonicalKey = getCanonicalFolderKey(folderPath, bucketId);
                    VideoFolder folder = getOrCreateFolder(folderMap, canonicalKey, bucketName, folderPath, isHidden);

                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    VideoItem videoItem = new VideoItem(contentUri, name, dataPath, size, dateModified, duration, isHidden);
                    folder.addVideo(videoItem);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void scanDirRecursive(File dir, Prefs prefs, Map<String, VideoFolder> folderMap, Set<String> allFolderPathsSet, Set<String> scannedDirPaths, int depth) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        if (depth > 8 || dir == null || !dir.exists() || !dir.isDirectory()) return;

        String canonicalPath = Prefs.normalizeFolderPath(dir.getAbsolutePath());
        if (scannedDirPaths.contains(canonicalPath)) {
            // Already scanned this exact directory in this run
            return;
        }
        scannedDirPaths.add(canonicalPath);

        // Check if directory traversal is allowed (stops traversal if directory is excluded)
        if (!prefs.isFolderTraversalAllowed(dir.getAbsolutePath())) {
            return;
        }

        boolean isHiddenDir = dir.getName().startsWith(".") || new File(dir, ".nomedia").exists() || canonicalPath.contains("/.");

        // If directory is hidden and showHidden is OFF, do not scan it or descend into it!
        if (isHiddenDir && !prefs.showHidden) {
            return;
        }

        File[] files = null;
        try {
            files = dir.listFiles();
        } catch (Exception ignored) {}
        if (files == null) return;

        if (prefs.isFolderAllowed(dir.getAbsolutePath(), dir.getName())) {
            allFolderPathsSet.add(dir.getAbsolutePath());

            List<VideoItem> videoList = new ArrayList<>();
            for (File file : files) {
                if (file.isFile() && isVideoFilename(file.getName())) {
                    boolean isFileHidden = isHiddenDir || file.getName().startsWith(".");
                    if (isFileHidden && !prefs.showHidden) {
                        continue;
                    }
                    Uri uri = Uri.fromFile(file);
                    videoList.add(new VideoItem(uri, file.getName(), file.getAbsolutePath(), file.length(), file.lastModified(), 0, isFileHidden));
                }
            }

            if (!videoList.isEmpty()) {
                VideoFolder folder = getOrCreateFolder(folderMap, canonicalPath, dir.getName(), dir.getAbsolutePath(), isHiddenDir);
                for (VideoItem v : videoList) {
                    folder.addVideo(v);
                }
            }
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirRecursive(file, prefs, folderMap, allFolderPathsSet, scannedDirPaths, depth + 1);
            }
        }
    }

    private static void scanSafScopeRecursive(DocumentFile dir, Prefs prefs, Map<String, VideoFolder> folderMap, Set<String> allFolderPathsSet, Set<String> scannedDirPaths, int depth) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        if (depth > 8 || dir == null || !dir.isDirectory()) return;

        String rawUri = dir.getUri().toString();
        String canonicalPath = Prefs.normalizeFolderPath(rawUri);
        if (scannedDirPaths.contains(canonicalPath)) {
            // Already scanned this folder via File or SAF
            return;
        }
        scannedDirPaths.add(canonicalPath);

        String name = dir.getName();
        String resolvedName = resolveFolderName(name, canonicalPath);

        boolean isHiddenDir = (name != null && name.startsWith(".")) || canonicalPath.contains("/.");

        // If directory is hidden and showHidden is OFF, do not scan it or descend into it!
        if (isHiddenDir && !prefs.showHidden) {
            return;
        }

        DocumentFile[] children = null;
        try {
            children = dir.listFiles();
        } catch (Exception ignored) {}
        if (children == null) return;

        allFolderPathsSet.add(canonicalPath);

        if (prefs.isFolderAllowed(canonicalPath, resolvedName) || prefs.isFolderAllowed(rawUri, resolvedName)) {
            List<VideoItem> videosInFolder = new ArrayList<>();
            for (DocumentFile child : children) {
                if (child.isFile() && (isVideoFilename(child.getName()) || (child.getType() != null && child.getType().startsWith("video/")))) {
                    boolean isFileHidden = isHiddenDir || (child.getName() != null && child.getName().startsWith("."));
                    if (isFileHidden && !prefs.showHidden) {
                        continue;
                    }
                    videosInFolder.add(new VideoItem(child.getUri(), child.getName(), child.getUri().toString(), child.length(), child.lastModified(), 0, isFileHidden));
                }
            }

            if (!videosInFolder.isEmpty()) {
                VideoFolder folder = getOrCreateFolder(folderMap, canonicalPath, resolvedName, canonicalPath, isHiddenDir);
                for (VideoItem v : videosInFolder) {
                    folder.addVideo(v);
                }
            }
        }

        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                scanSafScopeRecursive(child, prefs, folderMap, allFolderPathsSet, scannedDirPaths, depth + 1);
            }
        }
    }

    public static boolean isVideoFilename(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                || lower.endsWith(".avi") || lower.endsWith(".3gp") || lower.endsWith(".mov")
                || lower.endsWith(".flv") || lower.endsWith(".wmv") || lower.endsWith(".ts")
                || lower.endsWith(".m4v") || lower.endsWith(".mpg") || lower.endsWith(".mpeg")
                || lower.endsWith(".m2ts") || lower.endsWith(".vob") || lower.endsWith(".ogv")
                || lower.endsWith(".rmvb") || lower.endsWith(".asf") || lower.endsWith(".divx");
    }
}
