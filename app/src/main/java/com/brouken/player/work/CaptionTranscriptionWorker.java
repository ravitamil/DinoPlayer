package com.brouken.player.work;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.brouken.player.CaptionAutoJobs;
import com.brouken.player.CaptionPack;
import com.brouken.player.SubtitleGenerator;
import com.brouken.player.SubtitleTimeline;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WorkManager worker executing on-device transcription in the background
 * with standard battery-not-low and device-idle constraints.
 */
public class CaptionTranscriptionWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "CaptionTranscription";

    private volatile SubtitleGenerator.Job activeJob;

    public CaptionTranscriptionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void enqueue(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CaptionTranscriptionWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest
        );
    }

    public static void cancel(@NonNull Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        while (CaptionAutoJobs.canRun(context) && !isStopped()) {
            JSONObject item = CaptionAutoJobs.next(context);
            if (item == null) {
                break;
            }

            Uri uri = Uri.parse(item.optString("uri"));
            CaptionPack pack = CaptionPack.find(item.optString("pack"));
            if (pack == null) {
                CaptionAutoJobs.removeQueued(context, uri, null);
                continue;
            }

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicBoolean success = new AtomicBoolean(false);
            final AtomicBoolean retry = new AtomicBoolean(false);

            activeJob = SubtitleGenerator.generateSubtitlesAsync(
                    context,
                    uri,
                    item.optString("path", null),
                    0,
                    pack,
                    false,
                    true,
                    new SubtitleGenerator.SubtitleCallback() {
                        @Override
                        public void onCues(List<SubtitleTimeline.Cue> cues, long start, long end, int percent) {
                        }

                        @Override
                        public void onSubtitlesReady(Uri result) {
                            activeJob = null;
                            CaptionAutoJobs.removeQueued(context, uri, pack);
                            success.set(true);
                            latch.countDown();
                        }

                        @Override
                        public void onFailed(String reason) {
                            activeJob = null;
                            CaptionAutoJobs.removeQueued(context, uri, pack);
                            success.set(false);
                            latch.countDown();
                        }

                        @Override
                        public void onCancelled() {
                            activeJob = null;
                            retry.set(CaptionAutoJobs.enabled(context));
                            latch.countDown();
                        }
                    }
            );

            try {
                latch.await();
            } catch (InterruptedException e) {
                if (activeJob != null) {
                    activeJob.cancelForRetry();
                }
                return Result.retry();
            }

            if (isStopped()) {
                if (activeJob != null) {
                    activeJob.cancelForRetry();
                }
                return Result.retry();
            }

            if (retry.get()) {
                return Result.retry();
            }
        }

        return Result.success();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        if (activeJob != null) {
            activeJob.cancelForRetry();
            activeJob = null;
        }
    }
}
