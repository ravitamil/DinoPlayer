package com.brouken.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/** Local playback history, independent of the current resume-position preference. */
public final class WatchHistory {
    public static final class Entry {
        public long position, duration, updated;
        public boolean completed;
    }

    private final SharedPreferences preferences;

    public WatchHistory(Context context) {
        preferences = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE);
    }

    public Map<String, Entry> snapshot() {
        Map<String, Entry> result = new HashMap<>();
        for (Map.Entry<String, ?> item : preferences.getAll().entrySet()) {
            if (!(item.getValue() instanceof String)) continue;
            try {
                JSONObject json = new JSONObject((String) item.getValue());
                Entry entry = new Entry();
                entry.position = json.optLong("position");
                entry.duration = json.optLong("duration");
                entry.updated = json.optLong("updated");
                entry.completed = json.optBoolean("completed");
                result.put(item.getKey(), entry);
            } catch (Exception ignored) {
                // An invalid history entry must not prevent library browsing.
            }
        }
        return result;
    }

    public Entry latest(Uri uri) {
        if (uri == null) return null;
        try {
            JSONObject json = new JSONObject(preferences.getString(uri.toString(), "{}"));
            Entry entry = new Entry();
            entry.position = json.optLong("position");
            entry.duration = json.optLong("duration");
            entry.updated = json.optLong("updated");
            entry.completed = json.optBoolean("completed");
            return entry.duration > 0 ? entry : null;
        } catch (Exception ignored) { return null; }
    }

    public void forget(Uri uri) { preferences.edit().remove(uri.toString()).apply(); }

    public void record(Uri uri, long position, long duration, boolean ended) {
        if (uri == null || duration <= 0 || position < 0 || (!ended && position == 0)) return;
        String key = uri.toString();
        try {
            JSONObject previous = new JSONObject(preferences.getString(key, "{}"));
            JSONObject json = new JSONObject();
            json.put("position", Math.min(position, duration));
            json.put("duration", duration);
            json.put("completed", ended || previous.optBoolean("completed"));
            json.put("updated", System.currentTimeMillis());
            preferences.edit().putString(key, json.toString()).apply();
        } catch (Exception ignored) {
            // Playback continues when history cannot be saved.
        }
    }
}
