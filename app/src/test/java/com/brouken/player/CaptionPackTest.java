package com.brouken.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptionPackTest {
    @Test public void multilingualPackSupportsAutoDetectAndTranscribeDefault() {
        assertEquals("whisper-base-multilingual-v1", CaptionPack.MULTILINGUAL.id);
        assertEquals("auto", CaptionPack.MULTILINGUAL.language);
        assertEquals("transcribe", CaptionPack.MULTILINGUAL.task);
        assertFalse(CaptionPack.MULTILINGUAL.aligned);
        assertNotNull(CaptionPack.MULTILINGUAL.cacheIdentity());
        assertTrue(CaptionPack.MULTILINGUAL.cacheIdentity().startsWith("whisper-base-multilingual-v1:auto:transcribe"));
    }

    @Test public void catalogIsPinnedToHashesAndHttps() {
        for(CaptionPack pack:CaptionPack.ALL)for(CaptionPack.Asset asset:pack.assets) {
            assertTrue(asset.url.startsWith("https://"));
            assertTrue(asset.sha256.matches("[0-9a-f]{64}"));
            assertTrue(asset.bytes>0);
        }
    }

    @Test public void sizeIncludesAllRequiredParts() {
        assertEquals(161253144L, CaptionPack.MULTILINGUAL.downloadBytes());
        assertSame(CaptionPack.MULTILINGUAL, CaptionPack.find("unknown"));
        assertSame(CaptionPack.MULTILINGUAL, CaptionPack.find("hindi-base-translate-v1"));
        assertSame(CaptionPack.MULTILINGUAL, CaptionPack.find("english-tiny-aligned-v1"));
        assertSame(CaptionPack.MULTILINGUAL, CaptionPack.ENGLISH);
        assertSame(CaptionPack.MULTILINGUAL, CaptionPack.HINDI);
    }
}
