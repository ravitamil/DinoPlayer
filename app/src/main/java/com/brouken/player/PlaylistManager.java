package com.brouken.player;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Ordered playback queue. Availability checks and lookups never advance it. */
public final class PlaylistManager {
    private static final List<Uri> playlist = new ArrayList<>();
    private static final String STORE = "playback_queue";

    public static synchronized void setPlaylist(Context context, List<Uri> items, Uri currentUri) {
        playlist.clear();
        if (items != null) {
            for (Uri uri : new LinkedHashSet<>(items)) if (uri != null) playlist.add(uri);
        }
        JSONArray stored = new JSONArray();
        for (Uri uri : playlist) stored.put(uri.toString());
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString("items", stored.toString()).apply();
    }

    public static synchronized void remove(Context context,Uri uri) {
        restore(context);playlist.remove(uri);
        setPlaylist(context,new ArrayList<>(playlist),null);
    }

    public static synchronized void restore(Context context) {
        if (!playlist.isEmpty()) return;
        try {
            JSONArray stored = new JSONArray(context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString("items", "[]"));
            for (int i = 0; i < stored.length(); i++) playlist.add(Uri.parse(stored.getString(i)));
        } catch (JSONException e) {
            playlist.clear();
        }
    }

    public static synchronized Uri getNext(Uri currentUri) {
        int index = playlist.indexOf(currentUri);
        return index >= 0 && index + 1 < playlist.size() ? playlist.get(index + 1) : null;
    }

    public static synchronized Uri getPrevious(Uri currentUri) {
        int index = playlist.indexOf(currentUri);
        return index > 0 ? playlist.get(index - 1) : null;
    }

    public static synchronized boolean hasNext(Uri currentUri) {
        return getNext(currentUri) != null;
    }

    public static synchronized boolean hasPrevious(Uri currentUri) {
        return getPrevious(currentUri) != null;
    }

    public static synchronized int getPlaylistSize() {
        return playlist.size();
    }

    public static synchronized boolean contains(Uri uri) {
        return playlist.contains(uri);
    }
}
