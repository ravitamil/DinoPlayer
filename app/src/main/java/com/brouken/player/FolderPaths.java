package com.brouken.player;

import java.net.URI;
import java.net.URISyntaxException;

/** Converts external-storage document IDs to real paths without changing case or volume IDs. */
public final class FolderPaths {
    private FolderPaths() {}

    public static String normalize(String value) {
        if (value == null) return "";
        String path = value.trim();
        if (path.startsWith("content://")) {
            try {
                URI uri = new URI(path);
                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    String decoded = uri.getPath();
                    int start = decoded.lastIndexOf("/document/");
                    int prefix = 10;
                    if (start < 0) { start = decoded.indexOf("/tree/"); prefix = 6; }
                    if (start >= 0) {
                        String id = decoded.substring(start + prefix);
                        int colon = id.indexOf(':');
                        if (colon >= 0) {
                            String volume = id.substring(0, colon);
                            String relative = id.substring(colon + 1);
                            path = ("primary".equalsIgnoreCase(volume) ? "/storage/emulated/0/" : "/storage/" + volume + "/") + relative;
                        }
                    }
                }
            } catch (URISyntaxException ignored) {
                // Keep nonstandard provider identifiers intact rather than guessing a disk location.
            }
        }
        while (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        return path;
    }

    public static boolean isUnder(String child, String parent) {
        String c = normalize(child);
        String p = normalize(parent);
        return !c.isEmpty() && !p.isEmpty() && (c.equals(p) || c.startsWith(p + "/"));
    }
}
