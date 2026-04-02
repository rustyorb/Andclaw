package com.andforce.andclaw

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.andforce.andclaw.R
import java.io.File

class ScreenRecordService : Service() {

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val CHANNEL_ID = "screen_record_channel"
        private const val NOTIFICATION_ID = 1001

        private var resultCode: Int = 0
        private var resultData: Intent? = null

        var isRecording = false
            private set

        var lastRecordedFile: String? = null
            private set

        fun prepareResult(code: Int, data: Intent) {
            resultCode = code
            resultData = data
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "START" -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startRecording()
            }
            "STOP" -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        val data = resultData ?: run {
            Log.e(TAG, "resultData 为空")
            stopSelf()
            return
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        outputFile = File(getExternalFilesDir(null), "record_${System.currentTimeMillis()}.mp4")

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(6_000_000)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecord",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface, null, null
        )

        mediaRecorder?.start()
        isRecording = true
        Log.d(TAG, "录屏开始: ${outputFile?.absolutePath}")
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "mediaRecorder.stop 异常", e)
        }
        mediaRecorder?.release()
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        isRecording = false

        outputFile?.let { file ->
            if (file.exists()) {
                saveToMediaStore(file)
                lastRecordedFile = file.absolutePath
                Log.d(TAG, "录屏已保存: ${file.absolutePath}")
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveToMediaStore(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Andclaw")
        }
        contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_screen_record_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_screen_record_title))
            .setContentText(getString(R.string.notif_screen_record_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        if (isRecording) stopRecording()
        super.onDestroy()
    }
}
