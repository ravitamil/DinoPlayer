package com.brouken.player;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ui.DefaultTimeBar;

/** Standard accessible ExoPlayer seeking, including direct taps and drag scrubbing. */
public class CustomDefaultTimeBar extends DefaultTimeBar {
    public CustomDefaultTimeBar(Context context) { this(context, null); }
    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
