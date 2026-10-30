package com.brouken.player;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Legacy JobService, superseded by Jetpack WorkManager (CaptionTranscriptionWorker).
 * Kept only to gracefully complete any lingering scheduled jobs from previous versions.
 */
@Deprecated
public final class CaptionAutoJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
