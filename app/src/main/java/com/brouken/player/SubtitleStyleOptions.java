package com.brouken.player;

/** Validated, platform-independent preferences shared by playback and its preview. */
public final class SubtitleStyleOptions {
    public final boolean useAndroid,outline;
    public final int size,color,opacity,bottom;
    private static final float[] SIZES={16f,18f,22f};
    private static final int[] COLORS={0xffffffff,0xfffff176,0xffb9f6ca,0xff80deea};

    public SubtitleStyleOptions(boolean useAndroid,int size,int color,int opacity,boolean outline,int bottom) {
        this.useAndroid=useAndroid;
        this.size=size>=0 && size<SIZES.length?size:1;
        this.color=color>=0 && color<COLORS.length?color:0;
        this.opacity=Math.max(0,Math.min(100,opacity));
        this.outline=outline;
        this.bottom=Math.max(4,Math.min(25,bottom));
    }
    public static SubtitleStyleOptions defaults() { return new SubtitleStyleOptions(false,1,0,60,true,10); }
    public int textColor() { return COLORS[color]; }
    public int backgroundColor() { return Math.round(opacity*255f/100f)<<24; }
    public float textSizeSp(boolean pip,float androidScale) {
        float scale=useAndroid && !Float.isNaN(androidScale) && !Float.isInfinite(androidScale) && androidScale>0?androidScale:1f;
        return SIZES[useAndroid?1:size]*(pip?2f/3f:1f)*scale;
    }
    public float bottomFraction(boolean pip) { return (useAndroid?10:bottom)*(pip?0.008f:0.01f); }
}
