package com.brouken.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class SubtitleStyleOptionsTest {
    @Test public void defaultsPreserveExistingPlaybackAppearance() {
        SubtitleStyleOptions options=SubtitleStyleOptions.defaults();
        assertEquals(18,options.textSizeSp(false,1),0);assertEquals(12,options.textSizeSp(true,1),0);
        assertEquals(0x99000000,options.backgroundColor());assertEquals(0xffffffff,options.textColor());
        assertEquals(0.1f,options.bottomFraction(false),0.0001f);assertEquals(0.08f,options.bottomFraction(true),0.0001f);
    }
    @Test public void customStyleIsIndependentOfAndroidCaptionScale() {
        SubtitleStyleOptions custom=new SubtitleStyleOptions(false,2,1,30,false,20);
        assertEquals(22,custom.textSizeSp(false,3),0);assertEquals(22f*2f/3f,custom.textSizeSp(true,3),0.0001f);
        assertEquals(0.20f,custom.bottomFraction(false),0.0001f);assertFalse(custom.outline);
    }
    @Test public void androidModeUsesItsOwnScaleAndSharedDefaultGeometry() {
        SubtitleStyleOptions system=new SubtitleStyleOptions(true,2,1,5,false,25);
        assertEquals(27,system.textSizeSp(false,1.5f),0.0001f);
        assertEquals(18,system.textSizeSp(true,1.5f),0.0001f);
        assertEquals(SubtitleStyleOptions.defaults().bottomFraction(false),system.bottomFraction(false),0);
    }
    @Test public void corruptOrOutOfRangePreferencesCannotBreakLayout() {
        SubtitleStyleOptions invalid=new SubtitleStyleOptions(false,999,-1,200,true,-100);
        assertEquals(1,invalid.size);assertEquals(0,invalid.color);assertEquals(0xff000000,invalid.backgroundColor());
        assertEquals(0.04f,invalid.bottomFraction(false),0.0001f);
        assertEquals(0.25f,new SubtitleStyleOptions(false,1,0,-10,true,300).bottomFraction(false),0.0001f);
    }
    @Test public void invalidSystemScaleFallsBackToReadableDefaults() {
        SubtitleStyleOptions system=new SubtitleStyleOptions(true,0,0,60,true,10);
        assertEquals(18,system.textSizeSp(false,Float.NaN),0);assertEquals(18,system.textSizeSp(false,Float.POSITIVE_INFINITY),0);
        assertEquals(18,system.textSizeSp(false,0),0);assertEquals(18,system.textSizeSp(false,-1),0);
    }
    @Test public void transparentAndOpaqueBackgroundsPreserveTextColour() {
        SubtitleStyleOptions transparent=new SubtitleStyleOptions(false,1,2,0,true,10);
        SubtitleStyleOptions opaque=new SubtitleStyleOptions(false,1,2,100,true,10);
        assertEquals(0,transparent.backgroundColor());assertEquals(0xff000000,opaque.backgroundColor());
        assertEquals(transparent.textColor(),opaque.textColor());
    }
}
