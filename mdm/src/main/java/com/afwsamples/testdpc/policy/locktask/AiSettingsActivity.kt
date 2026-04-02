package com.afwsamples.testdpc.policy.locktask

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.afwsamples.testdpc.databinding.ActivityAiSettingsBinding
import com.afwsamples.testdpc.R
import com.base.services.BridgeStatus
import com.base.services.ClawBotLoginStatus
import com.base.services.IAiConfigService
import com.base.services.IRemoteBridgeService
import com.base.services.IRemoteChannelConfigService
import com.base.services.RemoteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.ext.android.inject
import java.net.HttpURLConnection
import java.net.URL

class AiSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiSettingsBinding
    private val aiConfigService: IAiConfigService by inject()
    private val channelConfig: IRemoteChannelConfigService by inject()
    private val remoteBridge: IRemoteBridgeService by inject()
    private var currentProvider = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupProviderSpinner()
        loadCurrentConfig()
        observeRemoteChannelSummary()

        binding.btnFetchModels.setOnClickListener { fetchModels() }
        binding.btnTestApi.setOnClickListener { testApiConnection() }

        binding.btnOpenRemoteChannelSettings.setOnClickListener {
            startActivity(Intent(this, RemoteChannelSettingsActivity::class.java))
        }

        binding.btnSave.setOnClickListener { saveAndFinish() }
    }

    private fun observeRemoteChannelSummary() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    channelConfig.activeRemoteChannel,
                    remoteBridge.telegramStatus,
                    remoteBridge.feishuStatus,
                    remoteBridge.clawBotStatus,
                    remoteBridge.clawBotLoginStatus
                ) { active, tg, fs, cb, cbLogin ->
                    active to summarizeRemoteLine(active, tg, fs, cb, cbLogin)
                }.collect { (_, line) ->
                    binding.tvRemoteChannelSummary.text = line
                }
            }
        }
    }

    private fun summarizeRemoteLine(
        active: RemoteChannel,
        tg: BridgeStatus,
        fs: BridgeStatus,
        cb: BridgeStatus,
        cbLogin: ClawBotLoginStatus
    ): String {
        val mode = when (active) {
            RemoteChannel.TELEGRAM -> "Telegram"
            RemoteChannel.FEISHU -> getString(R.string.feishu_label)
            RemoteChannel.CLAWBOT -> "ClawBot"
        }
        val detail = when (active) {
            RemoteChannel.TELEGRAM -> bridgeShort(tg)
            RemoteChannel.FEISHU -> bridgeShort(fs)
            RemoteChannel.CLAWBOT -> formatClawBotShort(cb, cbLogin)
        }
        return getString(R.string.channel_status_format, mode, detail)
    }

    private fun bridgeShort(s: BridgeStatus): String = when (s) {
        BridgeStatus.NOT_CONFIGURED -> getString(R.string.status_not_configured)
        BridgeStatus.STOPPED -> getString(R.string.status_stopped)
        BridgeStatus.CONNECTED -> getString(R.string.status_connected)
        BridgeStatus.DISCONNECTED -> getString(R.string.status_disconnected)
    }

    private fun formatClawBotShort(bridge: BridgeStatus, login: ClawBotLoginStatus): String {
        val b = bridgeShort(bridge)
        val l = when (login) {
            ClawBotLoginStatus.NOT_CONFIGURED -> getString(R.string.status_not_configured)
            ClawBotLoginStatus.LOGIN_REQUIRED -> getString(R.string.login_status_need_login)
            ClawBotLoginStatus.QR_READY -> getString(R.string.login_status_qr_ready)
            ClawBotLoginStatus.WAITING_CONFIRM -> getString(R.string.login_status_waiting_confirm)
            ClawBotLoginStatus.CONNECTED -> getString(R.string.login_status_logged_in)
            ClawBotLoginStatus.DISCONNECTED -> getString(R.string.status_disconnected)
            ClawBotLoginStatus.STOPPED -> getString(R.string.status_stopped)
        }
        return getString(R.string.bridge_status_format_kiosk, b, l)
    }

    private fun setupProviderSpinner() {
        val providers = listOf("Kimi Code", "Moonshot", "OpenAI")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, providers)
        binding.spinnerProvider.apply {
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                aiConfigService.saveProviderKey(currentProvider, binding.etApiKey.text.toString().trim())
                val selected = providers[position]
                currentProvider = selected
                val savedKey = aiConfigService.loadProviderKey(selected)
                when (selected) {
                    "Kimi Code" -> {
                        binding.etBaseUrl.setText("https://api.kimi.com/coding")
                        binding.etModel.setText("kimi-k2.5", false)
                        binding.etApiKey.setText(savedKey.ifEmpty { aiConfigService.defaultApiKey })
                    }
                    "Moonshot" -> {
                        binding.etBaseUrl.setText("https://api.moonshot.cn/v1")
                        binding.etModel.setText("kimi-k2-turbo-preview", false)
                        binding.etApiKey.setText(savedKey)
                    }
                    "OpenAI" -> {
                        binding.etBaseUrl.setText("https://api.openai.com/v1/chat/completions")
                        binding.etModel.setText("gpt-4o", false)
                        binding.etApiKey.setText(savedKey)
                    }
                }
            }
        }
    }

    private fun loadCurrentConfig() {
        currentProvider = aiConfigService.provider
        binding.spinnerProvider.setText(currentProvider, false)
        binding.etBaseUrl.setText(aiConfigService.apiUrl)
        val savedKey = aiConfigService.loadProviderKey(currentProvider)
        binding.etApiKey.setText(savedKey.ifEmpty { aiConfigService.apiKey })
        binding.etModel.setText(aiConfigService.model, false)
    }

    private fun saveAndFinish() {
        val provider = binding.spinnerProvider.text.toString()
        val apiKey = binding.etApiKey.text.toString().trim()
        aiConfigService.saveProviderKey(provider, apiKey)
        aiConfigService.updateConfig(
            provider = provider,
            apiUrl = binding.etBaseUrl.text.toString(),
            apiKey = apiKey,
            model = binding.etModel.text.toString()
        )
        finish()
    }

    private fun fetchModels() {
        val provider = binding.spinnerProvider.text.toString()
        val baseUrl = binding.etBaseUrl.text.toString().trim()
        val apiKey = binding.etApiKey.text.toString().trim()

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            showModelListResult(getString(R.string.err_fill_base_url_api_key), isError = true)
            return
        }

        binding.btnFetchModels.isEnabled = false
        showModelListResult(getString(R.string.fetching_models), isError = false)

        lifecycleScope.launch {
            val result = queryModels(provider, baseUrl, apiKey)
            binding.btnFetchModels.isEnabled = true
            if (result.second != null) {
                showModelListResult(result.second!!, isError = true)
            } else {
                val models = result.first
                val adapter = ArrayAdapter(
                    this@AiSettingsActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    models
                )
                binding.etModel.setAdapter(adapter)
                binding.etModel.showDropDown()
                showModelListResult(getString(R.string.models_fetched, models.size), isError = false)
            }
        }
    }

    private suspend fun queryModels(
        provider: String,
        baseUrl: String,
        apiKey: String
    ): Pair<List<String>, String?> = withContext(Dispatchers.IO) {
        try {
            val isKimiCode = provider.equals("Kimi Code", ignoreCase = true)
            val url = if (isKimiCode) {
                "${baseUrl.removeSuffix("/")}/v1/models"
            } else {
                "${baseUrl.removeSuffix("/").removeSuffix("/chat/completions")}/models"
            }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                if (isKimiCode) {
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                } else {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = conn.responseCode
            val respBody = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (code in 200..299) {
                val dataArr = JSONObject(respBody).getJSONArray("data")
                val models = (0 until dataArr.length())
                    .map { dataArr.getJSONObject(it).getString("id") }
                    .sorted()
                models to null
            } else {
                emptyList<String>() to getString(R.string.models_fetch_failed_http, code, respBody)
            }
        } catch (e: Exception) {
            emptyList<String>() to getString(R.string.models_fetch_failed, e.message)
        }
    }

    private fun showModelListResult(text: String, isError: Boolean) {
        binding.tvModelListResult.apply {
            visibility = View.VISIBLE
            this.text = text
            setTextColor(getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark))
        }
    }

    private fun testApiConnection() {
        val provider = binding.spinnerProvider.text.toString()
        val baseUrl = binding.etBaseUrl.text.toString().trim()
        val apiKey = binding.etApiKey.text.toString().trim()
        val model = binding.etModel.text.toString().trim()

        if (baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            showApiResult(getString(R.string.err_fill_api_config), isError = true)
            return
        }

        binding.btnTestApi.isEnabled = false
        showApiResult(getString(R.string.testing_connection), isError = false)

        lifecycleScope.launch {
            val isKimiCode = provider.equals("Kimi Code", ignoreCase = true)
            val result = if (isKimiCode) {
                testKimiCodeApi(baseUrl, apiKey, model)
            } else {
                testOpenAiCompatibleApi(baseUrl, apiKey, model)
            }
            binding.btnTestApi.isEnabled = true
            showApiResult(result.first, result.second)
        }
    }

    private suspend fun testKimiCodeApi(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.removeSuffix("/")}/v1/messages"
            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 64)
                if (!model.contains("k2.5")) {
                    put("temperature", 0.0)
                }
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Say 'OK' if you can hear me.")
                    })
                })
            }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val respBody = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (code in 200..299) {
                val text = JSONObject(respBody).getJSONArray("content")
                    .let { arr ->
                        (0 until arr.length())
                            .map { arr.getJSONObject(it) }
                            .firstOrNull { it.getString("type") == "text" }
                            ?.getString("text") ?: ""
                    }
                getString(R.string.connection_success, text.take(100)) to false
            } else {
                getString(R.string.connection_failed_http, code, respBody) to true
            }
        } catch (e: Exception) {
            getString(R.string.connection_failed, e.message) to true
        }
    }

    private suspend fun testOpenAiCompatibleApi(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = if (baseUrl.contains("chat/completions")) baseUrl
            else "${baseUrl.removeSuffix("/")}/chat/completions"

            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 64)
                if (!model.contains("k2.5")) {
                    put("temperature", 0.0)
                }
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Say 'OK' if you can hear me.")
                    })
                })
            }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val respBody = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (code in 200..299) {
                val text = JSONObject(respBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                getString(R.string.connection_success, text.take(100)) to false
            } else {
                getString(R.string.connection_failed_http, code, respBody) to true
            }
        } catch (e: Exception) {
            getString(R.string.connection_failed, e.message) to true
        }
    }

    private fun showApiResult(text: String, isError: Boolean) {
        binding.tvApiTestResult.apply {
            visibility = View.VISIBLE
            this.text = text
            setTextColor(getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark))
        }
    }
}
