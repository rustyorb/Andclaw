package com.andforce.andclaw.bridge

import org.junit.Assert.assertTrue
import org.junit.Test

class ClawBotMediaFallbackMessagesTest {

    @Test
    fun image_containsKindAndFileNameAndDisclaimer() {
        val s = ClawBotMediaFallbackMessages.image(caption = "cap", fileName = "a.png")
        // Bilingual: contains English "Image" and Chinese "图片"
        assertTrue(s.contains("Image"))
        assertTrue(s.contains("图片"))
        assertTrue(s.contains("a.png"))
        // Bilingual caption marker
        assertTrue(s.contains("caption / 说明：cap"))
        // Bilingual disclaimer
        assertTrue(s.contains("ClawBot media upload not implemented"))
        assertTrue(s.contains("未实现媒体远程发送"))
    }

    @Test
    fun video_omitsCaptionWhenBlank() {
        val s = ClawBotMediaFallbackMessages.video(caption = "   ", fileName = "v.mp4")
        assertTrue(s.contains("Video"))
        assertTrue(s.contains("视频"))
        assertTrue(s.contains("v.mp4"))
        assertTrue(!s.contains("说明："))
    }
}
