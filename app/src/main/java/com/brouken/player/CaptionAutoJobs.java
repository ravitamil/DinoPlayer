package com.brouken.player;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.brouken.player.work.CaptionDiscoveryWorker;
import com.brouken.player.work.CaptionTranscriptionWorker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Durable discovery queue; model downloads remain an explicit user action. */
public final class CaptionAutoJobs {
    private static volatile boolean playerActive;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("caption_auto", 0);
    }

    public static boolean enabled(Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    public static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("enabled", enabled).remove("known").apply();
        if (!enabled) {
            CaptionTranscriptionWorker.cancel(c);
            WorkManager.getInstance(c).cancelUniqueWork("CaptionDiscoveryPeriodic");
            WorkManager.getInstance(c).cancelUniqueWork("CaptionDiscoveryTrigger");
            try {
                JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                if (js != null) js.cancelAll();
            } catch (Exception ignored) {
            }

            synchronized (CaptionAutoJobs.class) {
                JSONArray queue = queue(c);
                for (int i = 0; i < queue.length(); i++) {
                    JSONObject item = queue.optJSONObject(i);
                    if (item == null) continue;
                    Uri uri = Uri.parse(item.optString("uri"));
                    CaptionPack pack = CaptionPack.find(item.optString("pack"));
                    CaptionWorkStore.Entry state = new CaptionWorkStore(c).read(uri, pack);
                    if (state != null && state.status == CaptionWorkStore.Status.QUEUED)
                        new CaptionWorkStore(c).update(uri, pack, state.token, CaptionWorkStore.Status.CANCELLED, 0, "");
                }
                prefs(c).edit().putString("queue", "[]").apply();
            }
        } else {
            scheduleWatch(c);
        }
    }

    public static synchronized JSONArray queue(Context c) {
        try {
            return new JSONArray(prefs(c).getString("queue", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static synchronized void observe(Context c, List<MediaScanner.VideoFolder> folders) {
        CaptionWorkStore.initialize(c);
        if (!enabled(c)) return;
        SharedPreferences p = prefs(c);
        Set<String> before = p.getStringSet("known", null), now = new HashSet<>();
        JSONArray queue = queue(c);
        CaptionPack pack = CaptionPackages.get(c).selected();
        for (MediaScanner.VideoFolder folder : folders) {
            for (MediaScanner.VideoItem video : folder.videos) {
                String uri = video.uri.toString();
                if (!now.add(uri)) continue;
                if (before != null && !before.contains(uri) && !new CaptionWorkStore(c).isDeleting(video.uri)) {
                    try {
                        JSONObject item = new JSONObject();
                        item.put("uri", uri);
                        item.put("path", video.path);
                        item.put("pack", pack.id);
                        boolean exists = false;
                        for (int i = 0; i < queue.length(); i++) {
                            if (uri.equals(queue.getJSONObject(i).optString("uri"))) exists = true;
                        }
                        if (!exists) {
                            queue.put(item);
                            new CaptionWorkStore(c).begin(video.uri, pack, "queued-" + UUID.randomUUID());
                        }
                    } catch (JSONException ignored) {
                    }
                }
            }
        }
        p.edit().putStringSet("known", now).putString("queue", queue.toString()).apply();
        kick(c);
        scheduleWatch(c);
    }

    public static synchronized void removeQueued(Context c, Uri uri, CaptionPack pack) {
        JSONArray original = queue(c), remaining = new JSONArray();
        for (int i = 0; i < original.length(); i++) {
            JSONObject item = original.optJSONObject(i);
            if (item == null) continue;
            if (!uri.toString().equals(item.optString("uri")) || (pack != null && !pack.id.equals(item.optString("pack")))) {
                remaining.put(item);
            }
        }
        prefs(c).edit().putString("queue", remaining.toString()).apply();
    }

    public static synchronized JSONObject next(Context c) {
        JSONArray queue = queue(c);
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item != null && CaptionPackages.get(c).isInstalled(CaptionPack.find(item.optString("pack")))) {
                return item;
            }
        }
        return null;
    }

    public static void playerActive(Context c, boolean active) {
        playerActive = active;
        if (active) SubtitleGenerator.preemptBackground();
        else kick(c);
    }

    public static boolean canRun(Context c) {
        return enabled(c) && !playerActive;
    }

    public static void kick(Context c) {
        CaptionWorkStore.initialize(c);
        if (enabled(c)) {
            JSONArray saved = queue(c);
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                Uri uri = Uri.parse(item.optString("uri"));
                CaptionPack pack = CaptionPack.find(item.optString("pack"));
                CaptionWorkStore store = new CaptionWorkStore(c);
                CaptionWorkStore.Entry entry = store.read(uri, pack);
                if (entry != null && entry.status == CaptionWorkStore.Status.CANCELLED && "App closed before completion".equals(entry.reason))
                    store.begin(uri, pack, "queued-" + UUID.randomUUID());
            }
        }
        if (!canRun(c) || next(c) == null) return;
        CaptionTranscriptionWorker.enqueue(c);
    }

    public static boolean canDiscover(Context c) {
        if (Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            return true;
        for (android.content.UriPermission permission : c.getContentResolver().getPersistedUriPermissions()) {
            if (permission.isReadPermission()) return true;
        }
        return false;
    }

    public static void scheduleWatch(Context c) {
        if (!enabled(c)) return;

        PeriodicWorkRequest discoveryRequest = new PeriodicWorkRequest.Builder(
                CaptionDiscoveryWorker.class, 15, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                "CaptionDiscoveryPeriodic",
                ExistingPeriodicWorkPolicy.KEEP,
                discoveryRequest
        );

        if (Build.VERSION.SDK_INT >= 24 && c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Constraints triggerConstraints = new Constraints.Builder()
                    .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
                    .setTriggerContentUpdateDelay(2000, TimeUnit.MILLISECONDS)
                    .setTriggerContentMaxDelay(10000, TimeUnit.MILLISECONDS)
                    .build();
            OneTimeWorkRequest triggerRequest = new OneTimeWorkRequest.Builder(CaptionDiscoveryWorker.class)
                    .setConstraints(triggerConstraints)
                    .build();
            WorkManager.getInstance(c).enqueueUniqueWork(
                    "CaptionDiscoveryTrigger",
                    ExistingWorkPolicy.REPLACE,
                    triggerRequest
            );
        }
    }
}
