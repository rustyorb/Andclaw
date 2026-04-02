package com.afwsamples.testdpc.policy.locktask

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.UserManager
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.andforce.andclaw.DeviceAdminReceiver
import com.andforce.mdm.center.DeviceStatusViewModel
import com.andforce.mdm.center.AppUtils
import com.afwsamples.testdpc.DevicePolicyManagerGateway
import com.afwsamples.testdpc.DevicePolicyManagerGatewayImpl
import com.afwsamples.testdpc.R
import com.afwsamples.testdpc.databinding.ActivitySetupKioskLayoutBinding
import com.afwsamples.testdpc.policy.locktask.viewmodule.KioskViewModule
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import com.base.services.BridgeStatus
import com.base.services.ClawBotLoginStatus
import com.base.services.RemoteChannel
import com.base.services.IRemoteBridgeService
import com.base.services.IRemoteChannelConfigService
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

open class SetupKioskModeActivity : AppCompatActivity() {
    private var mAdminComponentName: ComponentName? = null

    private var mDevicePolicyManager: DevicePolicyManager? = null
    private var mPackageManager: PackageManager? = null

    private var binding: ActivitySetupKioskLayoutBinding? = null


    private val kioskViewModule: KioskViewModule by viewModel()

    private var mDevicePolicyManagerGateway: DevicePolicyManagerGateway? = null
    private var mUserManager: UserManager? = null

    private var connectivityManager: ConnectivityManager? = null

    private var usbEnableDebugAlertDialog: AlertDialog? = null
    private var permissionGuideDialog: AlertDialog? = null

    private val deviceStatusViewModel: DeviceStatusViewModel by inject()

    private val channelConfig: IRemoteChannelConfigService by inject()
    private val remoteBridgeService: IRemoteBridgeService by inject()

    private var appsActivityClickCount = 0
    private var lastAppsClickTime = 0L


    private companion object {
        const val APPS_CLICK_TIMEOUT = 5000L // 5秒内需要完成5次点击
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mDevicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        mPackageManager = packageManager
        mUserManager = getSystemService(UserManager::class.java)

        binding = ActivitySetupKioskLayoutBinding.inflate(layoutInflater)
        binding?.let { binding ->
            setContentView(binding.root)
            
            // 设置网络按钮点击事件
            binding.setupNetwork.setOnClickListener {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            
            // 修改设备管理员按钮点击事件
            binding.setupDeviceOwner.setOnClickListener {
                if (kioskViewModule.deviceOwnerStateFlow.value) {
                    showRemoveDeviceOwnerDialog()
                } else {
                    showDeviceOwnerInstructions()
                }
            }

            binding.openChatActivity.setOnClickListener {
                openChatActivity()
            }

            binding.openTestActivity.setOnClickListener {
                openTestActivity()
            }

            binding.openAiSettings.setOnClickListener {
                openAiSettings()
            }

            binding.setupRemoteChannel.setOnClickListener {
                openRemoteChannelSettings()
            }

            setupRemoteChannelChips()
        }

        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        // 监听网络状态
        lifecycleScope.launch {
            connectivityManager?.let { connectivityManager ->
                deviceStatusViewModel.observeNetworkState(connectivityManager).collect { isConnected ->
                    binding?.apply {
                        networkStatus.text = if (isConnected) getString(R.string.network_connected) else getString(R.string.network_not_connected)
                        setupNetwork.visibility = if (!isConnected) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        // 如果网络已经链接，设置状态
        if (deviceStatusViewModel.isNetworkConnected(connectivityManager)) {
            binding?.apply {
                networkStatus.text = getString(R.string.network_connected)
                setupNetwork.visibility = View.GONE
            }
        }

        // 监听设备管理员状态
        lifecycleScope.launch {
            kioskViewModule.deviceOwnerStateFlow.collect { isDeviceOwner ->
                if (isDeviceOwner) {
                    mAdminComponentName = DeviceAdminReceiver.getComponentName(this@SetupKioskModeActivity)

                    mDevicePolicyManagerGateway =
                        DevicePolicyManagerGatewayImpl(
                            mDevicePolicyManager!!,
                            mUserManager!!,
                            mPackageManager!!,
                            getSystemService(LocationManager::class.java),
                            mAdminComponentName
                        )
                    usbEnableDebugAlertDialog?.dismiss()

                    mDevicePolicyManagerGateway?.setPasswordQuality(0, {}, {})
                    mDevicePolicyManagerGateway?.setKeyguardDisabled(true, {}, {})

                    remoteBridgeService.startEligibleBridges()
                } else {
                    remoteBridgeService.stopTelegramBridge()
                }

                binding?.apply {
                    deviceOwnerStatus.text = if (isDeviceOwner) getString(R.string.device_owner_enabled) else getString(R.string.device_owner_not_enabled)
                    setupDeviceOwner.text = if (isDeviceOwner) getString(R.string.btn_remove_device_owner) else getString(R.string.btn_setup_device_owner)
                    setupDeviceOwner.visibility = View.VISIBLE
                }

            }
        }

        lifecycleScope.launch {
            combine(
                channelConfig.activeRemoteChannel,
                remoteBridgeService.telegramStatus,
                remoteBridgeService.feishuStatus,
                remoteBridgeService.clawBotStatus,
                remoteBridgeService.clawBotLoginStatus
            ) { active, tg, fs, cb, cbLogin ->
                active to kioskStatusLine(active, tg, fs, cb, cbLogin)
            }.collect { (active, line) ->
                binding?.apply {
                    remoteChannelStatus.text = line
                    setupRemoteChannel.visibility = when {
                        needsRemoteSetup(active) -> View.VISIBLE
                        else -> View.GONE
                    }
                }
            }
        }

    }

    private val kioskChipListener =
        com.google.android.material.chip.ChipGroup.OnCheckedChangeListener { _, checkedId ->
            if (checkedId == View.NO_ID) return@OnCheckedChangeListener
            val ch = kioskChipIdToChannel(checkedId) ?: return@OnCheckedChangeListener
            if (channelConfig.getActiveRemoteChannel() == ch) return@OnCheckedChangeListener
            channelConfig.setActiveRemoteChannel(ch)
        }

    private fun kioskChipIdToChannel(id: Int): RemoteChannel? = when (id) {
        R.id.chip_telegram -> RemoteChannel.TELEGRAM
        R.id.chip_feishu -> RemoteChannel.FEISHU
        R.id.chip_clawbot -> RemoteChannel.CLAWBOT
        else -> null
    }

    private fun setupRemoteChannelChips() {
        binding?.chipGroupRemoteChannel?.let { group ->
            group.setOnCheckedChangeListener(null)
            when (channelConfig.getActiveRemoteChannel()) {
                RemoteChannel.TELEGRAM -> group.check(R.id.chip_telegram)
                RemoteChannel.FEISHU -> group.check(R.id.chip_feishu)
                RemoteChannel.CLAWBOT -> group.check(R.id.chip_clawbot)
            }
            group.setOnCheckedChangeListener(kioskChipListener)
        }
    }

    private fun needsRemoteSetup(active: RemoteChannel): Boolean {
        val tg = remoteBridgeService.telegramStatus.value
        val fs = remoteBridgeService.feishuStatus.value
        val cb = remoteBridgeService.clawBotStatus.value
        return when (active) {
            RemoteChannel.TELEGRAM -> tg == BridgeStatus.NOT_CONFIGURED || tg == BridgeStatus.DISCONNECTED
            RemoteChannel.FEISHU -> fs == BridgeStatus.NOT_CONFIGURED || fs == BridgeStatus.DISCONNECTED
            RemoteChannel.CLAWBOT -> cb == BridgeStatus.NOT_CONFIGURED || cb == BridgeStatus.DISCONNECTED
        }
    }

    private fun kioskStatusLine(
        active: RemoteChannel,
        tg: BridgeStatus,
        fs: BridgeStatus,
        cb: BridgeStatus,
        cbLogin: ClawBotLoginStatus
    ): String = when (active) {
        RemoteChannel.TELEGRAM -> bridgeStatusLabel(tg)
        RemoteChannel.FEISHU -> bridgeStatusLabel(fs)
        RemoteChannel.CLAWBOT -> formatClawBotKioskLine(cb, cbLogin)
    }

    private fun bridgeStatusLabel(status: BridgeStatus): String = when (status) {
        BridgeStatus.NOT_CONFIGURED -> getString(R.string.status_not_configured)
        BridgeStatus.STOPPED -> getString(R.string.status_stopped)
        BridgeStatus.CONNECTED -> getString(R.string.status_connected)
        BridgeStatus.DISCONNECTED -> getString(R.string.status_disconnected)
    }

    private fun formatClawBotKioskLine(bridge: BridgeStatus, login: ClawBotLoginStatus): String {
        val b = bridgeStatusLabel(bridge)
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

    override fun onResume() {
        super.onResume()
        setupRemoteChannelChips()
        checkRequiredPermissions()
    }

    private fun showRemoveDeviceOwnerDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_remove_device_owner_title)
            .setMessage(R.string.dialog_remove_device_owner_msg)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->

                AppUtils.showAllHideApps(this)

                mDevicePolicyManagerGateway?.clearDeviceOwnerApp(
                    {
                        Toast.makeText(
                            this,
                            getString(R.string.device_owner_removed),
                            Toast.LENGTH_SHORT
                        ).show()
                        kioskViewModule.updateDeviceOwnerState(false)
                    },
                    { e: Exception? ->
                        Toast.makeText(
                            this,
                            getString(R.string.remove_device_owner_failed, e),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

    private fun showDeviceOwnerInstructions() {
        val componentName = DeviceAdminReceiver.getReceiverComponentName(this).flattenToShortString()
        usbEnableDebugAlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_setup_device_owner_title)
            .setMessage(getString(R.string.dialog_setup_device_owner_msg) + "adb shell dpm set-device-owner $componentName")
            .setPositiveButton(R.string.btn_open_developer_options) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

    private fun checkRequiredPermissions() {
        if (!isAccessibilityServiceEnabled() || !isAccessibilityServiceConnected()) {
            showPermissionGuideDialog(
                title = getString(R.string.dialog_need_accessibility_title),
                message = getString(R.string.dialog_need_accessibility_msg),
                intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
            return
        }

        if (requiresManageAllFilesAccessPermission() && !Environment.isExternalStorageManager()) {
            showPermissionGuideDialog(
                title = getString(R.string.dialog_need_storage_title),
                message = getString(R.string.dialog_need_storage_msg),
                intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            )
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            showPermissionGuideDialog(
                title = getString(R.string.dialog_need_overlay_title),
                message = getString(R.string.dialog_need_overlay_msg),
                intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            )
            return
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val targetComponent = ComponentName(
            packageName,
            "com.andforce.andclaw.AgentAccessibilityService"
        ).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(targetComponent, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun isAccessibilityServiceConnected(): Boolean {
        val targetComponent = ComponentName(
            packageName,
            "com.andforce.andclaw.AgentAccessibilityService"
        ).flattenToString()
        val accessibilityManager =
            getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        return accessibilityManager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo?.let { info ->
                ComponentName(info.packageName, info.name).flattenToString()
            } == targetComponent }
    }

    private fun showPermissionGuideDialog(title: String, message: String, intent: Intent) {
        if (permissionGuideDialog?.isShowing == true) {
            return
        }
        permissionGuideDialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_go_to_settings) { _, _ -> startActivity(intent) }
            .setNegativeButton(R.string.btn_skip_for_now, null)
            .show()
    }

    private fun openAiSettings() {
        startActivity(Intent(this, AiSettingsActivity::class.java))
    }

    private fun openRemoteChannelSettings() {
        val i = Intent(this, RemoteChannelSettingsActivity::class.java).apply {
            putExtra(
                RemoteChannelSettingsActivity.EXTRA_INITIAL_CHANNEL,
                channelConfig.getActiveRemoteChannel().name
            )
        }
        startActivity(i)
    }

    private fun openChatActivity() {
        val intent = Intent().setClassName(packageName, "com.andforce.andclaw.ChatHistoryActivity")
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.start_chat_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTestActivity() {
        val intent = Intent().setClassName(packageName, "com.andforce.andclaw.ActionTestActivity")
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.start_test_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

}

internal fun requiresManageAllFilesAccessPermission(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return sdkInt >= Build.VERSION_CODES.R
}
