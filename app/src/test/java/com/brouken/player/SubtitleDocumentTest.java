package com.brouken.player;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class SubtitleDocumentTest {
    @Test public void srtRoundTripPreservesTextAndTimeline() throws Exception {
        String input="1\n00:00:01,250 --> 00:00:03,000\nHello\nworld\n\n2\n00:00:04,500 --> 00:00:05,000\nAgain\n";
        List<SubtitleTimeline.Cue> cues=SubtitleDocument.read(new StringReader(input));
        assertEquals(2,cues.size());assertEquals(1250,cues.get(0).startMs);assertEquals("Hello\nworld",cues.get(0).text);
        StringWriter output=new StringWriter();SubtitleDocument.write(output,cues,false);
        assertEquals(cues.get(1).startMs,SubtitleDocument.read(new StringReader(output.toString())).get(1).startMs);
    }
    @Test public void vttAcceptsCueSettingsAndExportsDotTimes() throws Exception {
        List<SubtitleTimeline.Cue> cues=SubtitleDocument.read(new StringReader("WEBVTT\n\nNOTE comment\nignored\n\nid\n00:01.25 --> 00:02.500 align:start\nCaption\n"));
        assertEquals(1250,cues.get(0).startMs);StringWriter out=new StringWriter();SubtitleDocument.write(out,cues,true);
        assertTrue(out.toString().startsWith("WEBVTT\n\n"));assertTrue(out.toString().contains("00:00:01.250 --> 00:00:02.500"));
    }
    @Test public void negativeOffsetDropsOnlyCaptionsEntirelyBeforeStart() throws Exception {
        List<SubtitleTimeline.Cue> cues=Arrays.asList(new SubtitleTimeline.Cue(100,400,"gone"),new SubtitleTimeline.Cue(450,900,"clipped"),new SubtitleTimeline.Cue(1200,1800,"later"));
        List<SubtitleTimeline.Cue> shifted=SubtitleDocument.shifted(cues,-500);
        assertEquals(2,shifted.size());assertEquals(0,shifted.get(0).startMs);assertEquals(400,shifted.get(0).endMs);assertEquals(700,shifted.get(1).startMs);
    }
    @Test(expected=IOException.class) public void invalidMinuteIsRejected() throws Exception { SubtitleDocument.parseTime("00:61:01.000"); }
    @Test(expected=IOException.class) public void endBeforeStartIsRejected() throws Exception { SubtitleDocument.read(new StringReader("1\n00:00:02,000 --> 00:00:01,000\nWrong\n")); }
    @Test public void overlappingCuesParseAndSaveCorrectly() throws Exception {
        String input = "1\n00:00:01,000 --> 00:00:03,000\nSpeaker 1\n\n2\n00:00:02,000 --> 00:00:04,000\nSpeaker 2\n";
        List<SubtitleTimeline.Cue> cues = SubtitleDocument.read(new StringReader(input));
        assertEquals(2, cues.size());
        assertEquals(1000, cues.get(0).startMs);
        assertEquals(3000, cues.get(0).endMs);
        assertEquals(2000, cues.get(1).startMs);
        assertEquals(4000, cues.get(1).endMs);

        StringWriter output = new StringWriter();
        SubtitleDocument.write(output, cues, false);
        List<SubtitleTimeline.Cue> roundTrip = SubtitleDocument.read(new StringReader(output.toString()));
        assertEquals(2, roundTrip.size());
        assertEquals("Speaker 1", roundTrip.get(0).text);
        assertEquals("Speaker 2", roundTrip.get(1).text);
        assertEquals(1000, roundTrip.get(0).startMs);
        assertEquals(2000, roundTrip.get(1).startMs);
    }
    @Test public void identicalStartTimeCuesAreSupported() throws Exception {
        List<SubtitleTimeline.Cue> cues = Arrays.asList(
            new SubtitleTimeline.Cue(1000, 3000, "Cue A"),
            new SubtitleTimeline.Cue(1000, 4000, "Cue B")
        );
        SubtitleDocument.validate(cues);
        StringWriter output = new StringWriter();
        SubtitleDocument.write(output, cues, false);
        List<SubtitleTimeline.Cue> roundTrip = SubtitleDocument.read(new StringReader(output.toString()));
        assertEquals(2, roundTrip.size());
        assertEquals(1000, roundTrip.get(0).startMs);
        assertEquals(1000, roundTrip.get(1).startMs);
    }
    @Test public void nestedOverlappingCuesSupported() throws Exception {
        List<SubtitleTimeline.Cue> cues = Arrays.asList(
            new SubtitleTimeline.Cue(1000, 5000, "Outer"),
            new SubtitleTimeline.Cue(2000, 3000, "Inner")
        );
        SubtitleDocument.validate(cues);
        StringWriter output = new StringWriter();
        SubtitleDocument.write(output, cues, true);
        List<SubtitleTimeline.Cue> roundTrip = SubtitleDocument.read(new StringReader(output.toString()));
        assertEquals(2, roundTrip.size());
        assertEquals("Outer", roundTrip.get(0).text);
        assertEquals("Inner", roundTrip.get(1).text);
    }
    @Test public void shiftingOverlappingCuesClampsStartCorrectly() throws Exception {
        List<SubtitleTimeline.Cue> cues = Arrays.asList(
            new SubtitleTimeline.Cue(500, 2500, "Early"),
            new SubtitleTimeline.Cue(1000, 3000, "Later")
        );
        List<SubtitleTimeline.Cue> shifted = SubtitleDocument.shifted(cues, -800);
        assertEquals(2, shifted.size());
        assertEquals(0, shifted.get(0).startMs);
        assertEquals(1700, shifted.get(0).endMs);
        assertEquals(200, shifted.get(1).startMs);
        assertEquals(2200, shifted.get(1).endMs);
    }
    @Test(expected=IOException.class) public void unsortedCuesAreRejected() throws Exception {
        SubtitleDocument.validate(Arrays.asList(new SubtitleTimeline.Cue(2000, 3000, "two"), new SubtitleTimeline.Cue(1000, 2000, "one")));
    }
    @Test(expected=IOException.class) public void unsupportedDocumentFailsClearly() throws Exception { SubtitleDocument.read(new StringReader("[Script Info]\nASS is not an SRT document")); }
    @Test(expected=IOException.class) public void shiftingEverythingBeforeStartIsRejected() throws Exception { SubtitleDocument.shifted(Collections.singletonList(new SubtitleTimeline.Cue(0,100,"one")),-500); }
    @Test(expected=IOException.class) public void excessiveCaptionTextIsBounded() throws Exception { char[] chars=new char[16385];Arrays.fill(chars,'x');SubtitleDocument.read(new StringReader("1\n00:00:00,000 --> 00:00:01,000\n"+new String(chars))); }
    @Test public void unicodeAndHourTimesSurviveExport() throws Exception {
        List<SubtitleTimeline.Cue> cues=Collections.singletonList(new SubtitleTimeline.Cue(3600123,3601456,"नमस्ते / வணக்கம்"));
        StringWriter output=new StringWriter();SubtitleDocument.write(output,cues,true);
        List<SubtitleTimeline.Cue> restored=SubtitleDocument.read(new StringReader(output.toString()));assertEquals(cues.get(0).text,restored.get(0).text);assertEquals(3600123,restored.get(0).startMs);
    }
}
