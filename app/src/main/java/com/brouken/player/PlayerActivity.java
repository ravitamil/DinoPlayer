package com.brouken.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.documentfile.provider.DocumentFile;

import com.brouken.player.dtpv.DoubleTapPlayerView;
import com.brouken.player.dtpv.youtube.YouTubeOverlay;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.StyledPlayerControlView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends Activity {

    private PlaybackStateListener playbackStateListener;
    private BroadcastReceiver mReceiver;
    private AudioManager mAudioManager;
    private MediaSessionCompat mediaSession;
    private DefaultTrackSelector trackSelector;
    public static LoudnessEnhancer loudnessEnhancer;

    private CustomStyledPlayerView playerView;
    public static SimpleExoPlayer player;

    private Object mPictureInPictureParamsBuilder;

    public Prefs mPrefs;
    public static BrightnessControl mBrightnessControl;
    public static boolean haveMedia;
    private boolean videoLoading;
    public static boolean controllerVisible;
    public static boolean controllerVisibleFully;
    public static Snackbar snackbar;
    private ExoPlaybackException errorToShow;
    public static int boostLevel = 0;

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
    private ImageButton buttonSubtitle;
    private ImageButton buttonLock;
    private ImageButton buttonUnlock;
    private ImageButton exoPrev;
    private ImageButton exoNext;
    private TextView durationView;
    private boolean mShowRemainingTime = false;
    private final Runnable mHideUnlockRunnable = () -> {
        if (buttonUnlock != null) buttonUnlock.setVisibility(View.GONE);
    };
    private ImageButton exoPlayPause;
    private ProgressBar loadingProgressBar;
    private StyledPlayerControlView controlView;

    private boolean restoreOrientationLock;
    private boolean restorePlayState;
    private boolean play;
    private final com.google.android.exoplayer2.text.TextOutput consistentSubtitleOutput=cues -> {
        SubtitleView view=playerView.getSubtitleView();
        if(view!=null)view.setCues(SubtitleAppearance.normalize(cues));
    };
    private boolean isScrubbing;
    private boolean scrubbingNoticeable;
    private long scrubbingStart;
    public boolean frameRendered;
    private boolean alive;
    public static boolean focusPlay = false;
    private Uri nextUri;
    public static boolean restoreControllerTimeout = false;
    public static boolean shortControllerTimeout = false;
    private OrientationEventListener orientationEventListener;
    private String mVideoPath = null;
    private LiveSubtitleController liveSubtitles;
    private boolean manualOrientation;

    final Rational rationalLimitWide = new Rational(239, 100);
    final Rational rationalLimitTall = new Rational(100, 239);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PlaylistManager.restore(this);
        // Rotate ASAP, before super/inflating to avoid glitches with activity launch animation
        mPrefs = new Prefs(this);
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
                        }
                    } else if (orientation >= 45 && orientation <= 135) {
                        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                        }
                    }
                } else {
                    if ((orientation >= 315 && orientation <= 360) || (orientation >= 0 && orientation <= 45)) {
                        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        }
                    } else if (orientation >= 135 && orientation <= 225) {
                        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                        }
                    }
                }
            }
        };

        super.onCreate(savedInstanceState);
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
        liveSubtitles = new LiveSubtitleController(this, playerView, () -> player);
        exoPlayPause = findViewById(R.id.exo_play_pause);
        loadingProgressBar = findViewById(R.id.loading);

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

        buttonAspectRatio = findViewById(R.id.button_aspect_ratio);
        if (buttonAspectRatio != null) {
            buttonAspectRatio.setOnClickListener(view -> {
                playerView.setScale(1.f);
                if (playerView.getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                    Utils.showText(playerView, getString(R.string.video_resize_crop));
                } else {
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                    Utils.showText(playerView, getString(R.string.video_resize_fit));
                }
                resetHideCallbacks();
            });
            Utils.setButtonEnabled(this, buttonAspectRatio, false);
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

        buttonLock = findViewById(R.id.button_lock);
        // Unlock belongs to the overlay, outside the controller that locking hides.
        buttonUnlock = new ImageButton(this);
        buttonUnlock.setImageResource(R.drawable.ic_lock_24dp);
        buttonUnlock.setColorFilter(Color.WHITE);
        buttonUnlock.setBackgroundResource(R.drawable.rounded_duration_background);
        buttonUnlock.setContentDescription(getString(R.string.unlock_screen));
        FrameLayout.LayoutParams unlockParams = new FrameLayout.LayoutParams(Utils.dpToPx(56), Utils.dpToPx(56), Gravity.BOTTOM | Gravity.START);
        unlockParams.leftMargin = Utils.dpToPx(24);
        unlockParams.bottomMargin = Utils.dpToPx(40);
        playerView.getOverlayFrameLayout().addView(buttonUnlock, unlockParams);
        buttonUnlock.setVisibility(View.GONE);
        if (buttonLock != null && buttonUnlock != null) {
            buttonLock.setOnClickListener(v -> {
                playerView.isLocked = true;
                playerView.hideController();
                buttonUnlock.setVisibility(View.VISIBLE);
                Utils.showText(playerView, "Screen Locked", 1200);
                buttonUnlock.removeCallbacks(mHideUnlockRunnable);
                buttonUnlock.postDelayed(mHideUnlockRunnable, 3000);
            });

            buttonUnlock.setOnClickListener(v -> {
                playerView.isLocked = false;
                buttonUnlock.setVisibility(View.GONE);
                playerView.showController();
                Utils.showText(playerView, "Screen Unlocked", 1200);
            });

            playerView.onLockedTouchCallback = () -> {
                if (buttonUnlock.getVisibility() == View.VISIBLE) {
                    buttonUnlock.setVisibility(View.GONE);
                } else {
                    buttonUnlock.setVisibility(View.VISIBLE);
                    buttonUnlock.removeCallbacks(mHideUnlockRunnable);
                    buttonUnlock.postDelayed(mHideUnlockRunnable, 3000);
                }
            };
        }

        exoPrev = findViewById(R.id.exo_prev);
        if (exoPrev != null) {
            exoPrev.setOnClickListener(view -> skipToPrevious());
        }

        exoNext = findViewById(R.id.exo_next);
        if (exoNext != null) {
            exoNext.setOnClickListener(view -> skipToNext());
        }
        findViewById(R.id.button_back).setOnClickListener(v -> returnToLibrary());
        findViewById(R.id.button_minimal_play).setOnClickListener(v -> dispatchPlayPause());
        findViewById(R.id.button_rotate).setOnClickListener(v -> {
            manualOrientation = true;
            boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
            setRequestedOrientation(portrait ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            playerView.showController();
        });

        durationView = findViewById(R.id.exo_duration);
        TextView positionView = findViewById(R.id.exo_position);
        if (durationView != null) {
            durationView.setOnClickListener(v -> {
                mShowRemainingTime = !mShowRemainingTime;
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
        }
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
            final Field field = StyledPlayerControlView.class.getDeclaredField("trackNameProvider");
            field.setAccessible(true);
            field.set(controlView, customDefaultTrackNameProvider);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
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

        playerView.setControllerVisibilityListener(new StyledPlayerControlView.VisibilityListener() {
            @Override
            public void onVisibilityChange(int visibility) {
                controllerVisible = visibility == View.VISIBLE;
                controllerVisibleFully = playerView.isControllerFullyVisible();
                liveSubtitles.setControlsVisible(controllerVisible);
                SubtitleView captions = playerView.getSubtitleView();
                SubtitleAppearance.apply(PlayerActivity.this,captions,isInPip(),controllerVisible);

                if (PlayerActivity.restoreControllerTimeout) {
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
        CaptionAutoJobs.playerActive(this,false);
        if (liveSubtitles != null) { liveSubtitles.clear();updateCaptionCancel(); }
        if (buttonUnlock != null) buttonUnlock.removeCallbacks(mHideUnlockRunnable);
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
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
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (!controllerVisibleFully) {
                    playerView.removeCallbacks(playerView.textClearRunnable);
                    long seekTo = player.getCurrentPosition() - 10_000;
                    if (seekTo < 0)
                        seekTo = 0;
                    player.setSeekParameters(SeekParameters.PREVIOUS_SYNC);
                    player.seekTo(seekTo);
                    playerView.setCustomErrorMessage(Utils.formatMilis(seekTo));
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (!controllerVisibleFully) {
                    playerView.removeCallbacks(playerView.textClearRunnable);
                    long seekTo = player.getCurrentPosition() + 10_000;
                    long seekMax = player.getDuration();
                    if (seekMax != C.TIME_UNSET && seekTo > seekMax)
                        seekTo = seekMax;
                    PlayerActivity.player.setSeekParameters(SeekParameters.NEXT_SYNC);
                    player.seekTo(seekTo);
                    playerView.setCustomErrorMessage(Utils.formatMilis(seekTo));
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (Utils.isTvBox(this)) {
                    if (controllerVisible && player.isPlaying()) {
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
            registerReceiver(mReceiver, new IntentFilter(ACTION_MEDIA_CONTROL));
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (restoreOrientationLock) {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                restoreOrientationLock = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(requestCode==4200) {
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null) {
                liveSubtitles.clear();mPrefs.updateSubtitle(data.getData());
                if(player!=null)applySubtitle(data.getData());
            }
            return;
        }

        if (resultCode == RESULT_OK && alive) {
            releasePlayer();
        }

        if (requestCode == REQUEST_CHOOSER_VIDEO) {
            if (resultCode == RESULT_OK) {
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
        } else if (requestCode == REQUEST_CHOOSER_SUBTITLE) {
            if (resultCode == RESULT_OK) {
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
        } else if (requestCode == REQUEST_CHOOSER_SCOPE_DIR) {
            if (resultCode == RESULT_OK) {
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
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

        // Init here because onStart won't follow when app was only paused when file chooser was shown
        // (for example pop-up file chooser on tablets)
        if (resultCode == RESULT_OK && alive) {
            initializePlayer();
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
            player = new SimpleExoPlayer.Builder(this, renderersFactory)
                    .setTrackSelector(trackSelector)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(this, extractorsFactory))
                    .build();
            final AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MOVIE)
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

        player.removeTextOutput(consistentSubtitleOutput);
        playerView.setPlayer(player);
        // StyledPlayerView's output is registered first; normalize the same frame before drawing.
        player.addTextOutput(consistentSubtitleOutput);
        if(playerView.getSubtitleView()!=null)playerView.getSubtitleView().setCues(SubtitleAppearance.normalize(player.getCurrentCues()));

        mediaSession = new MediaSessionCompat(this, getString(R.string.app_name));
        MediaSessionConnector mediaSessionConnector = new MediaSessionConnector(mediaSession);
        mediaSessionConnector.setPlayer(player);

        mediaSessionConnector.setMediaMetadataProvider(player -> {
            final String title = Utils.getFileName(PlayerActivity.this, mPrefs.mediaUri);
            if (title == null)
                return null;
            else
                return new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .build();
        });

        playerView.setControllerShowTimeoutMs(-1);

        if (haveMedia) {
            playerView.setResizeMode(mPrefs.resizeMode);

            if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                playerView.setScale(mPrefs.scale);
            } else {
                playerView.setScale(1.f);
            }

            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                    .setUri(mPrefs.mediaUri)
                    .setMimeType(mPrefs.mediaType);
            if (mPrefs.subtitleUri != null && Utils.fileExists(this, mPrefs.subtitleUri)) {
                final String subtitleMime = SubtitleUtils.getSubtitleMime(mPrefs.subtitleUri);
                final String subtitleLanguage = SubtitleUtils.getSubtitleLanguage(mPrefs.subtitleUri);
                String subtitleName = null;
                if (subtitleLanguage == null)
                    subtitleName = Utils.getFileName(this, mPrefs.subtitleUri);

                MediaItem.Subtitle subtitle = new MediaItem.Subtitle(mPrefs.subtitleUri, subtitleMime,
                        subtitleLanguage, C.SELECTION_FLAG_DEFAULT, C.ROLE_FLAG_SUBTITLE, subtitleName);
                mediaItemBuilder.setSubtitles(Collections.singletonList(subtitle));
            }
            player.setMediaItem(mediaItemBuilder.build());

            if (loudnessEnhancer != null) {
                loudnessEnhancer.release();
            }
            try {
                int audioSessionId = C.generateAudioSessionIdV21(this);
                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                player.setAudioSessionId(audioSessionId);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }

            // When audio session id changes?
            player.addAudioListener(new AudioListener() {
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
            });

            player.addVideoListener(new com.google.android.exoplayer2.video.VideoListener() {
                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    if (!manualOrientation && mPrefs.orientation == Utils.Orientation.VIDEO) {
                        Utils.applyOrientation(PlayerActivity.this, Utils.isPortrait(width, height, unappliedRotationDegrees));
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
            mediaSession.setActive(true);
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
            mediaSession.setActive(false);
            mediaSession.release();

            mPrefs.updateBrightness(mBrightnessControl.currentBrightnessLevel);
            mPrefs.updateOrientation();

            if (haveMedia) {
                saveWatchHistory();
                // Prevent overwriting temporarily inaccessible media position
                if (player.isCurrentWindowSeekable()) {
                    mPrefs.updatePosition(player.getCurrentPosition());
                }
                mPrefs.updateMeta(getSelectedTrackAudio(false), getSelectedTrackAudio(true), selectedSubtitle, playerView.getResizeMode(), playerView.getVideoSurfaceView().getScaleX());
            }

            if (player.isPlaying()) {
                restorePlayState = true;
            }
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
    }

    private void saveWatchHistory() {
        if (player != null && haveMedia && mPrefs.mediaUri != null) {
            new WatchHistory(this).record(mPrefs.mediaUri, player.getCurrentPosition(),
                    player.getDuration(), player.getPlaybackState() == Player.STATE_ENDED);
        }
    }

    private class PlaybackStateListener implements Player.EventListener{
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            playerView.removeCallbacks(watchCheckpoint);
            if (isPlaying) playerView.postDelayed(watchCheckpoint, 15000);
            if (!isPlaying) saveWatchHistory();
            playerView.setKeepScreenOn(isPlaying);
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
        public void onPlayerError(ExoPlaybackException error) {
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
        if (intent.resolveActivity(getPackageManager()) == null)
            showSnack(getText(R.string.error_files_missing).toString(), intent.toString());
        else
            startActivityForResult(intent, code);
    }

    public void setSelectedTrackSubtitle(final int trackIndex) {
        final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            final DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
            final DefaultTrackSelector.ParametersBuilder parametersBuilder = parameters.buildUpon();
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
            final DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
            final DefaultTrackSelector.ParametersBuilder parametersBuilder = parameters.buildUpon();
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
        final PendingIntent intent = PendingIntent.getBroadcast(PlayerActivity.this, requestCode,
                        new Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, controlType), PendingIntent.FLAG_IMMUTABLE);
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

    void showError(ExoPlaybackException error) {
        final String errorGeneral = error.getLocalizedMessage();
        String errorDetailed;

        switch (error.type) {
            case ExoPlaybackException.TYPE_SOURCE:
                errorDetailed = error.getSourceException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_RENDERER:
                errorDetailed = error.getRendererException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_UNEXPECTED:
                errorDetailed = error.getUnexpectedException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_REMOTE:
            default:
                errorDetailed = errorGeneral;
                break;
        }

        showSnack(errorGeneral, errorDetailed);
    }

    void showSnack(final String textPrimary, final String textSecondary) {
        snackbar = Snackbar.make(coordinatorLayout, textPrimary, Snackbar.LENGTH_LONG);
        if (textSecondary != null) {
            snackbar.setAction(R.string.error_details, v -> {
                final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
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
    }

    private void toggleSubtitles() {
        if (liveSubtitles.isActive()) {
            liveSubtitles.toggle();
            updateSubtitleButtonState(liveSubtitles.isEnabled());
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
            actions.add(() -> new AlertDialog.Builder(this).setMessage("Replace generated subtitles and saved edits after generation succeeds?")
                .setPositiveButton("Regenerate",(d,w)->generateCaptions(true)).setNegativeButton(android.R.string.cancel,null).show());
            options.add("Edit / export subtitles");
            actions.add(() -> {
                if(player!=null)player.pause();
                startActivityForResult(new Intent(this,SubtitleEditorActivity.class).setData(mPrefs.mediaUri).putExtra("path",mVideoPath).putExtra("subtitle",mPrefs.subtitleUri),4200);
            });
        }
        options.add(getString(R.string.subtitle_appearance));
        actions.add(() -> startActivity(new Intent(this,SettingsActivity.class).putExtra("subtitle_appearance",true)));
        options.add("Audio language & offline packs");
        actions.add(() -> startActivity(new Intent(this,SettingsActivity.class)));
        playerView.setControllerShowTimeoutMs(0);
        AlertDialog dialog = new AlertDialog.Builder(this)
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

        MediaItem.Subtitle subtitle = new MediaItem.Subtitle(subtitleUri, subtitleMime,
                subtitleLanguage, C.SELECTION_FLAG_DEFAULT, C.ROLE_FLAG_SUBTITLE, subtitleName);

        long currentPosition = player.getCurrentPosition();
        boolean wasPlaying = player.isPlaying();

        MediaItem currentItem = player.getCurrentMediaItem();
        MediaItem.Builder mediaItemBuilder;
        if (currentItem != null) {
            mediaItemBuilder = currentItem.buildUpon();
        } else {
            mediaItemBuilder = new MediaItem.Builder().setUri(mPrefs.mediaUri);
        }
        mediaItemBuilder.setSubtitles(Collections.singletonList(subtitle));

        player.setMediaItem(mediaItemBuilder.build(), currentPosition);
        player.prepare();
        if (wasPlaying) {
            player.play();
        }
        setSelectedTrackSubtitle(0);
        updateSubtitleButtonState(true);
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
        final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
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

            if (Build.VERSION.SDK_INT >= 31) {
                boolean shouldAutoEnter = player.isPlaying() && (mPrefs == null || mPrefs.autoPiP);
                try {
                    Method setAutoEnterEnabledMethod = builder.getClass().getMethod("setAutoEnterEnabled", boolean.class);
                    setAutoEnterEnabledMethod.invoke(builder, shouldAutoEnter);
                } catch (Exception e) {
                    // Method not available
                }
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
        final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
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
        }
    }

    void skipToPrevious() {
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
}
