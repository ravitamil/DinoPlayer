package com.brouken.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptionPackTest {
    @Test public void hindiUsesMultilingualTranslationAndSeparateCacheIdentity() {
        assertEquals("hi",CaptionPack.HINDI.language);
        assertEquals("translate",CaptionPack.HINDI.task);
        assertFalse(CaptionPack.HINDI.aligned);
        assertNotEquals(CaptionPack.ENGLISH.id,CaptionPack.HINDI.id);
        assertNotEquals(CaptionPack.ENGLISH.cacheIdentity(),CaptionPack.HINDI.cacheIdentity());
        assertTrue(CaptionPack.ENGLISH.cacheIdentity().contains(CaptionPack.ALIGNED_DECODER_SHA256));
    }
    @Test public void catalogIsPinnedToHashesAndHttps() {
        for(CaptionPack pack:CaptionPack.ALL)for(CaptionPack.Asset asset:pack.assets) {
            assertTrue(asset.url.startsWith("https://"));
            assertTrue(asset.sha256.matches("[0-9a-f]{64}"));
            assertTrue(asset.bytes>0);
        }
    }
    @Test public void sizeIncludesAllRequiredParts() {
        assertEquals(104271045L,CaptionPack.ENGLISH.downloadBytes());
        assertEquals(161253144L,CaptionPack.HINDI.downloadBytes());
        assertSame(CaptionPack.ENGLISH,CaptionPack.find("unknown"));
    }
}
