package com.brouken.player.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.brouken.player.CaptionAutoJobs;
import com.brouken.player.MediaScanner;
import com.brouken.player.Prefs;

public class CaptionDiscoveryWorker extends Worker {

    public CaptionDiscoveryWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!CaptionAutoJobs.canDiscover(context)) {
            return Result.success();
        }
        try {
            CaptionAutoJobs.observe(context, MediaScanner.scan(context, new Prefs(context)).displayedFolders);
        } catch (Exception ignored) {
        }
        return Result.success();
    }
}
