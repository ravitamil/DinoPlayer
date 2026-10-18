package com.brouken.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.CaptioningManager;

public final class SubtitleStylePreferences {
    private final Context context;
    private final SharedPreferences prefs;
    public SubtitleStylePreferences(Context context) {
        this.context=context;this.prefs=context.getSharedPreferences("subtitle_appearance",Context.MODE_PRIVATE);
    }
    public SubtitleStyleOptions read() {
        CaptioningManager captions=(CaptioningManager)context.getSystemService(Context.CAPTIONING_SERVICE);
        return new SubtitleStyleOptions(prefs.getBoolean("android",captions!=null && captions.isEnabled()),
                prefs.getInt("size",1),prefs.getInt("color",0),prefs.getInt("opacity",60),prefs.getBoolean("outline",true),prefs.getInt("bottom",10));
    }
    public void save(SubtitleStyleOptions value) {
        prefs.edit().putBoolean("android",value.useAndroid).putInt("size",value.size).putInt("color",value.color)
                .putInt("opacity",value.opacity).putBoolean("outline",value.outline).putInt("bottom",value.bottom).apply();
    }
}
