package com.brouken.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class WatchProgressTest {
    @Test public void completedRewatchStaysFull() { assertEquals(100, WatchProgress.percent(120, 9000, true)); }
    @Test public void partialAndUnwatched() {
        assertEquals(25, WatchProgress.percent(2500,10000,false));
        assertEquals(0, WatchProgress.percent(0,10000,false));
    }
    @Test public void invalidDurationAndPositionAreSafe() {
        assertEquals(0, WatchProgress.percent(5,0,false));
        assertEquals(0, WatchProgress.percent(-5,100,false));
        assertEquals(100, WatchProgress.percent(200,100,false));
    }
    @Test public void longVideosDoNotOverflow() { assertEquals(50, WatchProgress.percent(Long.MAX_VALUE/2,Long.MAX_VALUE,false)); }
    @Test public void completedVideoRestartsButRewatchCanResume() {
        assertEquals(0, WatchProgress.resumePosition(9000,9000));
        assertEquals(3000, WatchProgress.resumePosition(3000,9000));
        assertEquals(0, WatchProgress.resumePosition(-1,9000));
    }
}
