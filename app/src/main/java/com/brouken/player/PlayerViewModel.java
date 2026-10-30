package com.brouken.player;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.media3.ui.AspectRatioFrameLayout;

/**
 * ViewModel managing playback item state, playback speed, orientation lock,
 * subtitles, aspect ratio, and player UI controls state.
 */
public class PlayerViewModel extends ViewModel {

    public static final float[] PRESET_SPEEDS = new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    // 1. Playback Item
    private final MutableLiveData<Uri> mediaUri = new MutableLiveData<>(null);
    private final MutableLiveData<String> videoTitle = new MutableLiveData<>("");
    private final MutableLiveData<String> mediaType = new MutableLiveData<>(null);

    // 2. Playback Speed
    private final MutableLiveData<Float> playbackSpeed = new MutableLiveData<>(1.0f);

    // 3. Orientation Lock
    private final MutableLiveData<Boolean> isOrientationLocked = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> orientationMode = new MutableLiveData<>(0);

    // 4. Subtitles
    private final MutableLiveData<Boolean> subtitlesEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> subtitleTrack = new MutableLiveData<>(-1);
    private final MutableLiveData<Uri> subtitleUri = new MutableLiveData<>(null);

    // 5. Aspect Ratio
    private final MutableLiveData<Integer> resizeMode = new MutableLiveData<>(AspectRatioFrameLayout.RESIZE_MODE_FIT);

    // 6. Player State
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLocked = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isControllerVisible = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> showRemainingTime = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> boostLevel = new MutableLiveData<>(0);

    // --- Getters for LiveData ---

    @NonNull
    public LiveData<Uri> getMediaUri() {
        return mediaUri;
    }

    @NonNull
    public LiveData<String> getVideoTitle() {
        return videoTitle;
    }

    @NonNull
    public LiveData<String> getMediaType() {
        return mediaType;
    }

    @NonNull
    public LiveData<Float> getPlaybackSpeed() {
        return playbackSpeed;
    }

    @NonNull
    public LiveData<Boolean> isOrientationLocked() {
        return isOrientationLocked;
    }

    @NonNull
    public LiveData<Integer> getOrientationMode() {
        return orientationMode;
    }

    @NonNull
    public LiveData<Boolean> areSubtitlesEnabled() {
        return subtitlesEnabled;
    }

    @NonNull
    public LiveData<Integer> getSubtitleTrack() {
        return subtitleTrack;
    }

    @NonNull
    public LiveData<Uri> getSubtitleUri() {
        return subtitleUri;
    }

    @NonNull
    public LiveData<Integer> getResizeMode() {
        return resizeMode;
    }

    @NonNull
    public LiveData<Boolean> isPlaying() {
        return isPlaying;
    }

    @NonNull
    public LiveData<Boolean> isLocked() {
        return isLocked;
    }

    @NonNull
    public LiveData<Boolean> isControllerVisible() {
        return isControllerVisible;
    }

    @NonNull
    public LiveData<Boolean> getShowRemainingTime() {
        return showRemainingTime;
    }

    @NonNull
    public LiveData<Integer> getBoostLevel() {
        return boostLevel;
    }

    // --- Mutators & Actions ---

    public void setMediaItem(@Nullable Uri uri, @Nullable String title, @Nullable String type) {
        mediaUri.setValue(uri);
        videoTitle.setValue(title != null ? title : "");
        mediaType.setValue(type);
    }

    public void setPlaybackSpeed(float speed) {
        if (speed < 0.25f) speed = 0.25f;
        if (speed > 4.0f) speed = 4.0f;
        playbackSpeed.setValue(speed);
    }

    public float cyclePlaybackSpeed() {
        float current = playbackSpeed.getValue() != null ? playbackSpeed.getValue() : 1.0f;
        float next = PRESET_SPEEDS[0];
        boolean found = false;
        for (int i = 0; i < PRESET_SPEEDS.length; i++) {
            if (Math.abs(PRESET_SPEEDS[i] - current) < 0.05f) {
                next = PRESET_SPEEDS[(i + 1) % PRESET_SPEEDS.length];
                found = true;
                break;
            }
        }
        if (!found) {
            if (current >= PRESET_SPEEDS[PRESET_SPEEDS.length - 1] - 0.05f) {
                next = PRESET_SPEEDS[0];
            } else {
                for (float preset : PRESET_SPEEDS) {
                    if (preset > current + 0.05f) {
                        next = preset;
                        break;
                    }
                }
            }
        }
        setPlaybackSpeed(next);
        return next;
    }

    public void setOrientationLocked(boolean locked) {
        isOrientationLocked.setValue(locked);
    }

    public void setOrientationMode(int mode) {
        orientationMode.setValue(mode);
    }

    public boolean toggleOrientationLock() {
        boolean next = !Boolean.TRUE.equals(isOrientationLocked.getValue());
        setOrientationLocked(next);
        return next;
    }

    public void setSubtitlesEnabled(boolean enabled) {
        subtitlesEnabled.setValue(enabled);
    }

    public void setSubtitleTrack(int track) {
        subtitleTrack.setValue(track);
        subtitlesEnabled.setValue(track >= 0 || subtitleUri.getValue() != null);
    }

    public void setSubtitleUri(@Nullable Uri uri) {
        subtitleUri.setValue(uri);
        subtitlesEnabled.setValue(uri != null || (subtitleTrack.getValue() != null && subtitleTrack.getValue() >= 0));
    }

    public void setResizeMode(int mode) {
        resizeMode.setValue(mode);
    }

    public int toggleResizeMode() {
        int current = resizeMode.getValue() != null ? resizeMode.getValue() : AspectRatioFrameLayout.RESIZE_MODE_FIT;
        int next = (current == AspectRatioFrameLayout.RESIZE_MODE_FIT)
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FIT;
        setResizeMode(next);
        return next;
    }

    public void setIsPlaying(boolean playing) {
        isPlaying.setValue(playing);
    }

    public void setIsLocked(boolean locked) {
        isLocked.setValue(locked);
    }

    public boolean toggleLock() {
        boolean next = !Boolean.TRUE.equals(isLocked.getValue());
        setIsLocked(next);
        return next;
    }

    public void setControllerVisible(boolean visible) {
        isControllerVisible.setValue(visible);
    }

    public void setShowRemainingTime(boolean remaining) {
        showRemainingTime.setValue(remaining);
    }

    public boolean toggleShowRemainingTime() {
        boolean next = !Boolean.TRUE.equals(showRemainingTime.getValue());
        setShowRemainingTime(next);
        return next;
    }

    public void setBoostLevel(int level) {
        boostLevel.setValue(level);
    }
}
