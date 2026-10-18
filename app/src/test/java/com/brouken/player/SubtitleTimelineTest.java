package com.brouken.player;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class SubtitleTimelineTest {
    @Test public void seeksUseMediaTimeAndClearDuringSilence() {
        SubtitleTimeline timeline = new SubtitleTimeline();
        timeline.addAll(Arrays.asList(new SubtitleTimeline.Cue(8000, 9000, "later"),
                new SubtitleTimeline.Cue(1000, 2000, "first")));
        assertNull(timeline.at(0));
        assertEquals("first", timeline.at(1000).text);
        assertNull(timeline.at(2000));
        assertNull(timeline.at(5000));
        assertEquals("later", timeline.at(8500).text);
        assertEquals("first", timeline.at(1500).text);
        timeline.clear();
        assertTrue(timeline.isEmpty());
        assertNull(timeline.at(1500));
    }

    @Test public void tokenTimingIncludesChunkOffsetAndTokenDuration() {
        List<SubtitleTimeline.Cue> cues = SubtitleTimeline.fromRecognition("Hello world",
                new String[]{"Hello", " world"}, new float[]{0.2f, 0.8f}, new float[]{0.6f, 0.3f}, 8000, 4000);
        assertEquals(1, cues.size());
        assertEquals(8200, cues.get(0).startMs);
        assertEquals(9100, cues.get(0).endMs);
        assertEquals("Hello world", cues.get(0).text);
    }

    @Test public void longRecognitionCannotBecomeAWallOfText() {
        String text = String.join(" ", Collections.nCopies(50, "caption"));
        List<SubtitleTimeline.Cue> cues = SubtitleTimeline.fromRecognition(text, null, null, null, 0, 4000);
        assertTrue(cues.size() > 1);
        long end = 0;
        for (SubtitleTimeline.Cue cue : cues) {
            assertTrue(cue.text.length() <= 65);
            assertTrue(cue.text.split("\n").length <= 2);
            for (String line : cue.text.split("\n")) assertTrue(line.length() <= 32);
            assertTrue(cue.startMs >= end);
            assertTrue(cue.endMs <= 4000);
            end = cue.endMs;
        }
    }

    @Test public void invalidTimestampsUseOnlyTheBoundedSpeechRange() {
        List<SubtitleTimeline.Cue> cues = SubtitleTimeline.fromRecognition("fallback",
                new String[]{"a", " b"}, new float[]{1, 0}, null, 4000, 2000);
        assertEquals(4000, cues.get(0).startMs);
        assertEquals(6000, cues.get(0).endMs);
    }

    @Test public void emptySpeechAndSrtFormatting() {
        assertTrue(SubtitleTimeline.fromRecognition("  ", null, null, null, 0, 1000).isEmpty());
        assertEquals("01:01:01,234", SubtitleTimeline.timestamp(3661234));
        assertEquals("00:00:00,000", SubtitleTimeline.timestamp(-1));
    }

    @Test public void longUnbrokenWordsCannotOverflowCaptionLines() {
        List<SubtitleTimeline.Cue> cues = SubtitleTimeline.fromRecognition(
                String.join("", Collections.nCopies(120, "x")), null, null, null, 0, 4000);
        for (SubtitleTimeline.Cue cue : cues) {
            assertTrue(cue.text.split("\n").length <= 2);
            for (String line : cue.text.split("\n")) assertTrue(line.length() <= 32);
        }
    }
}
