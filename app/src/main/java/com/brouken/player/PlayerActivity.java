package com.brouken.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriPermission;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.LocaleList;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.CaptioningManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.WindowCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;

import com.brouken.player.dtpv.DoubleTapPlayerView;
import com.brouken.player.dtpv.youtube.YouTubeOverlay;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.media3.ui.TimeBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity implements PlayerControllerBridge {

    private PlaybackStateListener playbackStateListener;
    private BroadcastReceiver mReceiver;
    private AudioManager mAudioManager;
    private MediaSession mediaSession;
    private DefaultTrackSelector trackSelector;
    private LoudnessEnhancer loudnessEnhancer;

    private CustomStyledPlayerView playerView;
    private ExoPlayer player;

    private Object mPictureInPictureParamsBuilder;

    public Prefs mPrefs;
    private BrightnessControl mBrightnessControl;
    private boolean haveMedia;
    private boolean videoLoading;
    private boolean controllerVisible;
    private boolean controllerVisibleFully;
    private Snackbar snackbar;
    private PlaybackException errorToShow;
    private int boostLevel = 0;
    private boolean restoreOrientationLock;

    private final ActivityResultLauncher<Intent> videoChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (restoreOrientationLock) {
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                        restoreOrientationLock = false;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                handleChooserVideoResult(result.getResultCode(), result.getData());
            });

    private final ActivityResultLauncher<Intent> subtitleChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (restoreOrientationLock) {
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                        restoreOrientationLock = false;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                handleChooserSubtitleResult(result.getResultCode(), result.getData());
            });

    private final ActivityResultLauncher<Intent> scopeChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (restoreOrientationLock) {
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                        restoreOrientationLock = false;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                handleChooserScopeResult(result.getResultCode(), result.getData());
            });

    private final ActivityResultLauncher<Intent> subtitleEditorLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                handleSubtitleEditorResult(result.getResultCode(), result.getData());
            });

    private static final int REQUEST_CHOOSER_VIDEO = 1;
    private static final int REQUEST_CHOOSER_SUBTITLE = 2;
    private static final int REQUEST_CHOOSER_SCOPE_DIR = 10;
    public static final int CONTROLLER_TIMEOUT = 5000;
    private final Runnable watchCheckpoint = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                saveWatchHistory();
                playerView.postDelayed(this, 15000);
            }
        }
    };
    private static final String ACTION_MEDIA_CONTROL = "media_control";
    private static final String EXTRA_CONTROL_TYPE = "control_type";
    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int CONTROL_TYPE_PLAY = 1;
    private static final int CONTROL_TYPE_PAUSE = 2;

    private CoordinatorLayout coordinatorLayout;
    private TextView titleView;
    private ImageButton buttonOpen;
    private ImageButton buttonPiP;
    private ImageButton buttonAspectRatio;
    private TextView aspectRatioPill;
    private TextView buttonSpeed;
    private ImageButton buttonSubtitle;
    private ImageButton buttonCast;
    private ImageButton buttonLock;
    private ImageButton buttonUnlock;
    private ImageButton exoPrev;
    private ImageButton exoNext;
    private TextView durationView;
    private boolean mShowRemainingTime = false;
    private PlayerViewModel playerViewModel;
    private final Runnable mHideUnlockRunnable = () -> {
        if (buttonUnlock != null) {
            buttonUnlock.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> buttonUnlock.setVisibility(View.GONE)).start();
        }
    };

    private void showUnlockButton() {
        if (buttonUnlock == null) return;
        buttonUnlock.removeCallbacks(mHideUnlockRunnable);
        buttonUnlock.setVisibility(View.VISIBLE);
        buttonUnlock.animate().alpha(1f).setDuration(250).start();
        buttonUnlock.postDelayed(mHideUnlockRunnable, 3000);
    }
    private ImageButton exoPlayPause;
    private ProgressBar loadingProgressBar;
    private PlayerControlView controlView;

    private boolean restorePlayState;
    private boolean play;
    private final Player.Listener consistentSubtitleOutput = new Player.Listener() {
        @Override
        public void onCues(@NonNull CueGroup cueGroup) {
            SubtitleView view = playerView.getSubtitleView();
            if (view != null) view.setCues(SubtitleAppearance.normalize(cueGroup.cues));
        }
    };
    private boolean isScrubbing;
    private boolean scrubbingNoticeable;
    private long scrubbingStart;
    public boolean frameRendered;
    private boolean alive;
    private boolean focusPlay = false;
    private Uri nextUri;
    private boolean restoreControllerTimeout = false;
    private boolean shortControllerTimeout = false;
    private OrientationEventListener orientationEventListener;
    private String mVideoPath = null;
    private LiveSubtitleController liveSubtitles;
    private long mSubtitleOffsetMs = 0L;
    private boolean manualOrientation;

    final Rational rationalLimitWide = new Rational(239, 100);
    final Rational rationalLimitTall = new Rational(100, 239);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PlaylistManager.restore(this);
        // Rotate ASAP, before super/inflating to avoid glitches with activity launch animation
        mPrefs = new Prefs(this);
        mSubtitleOffsetMs = mPrefs.subtitleOffset;
        Utils.setOrientation(this, mPrefs.orientation);

        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN || player == null)
                    return;
                if (manualOrientation || mPrefs.orientation != Utils.Orientation.VIDEO)
                    return;
                if (Utils.isAutoRotateOn(PlayerActivity.this))
                    return;

                final Format format = player.getVideoFormat();
                if (format == null)
                    return;

                boolean isPortrait = Utils.isPortrait(format);
                if (!isPortrait) {
                    if (orientation >= 225 && orientation <= 315) {
                        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            if (playerViewModel != null) playerViewModel.setOrientationMode(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        }
                    } else if (orientation >= 45 && orientation <= 135) {
                        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                            if (playerViewModel != null) playerViewModel.setOrientationMode(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                        }
                    }
                } else {
                    if ((orientation >= 315 && orientation <= 360) || (orientation >= 0 && orientation <= 45)) {
                        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                            if (playerViewModel != null) playerViewModel.setOrientationMode(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        }
                    } else if (orientation >= 135 && orientation <= 225) {
                        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                            if (playerViewModel != null) playerViewModel.setOrientationMode(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                        }
                    }
                }
            }
        };

        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (playerView != null && playerView.isLocked) {
                    if (playerView.onLockedTouchCallback != null) {
                        playerView.onLockedTouchCallback.run();
                    }
                } else if (controllerVisible) {
                    playerView.hideController();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
        if (Build.VERSION.SDK_INT == 28 && Build.MANUFACTURER.toLowerCase().equals("xiaomi") && Build.DEVICE.toLowerCase().equals("oneday")) {
            setContentView(R.layout.activity_player_textureview);
        } else {
            setContentView(R.layout.activity_player);
        }

        if (getIntent().getData() != null) {
            mVideoPath = getIntent().getStringExtra("video_path");
            if (mVideoPath == null) {
                mVideoPath = SubtitleGenerator.resolveRealPath(this, getIntent().getData());
            }
            mPrefs.updateMedia(this, getIntent().getData(), getIntent().getType());
            searchSubtitles();
			focusPlay = true;
        }

        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        playerView = findViewById(R.id.video_view);
        playerView.setControllerBridge(this);
        liveSubtitles = new LiveSubtitleController(this, playerView, () -> player);
        exoPlayPause = findViewById(R.id.exo_play_pause);
        loadingProgressBar = findViewById(R.id.loading);

        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);
        playerViewModel.getPlaybackSpeed().observe(this, this::updateSpeedButtonText);
        playerViewModel.getResizeMode().observe(this, mode -> {
            playerView.setResizeMode(mode);
            updateAspectRatioPill(mode);
        });
        playerViewModel.isLocked().observe(this, locked -> {
            boolean isLocked = Boolean.TRUE.equals(locked);
            playerView.isLocked = isLocked;
            if (isLocked) {
                playerView.hideController();
                showUnlockButton();
            } else {
                if (buttonUnlock != null) {
                    buttonUnlock.removeCallbacks(mHideUnlockRunnable);
                    buttonUnlock.animate().alpha(0f).setDuration(200)
                            .withEndAction(() -> buttonUnlock.setVisibility(View.GONE)).start();
                }
            }
        });
        playerViewModel.getShowRemainingTime().observe(this, remaining -> {
            mShowRemainingTime = Boolean.TRUE.equals(remaining);
            updateDurationText();
        });
        playerViewModel.isOrientationLocked().observe(this, locked -> {
            manualOrientation = Boolean.TRUE.equals(locked);
        });
        playerViewModel.getOrientationMode().observe(this, mode -> {
            if (mode != null && mode != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED && getRequestedOrientation() != mode) {
                setRequestedOrientation(mode);
            }
        });

        playerView.setShowNextButton(true);
        playerView.setShowPreviousButton(true);
        playerView.setShowFastForwardButton(false);
        playerView.setShowRewindButton(false);

        playerView.setControllerHideOnTouch(false);
        playerView.setControllerAutoShow(true);

        ((DoubleTapPlayerView)playerView).setDoubleTapEnabled(false);

        // https://github.com/google/ExoPlayer/issues/5765
        CustomDefaultTimeBar timeBar = playerView.findViewById(R.id.exo_progress);
        timeBar.setBufferedColor(0x33FFFFFF);

        timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                if (player == null) {
                    return;
                }
                restorePlayState = player.isPlaying();
                if (restorePlayState) {
                    player.pause();
                }
                scrubbingNoticeable = false;
                isScrubbing = true;
                frameRendered = true;
                playerView.setControllerShowTimeoutMs(-1);
                scrubbingStart = player.getCurrentPosition();
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                reportScrubbing(position);
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                reportScrubbing(position);
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                playerView.setCustomErrorMessage(null);
                isScrubbing = false;
                if (restorePlayState) {
                    restorePlayState = false;
                    playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
                    player.setPlayWhenReady(true);
                }
            }
        });

        buttonOpen = findViewById(R.id.button_open);
        if (buttonOpen != null) {
            buttonOpen.setOnClickListener(view -> {
                returnToLibrary();
            });
            buttonOpen.setOnLongClickListener(view -> {
                openFile(mPrefs.mediaUri);
                return true;
            });
        }

        buttonPiP = findViewById(R.id.button_pip);
        if (isPiPSupported()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mPictureInPictureParamsBuilder = new PictureInPictureParams.Builder();
                updatePiPParams();
                updatePictureInPictureActions(R.drawable.ic_play_arrow_24dp, "Play", CONTROL_TYPE_PLAY, REQUEST_PLAY);
            }

            if (buttonPiP != null) {
                buttonPiP.setVisibility(View.VISIBLE);
                buttonPiP.setOnClickListener(view -> enterPiP());
                buttonPiP.setOnLongClickListener(v -> {
                    buttonPiP.performHapticFeedback(mPrefs.toggleAutoPiP() ?
                            HapticFeedbackConstants.VIRTUAL_KEY : HapticFeedbackConstants.LONG_PRESS);
                    resetHideCallbacks();
                    return true;
                });
                Utils.setButtonEnabled(this, buttonPiP, false);
            }
        } else if (buttonPiP != null) {
            buttonPiP.setVisibility(View.GONE);
        }

        aspectRatioPill = findViewById(R.id.aspect_ratio_pill);
        buttonAspectRatio = findViewById(R.id.button_aspect_ratio);
        View.OnClickListener aspectClickListener = view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            playerView.setScale(1.f);
            int nextMode;
            if (playerView.getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                playerView.setResizeMode(nextMode);
                Utils.showText(playerView, getString(R.string.video_resize_crop));
            } else {
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                playerView.setResizeMode(nextMode);
                Utils.showText(playerView, getString(R.string.video_resize_fit));
            }
            if (playerViewModel != null) playerViewModel.setResizeMode(nextMode);
            mPrefs.resizeMode = nextMode;
            updateAspectRatioPill(nextMode);
            resetHideCallbacks();
        };
        if (buttonAspectRatio != null) {
            buttonAspectRatio.setOnClickListener(aspectClickListener);
            Utils.setButtonEnabled(this, buttonAspectRatio, false);
        }
        if (aspectRatioPill != null) {
            aspectRatioPill.setOnClickListener(aspectClickListener);
        }

        buttonSpeed = findViewById(R.id.button_speed);
        if (buttonSpeed != null) {
            buttonSpeed.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (player != null && playerViewModel != null) {
                    float nextSpeed = playerViewModel.cyclePlaybackSpeed();
                    player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(nextSpeed));
                    updateSpeedButtonText(nextSpeed);
                    Utils.showText(playerView, String.format(Locale.ROOT, "Speed: %.2fx", nextSpeed).replace(".00x", ".0x"), 1000);
                }
            });
            buttonSpeed.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (player != null && playerViewModel != null) {
                    playerViewModel.setPlaybackSpeed(1.0f);
                    player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(1.0f));
                    updateSpeedButtonText(1.0f);
                    Utils.showText(playerView, "Speed: 1.0x", 1000);
                }
                return true;
            });
        }

        findViewById(R.id.button_cancel_captions).setOnClickListener(v -> { liveSubtitles.stopGeneration();updateCaptionCancel(); });
        buttonSubtitle = findViewById(R.id.button_subtitle);
        if (buttonSubtitle != null) {
            buttonSubtitle.setOnClickListener(view -> showSubtitleOptions());
            buttonSubtitle.setOnLongClickListener(view -> {
                if (getTrackCountSubtitle() > 0) {
                    showSubtitleTracksDialog();
                } else {
                    forceGenerateSubtitles();
                }
                return true;
            });
            updateSubtitleButtonState(mPrefs.subtitleTrack >= 0 || mPrefs.subtitleUri != null);
        }

        buttonCast = findViewById(R.id.button_cast);
        if (buttonCast != null) {
            buttonCast.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                String videoTitle = (titleView != null && titleView.getText() != null)
                        ? titleView.getText().toString()
                        : (mPrefs != null && mPrefs.mediaUri != null ? Utils.getFileName(this, mPrefs.mediaUri) : "Current Video");
                android.net.Uri videoUri = mPrefs != null ? mPrefs.mediaUri : null;
                CastBottomSheetDialogFragment castSheet = CastBottomSheetDialogFragment.newInstance(videoUri, videoTitle);
                castSheet.show(getSupportFragmentManager(), "CastBottomSheet");
            });
        }

        buttonLock = findViewById(R.id.button_lock);
        // Modern minimalist floating unlock pill with smooth fade animations and touch feedback
        buttonUnlock = new ImageButton(this);
        buttonUnlock.setImageResource(R.drawable.ic_lock_24dp);
        buttonUnlock.setColorFilter(Color.WHITE);
        buttonUnlock.setBackgroundResource(R.drawable.unlock_pill_background);
        buttonUnlock.setContentDescription(getString(R.string.unlock_screen));
        int pad = Utils.dpToPx(14);
        buttonUnlock.setPadding(pad, pad, pad, pad);
        FrameLayout.LayoutParams unlockParams = new FrameLayout.LayoutParams(Utils.dpToPx(52), Utils.dpToPx(52), Gravity.BOTTOM | Gravity.START);
        unlockParams.leftMargin = Utils.dpToPx(24);
        unlockParams.bottomMargin = Utils.dpToPx(36);
        playerView.getOverlayFrameLayout().addView(buttonUnlock, unlockParams);
        buttonUnlock.setVisibility(View.GONE);
        buttonUnlock.setAlpha(0f);
        if (buttonLock != null && buttonUnlock != null) {
            buttonLock.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                playerView.isLocked = true;
                if (playerViewModel != null) playerViewModel.setIsLocked(true);
                playerView.hideController();
                showUnlockButton();
                Utils.showText(playerView, "Screen Locked", 1200);
            });

            buttonUnlock.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                playerView.isLocked = false;
                if (playerViewModel != null) playerViewModel.setIsLocked(false);
                buttonUnlock.removeCallbacks(mHideUnlockRunnable);
                buttonUnlock.animate().alpha(0f).setDuration(200)
                        .withEndAction(() -> buttonUnlock.setVisibility(View.GONE)).start();
                playerView.showController();
                Utils.showText(playerView, "Screen Unlocked", 1200);
            });

            playerView.onLockedTouchCallback = () -> {
                if (buttonUnlock.getVisibility() == View.VISIBLE && buttonUnlock.getAlpha() > 0.5f) {
                    buttonUnlock.removeCallbacks(mHideUnlockRunnable);
                    buttonUnlock.animate().alpha(0f).setDuration(200)
                            .withEndAction(() -> buttonUnlock.setVisibility(View.GONE)).start();
                } else {
                    showUnlockButton();
                }
            };
        }

        exoPrev = findViewById(R.id.btn_prev);
        if (exoPrev == null) {
            exoPrev = findViewById(R.id.exo_prev);
        }
        if (exoPrev != null) {
            exoPrev.setOnClickListener(view -> skipToPrevious());
        }

        exoNext = findViewById(R.id.btn_next);
        if (exoNext == null) {
            exoNext = findViewById(R.id.exo_next);
        }
        if (exoNext != null) {
            exoNext.setOnClickListener(view -> skipToNext());
        }
        findViewById(R.id.button_back).setOnClickListener(v -> returnToLibrary());
        findViewById(R.id.button_minimal_play).setOnClickListener(v -> dispatchPlayPause());
        findViewById(R.id.button_rotate).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            manualOrientation = true;
            boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
            int target = portrait ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
            if (playerViewModel != null) {
                playerViewModel.setOrientationLocked(true);
                playerViewModel.setOrientationMode(target);
            } else {
                setRequestedOrientation(target);
            }
            playerView.showController();
        });

        durationView = findViewById(R.id.exo_duration);
        TextView positionView = findViewById(R.id.exo_position);
        if (durationView != null) {
            durationView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                boolean showRemaining = (playerViewModel != null)
                        ? playerViewModel.toggleShowRemainingTime()
                        : !mShowRemainingTime;
                mShowRemainingTime = showRemaining;
                updateDurationText();
            });

            durationView.addTextChangedListener(new TextWatcher() {
                private boolean mUpdating = false;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (mUpdating || !mShowRemainingTime || player == null) return;
                    long duration = player.getDuration();
                    if (duration <= 0) return;
                    long remaining = Math.max(0, duration - player.getCurrentPosition());
                    String remainingStr = "-" + Utils.formatMilis(remaining);
                    if (!remainingStr.equals(s.toString())) {
                        mUpdating = true;
                        durationView.setText(remainingStr);
                        mUpdating = false;
                    }
                }
            });
        }
        if (positionView != null) {
            positionView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (mShowRemainingTime) {
                        updateDurationText();
                    }
                }
            });
        }

        titleView = findViewById(R.id.player_title);
        titleView.setVisibility(View.GONE);

        titleView.setOnLongClickListener(view -> {
            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, mPrefs.mediaUri);
            if (mPrefs.mediaType == null)
                shareIntent.setType("video/*");
            else
                shareIntent.setType(mPrefs.mediaType);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(shareIntent);
            return true;
        });

        controlView = playerView.findViewById(R.id.exo_controller);
        if (controlView != null) {
            controlView.setAnimationEnabled(false);
            controlView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                if (windowInsets != null) {
                    view.setPadding(0, windowInsets.getSystemWindowInsetTop(),
                            0, windowInsets.getSystemWindowInsetBottom());

                    int insetLeft = windowInsets.getSystemWindowInsetLeft();
                    int insetRight = windowInsets.getSystemWindowInsetRight();

                    int paddingLeft = 0;
                    int marginLeft = insetLeft;

                    int paddingRight = 0;
                    int marginRight = insetRight;

                    if (Build.VERSION.SDK_INT >= 28 && windowInsets.getDisplayCutout() != null) {
                        if (windowInsets.getDisplayCutout().getSafeInsetLeft() == insetLeft) {
                            paddingLeft = insetLeft;
                            marginLeft = 0;
                        }
                        if (windowInsets.getDisplayCutout().getSafeInsetRight() == insetRight) {
                            paddingRight = insetRight;
                            marginRight = 0;
                        }
                    }

                    Utils.setViewParams(findViewById(R.id.player_top_bar), paddingLeft, 0, paddingRight, 0,
                            marginLeft, 0, marginRight, 0);

                    Utils.setViewParams(findViewById(R.id.exo_bottom_bar), paddingLeft, 0, paddingRight, 0,
                            marginLeft, 0, marginRight, 0);

                    windowInsets.consumeSystemWindowInsets();
                }
                return windowInsets;
            });

            try {
                CustomDefaultTrackNameProvider customDefaultTrackNameProvider = new CustomDefaultTrackNameProvider(getResources());
                final Field field = PlayerControlView.class.getDeclaredField("trackNameProvider");
                field.setAccessible(true);
                field.set(controlView, customDefaultTrackNameProvider);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        exoPlayPause.setOnClickListener(view -> dispatchPlayPause());

        // Prevent double tap actions in controller
        findViewById(R.id.exo_bottom_bar).setOnTouchListener((v, event) -> true);

        playbackStateListener = new PlaybackStateListener();

        mBrightnessControl = new BrightnessControl(this);
        if (mPrefs.brightness >= 0) {
            mBrightnessControl.currentBrightnessLevel = mPrefs.brightness;
            mBrightnessControl.setScreenBrightness(mBrightnessControl.levelToBrightness(mBrightnessControl.currentBrightnessLevel));
        }

        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                controllerVisible = visibility == View.VISIBLE;
                controllerVisibleFully = playerView.isControllerFullyVisible();
                if (playerViewModel != null) {
                    playerViewModel.setControllerVisible(controllerVisible);
                }
                if (controllerVisible) {
                    updatePlaylistButtons();
                }
                liveSubtitles.setControlsVisible(controllerVisible);
                SubtitleView captions = playerView.getSubtitleView();
                SubtitleAppearance.apply(PlayerActivity.this,captions,isInPip(),controllerVisible);

                if (restoreControllerTimeout) {
                    restoreControllerTimeout = false;
                    if (player == null || !player.isPlaying()) {
                        playerView.setControllerShowTimeoutMs(-1);
                    } else {
                        playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
                    }
                }


                // https://developer.android.com/training/system-ui/immersive
                if (visibility == View.VISIBLE) {
                    Utils.showSystemUi(playerView);
                    // Because when using dpad controls, focus resets to first item in bottom controls bar
                    findViewById(R.id.exo_play_pause).requestFocus();
                } else {
                    Utils.hideSystemUi(playerView);
                }

                if (controllerVisible && playerView.isControllerFullyVisible()) {
                    if (mPrefs.firstRun && !haveMedia) {
                        TapTargetView.showFor(PlayerActivity.this,
                                TapTarget.forView(buttonOpen, getString(R.string.onboarding_open_title), getString(R.string.onboarding_open_description))
                                        .outerCircleColor(R.color.green)
                                        .targetCircleColor(R.color.white)
                                        .titleTextSize(22)
                                        .titleTextColor(R.color.white)
                                        .descriptionTextSize(14)
                                        .cancelable(true),
                                new TapTargetView.Listener() {
                                    @Override
                                    public void onTargetClick(TapTargetView view) {
                                        super.onTargetClick(view);
                                        buttonOpen.performClick();
                                    }
                                });
                        // TODO: Explain gestures?
                        //  "Use vertical and horizontal gestures to change brightness, volume and seek in video"
                        mPrefs.markFirstRun();
                    }
                    if (errorToShow != null) {
                        showError(errorToShow);
                        errorToShow = null;
                    }
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        CaptionAutoJobs.playerActive(this,true);
        mPrefs = new Prefs(this);
        alive = true;
        if (orientationEventListener != null && orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
        updateSubtitleStyle();
        if (player == null) initializePlayer();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        saveWatchHistory();
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (snackbar != null) {
            if (snackbar.isShown()) {
                snackbar.dismiss();
            }
            snackbar = null;
        }
        if(!isInPip())CaptionAutoJobs.playerActive(this,false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) {
            return;
        }
        alive = false;
        if (orientationEventListener != null) {
            orientationEventListener.disable();
        }
        releasePlayer();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent != null && intent.getData() != null) {
            releasePlayer();
            setIntent(intent);
            mVideoPath = intent.getStringExtra("video_path");
            mPrefs.updateMedia(this, intent.getData(), intent.getType());
            searchSubtitles();
			focusPlay = true;
            initializePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        if (playerView != null) {
            playerView.setControllerBridge(null);
        }
        CaptionAutoJobs.playerActive(this,false);
        if (liveSubtitles != null) { liveSubtitles.clear();updateCaptionCancel(); }
        if (buttonUnlock != null) buttonUnlock.removeCallbacks(mHideUnlockRunnable);
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (isFinishing() && player != null && playerView.getPlayer() == player) releasePlayer();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                playerView.removeCallbacks(playerView.textClearRunnable);
                Utils.adjustVolume(mAudioManager, playerView, keyCode == KeyEvent.KEYCODE_VOLUME_UP, event.getRepeatCount() == 0);
                return true;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (!controllerVisibleFully) {
                    if (player != null) {
                        if (player.isPlaying()) {
                            player.pause();
                        } else {
                            player.play();
                        }
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (!controllerVisibleFully) {
                    if (player != null) {
                        playerView.removeCallbacks(playerView.textClearRunnable);
                        long seekTo = player.getCurrentPosition() - 10_000;
                        if (seekTo < 0)
                            seekTo = 0;
                        player.setSeekParameters(SeekParameters.PREVIOUS_SYNC);
                        player.seekTo(seekTo);
                        playerView.setCustomErrorMessage(Utils.formatMilis(seekTo));
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (!controllerVisibleFully) {
                    if (player != null) {
                        playerView.removeCallbacks(playerView.textClearRunnable);
                        long seekTo = player.getCurrentPosition() + 10_000;
                        long seekMax = player.getDuration();
                        if (seekMax != C.TIME_UNSET && seekTo > seekMax)
                            seekTo = seekMax;
                        player.setSeekParameters(SeekParameters.NEXT_SYNC);
                        player.seekTo(seekTo);
                        playerView.setCustomErrorMessage(Utils.formatMilis(seekTo));
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (Utils.isTvBox(this)) {
                    if (controllerVisible && player != null && player.isPlaying()) {
                        playerView.hideController();
                        return true;
                    }
                }
                break;
            default:
                if (!controllerVisibleFully) {
                    playerView.showController();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                playerView.postDelayed(playerView.textClearRunnable, CustomStyledPlayerView.MESSAGE_TIMEOUT_KEY);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
                playerView.postDelayed(playerView.textClearRunnable, CustomStyledPlayerView.MESSAGE_TIMEOUT_KEY);
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        if (isInPictureInPictureMode) {
            liveSubtitles.resize(true);
            setSubtitleTextSizePiP();
            playerView.setScale(1.f);
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction()) || player == null) {
                        return;
                    }

                    switch (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                        case CONTROL_TYPE_PLAY:
                            player.play();
                            break;
                        case CONTROL_TYPE_PAUSE:
                            player.pause();
                            break;
                    }
                }
            };
            ContextCompat.registerReceiver(this, mReceiver, new IntentFilter(ACTION_MEDIA_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            liveSubtitles.resize(false);
            setSubtitleTextSize();
            if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                playerView.setScale(mPrefs.scale);
            }
            if (mReceiver != null) {
                unregisterReceiver(mReceiver);
                mReceiver = null;
            }

        }
    }

    private void handleChooserVideoResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && alive) {
            releasePlayer();
        }

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            boolean uriAlreadyTaken = false;

            // https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html
            final ContentResolver contentResolver = getContentResolver();
            for (UriPermission persistedUri : contentResolver.getPersistedUriPermissions()) {
                if (persistedUri.getUri().equals(mPrefs.scopeUri)
                        || persistedUri.getUri().getPathSegments().contains("tree")) {
                    continue;
                } else if (persistedUri.getUri().equals(uri)) {
                    uriAlreadyTaken = true;
                } else {
                    try {
                        contentResolver.releasePersistableUriPermission(persistedUri.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (!uriAlreadyTaken) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }

            mPrefs.updateMedia(this, uri, data.getType());
            searchSubtitles();
        }

        if (resultCode == RESULT_OK && alive) {
            initializePlayer();
        }
    }

    private void handleChooserSubtitleResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && alive) {
            releasePlayer();
        }

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                e.printStackTrace();
            }

            // Convert subtitles to UTF-8 if necessary
            SubtitleUtils.clearCache(this);
            uri = SubtitleUtils.convertToUTF(this, uri);

            mPrefs.updateSubtitle(uri);
        }

        if (resultCode == RESULT_OK && alive) {
            initializePlayer();
        }
    }

    private void handleChooserScopeResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && alive) {
            releasePlayer();
        }

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                mPrefs.updateScope(uri);
                mPrefs.markScopeAsked();
                searchSubtitles();
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }

        if (resultCode == RESULT_OK && alive) {
            initializePlayer();
        }
    }

    private void handleSubtitleEditorResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            liveSubtitles.clear();
            mPrefs.updateSubtitle(data.getData());
            if (player != null) applySubtitle(data.getData());
        }
    }


    public void initializePlayer() {
        if (SubtitleGenerator.isObsoleteCache(mPrefs.subtitleUri)) mPrefs.updateSubtitle(null);
        haveMedia = mPrefs.mediaUri != null && (Utils.fileExists(this, mPrefs.mediaUri) || Utils.isSupportedNetworkUri(mPrefs.mediaUri));
        if (haveMedia && mPrefs.mediaUri != null) {
            String path = SubtitleGenerator.resolveRealPath(this, mPrefs.mediaUri);
            mVideoPath = path;
            if (SubtitleGenerator.isGeneratedCache(mPrefs.subtitleUri)
                    && (!new File(mPrefs.subtitleUri.getPath()).isFile()
                    || !Uri.fromFile(SubtitleGenerator.getCachedSubtitleFile(this, mPrefs.mediaUri, path)).equals(mPrefs.subtitleUri))) {
                mPrefs.updateSubtitle(null);
            }
            if(CaptionLibrary.isSaved(this,mPrefs.subtitleUri)
                    && (!new File(mPrefs.subtitleUri.getPath()).isFile()
                    || !Uri.fromFile(CaptionLibrary.saved(this,mPrefs.mediaUri,CaptionPackages.get(this).selected())).equals(mPrefs.subtitleUri)))mPrefs.updateSubtitle(null);
            if (mPrefs.subtitleUri == null) {
                File cached = CaptionLibrary.find(this, mPrefs.mediaUri, mVideoPath);
                if (cached != null && cached.isFile() && cached.length() > 0) mPrefs.updateSubtitle(Uri.fromFile(cached));
            }
        }

        if (player == null) {
            trackSelector = new DefaultTrackSelector(this);
            if (Build.VERSION.SDK_INT >= 24) {
                final LocaleList localeList = Resources.getSystem().getConfiguration().getLocales();
                final List<String> locales = new ArrayList<>();
                for (int i = 0; i < localeList.size(); i++) {
                    locales.add(localeList.get(i).getISO3Language());
                }
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredAudioLanguages(locales.toArray(new String[0]))
                );
            } else {
                final Locale locale = Resources.getSystem().getConfiguration().locale;
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredAudioLanguage(locale.getISO3Language())
                );
            }
            RenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
            // https://github.com/google/ExoPlayer/issues/8571
            final DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                    .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE);
            player = new ExoPlayer.Builder(this, renderersFactory)
                    .setTrackSelector(trackSelector)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(this, extractorsFactory))
                    .build();
            final AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build();
            player.setAudioAttributes(audioAttributes, true);

            final YouTubeOverlay youTubeOverlay = findViewById(R.id.youtube_overlay);

            youTubeOverlay.performListener(new YouTubeOverlay.PerformListener() {
                @Override
                public void onAnimationStart() {
                    youTubeOverlay.setAlpha(1.0f);
                    youTubeOverlay.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd() {
                    youTubeOverlay.animate()
                            .alpha(0.0f)
                            .setDuration(300)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    youTubeOverlay.setVisibility(View.GONE);
                                    youTubeOverlay.setAlpha(1.0f);
                                }
                            });
                }
            });

            youTubeOverlay.player(player);
        }

        player.removeListener(consistentSubtitleOutput);
        playerView.setPlayer(player);
        // PlayerView's output is registered first; normalize the same frame before drawing.
        player.addListener(consistentSubtitleOutput);
        if (playerView.getSubtitleView() != null) playerView.getSubtitleView().setCues(SubtitleAppearance.normalize(player.getCurrentCues().cues));

        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        final Intent sessionIntent = new Intent(this, PlayerActivity.class);
        final PendingIntent sessionActivity = PendingIntent.getActivity(this, 0, sessionIntent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build();

        playerView.setControllerShowTimeoutMs(-1);

        if (haveMedia) {
            playerView.setResizeMode(mPrefs.resizeMode);
            if (playerViewModel != null) {
                playerViewModel.setResizeMode(mPrefs.resizeMode);
            }
            updateAspectRatioPill(mPrefs.resizeMode);
            if (aspectRatioPill != null) {
                aspectRatioPill.setVisibility(View.VISIBLE);
            }

            if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                playerView.setScale(mPrefs.scale);
            } else {
                playerView.setScale(1.f);
            }

            final String mediaTitle = Utils.getFileName(this, mPrefs.mediaUri);
            MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                    .setTitle(mediaTitle)
                    .setDisplayTitle(mediaTitle)
                    .build();

            if (playerViewModel != null) {
                playerViewModel.setMediaItem(mPrefs.mediaUri, mediaTitle, mPrefs.mediaType);
                if (mPrefs.subtitleUri != null) {
                    playerViewModel.setSubtitleUri(mPrefs.subtitleUri);
                }
                playerViewModel.setSubtitleTrack(mPrefs.subtitleTrack);
            }

            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                    .setUri(mPrefs.mediaUri)
                    .setMimeType(mPrefs.mediaType)
                    .setMediaMetadata(mediaMetadata);
            if (mPrefs.subtitleUri != null && Utils.fileExists(this, mPrefs.subtitleUri)) {
                final String subtitleMime = SubtitleUtils.getSubtitleMime(mPrefs.subtitleUri);
                final String subtitleLanguage = SubtitleUtils.getSubtitleLanguage(mPrefs.subtitleUri);
                String subtitleName = null;
                if (subtitleLanguage == null)
                    subtitleName = Utils.getFileName(this, mPrefs.subtitleUri);

                MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(mPrefs.subtitleUri)
                        .setMimeType(subtitleMime)
                        .setLanguage(subtitleLanguage)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                        .setLabel(subtitleName)
                        .build();
                mediaItemBuilder.setSubtitleConfigurations(Collections.singletonList(subtitle));
            }
            player.setMediaItem(mediaItemBuilder.build());

            if (loudnessEnhancer != null) {
                loudnessEnhancer.release();
            }
            try {
                int audioSessionId = mAudioManager != null ? mAudioManager.generateAudioSessionId() : C.AUDIO_SESSION_ID_UNSET;
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                    player.setAudioSessionId(audioSessionId);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }

            player.addListener(new Player.Listener() {
                @Override
                public void onAudioSessionIdChanged(int audioSessionId) {
                    if (loudnessEnhancer != null) {
                        loudnessEnhancer.release();
                    }
                    try {
                        loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    if (!manualOrientation && mPrefs.orientation == Utils.Orientation.VIDEO) {
                        Utils.applyOrientation(PlayerActivity.this, Utils.isPortrait(videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees));
                    }
                }
            });

            videoLoading = true;

            updateLoading(true);

            play = true;

            player.seekTo(mPrefs.getPosition());

            titleView.setText(Utils.getFileName(this, mPrefs.mediaUri));
            titleView.setVisibility(View.VISIBLE);

            if (buttonPiP != null)
                Utils.setButtonEnabled(this, buttonPiP, true);

            Utils.setButtonEnabled(this, buttonAspectRatio, true);

            ((DoubleTapPlayerView)playerView).setDoubleTapEnabled(true);

            nextUri = findNext();
            updatePlaylistButtons();

            player.setHandleAudioBecomingNoisy(true);
        } else {
            playerView.showController();
        }

        player.addListener(playbackStateListener);
        player.prepare();

        if (restorePlayState || play) {
            restorePlayState = false;
            player.play();
        }
    }

    public void releasePlayer() {
        playerView.removeCallbacks(watchCheckpoint);
        playerView.cancelPendingGestures();
        int selectedSubtitle = liveSubtitles != null && liveSubtitles.isActive()
                ? (liveSubtitles.isEnabled() ? 0 : Integer.MIN_VALUE) : getSelectedTrackSubtitle();
        if (liveSubtitles != null) { liveSubtitles.clear();updateCaptionCancel(); }
        playerView.removeCallbacks(mHideUnlockRunnable);
        if (buttonUnlock != null) buttonUnlock.removeCallbacks(mHideUnlockRunnable);
        playerView.isLocked = false;
        if (buttonUnlock != null) buttonUnlock.setVisibility(View.GONE);
        if (player != null) {
            if (mediaSession != null) {
                mediaSession.release();
                mediaSession = null;
            }

            mPrefs.updateBrightness(mBrightnessControl.currentBrightnessLevel);
            mPrefs.updateOrientation();

            if (haveMedia) {
                saveWatchHistory();
                // Prevent overwriting temporarily inaccessible media position
                if (player.isCurrentMediaItemSeekable()) {
                    mPrefs.updatePosition(player.getCurrentPosition());
                }
                mPrefs.updateMeta(getSelectedTrackAudio(false), getSelectedTrackAudio(true), selectedSubtitle, playerView.getResizeMode(), playerView.getVideoSurfaceView().getScaleX());
            }

            if (player.isPlaying()) {
                restorePlayState = true;
            }
            player.removeListener(consistentSubtitleOutput);
            player.removeListener(playbackStateListener);
            player.clearMediaItems();
            player.release();
            player = null;
            if (loudnessEnhancer != null) {
                loudnessEnhancer.release();
                loudnessEnhancer = null;
            }
        }
        titleView.setVisibility(View.GONE);
        if (buttonPiP != null)
            Utils.setButtonEnabled(this, buttonPiP, false);
        Utils.setButtonEnabled(this, buttonAspectRatio, false);
        if (aspectRatioPill != null) {
            aspectRatioPill.setVisibility(View.GONE);
        }
    }

    private void saveWatchHistory() {
        if (player != null && haveMedia && mPrefs.mediaUri != null) {
            new WatchHistory(this).record(mPrefs.mediaUri, player.getCurrentPosition(),
                    player.getDuration(), player.getPlaybackState() == Player.STATE_ENDED);
        }
    }

    private class PlaybackStateListener implements Player.Listener {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            playerView.removeCallbacks(watchCheckpoint);
            if (isPlaying) playerView.postDelayed(watchCheckpoint, 15000);
            if (!isPlaying) saveWatchHistory();
            playerView.setKeepScreenOn(isPlaying);
            if (playerViewModel != null) {
                playerViewModel.setIsPlaying(isPlaying);
            }
            ((ImageButton) findViewById(R.id.button_minimal_play)).setImageResource(
                    isPlaying ? R.drawable.ic_pause_24dp : R.drawable.ic_play_arrow_24dp);

            if (isPiPSupported()) {
                if (isPlaying) {
                    updatePictureInPictureActions(R.drawable.ic_pause_24dp, "Pause", CONTROL_TYPE_PAUSE, REQUEST_PAUSE);
                } else {
                    updatePictureInPictureActions(R.drawable.ic_play_arrow_24dp, "Play", CONTROL_TYPE_PLAY, REQUEST_PLAY);
                }
                updatePiPParams();
            }

            if (!isScrubbing) {
                if (isPlaying) {
                    shortControllerTimeout = false;
                    restoreControllerTimeout = false;
                    playerView.setControllerShowTimeoutMs(CONTROLLER_TIMEOUT);
                } else {
                    playerView.setControllerShowTimeoutMs(-1);
                }
            }
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            boolean isNearEnd = false;
            final long duration = player.getDuration();
            if (duration != C.TIME_UNSET) {
                final long position = player.getCurrentPosition();
                if (position + 4000 >= duration) {
                    isNearEnd = true;
                }
            }
            setEndControlsVisible(haveMedia && (state == Player.STATE_ENDED || isNearEnd));

            if (state == Player.STATE_ENDED) {
                saveWatchHistory();
                if (mPrefs.autoPlayNext && nextUri != null) {
                    playerView.post(PlayerActivity.this::skipToNext);
                    return;
                }
            }

            if (state == Player.STATE_READY) {
                frameRendered = true;

                if (videoLoading) {
                    videoLoading = false;

                    final Format format = player.getVideoFormat();
                    float frameRateExo = Format.NO_VALUE;

                    if (format != null) {
                        if (!manualOrientation && mPrefs.orientation == Utils.Orientation.VIDEO) {
                            Utils.applyOrientation(PlayerActivity.this, Utils.isPortrait(format));
                        }
                        frameRateExo = format.frameRate;
                    }
                    updatePiPParams();

                    boolean switched = Utils.switchFrameRate(PlayerActivity.this, frameRateExo, mPrefs.mediaUri);
                    if (play) {
                        play = false;
                        if (!switched) {
                            player.play();
                            playerView.showController();
                        }
                    }

                    updateLoading(false);

                    if (mPrefs.audioTrack != -1 && mPrefs.audioTrackFfmpeg != -1) {
                        setSelectedTrackAudio(mPrefs.audioTrack, false);
                        setSelectedTrackAudio(mPrefs.audioTrackFfmpeg, true);
                    }
                    if (mPrefs.subtitleTrack != -1 && (mPrefs.subtitleTrack < getTrackCountSubtitle() || mPrefs.subtitleTrack == Integer.MIN_VALUE)) {
                        setSelectedTrackSubtitle(mPrefs.subtitleTrack);
                    } else if (mPrefs.subtitleTrack == -1 && getTrackCountSubtitle() > 0) {
                        setSelectedTrackSubtitle(0);
                    } else if (getTrackCountSubtitle() == 0 && mPrefs.subtitleUri == null) {
                        triggerAutoSubtitleGeneration();
                    }
                    updateSubtitleButtonState(getSelectedTrackSubtitle() >= 0 || mPrefs.subtitleUri != null);
                    updatePlaylistButtons();
                }
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            updateLoading(false);
            if (liveSubtitles != null) { liveSubtitles.clear();updateCaptionCancel(); }
            errorToShow = null;
            playerView.setControllerShowTimeoutMs(0);
            playerView.showController();
            showError(error);
        }
    }

    private void enableRotation() {
        try {
            if (Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 0) {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
                restoreOrientationLock = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openFile(Uri pickerInitialUri) {
        if (Utils.isTvBox(this)) {
            Utils.alternativeChooser(this, pickerInitialUri, true);
        } else {
            enableRotation();

            final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT, pickerInitialUri);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");

            safelyStartActivityForResult(intent, REQUEST_CHOOSER_VIDEO);
        }
    }

    private void loadSubtitleFile(Uri pickerInitialUri) {
        Toast.makeText(PlayerActivity.this, R.string.open_subtitles, Toast.LENGTH_SHORT).show();

        if (Utils.isTvBox(this)) {
            Utils.alternativeChooser(this, pickerInitialUri, false);
        } else {
            enableRotation();

            final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT, pickerInitialUri);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            final String[] supportedMimeTypes = {
                    MimeTypes.APPLICATION_SUBRIP,
                    MimeTypes.TEXT_SSA,
                    MimeTypes.TEXT_VTT,
                    MimeTypes.APPLICATION_TTML,
                    "text/*",
                    "application/octet-stream"
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes);

            safelyStartActivityForResult(intent, REQUEST_CHOOSER_SUBTITLE);
        }
    }

    private void requestDirectoryAccess() {
        enableRotation();

        Uri initialUri = null;

        if (Build.VERSION.SDK_INT >= 26) {
            final String authority = "com.android.externalstorage.documents";
            final String documentId = "primary:" + Environment.DIRECTORY_MOVIES;
            initialUri = DocumentsContract.buildDocumentUri(authority, documentId);
        }

        final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT_TREE, initialUri);

        safelyStartActivityForResult(intent, REQUEST_CHOOSER_SCOPE_DIR);
    }

    private Intent createBaseFileIntent(final String action, final Uri initialUri) {
        final Intent intent = new Intent(action);

        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        if (Build.VERSION.SDK_INT >= 26 && initialUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }

        return intent;
    }

    void safelyStartActivityForResult(final Intent intent, final int code) {
        if (intent.resolveActivity(getPackageManager()) == null) {
            showSnack(getText(R.string.error_files_missing).toString(), intent.toString());
            return;
        }
        switch (code) {
            case REQUEST_CHOOSER_VIDEO:
                videoChooserLauncher.launch(intent);
                break;
            case REQUEST_CHOOSER_SUBTITLE:
                subtitleChooserLauncher.launch(intent);
                break;
            case REQUEST_CHOOSER_SCOPE_DIR:
                scopeChooserLauncher.launch(intent);
                break;
            default:
                Log.w("PlayerActivity", "Unhandled request code in safelyStartActivityForResult: " + code);
                break;
        }
    }

    public void setSelectedTrackSubtitle(final int trackIndex) {
        final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            final DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.buildUponParameters();
            for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); rendererIndex++) {
                if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_TEXT) {
                    if (trackIndex == Integer.MIN_VALUE) {
                        parametersBuilder.setRendererDisabled(rendererIndex, true);
                    } else {
                        parametersBuilder.setRendererDisabled(rendererIndex, false);
                        if (trackIndex == -1) {
                            parametersBuilder.clearSelectionOverrides(rendererIndex);
                        } else {
                            final int [] tracks = {0};
                            final DefaultTrackSelector.SelectionOverride selectionOverride = new DefaultTrackSelector.SelectionOverride(trackIndex, tracks);
                            parametersBuilder.setSelectionOverride(rendererIndex, mappedTrackInfo.getTrackGroups(rendererIndex), selectionOverride);
                        }
                    }
                }
            }
            trackSelector.setParameters(parametersBuilder);
        }
        if (playerViewModel != null) {
            playerViewModel.setSubtitleTrack(trackIndex);
        }
    }

    public int getSelectedTrackSubtitle() {
        if (trackSelector != null) {
            final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
            if (mappedTrackInfo != null) {
                for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); rendererIndex++) {
                    if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_TEXT) {
                        final TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
                        final DefaultTrackSelector.SelectionOverride selectionOverride = trackSelector.getParameters().getSelectionOverride(rendererIndex, trackGroups);
                        DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
                        if (parameters.getRendererDisabled(rendererIndex)) {
                            return Integer.MIN_VALUE;
                        }
                        if (selectionOverride == null) {
                            return -1;
                        }
                        return selectionOverride.groupIndex;
                    }
                }
            }
        }
        return -1;
    }

    public int getSelectedTrackAudio(final boolean ffmpeg) {
        if (trackSelector != null) {
            final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
            if (mappedTrackInfo != null) {
                for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); rendererIndex++) {
                    if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_AUDIO) {
                        final String rendererName = mappedTrackInfo.getRendererName(rendererIndex);
                        if ((rendererName.toLowerCase().contains("ffmpeg") && !ffmpeg) ||
                                (!rendererName.toLowerCase().contains("ffmpeg") && ffmpeg))
                            continue;
                        final TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
                        final DefaultTrackSelector.SelectionOverride selectionOverride = trackSelector.getParameters().getSelectionOverride(rendererIndex, trackGroups);
                        DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
                        if (parameters.getRendererDisabled(rendererIndex)) {
                            return Integer.MIN_VALUE;
                        }
                        if (selectionOverride == null) {
                            return -1;
                        }
                        return selectionOverride.groupIndex;
                    }
                }
            }
        }
        return -1;
    }

    public void setSelectedTrackAudio(final int trackIndex, final boolean ffmpeg) {
        final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            final DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.buildUponParameters();
            for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); rendererIndex++) {
                if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_AUDIO) {
                    final String rendererName = mappedTrackInfo.getRendererName(rendererIndex);
                    if ((rendererName.toLowerCase().contains("ffmpeg") && !ffmpeg) ||
                            (!rendererName.toLowerCase().contains("ffmpeg") && ffmpeg))
                        continue;
                    if (trackIndex == Integer.MIN_VALUE) {
                        parametersBuilder.setRendererDisabled(rendererIndex, true);
                    } else {
                        parametersBuilder.setRendererDisabled(rendererIndex, false);
                        if (trackIndex == -1) {
                            parametersBuilder.clearSelectionOverrides(rendererIndex);
                        } else {
                            final int [] tracks = {0};
                            final DefaultTrackSelector.SelectionOverride selectionOverride = new DefaultTrackSelector.SelectionOverride(trackIndex, tracks);
                            parametersBuilder.setSelectionOverride(rendererIndex, mappedTrackInfo.getTrackGroups(rendererIndex), selectionOverride);
                        }
                    }
                }
            }
            trackSelector.setParameters(parametersBuilder);
        }
    }

    public int getTrackCountSubtitle() {
        if (trackSelector != null) {
            final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
            if (mappedTrackInfo != null) {
                for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); rendererIndex++) {
                    if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_TEXT) {
                        final TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
                        return trackGroups.length;
                    }
                }
            }
        }
        return 0;
    }

    void setSubtitleTextSize() {
        setSubtitleTextSize(getResources().getConfiguration().orientation);
    }

    void setSubtitleTextSize(final int orientation) {
        SubtitleAppearance.apply(this,playerView.getSubtitleView(),false,controllerVisible);
    }

    void setSubtitleTextSizePiP() {
        SubtitleAppearance.apply(this,playerView.getSubtitleView(),true,controllerVisible);
    }

    boolean isPiPSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    @TargetApi(26)
    void updatePictureInPictureActions(final int iconId, final String title, final int controlType, final int requestCode) {
        final ArrayList<RemoteAction> actions = new ArrayList<>();
        final Intent controlIntent = new Intent(ACTION_MEDIA_CONTROL)
                .setPackage(getPackageName())
                .putExtra(EXTRA_CONTROL_TYPE, controlType);
        final PendingIntent intent = PendingIntent.getBroadcast(PlayerActivity.this, requestCode,
                controlIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        final Icon icon = Icon.createWithResource(PlayerActivity.this, iconId);
        actions.add(new RemoteAction(icon, title, title, intent));
        ((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).setActions(actions);
        setPictureInPictureParams(((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).build());
    }

    private boolean isInPip() {
        if (!isPiPSupported())
            return false;
        return isInPictureInPictureMode();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        SubtitleAppearance.apply(this,playerView.getSubtitleView(),isInPip(),controllerVisible);
        liveSubtitles.resize(isInPip());
        if (controllerVisible && !isInPip() && !playerView.isLocked) playerView.showController();
    }

    void showError(PlaybackException error) {
        final String errorGeneral = error.getLocalizedMessage();
        String errorDetailed = errorGeneral;

        if (error instanceof ExoPlaybackException) {
            ExoPlaybackException exoError = (ExoPlaybackException) error;
            switch (exoError.type) {
                case ExoPlaybackException.TYPE_SOURCE:
                    if (exoError.getSourceException() != null)
                        errorDetailed = exoError.getSourceException().getLocalizedMessage();
                    break;
                case ExoPlaybackException.TYPE_RENDERER:
                    if (exoError.getRendererException() != null)
                        errorDetailed = exoError.getRendererException().getLocalizedMessage();
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    if (exoError.getUnexpectedException() != null)
                        errorDetailed = exoError.getUnexpectedException().getLocalizedMessage();
                    break;
                case ExoPlaybackException.TYPE_REMOTE:
                default:
                    errorDetailed = errorGeneral;
                    break;
            }
        }

        showSnack(errorGeneral, errorDetailed);
    }

    void showSnack(final String textPrimary, final String textSecondary) {
        snackbar = Snackbar.make(coordinatorLayout, textPrimary, Snackbar.LENGTH_LONG);
        if (textSecondary != null) {
            snackbar.setAction(R.string.error_details, v -> {
                final AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(PlayerActivity.this);
                builder.setMessage(textSecondary);
                builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog dialog = builder.create();
                dialog.show();
            });
        }
        snackbar.setAnchorView(R.id.exo_bottom_bar);
        snackbar.show();
    }

    void reportScrubbing(long position) {
        final long diff = position - scrubbingStart;
        if (Math.abs(diff) > 1000) {
            scrubbingNoticeable = true;
        }
        if (scrubbingNoticeable) {
            playerView.clearIcon();
            playerView.setCustomErrorMessage(Utils.formatMilisSign(diff));
        }
        if (frameRendered) {
            frameRendered = false;
            player.seekTo(position);
        }
    }

    void updateSubtitleStyle() {
        SubtitleAppearance.apply(this,playerView.getSubtitleView(),isInPip(),controllerVisible);
        liveSubtitles.resize(isInPip());
    }

    void searchSubtitles() {
        if (mPrefs.mediaUri == null)
            return;
        mVideoPath = SubtitleGenerator.resolveRealPath(this, mPrefs.mediaUri);

        // 1. Check if cached generated/extracted subtitle already exists for this video
        File cachedSrt = CaptionLibrary.find(this, mPrefs.mediaUri, mVideoPath);
        if (cachedSrt != null && cachedSrt.exists() && cachedSrt.length() > 30) {
            mPrefs.updateSubtitle(Uri.fromFile(cachedSrt));
            return;
        }

        // 2. Check if a local subtitle exists right next to the video on disk
        if (mVideoPath == null && "file".equals(mPrefs.mediaUri.getScheme())) {
            mVideoPath = mPrefs.mediaUri.getPath();
        }
        if (mVideoPath != null) {
            File localSub = SubtitleGenerator.findLocalSubtitle(mVideoPath);
            if (localSub != null) {
                Uri subUri = SubtitleUtils.convertToUTF(this, Uri.fromFile(localSub));
                mPrefs.updateSubtitle(subUri);
                return;
            }
        }

        // 3. Fall back to SAF scope / TV box search
        if (mPrefs.scopeUri != null || Utils.isTvBox(this)) {
            DocumentFile video = null;
            File videoRaw = null;

            if (!Utils.isTvBox(this) && mPrefs.scopeUri != null) {
                if ("com.android.externalstorage.documents".equals(mPrefs.mediaUri.getHost())) {
                    // Fast search based on path in uri
                    video = SubtitleUtils.findUriInScope(this, mPrefs.scopeUri, mPrefs.mediaUri);
                } else {
                    // Slow search based on matching metadata, no path in uri
                    // Provider "com.android.providers.media.documents" when using "Videos" tab in file picker
                    DocumentFile fileScope = DocumentFile.fromTreeUri(this, mPrefs.scopeUri);
                    DocumentFile fileMedia = DocumentFile.fromSingleUri(this, mPrefs.mediaUri);
                    video = SubtitleUtils.findDocInScope(fileScope, fileMedia);
                }
            } else if (Utils.isTvBox(this)) {
                videoRaw = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                video = DocumentFile.fromFile(videoRaw);
            }

            if (video != null) {
                DocumentFile subtitle;
                if (!Utils.isTvBox(this)) {
                    subtitle = SubtitleUtils.findSubtitle(video);
                } else {
                    File parentRaw = videoRaw.getParentFile();
                    DocumentFile dir = DocumentFile.fromFile(parentRaw);
                    subtitle = SubtitleUtils.findSubtitle(video, dir);
                }

                if (subtitle != null) {
                    Uri subtitleUri = subtitle.getUri();
                    SubtitleUtils.clearCache(this);
                    subtitleUri = SubtitleUtils.convertToUTF(this, subtitleUri);
                    mPrefs.updateSubtitle(subtitleUri);
                }
            }
        }
    }

    private void updateDurationText() {
        if (durationView == null || player == null) return;
        long duration = player.getDuration();
        if (duration <= 0) return;
        if (mShowRemainingTime) {
            long remaining = Math.max(0, duration - player.getCurrentPosition());
            durationView.setText("-" + Utils.formatMilis(remaining));
        } else {
            durationView.setText(Utils.formatMilis(duration));
        }
    }

    private void updateSubtitleButtonState(boolean enabled) {
        if (buttonSubtitle != null) {
            if (enabled) {
                buttonSubtitle.setAlpha(1.0f);
                buttonSubtitle.setColorFilter(getResources().getColor(R.color.player_accent));
            } else {
                buttonSubtitle.setAlpha(0.6f);
                buttonSubtitle.setColorFilter(Color.WHITE);
            }
        }
        if (playerViewModel != null) {
            playerViewModel.setSubtitlesEnabled(enabled);
        }
    }

    private void updateAspectRatioPill(int mode) {
        if (aspectRatioPill != null) {
            if (mode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                aspectRatioPill.setText("CROP");
            } else if (mode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                aspectRatioPill.setText("FILL");
            } else {
                aspectRatioPill.setText("FIT");
            }
            if (haveMedia) {
                aspectRatioPill.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateAspectRatioPill(Integer mode) {
        if (mode != null) {
            updateAspectRatioPill((int) mode);
        }
    }

    private void updateSpeedButtonText(float speed) {
        if (buttonSpeed != null) {
            String text = String.format(Locale.ROOT, "%.2fx", speed).replace(".00x", ".0x");
            buttonSpeed.setText(text);
        }
    }

    private void updateSpeedButtonText(Float speed) {
        if (speed != null) {
            updateSpeedButtonText((float) speed);
        }
    }

    private void toggleSubtitles() {
        if (liveSubtitles.isEnabled()) {
            liveSubtitles.toggle();
            updateSubtitleButtonState(liveSubtitles.isEnabled());
            String mode = mPrefs.captionTranslate ? "AI Captions (Translate to EN)" : "AI Captions (Original)";
            Utils.showText(playerView, liveSubtitles.isEnabled() ? mode + " on" : "AI Captions off");
            return;
        }
        int current = getSelectedTrackSubtitle();
        if (current >= 0) {
            setSelectedTrackSubtitle(Integer.MIN_VALUE);
            mPrefs.updateSubtitleTrack(Integer.MIN_VALUE);
            updateSubtitleButtonState(false);
            Utils.showText(playerView, "Subtitles off");
        } else {
            int targetTrack = (mPrefs.subtitleTrack >= 0 && mPrefs.subtitleTrack < getTrackCountSubtitle()) ? mPrefs.subtitleTrack : 0;
            setSelectedTrackSubtitle(targetTrack);
            mPrefs.updateSubtitleTrack(targetTrack);
            updateSubtitleButtonState(true);
            Utils.showText(playerView, "Subtitles on");
        }
    }

    private void showSubtitleTracksDialog() {
        if (trackSelector == null) return;
        final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return;

        List<String> trackNames = new ArrayList<>();
        List<Integer> trackIndices = new ArrayList<>();

        trackNames.add(getString(R.string.exo_track_selection_none));
        trackIndices.add(Integer.MIN_VALUE);

        int currentSelected = getSelectedTrackSubtitle();
        int selectedDialogIndex = (currentSelected < 0) ? 0 : -1;

        for (int r = 0; r < mappedTrackInfo.getRendererCount(); r++) {
            if (mappedTrackInfo.getRendererType(r) == C.TRACK_TYPE_TEXT) {
                TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(r);
                for (int i = 0; i < trackGroups.length; i++) {
                    Format format = trackGroups.get(i).getFormat(0);
                    String label = format.label != null ? format.label : (format.language != null ? format.language : ("Track " + (i + 1)));
                    trackNames.add(label);
                    trackIndices.add(i);
                    if (currentSelected == i) {
                        selectedDialogIndex = trackNames.size() - 1;
                    }
                }
            }
        }

        if (trackNames.size() <= 1) {
            forceGenerateSubtitles();
            return;
        }

        CharSequence[] items = trackNames.toArray(new CharSequence[0]);
        AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.exo_track_selection_title_text);
        builder.setSingleChoiceItems(items, selectedDialogIndex, (dialog, which) -> {
            int chosenTrack = trackIndices.get(which);
            setSelectedTrackSubtitle(chosenTrack);
            mPrefs.updateSubtitleTrack(chosenTrack);
            updateSubtitleButtonState(chosenTrack >= 0);
            dialog.dismiss();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showSubtitleOptions() {
        showSubtitleBottomSheet();
    }

    private void showSubtitleBottomSheet() {
        SubtitleBottomSheetDialogFragment sheet = SubtitleBottomSheetDialogFragment.newInstance();
        sheet.setListener(new SubtitleBottomSheetDialogFragment.SubtitleDialogListener() {
            @Override
            public void onSelectTrack(int trackIndex) {
                liveSubtitles.clear();
                setSelectedTrackSubtitle(trackIndex);
                mPrefs.updateSubtitleTrack(trackIndex);
                if (trackIndex < 0) {
                    mPrefs.updateSubtitle(null);
                }
                updateSubtitleButtonState(trackIndex >= 0);
                Utils.showText(playerView, trackIndex >= 0 ? "Subtitles on" : "Subtitles off");
            }

            @Override
            public void onLoadExternalFile() {
                liveSubtitles.clear();
                loadSubtitleFile(mPrefs.mediaUri);
            }

            @Override
            public void onToggleAiCaptions() {
                if (liveSubtitles.isEnabled()) {
                    liveSubtitles.toggle();
                    updateSubtitleButtonState(liveSubtitles.isEnabled());
                } else if (liveSubtitles.isActive()) {
                    liveSubtitles.toggle();
                    setSelectedTrackSubtitle(Integer.MIN_VALUE);
                    mPrefs.updateSubtitleTrack(Integer.MIN_VALUE);
                    updateSubtitleButtonState(true);
                } else {
                    setSelectedTrackSubtitle(Integer.MIN_VALUE);
                    mPrefs.updateSubtitleTrack(Integer.MIN_VALUE);
                    liveSubtitles.start(mPrefs.mediaUri, mVideoPath, subUri -> {
                        updateCaptionCancel();
                        updateSubtitleButtonState(true);
                    }, () -> {
                        updateCaptionCancel();
                        updateSubtitleButtonState(liveSubtitles.isEnabled());
                    }, true);
                }
            }

            @Override
            public void onOffsetChanged(long newOffsetMs) {
                mSubtitleOffsetMs = newOffsetMs;
                mPrefs.updateSubtitleOffset(newOffsetMs);
                liveSubtitles.setSubtitleOffsetMs(newOffsetMs);
                Utils.showText(playerView, "Subtitle delay: " + (newOffsetMs > 0 ? "+" : "") + newOffsetMs + " ms");
            }

            @Override
            public void onOpenAppearance() {
                SubtitleAppearanceSettings.show(PlayerActivity.this, () -> SubtitleAppearance.apply(PlayerActivity.this, playerView.getSubtitleView(), isInPip(), controllerVisible));
            }

            @Override
            public long getCurrentOffsetMs() {
                return mSubtitleOffsetMs;
            }

            @Override
            public int getCurrentTrackIndex() {
                return getSelectedTrackSubtitle();
            }

            @Override
            public boolean isAiCaptionsActive() {
                return liveSubtitles != null && liveSubtitles.isEnabled();
            }

            @Override
            public boolean isAiCaptionsGenerating() {
                return liveSubtitles != null && liveSubtitles.isGenerating();
            }

            @Override
            public int getAiCaptionsPercent() {
                return liveSubtitles != null ? liveSubtitles.getPercent() : 0;
            }

            @Override
            public Uri getExternalUri() {
                return mPrefs.subtitleUri;
            }

            @Override
            public List<SubtitleBottomSheetDialogFragment.SubtitleTrackItem> getAvailableTracks() {
                List<SubtitleBottomSheetDialogFragment.SubtitleTrackItem> items = new ArrayList<>();
                if (trackSelector == null) return items;
                MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
                if (mappedTrackInfo == null) return items;

                int currentSelected = getSelectedTrackSubtitle();
                for (int r = 0; r < mappedTrackInfo.getRendererCount(); r++) {
                    if (mappedTrackInfo.getRendererType(r) == C.TRACK_TYPE_TEXT) {
                        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(r);
                        for (int i = 0; i < trackGroups.length; i++) {
                            Format format = trackGroups.get(i).getFormat(0);
                            String label = format.label != null ? format.label : ("Track " + (i + 1));
                            String lang = format.language != null ? format.language.toUpperCase(Locale.ROOT) : null;
                            String codec = SubtitleUtils.getMimeTypeAbbreviation(format.sampleMimeType);
                            boolean isSelected = currentSelected == i;
                            items.add(new SubtitleBottomSheetDialogFragment.SubtitleTrackItem(i, label, lang, codec, isSelected));
                        }
                    }
                }
                return items;
            }
        });
        sheet.show(getSupportFragmentManager(), "SubtitleBottomSheet");
    }

    private void legacyShowSubtitleOptions() {
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (liveSubtitles.isActive() || getTrackCountSubtitle() > 0 || mPrefs.subtitleUri != null) {
            options.add(getString(R.string.captions_toggle));
            actions.add(this::toggleSubtitles);
        }
        if (getTrackCountSubtitle() > 0) {
            options.add(getString(R.string.captions_tracks));
            actions.add(() -> {
                liveSubtitles.clear();
                showSubtitleTracksDialog();
            });
        }
        options.add(getString(R.string.captions_open));
        actions.add(() -> loadSubtitleFile(mPrefs.mediaUri));
        if (liveSubtitles.isGenerating()) {
            options.add(getString(R.string.captions_cancel));
            actions.add(liveSubtitles::stopGeneration);
        } else {
            options.add(getString(R.string.captions_create));
            actions.add(this::forceGenerateSubtitles);
            options.add("Regenerate subtitles");
            actions.add(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setMessage("Replace generated subtitles and saved edits after generation succeeds?")
                .setPositiveButton("Regenerate",(d,w)->generateCaptions(true)).setNegativeButton(android.R.string.cancel,null).show());
            options.add("Edit / export subtitles");
            actions.add(() -> {
                if (player != null) player.pause();
                subtitleEditorLauncher.launch(new Intent(this, SubtitleEditorActivity.class).setData(mPrefs.mediaUri).putExtra("path", mVideoPath).putExtra("subtitle", mPrefs.subtitleUri));
            });
        }
        options.add(getString(R.string.subtitle_appearance));
        actions.add(() -> startActivity(new Intent(this,SettingsActivity.class).putExtra("subtitle_appearance",true)));
        options.add("Audio language & offline packs");
        actions.add(() -> startActivity(new Intent(this,SettingsActivity.class)));
        playerView.setControllerShowTimeoutMs(0);
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.subtitle_options)
                .setItems(options.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnDismissListener(d -> resetHideCallbacks());
        dialog.show();
    }

    private void updateCaptionCancel() {
        View cancel=findViewById(R.id.button_cancel_captions);
        cancel.setVisibility(liveSubtitles.isGenerating()?View.VISIBLE:View.GONE);
        cancel.setEnabled(!liveSubtitles.isCancelling());
    }

    private void forceGenerateSubtitles() { generateCaptions(false); }

    private void generateCaptions(boolean force) {
        if (mPrefs.mediaUri == null || player == null || liveSubtitles.isGenerating()) return;
        final Uri targetUri = mPrefs.mediaUri;
        liveSubtitles.start(targetUri, mVideoPath, subtitleUri -> {
            if (!targetUri.equals(mPrefs.mediaUri) || player == null) return;
            if (liveSubtitles.hasCues()) {
                mPrefs.updateSubtitle(subtitleUri);
            } else {
                applySubtitle(subtitleUri);
            }
        }, () -> { updateSubtitleButtonState(liveSubtitles.isEnabled());updateCaptionCancel(); },force);
        updateCaptionCancel();
        updateSubtitleButtonState(liveSubtitles.isEnabled());
    }

    private void triggerAutoSubtitleGeneration() {
        String request=getIntent().getStringExtra("caption_request");
        if(request!=null) { getIntent().removeExtra("caption_request");generateCaptions("regenerate".equals(request));return; }
        CaptionWorkStore.Entry previous=new CaptionWorkStore(this).read(mPrefs.mediaUri,CaptionPackages.get(this).selected());
        if(previous!=null && (previous.status==CaptionWorkStore.Status.CANCELLED || previous.status==CaptionWorkStore.Status.FAILED))return;
        if (!mPrefs.liveCaptions) {
            return;
        }
        if (getTrackCountSubtitle() > 0 || mPrefs.subtitleUri != null) {
            return;
        }
        forceGenerateSubtitles();
    }

    private void applySubtitle(Uri subtitleUri) {
        if (player == null || subtitleUri == null) return;
        mPrefs.updateSubtitle(subtitleUri);
        final String subtitleMime = SubtitleUtils.getSubtitleMime(subtitleUri);
        final String subtitleLanguage = SubtitleUtils.getSubtitleLanguage(subtitleUri);
        String subtitleName = Utils.getFileName(this, subtitleUri);
        if (subtitleName == null) subtitleName = "Auto Subtitles";

        MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(subtitleMime)
                .setLanguage(subtitleLanguage)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .setLabel(subtitleName)
                .build();

        long currentPosition = player.getCurrentPosition();
        boolean wasPlaying = player.isPlaying();

        MediaItem currentItem = player.getCurrentMediaItem();
        MediaItem.Builder mediaItemBuilder;
        if (currentItem != null) {
            mediaItemBuilder = currentItem.buildUpon();
        } else {
            mediaItemBuilder = new MediaItem.Builder().setUri(mPrefs.mediaUri);
        }
        mediaItemBuilder.setSubtitleConfigurations(Collections.singletonList(subtitle));

        player.setMediaItem(mediaItemBuilder.build(), currentPosition);
        player.prepare();
        if (wasPlaying) {
            player.play();
        }
        setSelectedTrackSubtitle(0);
        updateSubtitleButtonState(true);
        if (playerViewModel != null) {
            playerViewModel.setSubtitleUri(subtitleUri);
        }
    }

    Uri findNext() {
        final Uri playlistNext = PlaylistManager.getNext(mPrefs.mediaUri);
        if (PlaylistManager.contains(mPrefs.mediaUri)) {
            return playlistNext;
        }

        // TODO: Unify with searchSubtitles()
        if (mPrefs.scopeUri != null || Utils.isTvBox(this)) {
            DocumentFile video = null;
            File videoRaw = null;

            if (!Utils.isTvBox(this) && mPrefs.scopeUri != null) {
                if ("com.android.externalstorage.documents".equals(mPrefs.mediaUri.getHost())) {
                    // Fast search based on path in uri
                    video = SubtitleUtils.findUriInScope(this, mPrefs.scopeUri, mPrefs.mediaUri);
                } else {
                    // Slow search based on matching metadata, no path in uri
                    // Provider "com.android.providers.media.documents" when using "Videos" tab in file picker
                    DocumentFile fileScope = DocumentFile.fromTreeUri(this, mPrefs.scopeUri);
                    DocumentFile fileMedia = DocumentFile.fromSingleUri(this, mPrefs.mediaUri);
                    video = SubtitleUtils.findDocInScope(fileScope, fileMedia);
                }
            } else if (Utils.isTvBox(this)) {
                videoRaw = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                video = DocumentFile.fromFile(videoRaw);
            }

            if (video != null) {
                DocumentFile next;
                if (!Utils.isTvBox(this)) {
                    next = SubtitleUtils.findNext(video);
                } else {
                    File parentRaw = videoRaw.getParentFile();
                    DocumentFile dir = DocumentFile.fromFile(parentRaw);
                    next = SubtitleUtils.findNext(video, dir);
                }
                if (next != null) {
                    return next.getUri();
                }
            }
        }
        return null;
    }

    void askForScope(boolean loadSubtitlesOnCancel, boolean skipToNextOnCancel) {
        final AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(PlayerActivity.this);
        builder.setMessage(String.format(getString(R.string.request_scope), getString(R.string.app_name)));
        builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> requestDirectoryAccess()
        );
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            mPrefs.markScopeAsked();
            if (loadSubtitlesOnCancel) {
                loadSubtitleFile(mPrefs.mediaUri);
            }
            if (skipToNextOnCancel) {
                nextUri = findNext();
                if (nextUri != null) {
                    skipToNext();
                }
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    void resetHideCallbacks() {
        playerView.setControllerShowTimeoutMs(player != null && player.isPlaying() ? CONTROLLER_TIMEOUT : 0);
    }

    private void updateLoading(final boolean enableLoading) {
        if (enableLoading) {
            exoPlayPause.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.VISIBLE);
        } else {
            loadingProgressBar.setVisibility(View.GONE);
            exoPlayPause.setVisibility(View.VISIBLE);
            if (focusPlay) {
                focusPlay = false;
                exoPlayPause.requestFocus();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onUserLeaveHint() {
        if (mPrefs != null && mPrefs.autoPiP && player != null && player.isPlaying() && isPiPSupported())
            enterPiP();
        else
            super.onUserLeaveHint();
    }

    private void updatePiPParams() {
        if (!isPiPSupported() || player == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mPictureInPictureParamsBuilder == null) {
                mPictureInPictureParamsBuilder = new PictureInPictureParams.Builder();
            }
            PictureInPictureParams.Builder builder = (PictureInPictureParams.Builder) mPictureInPictureParamsBuilder;
            final Format format = player.getVideoFormat();
            if (format != null && format.width > 0 && format.height > 0) {
                Rational rational;
                if (Utils.isRotated(format)) {
                    rational = new Rational(format.height, format.width);
                } else {
                    rational = new Rational(format.width, format.height);
                }
                if (rational.floatValue() > rationalLimitWide.floatValue()) {
                    rational = rationalLimitWide;
                } else if (rational.floatValue() < rationalLimitTall.floatValue()) {
                    rational = rationalLimitTall;
                }
                builder.setAspectRatio(rational);

                final View videoSurfaceView = (playerView != null) ? playerView.getVideoSurfaceView() : null;
                if (videoSurfaceView != null) {
                    Rect sourceRectHint = new Rect();
                    videoSurfaceView.getGlobalVisibleRect(sourceRectHint);
                    builder.setSourceRectHint(sourceRectHint);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                boolean shouldAutoEnter = player.isPlaying() && (mPrefs == null || mPrefs.autoPiP);
                builder.setAutoEnterEnabled(shouldAutoEnter);
            }

            try {
                setPictureInPictureParams(builder.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void enterPiP() {
        final AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOpsManager != null) {
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), getPackageName());
            } else {
                mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), getPackageName());
            }
            if (AppOpsManager.MODE_ALLOWED != mode) {
                startActivity(new Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", Uri.fromParts("package", getPackageName(), null)));
                return;
            }
        }

        playerView.setControllerAutoShow(false);
        playerView.setControllerShowTimeoutMs(0);
        playerView.hideController();

        updatePiPParams();

        try {
            if (mPictureInPictureParamsBuilder != null) {
                enterPictureInPictureMode(((PictureInPictureParams.Builder) mPictureInPictureParamsBuilder).build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void setEndControlsVisible(boolean visible) {
        // Redesigned controller does not use legacy delete/next overlay buttons
    }

    void askDeleteMedia() {
        final AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(PlayerActivity.this);
        builder.setMessage(getString(R.string.delete_query));
        builder.setPositiveButton(R.string.delete_confirmation, (dialogInterface, i) -> {
            releasePlayer();
            deleteMedia();
            if (nextUri == null) {
                haveMedia = false;
                setEndControlsVisible(false);
                playerView.setControllerShowTimeoutMs(-1);
            } else {
                skipToNext();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {});
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    void deleteMedia() {
        try {
            DocumentsContract.deleteDocument(getContentResolver(), mPrefs.mediaUri);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void dispatchPlayPause() {
        if (player == null) return;
        int state = player.getPlaybackState();
        if (state == Player.STATE_IDLE) player.prepare();
        if (state == Player.STATE_ENDED) player.seekToDefaultPosition();
        if (state == Player.STATE_ENDED || !player.getPlayWhenReady()) player.play();
        else player.pause();
        playerView.showController();
    }

    private void returnToLibrary() {
        Intent intent = new Intent(this, FolderBrowserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void updatePlaylistButtons() {
        if (exoPrev != null) {
            exoPrev.setEnabled(haveMedia);
            exoPrev.setAlpha(haveMedia ? 1f : 0.4f);
        }
        if (exoNext != null) {
            boolean available = PlaylistManager.hasNext(mPrefs.mediaUri) || nextUri != null;
            exoNext.setEnabled(available);
            exoNext.setAlpha(available ? 1f : 0.4f);
        }
    }

    void skipToNext() {
        Uri next = PlaylistManager.getNext(mPrefs.mediaUri);
        if (next == null) {
            next = nextUri;
        }
        if (next != null) {
            releasePlayer();
            mVideoPath = SubtitleGenerator.resolveRealPath(this, next);
            mPrefs.updateMedia(this, next, null);
            mPrefs.updatePosition(0L);
            searchSubtitles();
            play = true;
            initializePlayer();
        } else {
            Utils.showText(playerView, getString(R.string.no_next_video));
        }
    }

    void skipToPrevious() {
        if (player != null && player.getCurrentPosition() > 3000) {
            player.seekTo(0);
            return;
        }
        Uri prev = PlaylistManager.getPrevious(mPrefs.mediaUri);
        if (prev != null) {
            releasePlayer();
            mVideoPath = SubtitleGenerator.resolveRealPath(this, prev);
            mPrefs.updateMedia(this, prev, null);
            mPrefs.updatePosition(0L);
            searchSubtitles();
            play = true;
            initializePlayer();
        } else if (player != null) {
            player.seekTo(0);
        }
    }

    @Override
    public ExoPlayer getPlayer() {
        return player;
    }

    @Override
    public boolean hasMedia() {
        return haveMedia;
    }

    @Override
    public boolean isControllerVisible() {
        return controllerVisible;
    }

    @Override
    public boolean isRestoreControllerTimeout() {
        return restoreControllerTimeout;
    }

    @Override
    public void setRestoreControllerTimeout(boolean restore) {
        restoreControllerTimeout = restore;
    }

    @Override
    public boolean dismissSnackbar() {
        if (snackbar != null && snackbar.isShown()) {
            snackbar.dismiss();
            snackbar = null;
            return true;
        }
        return false;
    }

    @Override
    public BrightnessControl getBrightnessControl() {
        return mBrightnessControl;
    }

    @Override
    public LoudnessEnhancer getLoudnessEnhancer() {
        return loudnessEnhancer;
    }

    @Override
    public int getBoostLevel() {
        return boostLevel;
    }

    @Override
    public void setBoostLevel(int level) {
        boostLevel = level;
        if (playerViewModel != null) {
            playerViewModel.setBoostLevel(level);
        }
    }

    @Override
    public void setFocusPlay(boolean focus) {
        focusPlay = focus;
    }
}
