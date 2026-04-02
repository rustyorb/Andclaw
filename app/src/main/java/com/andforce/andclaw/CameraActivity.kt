package com.andforce.andclaw

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.andforce.andclaw.R
import com.andforce.andclaw.databinding.ActivityCameraBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CAMERA_ACTION = "camera_action"
        const val ACTION_TAKE_PHOTO = "take_photo"
        const val ACTION_START_VIDEO = "start_video"
        const val ACTION_STOP_VIDEO = "stop_video"

        @Volatile
        var lastResult: String? = null

        @Volatile
        var lastPhotoUri: Uri? = null

        @Volatile
        var lastVideoUri: Uri? = null
    }

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraReady = false
    private var pendingAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupButtons()

        pendingAction = intent.getStringExtra(EXTRA_CAMERA_ACTION)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            initCamera()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.getStringExtra(EXTRA_CAMERA_ACTION) ?: return
        if (cameraReady) {
            executeAction(action)
        } else {
            pendingAction = action
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            initCamera()
        } else {
            lastResult = getString(R.string.camera_permission_denied)
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.viewFinder.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA,
                preview, imageCapture, videoCapture
            )

            cameraReady = true
            updateStatus(getString(R.string.camera_ready))

            pendingAction?.let {
                pendingAction = null
                executeAction(it)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupButtons() {
        binding.btnTakePhoto.setOnClickListener { takePhoto() }
        binding.btnStartVideo.setOnClickListener { startVideoRecording() }
        binding.btnStopVideo.setOnClickListener { stopVideoRecording() }
    }

    private fun executeAction(action: String) {
        when (action) {
            ACTION_TAKE_PHOTO -> takePhoto()
            ACTION_START_VIDEO -> startVideoRecording()
            ACTION_STOP_VIDEO -> stopVideoRecording()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        updateStatus(getString(R.string.camera_taking_photo))

        val fileName = "photo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Andclaw")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()

        capture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lastPhotoUri = output.savedUri
                    val msg = getString(R.string.camera_photo_done, fileName)
                    lastResult = msg
                    updateStatus(msg)
                    binding.root.postDelayed({ finish() }, 1500)
                }

                override fun onError(exc: ImageCaptureException) {
                    val msg = getString(R.string.camera_photo_failed, exc.message)
                    lastResult = msg
                    updateStatus(msg)
                }
            }
        )
    }

    @OptIn(ExperimentalPersistentRecording::class)
    private fun startVideoRecording() {
        val vc = videoCapture ?: return
        if (activeRecording != null) {
            updateStatus(getString(R.string.camera_video_in_progress))
            return
        }

        updateStatus(getString(R.string.camera_video_starting))

        val fileName = "video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Andclaw")
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        activeRecording = vc.output
            .prepareRecording(this, outputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(this@CameraActivity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        lastResult = getString(R.string.camera_video_started)
                        updateStatus(getString(R.string.camera_video_in_progress_status))
                        binding.btnStartVideo.isEnabled = false
                        binding.btnStopVideo.isEnabled = true
                        binding.btnTakePhoto.isEnabled = false
                    }

                    is VideoRecordEvent.Finalize -> {
                        val msg = if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                            getString(R.string.camera_video_failed, event.cause?.message)
                        } else {
                            lastVideoUri = event.outputResults.outputUri
                            getString(R.string.camera_video_done, fileName)
                        }
                        lastResult = msg
                        updateStatus(msg)
                        activeRecording = null
                        binding.btnStartVideo.isEnabled = true
                        binding.btnStopVideo.isEnabled = false
                        binding.btnTakePhoto.isEnabled = true
                        binding.root.postDelayed({ finish() }, 1500)
                    }
                }
            }
    }

    private fun stopVideoRecording() {
        val rec = activeRecording
        if (rec == null) {
            updateStatus(getString(R.string.camera_video_not_in_progress))
            return
        }
        updateStatus(getString(R.string.camera_video_stopping))
        rec.stop()
    }

    private fun updateStatus(text: String) {
        binding.tvStatus.text = text
    }

    override fun onDestroy() {
        super.onDestroy()
        activeRecording?.stop()
    }
}
