package com.andforce.andclaw.bridge

/**
 * ClawBot / iLink only supports text and typing; media is gracefully degraded to a text
 * notification to avoid silently dropping messages.
 */
internal object ClawBotMediaFallbackMessages {

    fun image(caption: String?, fileName: String): String =
        build(kindLabel = "Image / 图片", caption = caption, fileName = fileName)

    fun video(caption: String?, fileName: String): String =
        build(kindLabel = "Video / 视频", caption = caption, fileName = fileName)

    fun audio(caption: String?, fileName: String): String =
        build(kindLabel = "Audio / 音频", caption = caption, fileName = fileName)

    private fun build(kindLabel: String, caption: String?, fileName: String): String {
        val cap = caption?.trim()?.takeIf { it.isNotEmpty() }?.let { ", caption / 说明：$it" } ?: ""
        return "Media saved locally ($kindLabel, file / 文件：$fileName$cap). " +
            "ClawBot media upload not implemented in this project — please check the file on the device. / " +
            "媒体已保存到本地，ClawBot 当前协议在本项目未实现媒体远程发送，请在本机查看原文件。"
    }
}
