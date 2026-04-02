package com.afwsamples.testdpc.policy.locktask

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.afwsamples.testdpc.R
import com.afwsamples.testdpc.databinding.ActivityRemoteChannelSettingsBinding
import com.base.services.BridgeStatus
import com.base.services.ClawBotLoginStatus
import com.base.services.ClawBotQrPollPhase
import com.base.services.IRemoteBridgeService
import com.base.services.IRemoteChannelConfigService
import com.base.services.RemoteChannel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.android.ext.android.inject
import java.net.HttpURLConnection
import java.net.URL

class RemoteChannelSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoteChannelSettingsBinding
    private val channelConfig: IRemoteChannelConfigService by inject()
    private val remoteBridge: IRemoteBridgeService by inject()
    private var clawBotLoginJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteChannelSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        intent.getStringExtra(EXTRA_INITIAL_CHANNEL)?.let { name ->
            runCatching { RemoteChannel.valueOf(name) }.getOrNull()?.let { ch ->
                channelConfig.setActiveRemoteChannel(ch)
            }
        }

        loadChannelConfig()
        observeClawBotStatusLine()
        observeFeishuStatusLine()
        setupConnectionModeChips()

        binding.btnTestTg.setOnClickListener { testTelegram() }

        binding.btnClawBotLogin.setOnClickListener { startClawBotQrLogin() }
        binding.btnClawBotClearAuth.setOnClickListener {
            cancelClawBotLogin()
            channelConfig.clearClawBotAuthState()
            remoteBridge.startEligibleBridges()
            hideQrCode()
            showClawBotResult(getString(R.string.clawbot_login_cleared), isError = false)
        }

        binding.btnTestFeishu.setOnClickListener { testFeishu() }

        binding.btnSave.setOnClickListener { saveAndFinish() }
    }

    override fun onDestroy() {
        cancelClawBotLogin()
        super.onDestroy()
    }

    private fun setupConnectionModeChips() {
        syncChipsFromConfig()
        binding.chipGroupConnectionMode.setOnCheckedChangeListener(chipListener)
        refreshDetailCardVisibility()
    }

    private val chipListener =
        com.google.android.material.chip.ChipGroup.OnCheckedChangeListener { _, checkedId ->
            if (checkedId == View.NO_ID) return@OnCheckedChangeListener
            val ch = chipIdToChannel(checkedId) ?: return@OnCheckedChangeListener
            if (channelConfig.getActiveRemoteChannel() == ch) return@OnCheckedChangeListener
            channelConfig.setActiveRemoteChannel(ch)
            refreshDetailCardVisibility()
        }

    /** 与 Chip 三选一联动，仅展示当前通道的配置卡片。 */
    private fun refreshDetailCardVisibility() {
        when (channelConfig.getActiveRemoteChannel()) {
            RemoteChannel.TELEGRAM -> {
                binding.cardTelegramConfig.visibility = View.VISIBLE
                binding.cardClawbotConfig.visibility = View.GONE
                binding.cardFeishuConfig.visibility = View.GONE
                cancelClawBotLogin()
                hideQrCode()
            }
            RemoteChannel.CLAWBOT -> {
                binding.cardTelegramConfig.visibility = View.GONE
                binding.cardClawbotConfig.visibility = View.VISIBLE
                binding.cardFeishuConfig.visibility = View.GONE
            }
            RemoteChannel.FEISHU -> {
                binding.cardTelegramConfig.visibility = View.GONE
                binding.cardClawbotConfig.visibility = View.GONE
                binding.cardFeishuConfig.visibility = View.VISIBLE
                cancelClawBotLogin()
                hideQrCode()
            }
        }
    }

    private fun chipIdToChannel(id: Int): RemoteChannel? = when (id) {
        R.id.chip_telegram -> RemoteChannel.TELEGRAM
        R.id.chip_feishu -> RemoteChannel.FEISHU
        R.id.chip_clawbot -> RemoteChannel.CLAWBOT
        else -> null
    }

    private fun syncChipsFromConfig() {
        binding.chipGroupConnectionMode.setOnCheckedChangeListener(null)
        when (channelConfig.getActiveRemoteChannel()) {
            RemoteChannel.TELEGRAM -> binding.chipGroupConnectionMode.check(R.id.chip_telegram)
            RemoteChannel.FEISHU -> binding.chipGroupConnectionMode.check(R.id.chip_feishu)
            RemoteChannel.CLAWBOT -> binding.chipGroupConnectionMode.check(R.id.chip_clawbot)
        }
        binding.chipGroupConnectionMode.setOnCheckedChangeListener(chipListener)
    }

    private fun loadChannelConfig() {
        binding.etTgToken.setText(channelConfig.tgToken)
        val savedChatId = channelConfig.getTgChatId()
        binding.etTgChatId.setText(if (savedChatId == 0L) "" else savedChatId.toString())
        binding.etFeishuAppId.setText(channelConfig.getFeishuAppId())
        binding.etFeishuAppSecret.setText(channelConfig.getFeishuAppSecret())
    }

    private fun saveAndFinish() {
        channelConfig.setTgToken(binding.etTgToken.text.toString().trim())
        val chatId = binding.etTgChatId.text.toString().trim().toLongOrNull() ?: 0L
        channelConfig.setTgChatId(chatId)
        channelConfig.setFeishuAppId(binding.etFeishuAppId.text.toString().trim())
        channelConfig.setFeishuAppSecret(binding.etFeishuAppSecret.text.toString().trim())
        remoteBridge.startEligibleBridges()
        finish()
    }

    // region ClawBot QR Login

    private fun startClawBotQrLogin() {
        cancelClawBotLogin()
        binding.btnClawBotLogin.isEnabled = false
        binding.btnClawBotLogin.text = getString(R.string.clawbot_fetching_qr)
        showClawBotResult(getString(R.string.clawbot_fetching_qr), isError = false)
        hideQrCode()

        clawBotLoginJob = lifecycleScope.launch {
            try {
                runClawBotLoginFlow()
            } catch (e: Exception) {
                if (isActive) {
                    showClawBotResult(getString(R.string.clawbot_login_failed, e.message), isError = true)
                }
            } finally {
                binding.btnClawBotLogin.isEnabled = true
                binding.btnClawBotLogin.text = getString(R.string.btn_clawbot_login)
            }
        }
    }

    private suspend fun runClawBotLoginFlow() {
        var qrRefreshCount = 0
        val maxQrRefresh = 3

        while (qrRefreshCount < maxQrRefresh) {
            val qrResult = remoteBridge.requestClawBotQrCode()

            val qrBitmap = withContext(Dispatchers.Default) {
                generateQrBitmap(qrResult.qrcodeImgContent, 600)
            }

            withContext(Dispatchers.Main) {
                binding.ivClawBotQr.setImageBitmap(qrBitmap)
                binding.ivClawBotQr.visibility = View.VISIBLE
                binding.tvClawBotQrHint.visibility = View.VISIBLE
                binding.tvClawBotQrHint.text = getString(R.string.clawbot_qr_hint)
                showClawBotResult(getString(R.string.clawbot_waiting_scan), isError = false)
                binding.btnClawBotLogin.text = getString(R.string.clawbot_login_cancelled)
                binding.btnClawBotLogin.isEnabled = true
                binding.btnClawBotLogin.setOnClickListener {
                    cancelClawBotLogin()
                    hideQrCode()
                    showClawBotResult(getString(R.string.clawbot_login_cancelled), isError = false)
                    binding.btnClawBotLogin.text = getString(R.string.btn_clawbot_login)
                    binding.btnClawBotLogin.setOnClickListener { startClawBotQrLogin() }
                }
            }

            val deadline = System.currentTimeMillis() + 5 * 60 * 1000L

            while (System.currentTimeMillis() < deadline) {
                delay(1500)
                val pollResult = remoteBridge.pollClawBotQrCodeStatus(qrResult.qrcode)

                when (pollResult.phase) {
                    ClawBotQrPollPhase.WAIT -> { /* keep polling */ }
                    ClawBotQrPollPhase.SCANED -> {
                        withContext(Dispatchers.Main) {
                            binding.tvClawBotQrHint.text = getString(R.string.clawbot_scanned_confirm)
                            showClawBotResult(getString(R.string.clawbot_scanned_confirm), isError = false)
                        }
                    }
                    ClawBotQrPollPhase.CONFIRMED -> {
                        withContext(Dispatchers.Main) {
                            hideQrCode()
                            if (pollResult.authState != null) {
                                showClawBotResult(getString(R.string.clawbot_login_success), isError = false)
                                remoteBridge.startEligibleBridges()
                            } else {
                                showClawBotResult(getString(R.string.clawbot_incomplete_credentials), isError = true)
                            }
                        }
                        return
                    }
                    ClawBotQrPollPhase.EXPIRED -> {
                        qrRefreshCount++
                        withContext(Dispatchers.Main) {
                            if (qrRefreshCount < maxQrRefresh) {
                                showClawBotResult(
                                    getString(R.string.clawbot_qr_expired_refresh, qrRefreshCount, maxQrRefresh),
                                    isError = false
                                )
                            }
                        }
                        break
                    }
                    ClawBotQrPollPhase.UNKNOWN -> {
                        withContext(Dispatchers.Main) {
                            hideQrCode()
                            showClawBotResult(getString(R.string.clawbot_unknown_status), isError = true)
                        }
                        return
                    }
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                qrRefreshCount++
                withContext(Dispatchers.Main) {
                    if (qrRefreshCount >= maxQrRefresh) {
                        hideQrCode()
                        showClawBotResult(getString(R.string.clawbot_login_timeout), isError = true)
                    } else {
                        showClawBotResult(getString(R.string.clawbot_refresh_qr), isError = false)
                    }
                }
            }
        }

        if (qrRefreshCount >= maxQrRefresh) {
            withContext(Dispatchers.Main) {
                hideQrCode()
                showClawBotResult(getString(R.string.clawbot_too_many_retries), isError = true)
            }
        }
    }

    private fun cancelClawBotLogin() {
        clawBotLoginJob?.cancel()
        clawBotLoginJob = null
    }

    private fun hideQrCode() {
        binding.ivClawBotQr.visibility = View.GONE
        binding.ivClawBotQr.setImageBitmap(null)
        binding.tvClawBotQrHint.visibility = View.GONE
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    // endregion

    // region ClawBot Status

    private fun observeClawBotStatusLine() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    remoteBridge.clawBotStatus,
                    remoteBridge.clawBotLoginStatus
                ) { bridge, login -> bridge to login }.collect { (bridge, login) ->
                    binding.tvClawBotStatus.text = formatClawBotStatusLine(bridge, login)
                    updateClawBotLoginButtonText(login)
                }
            }
        }
    }

    private fun formatClawBotStatusLine(bridge: BridgeStatus, login: ClawBotLoginStatus): String {
        val b = when (bridge) {
            BridgeStatus.NOT_CONFIGURED -> getString(R.string.status_not_configured)
            BridgeStatus.STOPPED -> getString(R.string.status_stopped)
            BridgeStatus.CONNECTED -> getString(R.string.status_connected)
            BridgeStatus.DISCONNECTED -> getString(R.string.status_disconnected)
        }
        val l = when (login) {
            ClawBotLoginStatus.NOT_CONFIGURED -> getString(R.string.status_not_configured)
            ClawBotLoginStatus.LOGIN_REQUIRED -> getString(R.string.login_status_need_login)
            ClawBotLoginStatus.QR_READY -> getString(R.string.login_status_qr_ready)
            ClawBotLoginStatus.WAITING_CONFIRM -> getString(R.string.login_status_waiting_confirm)
            ClawBotLoginStatus.CONNECTED -> getString(R.string.login_status_logged_in)
            ClawBotLoginStatus.DISCONNECTED -> getString(R.string.status_disconnected)
            ClawBotLoginStatus.STOPPED -> getString(R.string.status_stopped)
        }
        return getString(R.string.bridge_status_format, b, l)
    }

    private fun updateClawBotLoginButtonText(login: ClawBotLoginStatus) {
        if (clawBotLoginJob?.isActive == true) return
        binding.btnClawBotLogin.text = when (login) {
            ClawBotLoginStatus.CONNECTED -> getString(R.string.btn_re_login)
            else -> getString(R.string.btn_clawbot_login)
        }
    }

    private fun showClawBotResult(text: String, isError: Boolean) {
        binding.tvClawBotResult.apply {
            visibility = View.VISIBLE
            this.text = text
            setTextColor(getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark))
        }
    }

    // endregion

    // region Telegram 测试

    private fun testTelegram() {
        val token = binding.etTgToken.text.toString().trim()
        if (token.isEmpty()) {
            showTgResult(getString(R.string.tg_err_fill_token), isError = true)
            return
        }

        binding.btnTestTg.isEnabled = false
        showTgResult(getString(R.string.tg_testing), isError = false)

        lifecycleScope.launch {
            val result = testTgGetMe(token)
            binding.btnTestTg.isEnabled = true
            showTgResult(result.first, result.second)
        }
    }

    private suspend fun testTgGetMe(token: String): Pair<String, Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/getMe"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val code = conn.responseCode
                val respBody = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                conn.disconnect()

                if (code in 200..299) {
                    val json = JSONObject(respBody)
                    if (json.optBoolean("ok")) {
                        val bot = json.getJSONObject("result")
                        val name = bot.optString("first_name", "")
                        val username = bot.optString("username", "")
                        getString(R.string.tg_success, name, username) to false
                    } else {
                        getString(R.string.tg_invalid_token, json.optString("description")) to true
                    }
                } else {
                    getString(R.string.tg_failed_http, code, respBody) to true
                }
            } catch (e: Exception) {
                getString(R.string.connection_failed, e.message) to true
            }
        }

    private fun showTgResult(text: String, isError: Boolean) {
        binding.tvTgTestResult.apply {
            visibility = View.VISIBLE
            this.text = text
            setTextColor(getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark))
        }
    }

    // endregion

    // region Feishu Status

    private fun observeFeishuStatusLine() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                remoteBridge.feishuStatus.collect { status ->
                    binding.tvFeishuStatus.text = formatFeishuStatusLine(status)
                }
            }
        }
    }

    private fun formatFeishuStatusLine(status: BridgeStatus): String {
        val s = when (status) {
            BridgeStatus.NOT_CONFIGURED -> getString(R.string.status_not_configured)
            BridgeStatus.STOPPED -> getString(R.string.status_stopped)
            BridgeStatus.CONNECTED -> getString(R.string.status_connected) + " ✓"
            BridgeStatus.DISCONNECTED -> getString(R.string.status_disconnected)
        }
        return "Status: $s"
    }

    // endregion

    // region Feishu Test

    private fun testFeishu() {
        val appId = binding.etFeishuAppId.text.toString().trim()
        val appSecret = binding.etFeishuAppSecret.text.toString().trim()

        if (appId.isEmpty() || appSecret.isEmpty()) {
            showFeishuResult(getString(R.string.err_fill_api_config), isError = true)
            return
        }

        binding.btnTestFeishu.isEnabled = false
        showFeishuResult(getString(R.string.testing_connection), isError = false)

        lifecycleScope.launch {
            val result = testFeishuToken(appId, appSecret)
            binding.btnTestFeishu.isEnabled = true
            showFeishuResult(result.first, result.second)
        }
    }

    private suspend fun testFeishuToken(appId: String, appSecret: String): Pair<String, Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"
                val body = JSONObject().apply {
                    put("app_id", appId)
                    put("app_secret", appSecret)
                }
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                val respBody = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                conn.disconnect()

                if (code in 200..299) {
                    val json = JSONObject(respBody)
                    val code = json.optInt("code", -1)
                    if (code == 0) {
                        getString(R.string.connection_success, "") to false
                    } else {
                        getString(R.string.tg_invalid_token, json.optString("msg")) to true
                    }
                } else {
                    getString(R.string.connection_failed_http, code, respBody) to true
                }
            } catch (e: Exception) {
                getString(R.string.connection_failed, e.message) to true
            }
        }

    private fun showFeishuResult(text: String, isError: Boolean) {
        binding.tvFeishuTestResult.apply {
            visibility = View.VISIBLE
            this.text = text
            setTextColor(getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark))
        }
    }

    // endregion

    companion object {
        const val EXTRA_INITIAL_CHANNEL = "extra_initial_channel"
    }
}
