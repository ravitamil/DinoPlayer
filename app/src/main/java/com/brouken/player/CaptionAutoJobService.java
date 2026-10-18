package com.brouken.player;

import android.app.job.*;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

public final class CaptionAutoJobService extends JobService {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService scanner=Executors.newSingleThreadExecutor();
    private final Set<JobParameters> stopped=Collections.newSetFromMap(new IdentityHashMap<>());
    private SubtitleGenerator.Job active;
    @Override public boolean onStartJob(JobParameters params) {
        CaptionWorkStore.initialize(this);
        if(params.getJobId()==CaptionAutoJobs.WATCH || params.getJobId()==CaptionAutoJobs.DISCOVER) {
            if(!CaptionAutoJobs.canDiscover(this))return false;
            scanner.execute(() -> {
                try { CaptionAutoJobs.observe(this,MediaScanner.scan(this,new Prefs(this)).displayedFolders); }
                catch(RuntimeException ignored) { }
                main.post(() -> { if(!stopped.remove(params))jobFinished(params,false);
                    // Content-trigger jobs must be registered again after completion.
                    main.postDelayed(() -> CaptionAutoJobs.scheduleWatch(this),1000);
                });
            });return true;
        }
        if(!CaptionAutoJobs.canRun(this))return false;
        JSONObject item=CaptionAutoJobs.next(this);if(item==null)return false;
        Uri uri=Uri.parse(item.optString("uri"));CaptionPack pack=CaptionPack.find(item.optString("pack"));
        active=SubtitleGenerator.generateSubtitlesAsync(this,uri,item.optString("path",null),0,pack,false,true,new SubtitleGenerator.SubtitleCallback() {
            @Override public void onCues(List<SubtitleTimeline.Cue> cues,long start,long end,int percent) { }
            private void done(boolean cancelled) {
                if(stopped.remove(params))return;
                active=null;
                if(!cancelled)CaptionAutoJobs.removeQueued(CaptionAutoJobService.this,uri,pack);
                jobFinished(params,cancelled && CaptionAutoJobs.canRun(CaptionAutoJobService.this));
                if(!cancelled)main.postDelayed(() -> CaptionAutoJobs.kick(CaptionAutoJobService.this),1000);
            }
            @Override public void onSubtitlesReady(Uri result) { done(false); }
            @Override public void onFailed(String reason) { done(false); }
            @Override public void onCancelled() { done(true); }
        });return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        stopped.add(params);
        if(params.getJobId()==CaptionAutoJobs.WORK && active!=null) { if(CaptionAutoJobs.enabled(this))active.cancelForRetry();else active.cancel();active=null; }
        return CaptionAutoJobs.enabled(this);
    }
    @Override public void onDestroy() { scanner.shutdownNow();super.onDestroy(); }
}
