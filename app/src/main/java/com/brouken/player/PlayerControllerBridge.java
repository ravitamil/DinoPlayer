package com.brouken.player;

import android.media.audiofx.LoudnessEnhancer;
import androidx.media3.exoplayer.ExoPlayer;

public interface PlayerControllerBridge {
    int CONTROLLER_TIMEOUT = 5000;

    ExoPlayer getPlayer();
    boolean hasMedia();
    boolean isControllerVisible();
    boolean isRestoreControllerTimeout();
    void setRestoreControllerTimeout(boolean restore);
    boolean dismissSnackbar();
    BrightnessControl getBrightnessControl();
    LoudnessEnhancer getLoudnessEnhancer();
    int getBoostLevel();
    void setBoostLevel(int level);
    void setFocusPlay(boolean focusPlay);
}
