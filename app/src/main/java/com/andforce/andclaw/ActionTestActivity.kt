package com.andforce.andclaw

import android.accessibilityservice.AccessibilityService
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.util.Base64
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.afwsamples.testdpc.common.Util
import com.andforce.andclaw.databinding.ActivityActionTestBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActionTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ActionTest"
        private const val TEST_DOWNLOAD_URL =
            "https://raw.githubusercontent.com/nicehash/NiceHashQuickMiner/main/LICENSE"
    }

    private lateinit var binding: ActivityActionTestBinding

    private val screenCaptureRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            ScreenRecordService.prepareResult(result.resultCode, result.data!!)
            val intent = Intent(this, ScreenRecordService::class.java).apply {
                action = "START"
            }
            startForegroundService(intent)
            binding.btnStartRecord.isEnabled = false
            binding.btnStopRecord.isEnabled = true
            log("Screen recording started")
        } else {
            log("Screen recording cancelled by user")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActionTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupScreenshot()
        setupScreenRecord()
        setupDownload()
        setupTextInput()
        setupGestures()
        setupGlobalActions()
        setupAppManagement()
        setupClipboard()
        setupShare()

        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
        }
    }

    // --- 截屏 ---

    private fun setupScreenshot() {
        binding.btnScreenshot.setOnClickListener {
            val service = AgentAccessibilityService.instance
            if (service == null) {
                log("Error: Accessibility Service not enabled")
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                log("Error: Screenshot requires API 30+, current API ${Build.VERSION.SDK_INT}")
                return@setOnClickListener
            }
            log("Taking screenshot...")
            service.captureScreenshot { bitmap ->
                runOnUiThread {
                    if (bitmap != null) {
                        val path = saveScreenshot(bitmap)
                        log("Screenshot saved: ${bitmap.width}x${bitmap.height}, path: $path")
                    } else {
                        log("Screenshot failed")
                    }
                }
            }
        }

        binding.btnScreenshotToAi.setOnClickListener {
            val service = AgentAccessibilityService.instance
            if (service == null) {
                log("Error: Accessibility Service not enabled")
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                log("Error: Screenshot requires API 30+")
                return@setOnClickListener
            }
            log("Taking screenshot and sending to AI...")
            service.captureScreenshot { bitmap ->
                if (bitmap == null) {
                    runOnUiThread { log("Screenshot failed") }
                    return@captureScreenshot
                }
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                val screenData = AgentAccessibilityService.instance?.captureScreenHierarchy() ?: "Empty"
                runOnUiThread { log("Screenshot done (${baos.size() / 1024}KB), sending to AI...") }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val config = AgentController.config
                        val response = Utils.callLLMWithHistory(
                            "描述当前屏幕内容", screenData, emptyList(), config,
                            this@ActionTestActivity, screenshotBase64 = base64
                        )
                        withContext(Dispatchers.Main) {
                            log("AI response:\n$response")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            log("AI request failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun saveScreenshot(bitmap: Bitmap): String {
        val fileName = "screenshot_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Andclaw")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        return "Pictures/Andclaw/$fileName"
    }

    // --- 录屏 ---

    private fun setupScreenRecord() {
        binding.btnStartRecord.setOnClickListener {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureRequest.launch(projectionManager.createScreenCaptureIntent())
        }

        binding.btnStopRecord.setOnClickListener {
            val intent = Intent(this, ScreenRecordService::class.java).apply {
                action = "STOP"
            }
            startService(intent)
            binding.btnStartRecord.isEnabled = true
            binding.btnStopRecord.isEnabled = false
            log("Screen recording stopped, file: ${ScreenRecordService.lastRecordedFile ?: "unknown"}")
        }
    }

    // --- 文件下载 ---

    private fun setupDownload() {
        binding.btnDownload.setOnClickListener {
            try {
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse(TEST_DOWNLOAD_URL)).apply {
                    setTitle("Andclaw Test Download")
                    setDescription("Downloading test file...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir("Download", "andclaw_test.txt")
                }
                val downloadId = dm.enqueue(request)
                log("Download started, ID=$downloadId, URL=$TEST_DOWNLOAD_URL")
            } catch (e: Exception) {
                log("Download failed: ${e.message}")
            }
        }
    }

    // --- 文本输入 ---

    private fun setupTextInput() {
        binding.btnTextInput.setOnClickListener {
            val service = AgentAccessibilityService.instance
            if (service == null) {
                log("Error: Accessibility Service not enabled")
                return@setOnClickListener
            }

            val editText = EditText(this).apply {
                hint = getString(R.string.action_test_text_input_hint)
                setText("Hello from Andclaw!")
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_test_text_input_title)
                .setView(editText)
                .setPositiveButton(R.string.action_test_btn_inject) { _, _ ->
                    val text = editText.text.toString()
                    val result = service.inputText(text)
                    log("Text inject ${if (result) "success" else "failed"}: \"$text\"")
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    // --- 手势操作 ---

    private fun setupGestures() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val cx = metrics.widthPixels / 2
        val cy = metrics.heightPixels / 2
        val offset = metrics.heightPixels / 4

        binding.btnSwipeUp.setOnClickListener {
            withService { it.swipe(cx, cy + offset, cx, cy - offset) }
            log("Swipe up: ($cx,${cy + offset}) -> ($cx,${cy - offset})")
        }
        binding.btnSwipeDown.setOnClickListener {
            withService { it.swipe(cx, cy - offset, cx, cy + offset) }
            log("Swipe down: ($cx,${cy - offset}) -> ($cx,${cy + offset})")
        }
        binding.btnSwipeLeft.setOnClickListener {
            val xOffset = metrics.widthPixels / 4
            withService { it.swipe(cx + xOffset, cy, cx - xOffset, cy) }
            log("Swipe left: (${cx + xOffset},$cy) -> (${cx - xOffset},$cy)")
        }
        binding.btnSwipeRight.setOnClickListener {
            val xOffset = metrics.widthPixels / 4
            withService { it.swipe(cx - xOffset, cy, cx + xOffset, cy) }
            log("Swipe right: (${cx - xOffset},$cy) -> (${cx + xOffset},$cy)")
        }
        binding.btnLongPress.setOnClickListener {
            withService { it.longPress(cx, cy) }
            log("Long press center: ($cx, $cy)")
        }
    }

    // --- 全局操作 ---

    private fun setupGlobalActions() {
        binding.btnBack.setOnClickListener {
            withService { it.globalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
            log("Global action: Back")
        }
        binding.btnHome.setOnClickListener {
            withService { it.globalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
            log("Global action: Home")
        }
        binding.btnRecents.setOnClickListener {
            withService { it.globalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) }
            log("Global action: Recents")
        }
        binding.btnNotifications.setOnClickListener {
            withService { it.globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
            log("Global action: Notifications")
        }
        binding.btnQuickSettings.setOnClickListener {
            withService { it.globalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) }
            log("Global action: Quick Settings")
        }
    }

    // --- 应用管理 (DPM) ---

    private fun setupAppManagement() {
        val dpmBridge by lazy { DpmBridge(this) }

        binding.btnInstallApk.setOnClickListener {
            if (!Util.isDeviceOwner(this)) {
                log("Error: Device Owner permission required")
                return@setOnClickListener
            }
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            log("Scanning: ${downloadDir.absolutePath}, exists=${downloadDir.exists()}")
            val apkFiles = downloadDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            if (apkFiles.isEmpty()) {
                log("No APK files in Downloads directory")
                return@setOnClickListener
            }

            val names = apkFiles.map { "${it.name} (${it.length() / 1024}KB)" }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_test_select_apk)
                .setItems(names) { _, which ->
                    val file = apkFiles[which]
                    log("Installing: ${file.name} …")
                    val result = dpmBridge.execute("installPackage", mapOf("file_path" to file.absolutePath))
                    log("Install result: ${result.message}")
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        binding.btnUninstallApp.setOnClickListener {
            if (!Util.isDeviceOwner(this)) {
                log("Error: Device Owner permission required")
                return@setOnClickListener
            }
            val editText = EditText(this).apply { hint = getString(R.string.action_test_uninstall_pkg_hint) }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_test_uninstall_title)
                .setView(editText)
                .setPositiveButton(R.string.action_test_btn_uninstall) { _, _ ->
                    val pkg = editText.text.toString().trim()
                    if (pkg.isNotEmpty()) {
                        log("Uninstalling: $pkg …")
                        val result = dpmBridge.execute("uninstallPackage", mapOf("package_name" to pkg))
                        log("Uninstall result: ${result.message}")
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        binding.btnGrantPermission.setOnClickListener {
            if (!Util.isDeviceOwner(this)) {
                log("Error: Device Owner permission required")
                return@setOnClickListener
            }
            val editText = EditText(this).apply {
                hint = getString(R.string.action_test_grant_perm_pkg_hint)
                setText("com.example.app")
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_test_grant_perm_title)
                .setMessage(R.string.action_test_grant_perm_msg)
                .setView(editText)
                .setPositiveButton(R.string.action_test_btn_grant) { _, _ ->
                    val pkg = editText.text.toString().trim()
                    if (pkg.isNotEmpty()) {
                        grantAllPermissions(pkg, dpmBridge)
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    private fun grantAllPermissions(packageName: String, dpmBridge: DpmBridge) {
        try {
            val pkgInfo = packageManager.getPackageInfo(packageName,
                PackageManager.GET_PERMISSIONS)
            val permissions = pkgInfo.requestedPermissions ?: emptyArray()
            var granted = 0
            for (perm in permissions) {
                val result = dpmBridge.execute("setPermissionGrantState", mapOf(
                    "package_name" to packageName,
                    "permission" to perm,
                    "grant_state" to 1
                ))
                if (result.success) granted++
            }
            log("Granted $granted/${permissions.size} permissions to $packageName")
        } catch (e: Exception) {
            log("Grant failed: ${e.message}")
        }
    }

    // --- 剪贴板 ---

    private fun setupClipboard() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        binding.btnCopy.setOnClickListener {
            val testText = "Andclaw Test ${System.currentTimeMillis()}"
            cm.setPrimaryClip(ClipData.newPlainText("test", testText))
            log("Copied to clipboard: \"$testText\"")
        }

        binding.btnPaste.setOnClickListener {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: "(non-text content)"
                log("Clipboard content: \"$text\"")
            } else {
                log("Clipboard is empty")
            }
        }
    }

    // --- 分享 ---

    private fun setupShare() {
        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Hello from Andclaw! Share test")
            }
            startActivity(Intent.createChooser(shareIntent, "Share to"))
            log("Share initiated")
        }
    }

    private inline fun withService(action: (AgentAccessibilityService) -> Unit): Boolean {
        val service = AgentAccessibilityService.instance
        if (service == null) {
            log("Error: Accessibility Service not enabled")
            return false
        }
        action(service)
        return true
    }

    // --- 日志 ---

    private fun log(msg: String) {
        Log.d(TAG, msg)
        runOnUiThread {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            binding.tvLog.append("[$time] $msg\n")
            binding.logScrollView.post {
                binding.logScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }
}
