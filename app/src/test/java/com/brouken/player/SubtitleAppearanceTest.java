package com.brouken.player;

import androidx.media3.common.text.Cue;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class SubtitleAppearanceTest {
    @Test public void importedPositionAndFontSizeCannotOverrideAppPresentation() {
        Cue imported=new Cue.Builder().setText("Caption").setLine(0.1f,Cue.LINE_TYPE_FRACTION)
                .setPosition(0.05f).setSize(0.2f).setTextSize(72,Cue.TEXT_SIZE_TYPE_ABSOLUTE)
                .setWindowColor(0xffff0000).setVerticalType(Cue.VERTICAL_TYPE_RL).build();
        Cue normalized=SubtitleAppearance.normalize(Collections.singletonList(imported)).get(0);
        Cue live=SubtitleAppearance.text("Caption");
        assertEquals("Caption",normalized.text.toString());
        assertEquals(live.position,normalized.position,0);assertEquals(live.size,normalized.size,0);
        assertEquals(Cue.DIMEN_UNSET,normalized.line,0);assertEquals(Cue.DIMEN_UNSET,normalized.textSize,0);
        assertFalse(normalized.windowColorSet);assertEquals(Cue.TYPE_UNSET,normalized.verticalType);
        assertEquals(0.1f,imported.line,0); // Input remains untouched.
    }
    @Test public void multipleCaptionsRetainContentAndOrder() {
        List<Cue> result=SubtitleAppearance.normalize(Arrays.asList(SubtitleAppearance.text("First\nline"),SubtitleAppearance.text("Second")));
        assertEquals(2,result.size());assertEquals("First\nline",result.get(0).text.toString());assertEquals("Second",result.get(1).text.toString());
        assertTrue(SubtitleAppearance.normalize(Collections.emptyList()).isEmpty());
    }
}
