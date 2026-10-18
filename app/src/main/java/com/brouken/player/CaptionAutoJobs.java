package com.brouken.player;

import android.app.job.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import org.json.*;
import java.util.*;

/** Durable discovery queue; model downloads remain an explicit user action. */
public final class CaptionAutoJobs {
    static final int WATCH=7101, WORK=7102, DISCOVER=7103;
    private static volatile boolean playerActive;
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("caption_auto",0); }
    public static boolean enabled(Context c) { return prefs(c).getBoolean("enabled",false); }
    public static void setEnabled(Context c,boolean enabled) {
        prefs(c).edit().putBoolean("enabled",enabled).remove("known").apply();
        if(!enabled) {
            jobs(c).cancel(WATCH);jobs(c).cancel(WORK);jobs(c).cancel(DISCOVER);
            synchronized(CaptionAutoJobs.class) {
                JSONArray queue=queue(c);
                for(int i=0;i<queue.length();i++) { JSONObject item=queue.optJSONObject(i);if(item==null)continue;
                    Uri uri=Uri.parse(item.optString("uri"));CaptionPack pack=CaptionPack.find(item.optString("pack"));
                    CaptionWorkStore.Entry state=new CaptionWorkStore(c).read(uri,pack);
                    if(state!=null && state.status==CaptionWorkStore.Status.QUEUED)
                        new CaptionWorkStore(c).update(uri,pack,state.token,CaptionWorkStore.Status.CANCELLED,0,"");
                }
                prefs(c).edit().putString("queue","[]").apply();
            }
        } else scheduleWatch(c);
    }
    static JobScheduler jobs(Context c) { return (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE); }
    static synchronized JSONArray queue(Context c) {
        try { return new JSONArray(prefs(c).getString("queue","[]")); }catch(JSONException e) { return new JSONArray(); }
    }
    public static synchronized void observe(Context c,List<MediaScanner.VideoFolder> folders) {
        CaptionWorkStore.initialize(c);
        if(!enabled(c))return;
        SharedPreferences p=prefs(c);Set<String> before=p.getStringSet("known",null),now=new HashSet<>();
        JSONArray queue=queue(c);CaptionPack pack=CaptionPackages.get(c).selected();
        for(MediaScanner.VideoFolder folder:folders)for(MediaScanner.VideoItem video:folder.videos) {
            String uri=video.uri.toString();if(!now.add(uri))continue;
            if(before!=null && !before.contains(uri) && !new CaptionWorkStore(c).isDeleting(video.uri)) {
                try {
                    JSONObject item=new JSONObject();item.put("uri",uri);item.put("path",video.path);item.put("pack",pack.id);
                    boolean exists=false;for(int i=0;i<queue.length();i++)if(uri.equals(queue.getJSONObject(i).optString("uri")))exists=true;
                    if(!exists) { queue.put(item);new CaptionWorkStore(c).begin(video.uri,pack,"queued-"+UUID.randomUUID()); }
                }catch(JSONException ignored) { }
            }
        }
        p.edit().putStringSet("known",now).putString("queue",queue.toString()).apply();
        kick(c);scheduleWatch(c);
    }
    public static synchronized void removeQueued(Context c,Uri uri,CaptionPack pack) {
        JSONArray original=queue(c),remaining=new JSONArray();
        for(int i=0;i<original.length();i++) {
            JSONObject item=original.optJSONObject(i);if(item==null)continue;
            if(!uri.toString().equals(item.optString("uri")) || (pack!=null && !pack.id.equals(item.optString("pack"))))remaining.put(item);
        }
        prefs(c).edit().putString("queue",remaining.toString()).apply();
    }
    static synchronized JSONObject next(Context c) {
        JSONArray queue=queue(c);
        for(int i=0;i<queue.length();i++) {
            JSONObject item=queue.optJSONObject(i);
            if(item!=null && CaptionPackages.get(c).isInstalled(CaptionPack.find(item.optString("pack"))))return item;
        }
        return null;
    }
    public static void playerActive(Context c,boolean active) {
        playerActive=active;
        if(active)SubtitleGenerator.preemptBackground();else kick(c);
    }
    static boolean canRun(Context c) { return enabled(c) && !playerActive; }
    private static boolean pending(Context c,int id) { for(JobInfo job:jobs(c).getAllPendingJobs())if(job.getId()==id)return true;return false; }
    public static void kick(Context c) {
        CaptionWorkStore.initialize(c);
        if(enabled(c)) {
            JSONArray saved=queue(c);
            for(int i=0;i<saved.length();i++) {
                JSONObject item=saved.optJSONObject(i);if(item==null)continue;
                Uri uri=Uri.parse(item.optString("uri"));CaptionPack pack=CaptionPack.find(item.optString("pack"));
                CaptionWorkStore store=new CaptionWorkStore(c);CaptionWorkStore.Entry entry=store.read(uri,pack);
                if(entry!=null && entry.status==CaptionWorkStore.Status.CANCELLED && "App closed before completion".equals(entry.reason))
                    store.begin(uri,pack,"queued-"+UUID.randomUUID());
            }
        }
        if(!canRun(c) || next(c)==null)return;
        if(!pending(c,WORK))jobs(c).schedule(new JobInfo.Builder(WORK,new ComponentName(c,CaptionAutoJobService.class))
                .setMinimumLatency(1000).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_LINEAR).build());
    }
    static boolean canDiscover(Context c) {
        if(Build.VERSION.SDK_INT<23 || c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==android.content.pm.PackageManager.PERMISSION_GRANTED)return true;
        for(android.content.UriPermission permission:c.getContentResolver().getPersistedUriPermissions())if(permission.isReadPermission())return true;
        return false;
    }
    public static void scheduleWatch(Context c) {
        if(!enabled(c))return;
        // Periodic reconciliation also discovers SAF videos and closes gaps between content-trigger registrations.
        if(!pending(c,DISCOVER))jobs(c).schedule(new JobInfo.Builder(DISCOVER,new ComponentName(c,CaptionAutoJobService.class))
                .setPeriodic(15L*60*1000).setPersisted(true).build());
        if(Build.VERSION.SDK_INT<24 || pending(c,WATCH) || c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        jobs(c).schedule(new JobInfo.Builder(WATCH,new ComponentName(c,CaptionAutoJobService.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(2000).setTriggerContentMaxDelay(10000).build());
    }
}
