package com.brouken.player;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.content.res.ColorStateList;
import android.widget.Toast;
import androidx.media3.common.text.Cue;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.SubtitleView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Displays incremental captions against the media clock without replacing/rebuffering the video. */
public final class LiveSubtitleController {
    public interface PlayerSource { ExoPlayer get(); }
    public interface Completion { void accept(Uri uri); }
    private final Activity activity;
    private final CustomStyledPlayerView playerView;
    private final PlayerSource player;
    private final SubtitleView subtitles;
    private final TextView status;
    private final ProgressBar progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SubtitleTimeline timeline = new SubtitleTimeline();
    private final List<long[]> completedRanges = new ArrayList<>();
    private SubtitleGenerator.Job job;
    private SubtitleTimeline.Cue displayed;
    private boolean enabled;
    private long subtitleOffsetMs = 0L;
    private int generation;
    private int percent;
    private int renderedPercent=-1;
    private boolean renderedReady;
    private boolean inPip;
    private boolean controlsVisible;
    private boolean hasNotice;
    private Runnable onStateChanged=()->{};
    public LiveSubtitleController(Activity activity, CustomStyledPlayerView playerView,
                                  PlayerSource player) {
        this.activity = activity;
        this.playerView = playerView;
        this.player = player;
        FrameLayout overlay = playerView.getOverlayFrameLayout();
        subtitles = new SubtitleView(activity);
        overlay.addView(subtitles, new FrameLayout.LayoutParams(-1, -1));
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        int accent = activity.getResources().getColor(R.color.player_accent);
        progress.setProgressTintList(ColorStateList.valueOf(accent));
        progress.setIndeterminateTintList(ColorStateList.valueOf(accent));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x22ffffff));
        progress.setAlpha(0.45f);
        progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.addView(progress, new FrameLayout.LayoutParams(-1, Utils.dpToPx(3), Gravity.TOP));
        progress.setVisibility(View.GONE);
        controlsVisible = playerView.isControllerFullyVisible();
        status = new TextView(activity);
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setMinHeight(Utils.dpToPx(48));
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(2);
        status.setBackgroundResource(R.drawable.caption_status_background);
        int padding = Utils.dpToPx(8);
        status.setPadding(padding, padding, padding, padding);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = Utils.dpToPx(56);
        overlay.addView(status, params);
        status.setVisibility(View.GONE);
        resize(false);
    }
    public void start(Uri uri, String path, Completion complete, Runnable stateChanged) {
        start(uri,path,complete,stateChanged,false);
    }
    public void start(Uri uri, String path, Completion complete, Runnable stateChanged, boolean force) {
        clear();
        onStateChanged=stateChanged;
        if (uri == null || !("content".equals(uri.getScheme()) || "file".equals(uri.getScheme()))) {
            status.setText(R.string.captions_local_only);
            hasNotice = true;
            updateIndicator();
            stateChanged.run();
            return;
        }
        CaptionPackages packages = CaptionPackages.get(activity);
        CaptionPack selected = packages.selected();
        if (!packages.isInstalled(selected)) {
            packages.reconcile();
            status.setText(activity.getString(R.string.captions_install_pack, selected.title));
            hasNotice = true;
            updateIndicator();
            status.setOnClickListener(v -> activity.startActivity(
                    new android.content.Intent(activity, SettingsActivity.class)));
            stateChanged.run();
            return;
        }
        status.setOnClickListener(null);
        enabled = true;
        int request = generation;
        ExoPlayer current = player.get();
        status.setText(activity.getString(R.string.captions_preparing_pack, selected.title));
        job = SubtitleGenerator.generateSubtitlesAsync(activity, uri, path,
                current != null ? current.getCurrentPosition() : 0, selected, force, false, new SubtitleGenerator.SubtitleCallback() {
            @Override
            public void onCues(List<SubtitleTimeline.Cue> cues, long startMs, long endMs, int progress) {
                if (request != generation || activity.isFinishing() || activity.isDestroyed()) return;
                timeline.addAll(cues);
                completedRanges.add(new long[]{startMs, endMs});
                percent = progress;
                updateIndicator();
                stateChanged.run();
            }
            @Override
            public void onSubtitlesReady(Uri subtitleUri) {
                if (request != generation || activity.isFinishing() || activity.isDestroyed()) return;
                job = null;
                updateIndicator();
                if (timeline.isEmpty()) {
                    enabled = false;
                    handler.removeCallbacks(tick);
                }
                complete.accept(subtitleUri);
                stateChanged.run();
                Toast.makeText(activity, R.string.captions_complete, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailed(String reason) {
                if (request != generation || activity.isFinishing() || activity.isDestroyed()) return;
                job = null;
                showRetry("Failed · Retry",uri,path,complete,stateChanged,force);
                if (timeline.isEmpty()) enabled = false;
                stateChanged.run();
                Toast.makeText(activity, reason, Toast.LENGTH_LONG).show();
            }
            @Override public void onCancelled() {
                if (request != generation || activity.isFinishing() || activity.isDestroyed()) return;
                job=null;
                showRetry("Cancelled · Retry",uri,path,complete,stateChanged,force);
                stateChanged.run();
            }
        });
        handler.post(tick);
        stateChanged.run();
    }
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ExoPlayer current = player.get();
            if (current != null) {
                long position = current.getCurrentPosition() + subtitleOffsetMs;
                if (job != null) job.updatePlaybackPosition(position);
                SubtitleTimeline.Cue cue = enabled ? timeline.at(position) : null;
                if (displayed != cue) {
                    displayed = cue;
                    subtitles.setCues(cue == null ? Collections.emptyList() : Collections.singletonList(
                            SubtitleAppearance.text(cue.text)));
                }
                SubtitleView nativeSubtitles = playerView.getSubtitleView();
                if (nativeSubtitles != null) nativeSubtitles.setVisibility((enabled && !timeline.isEmpty()) ? View.INVISIBLE : View.VISIBLE);
                if (job != null && !job.isCancelling() && enabled && controlsVisible && !inPip) {
                    boolean ready = false;
                    for (long[] range : completedRanges) if (position >= range[0] && position < range[1]) ready = true;
                    if(renderedPercent!=percent || renderedReady!=ready) {
                        renderedPercent=percent;renderedReady=ready;
                        status.setText(activity.getString(ready ? R.string.captions_progress_compact : R.string.captions_waiting_compact, percent));
                    }
                }
                updateIndicator();
            }
            handler.postDelayed(this, 100);
        }
    };
    public boolean isActive() {
        return job != null || !timeline.isEmpty();
    }
    public boolean isGenerating() {
        return job != null;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public boolean hasCues() {
        return !timeline.isEmpty();
    }
    public void toggle() {
        enabled = !enabled;
        if (!enabled) {
            subtitles.setCues(Collections.emptyList());
            SubtitleView nativeSubtitles = playerView.getSubtitleView();
            if (nativeSubtitles != null) nativeSubtitles.setVisibility(View.VISIBLE);
        }
        updateIndicator();
    }

    public void setSubtitleOffsetMs(long offsetMs) {
        this.subtitleOffsetMs = offsetMs;
    }

    public int getPercent() {
        return percent;
    }
    private void showRetry(String text,Uri uri,String path,Completion complete,Runnable changed,boolean force) {
        hasNotice=true; status.setText(text);
        status.setOnClickListener(v -> { if(job==null)start(uri,path,complete,changed,force); });
        updateIndicator();
    }
    public boolean isCancelling() { return job!=null && job.isCancelling(); }
    public void stopGeneration() {
        if(job==null || job.isCancelling())return;
        hasNotice=true;
        status.setText("Cancelling…"); status.setOnClickListener(null);
        job.cancel(); updateIndicator();onStateChanged.run();
    }
    public void resize(boolean pip) {
        inPip = pip;
        updateIndicator();
        SubtitleAppearance.apply(activity,subtitles,pip,controlsVisible);
    }
    public void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        updateIndicator();
        SubtitleAppearance.apply(activity,subtitles,inPip,controlsVisible);
    }
    private void updateIndicator() {
        boolean generating = job != null && enabled && !inPip;
        progress.setVisibility(generating ? View.VISIBLE : View.GONE);
        if (generating) {
            progress.setIndeterminate(percent == 0);
            progress.setProgress(percent);
        }
        status.setVisibility(!inPip && controlsVisible && (hasNotice || generating) ? View.VISIBLE : View.GONE);
    }
    public void clear() {
        generation++;
        if (job != null) job.cancel();
        job = null;
        handler.removeCallbacks(tick);
        timeline.clear();
        completedRanges.clear();
        enabled = false;
        percent = 0;
        renderedPercent=-1;
        hasNotice = false;
        displayed = null;
        subtitles.setCues(Collections.emptyList());
        status.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        SubtitleView nativeSubtitles = playerView.getSubtitleView();
        status.setOnClickListener(null);
        if (nativeSubtitles != null) nativeSubtitles.setVisibility(View.VISIBLE);
    }
}
