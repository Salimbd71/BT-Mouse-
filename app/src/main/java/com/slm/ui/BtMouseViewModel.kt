package com.slm.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slm.bluetooth.BluetoothHidController
import com.slm.bluetooth.BtConnectionState
import com.slm.bluetooth.ConnectionDiagnostic
import com.slm.bluetooth.HidKeyMap
import com.slm.bluetooth.HidMotionEngine
import com.slm.bluetooth.HidTelemetry
import com.slm.bluetooth.NearbyBtDevice
import com.slm.bluetooth.PairedHostDevice
import com.slm.data.AppSettings
import com.slm.data.MouseButtonsPosition
import com.slm.data.SettingsRepository
import com.slm.data.ThemeMode
import com.slm.data.TransportMode
import com.slm.wifi.FileTransferRepository
import com.slm.wifi.FileTransferService
import com.slm.wifi.FileTransferUiState
import com.slm.wifi.PcFileOffer
import com.slm.wifi.PcSystemAction
import com.slm.wifi.WifiConnectionState
import com.slm.wifi.WifiHostDevice
import com.slm.wifi.WifiMouseController
import com.slm.wifi.WifiPcToolsClient
import com.slm.wifi.parseQrConnectPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ControllerTab {
    TOUCHPAD,
    KEYBOARD,
    TV_REMOTE,
}

data class BtMouseUiState(
    val settings: AppSettings = AppSettings(),
    val settingsLoaded: Boolean = false,
    val connectionState: BtConnectionState = BtConnectionState.Initializing,
    val pairedDevices: List<PairedHostDevice> = emptyList(),
    val nearbyBtDevices: List<NearbyBtDevice> = emptyList(),
    val isBtScanning: Boolean = false,
    val telemetry: HidTelemetry = HidTelemetry(),
    val diagnostic: ConnectionDiagnostic? = null,
    val localBluetoothName: String? = null,
    val wifiConnectionState: WifiConnectionState = WifiConnectionState.Idle,
    val wifiHosts: List<WifiHostDevice> = emptyList(),
    val wifiTransmitting: Boolean = false,
    val fileTransferState: FileTransferUiState = FileTransferUiState(),
    val activeTab: ControllerTab = ControllerTab.TOUCHPAD,
    val showLiveTypeBar: Boolean = false,
    val liveTypeBuffer: String = "",
    val latchedModifiers: Int = 0,
    val isLeftPressed: Boolean = false,
    val isRightPressed: Boolean = false,
    val isMiddlePressed: Boolean = false,
    val showDevicesSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showFileTransferSheet: Boolean = false,
    val showPcToolsSheet: Boolean = false,
    val showQrScannerDialog: Boolean = false,
    val lastSyncedClipboard: String? = null,
    val pcToolsStatus: String? = null,
    val pcToolsBusy: Boolean = false,
)

class BtMouseViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val motionEngine = HidMotionEngine()
    val hidController = BluetoothHidController(application, motionEngine)
    val wifiController = WifiMouseController(application, motionEngine)

    private val _localUi = MutableStateFlow(LocalUiState())

    private data class LocalUiState(
        val activeTab: ControllerTab = ControllerTab.TOUCHPAD,
        val showLiveTypeBar: Boolean = false,
        val liveTypeBuffer: String = "",
        val latchedModifiers: Int = 0,
        val isLeftPressed: Boolean = false,
        val isRightPressed: Boolean = false,
        val isMiddlePressed: Boolean = false,
        val showDevicesSheet: Boolean = false,
        val showSettingsSheet: Boolean = false,
        val showFileTransferSheet: Boolean = false,
        val showPcToolsSheet: Boolean = false,
        val showQrScannerDialog: Boolean = false,
        val lastSyncedClipboard: String? = null,
        val pcToolsStatus: String? = null,
        val pcToolsBusy: Boolean = false,
    )

    private data class BluetoothSnapshot(
        val connectionState: BtConnectionState,
        val pairedDevices: List<PairedHostDevice>,
        val nearbyDevices: List<NearbyBtDevice>,
        val isScanning: Boolean,
        val telemetry: HidTelemetry,
        val diagnostic: ConnectionDiagnostic?,
        val localBluetoothName: String?,
    )

    private data class WifiSnapshot(
        val connectionState: WifiConnectionState,
        val hosts: List<WifiHostDevice>,
        val isTransmitting: Boolean,
    )

    private val btScanSnapshot = combine(
        hidController.nearbyDevices,
        hidController.isScanning,
    ) { nearby, scanning ->
        nearby to scanning
    }

    private val bluetoothSnapshot = combine(
        hidController.connectionState,
        hidController.pairedDevices,
        btScanSnapshot,
        hidController.telemetry,
        hidController.diagnostic,
    ) { connState, devices, (nearby, scanning), telemetry, diagnostic ->
        Triple(connState, devices, Triple(nearby, scanning, telemetry to diagnostic))
    }.combine(hidController.localAdapterName) { (connState, devices, extra), localName ->
        val (nearby, scanning, telDiag) = extra
        BluetoothSnapshot(
            connectionState = connState,
            pairedDevices = devices,
            nearbyDevices = nearby,
            isScanning = scanning,
            telemetry = telDiag.first,
            diagnostic = telDiag.second,
            localBluetoothName = localName,
        )
    }

    private val wifiSnapshot = combine(
        wifiController.connectionState,
        wifiController.discoveredHosts,
        wifiController.isTransmitting,
    ) { connState, hosts, tx ->
        WifiSnapshot(connState, hosts, tx)
    }

    val uiState: StateFlow<BtMouseUiState> = combine(
        settingsRepository.settingsFlow.map { it to true },
        bluetoothSnapshot,
        wifiSnapshot,
        FileTransferRepository.state,
        _localUi,
    ) { (settings, settingsLoaded), bt, wifi, fileTransfer, local ->
        hidController.updateSettings(
            pollingRateHz = settings.pollingRateHz,
            autoConnect = settings.autoConnect,
            preferredAddress = settings.lastConnectedAddress,
            bluetoothModeActive = settings.transportMode == TransportMode.BLUETOOTH,
        )
        wifiController.updateSettings(
            pollingRateHz = settings.pollingRateHz,
            autoConnect = settings.autoConnect,
            lastIp = settings.lastWifiHostIp,
            lastHostName = settings.lastWifiHostName,
        )
        BtMouseUiState(
            settings = settings,
            settingsLoaded = settingsLoaded,
            connectionState = bt.connectionState,
            pairedDevices = bt.pairedDevices,
            nearbyBtDevices = bt.nearbyDevices,
            isBtScanning = bt.isScanning,
            telemetry = bt.telemetry,
            diagnostic = bt.diagnostic,
            localBluetoothName = bt.localBluetoothName,
            wifiConnectionState = wifi.connectionState,
            wifiHosts = wifi.hosts,
            wifiTransmitting = wifi.isTransmitting,
            fileTransferState = fileTransfer,
            activeTab = local.activeTab,
            showLiveTypeBar = local.showLiveTypeBar,
            liveTypeBuffer = local.liveTypeBuffer,
            latchedModifiers = local.latchedModifiers,
            isLeftPressed = local.isLeftPressed,
            isRightPressed = local.isRightPressed,
            isMiddlePressed = local.isMiddlePressed,
            showDevicesSheet = local.showDevicesSheet,
            showSettingsSheet = local.showSettingsSheet,
            showFileTransferSheet = local.showFileTransferSheet,
            showPcToolsSheet = local.showPcToolsSheet,
            showQrScannerDialog = local.showQrScannerDialog,
            lastSyncedClipboard = local.lastSyncedClipboard,
            pcToolsStatus = local.pcToolsStatus,
            pcToolsBusy = local.pcToolsBusy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BtMouseUiState(),
    )

    init {
        hidController.onHostConnected = { address, name ->
            viewModelScope.launch {
                settingsRepository.setLastConnectedDevice(address, name)
            }
        }
        wifiController.onHostConnected = { ip, name ->
            viewModelScope.launch {
                settingsRepository.setLastWifiHost(ip, name)
            }
        }
        wifiController.onFileOffersAvailable = {
            viewModelScope.launch {
                wifiController.currentHostIp()?.let { ip ->
                    FileTransferRepository.refreshIncomingOffers(ip)
                }
            }
        }
        hidController.initialize()
        viewModelScope.launch {
            val initialSettings = settingsRepository.settingsFlow.first()
            wifiController.updateSettings(
                pollingRateHz = initialSettings.pollingRateHz,
                autoConnect = initialSettings.autoConnect,
                lastIp = initialSettings.lastWifiHostIp,
                lastHostName = initialSettings.lastWifiHostName,
            )
            if (initialSettings.transportMode == TransportMode.WIFI) {
                wifiController.tryAutoReconnect(force = true)
            }
        }
    }

    private fun isWifiMode(): Boolean = uiState.value.settings.transportMode == TransportMode.WIFI

    fun selectTransportMode(mode: TransportMode) {
        motionEngine.reset()
        viewModelScope.launch {
            settingsRepository.setTransportMode(mode)
        }
        if (mode == TransportMode.WIFI) {
            wifiController.tryAutoReconnect(force = true)
        }
    }

    fun onPermissionsUpdated() {
        hidController.initialize()
    }

    fun markPermissionsOnboardingDone() {
        viewModelScope.launch {
            settingsRepository.setPermissionsOnboardingDone(true)
        }
    }

    fun refreshDevices() {
        hidController.refreshPairedDevices()
        if (isWifiMode()) {
            wifiController.tryAutoReconnect()
        }
    }

    fun scanWifiHosts() {
        wifiController.startDiscovery(autoConnectIfFound = false)
    }

    fun connectWifiIp(ip: String, name: String? = null) {
        wifiController.connectToIp(ip, name)
    }

    fun disconnectWifi() {
        wifiController.disconnect()
    }

    fun selectTab(tab: ControllerTab) {
        _localUi.update { it.copy(activeTab = tab) }
    }

    fun toggleLiveTypeBar() {
        _localUi.update { it.copy(showLiveTypeBar = !it.showLiveTypeBar) }
    }

    fun setDevicesSheetVisible(visible: Boolean) {
        if (visible) {
            hidController.refreshPairedDevices()
            if (isWifiMode()) {
                wifiController.startDiscovery(autoConnectIfFound = false)
            }
        } else {
            hidController.stopNearbyScan()
        }
        _localUi.update { it.copy(showDevicesSheet = visible) }
    }

    fun setSettingsSheetVisible(visible: Boolean) {
        _localUi.update { it.copy(showSettingsSheet = visible) }
    }

    fun setFileTransferSheetVisible(visible: Boolean) {
        _localUi.update { it.copy(showFileTransferSheet = visible) }
        if (visible) {
            refreshPcFileOffers()
        }
    }

    fun setPcToolsSheetVisible(visible: Boolean) {
        _localUi.update { it.copy(showPcToolsSheet = visible) }
    }

    fun setQrScannerVisible(visible: Boolean) {
        _localUi.update { it.copy(showQrScannerDialog = visible) }
    }

    fun connectFromQrCode(rawQr: String) {
        val payload = parseQrConnectPayload(rawQr) ?: return
        _localUi.update {
            it.copy(
                showQrScannerDialog = false,
                showDevicesSheet = false,
            )
        }
        viewModelScope.launch {
            settingsRepository.setTransportMode(TransportMode.WIFI)
            val bestIp = WifiPcToolsClient.pickBestReachableIp(payload.ips)
            if (bestIp.isNotEmpty()) {
                wifiController.connectToIp(bestIp, payload.hostName, payload.port)
            }
        }
    }

    fun sendVoiceText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (isWifiMode()) {
            wifiController.sendUnicodeText(clean)
        } else {
            hidController.sendTextString(clean)
        }
    }

    fun sendPhoneClipboardToPc(text: String) {
        val ip = wifiController.currentHostIp() ?: run {
            _localUi.update { it.copy(pcToolsStatus = "Connect Wi-Fi PC first") }
            return
        }
        if (text.isEmpty()) {
            _localUi.update { it.copy(pcToolsStatus = "Phone clipboard is empty") }
            return
        }
        viewModelScope.launch {
            _localUi.update { it.copy(pcToolsBusy = true, pcToolsStatus = null) }
            WifiPcToolsClient.sendClipboardToPc(ip, text)
                .onSuccess {
                    _localUi.update {
                        it.copy(
                            pcToolsBusy = false,
                            lastSyncedClipboard = text,
                            pcToolsStatus = "Clipboard sent to PC",
                        )
                    }
                }
                .onFailure { err ->
                    _localUi.update {
                        it.copy(
                            pcToolsBusy = false,
                            pcToolsStatus = err.message ?: "Failed to send clipboard",
                        )
                    }
                }
        }
    }

    fun pasteClipboardOnActiveHost(text: String) {
        if (text.isEmpty()) {
            _localUi.update { it.copy(pcToolsStatus = "Phone clipboard is empty") }
            return
        }
        val ip = wifiController.currentHostIp()
        if (isWifiMode() && ip != null) {
            viewModelScope.launch {
                _localUi.update { it.copy(pcToolsBusy = true, pcToolsStatus = null) }
                WifiPcToolsClient.sendClipboardToPc(ip, text)
                    .onSuccess {
                        wifiController.sendKeyPress(HidKeyMap.MOD_LEFT_CTRL, HidKeyMap.KEY_V)
                        _localUi.update {
                            it.copy(
                                pcToolsBusy = false,
                                lastSyncedClipboard = text,
                                pcToolsStatus = "Pasted on PC",
                            )
                        }
                    }
                    .onFailure { err ->
                        _localUi.update {
                            it.copy(
                                pcToolsBusy = false,
                                pcToolsStatus = err.message ?: "Failed to paste on PC",
                            )
                        }
                    }
            }
        } else {
            hidController.sendTextString(text)
            _localUi.update {
                it.copy(
                    lastSyncedClipboard = text,
                    pcToolsStatus = "Typed clipboard onto active host",
                )
            }
        }
    }

    fun fetchPcClipboardToPhone(onFetched: (String) -> Unit) {
        val ip = wifiController.currentHostIp() ?: run {
            _localUi.update { it.copy(pcToolsStatus = "Connect Wi-Fi PC first") }
            return
        }
        viewModelScope.launch {
            _localUi.update { it.copy(pcToolsBusy = true, pcToolsStatus = null) }
            WifiPcToolsClient.getPcClipboard(ip)
                .onSuccess { text ->
                    onFetched(text)
                    _localUi.update {
                        it.copy(
                            pcToolsBusy = false,
                            lastSyncedClipboard = text.ifEmpty { null },
                            pcToolsStatus = if (text.isEmpty()) {
                                "PC clipboard has no text"
                            } else {
                                "Copied PC clipboard to phone"
                            },
                        )
                    }
                }
                .onFailure { err ->
                    _localUi.update {
                        it.copy(
                            pcToolsBusy = false,
                            pcToolsStatus = err.message ?: "Failed to read PC clipboard",
                        )
                    }
                }
        }
    }

    fun executePcSystemAction(action: PcSystemAction) {
        val ip = wifiController.currentHostIp() ?: run {
            _localUi.update { it.copy(pcToolsStatus = "Connect Wi-Fi PC first") }
            return
        }
        viewModelScope.launch {
            _localUi.update { it.copy(pcToolsBusy = true, pcToolsStatus = null) }
            WifiPcToolsClient.sendSystemAction(ip, action)
                .onSuccess {
                    val msg = when (action) {
                        PcSystemAction.SCREENSHOT -> "Screenshot saved in Downloads\\BT mouse Files & copied"
                        else -> "${action.label} sent to PC"
                    }
                    _localUi.update { it.copy(pcToolsBusy = false, pcToolsStatus = msg) }
                }
                .onFailure { err ->
                    _localUi.update {
                        it.copy(
                            pcToolsBusy = false,
                            pcToolsStatus = err.message ?: "Failed: ${action.label}",
                        )
                    }
                }
        }
    }

    fun clearPcToolsStatus() {
        _localUi.update { it.copy(pcToolsStatus = null) }
    }

    fun sendFilesToPc(uris: List<Uri>) {
        val ip = wifiController.currentHostIp() ?: return
        if (uris.isEmpty()) return
        FileTransferService.startUpload(getApplication(), ip, uris)
    }

    fun refreshPcFileOffers() {
        val ip = wifiController.currentHostIp() ?: return
        viewModelScope.launch {
            FileTransferRepository.refreshIncomingOffers(ip)
        }
    }

    fun downloadPcFile(offer: PcFileOffer) {
        val ip = wifiController.currentHostIp() ?: return
        FileTransferService.startDownload(getApplication(), ip, listOf(offer))
    }

    fun downloadAllPcFiles() {
        val ip = wifiController.currentHostIp() ?: return
        val offers = uiState.value.fileTransferState.incomingOffers
        if (offers.isEmpty()) return
        FileTransferService.startDownload(getApplication(), ip, offers)
    }

    fun cancelFileTransfer() {
        FileTransferService.cancelTransfers(getApplication())
    }

    fun clearFileTransferError() {
        FileTransferRepository.clearError()
    }

    // ==================== MOUSE & TOUCHPAD ACTIONS ====================

    private fun notifyMotion() {
        if (isWifiMode()) {
            wifiController.notifyMotionEnqueued()
        } else {
            hidController.notifyMotionEnqueued()
        }
    }

    fun onTouchpadMove(rawDx: Float, rawDy: Float) {
        val s = uiState.value.settings
        motionEngine.enqueuePointerDelta(
            rawDx = rawDx,
            rawDy = rawDy,
            sensitivity = s.pointerSensitivity,
            accelerationEnabled = s.pointerAcceleration,
        )
        notifyMotion()
    }

    fun onTouchpadScroll(rawDx: Float, rawDy: Float) {
        val s = uiState.value.settings
        motionEngine.enqueueScrollDelta(
            rawDx = rawDx,
            rawDy = rawDy,
            scrollSensitivity = s.scrollSensitivity,
            naturalScroll = s.naturalScroll,
        )
        notifyMotion()
    }

    fun onWheelStripScroll(rawDy: Float) {
        val s = uiState.value.settings
        val polarity = if (s.naturalScroll) 1f else -1f
        motionEngine.enqueueWheelNotches(verticalNotches = rawDy * s.scrollSensitivity * 0.16f * polarity)
        notifyMotion()
    }

    fun onPinchZoom(zoomScaleChange: Float) {
        val delta = (zoomScaleChange - 1f) * 18f
        if (kotlin.math.abs(delta) < 0.35f) return
        val wheelStep = if (delta > 0) 1 else -1
        if (isWifiMode()) {
            wifiController.sendPinchZoom(wheelStep)
        } else {
            hidController.sendKeyPress(HidKeyMap.MOD_LEFT_CTRL, HidKeyMap.KEY_NONE)
            motionEngine.enqueueWheelNotches(verticalNotches = wheelStep.toFloat())
            notifyMotion()
        }
    }

    fun onLeftClick() {
        if (isWifiMode()) wifiController.clickMouseButton(HidKeyMap.MOUSE_LEFT)
        else hidController.clickMouseButton(HidKeyMap.MOUSE_LEFT)
    }

    fun onRightClick() {
        if (isWifiMode()) wifiController.clickMouseButton(HidKeyMap.MOUSE_RIGHT)
        else hidController.clickMouseButton(HidKeyMap.MOUSE_RIGHT)
    }

    fun onMiddleClick() {
        if (isWifiMode()) wifiController.clickMouseButton(HidKeyMap.MOUSE_MIDDLE)
        else hidController.clickMouseButton(HidKeyMap.MOUSE_MIDDLE)
    }

    fun setLeftButtonHeld(pressed: Boolean) {
        _localUi.update { it.copy(isLeftPressed = pressed) }
        if (isWifiMode()) wifiController.setMouseButton(HidKeyMap.MOUSE_LEFT, pressed)
        else hidController.setMouseButton(HidKeyMap.MOUSE_LEFT, pressed)
    }

    fun setRightButtonHeld(pressed: Boolean) {
        _localUi.update { it.copy(isRightPressed = pressed) }
        if (isWifiMode()) wifiController.setMouseButton(HidKeyMap.MOUSE_RIGHT, pressed)
        else hidController.setMouseButton(HidKeyMap.MOUSE_RIGHT, pressed)
    }

    fun setMiddleButtonHeld(pressed: Boolean) {
        _localUi.update { it.copy(isMiddlePressed = pressed) }
        if (isWifiMode()) wifiController.setMouseButton(HidKeyMap.MOUSE_MIDDLE, pressed)
        else hidController.setMouseButton(HidKeyMap.MOUSE_MIDDLE, pressed)
    }

    // ==================== KEYBOARD & TV REMOTE ACTIONS ====================

    fun toggleModifier(modMask: Int) {
        _localUi.update { state ->
            state.copy(latchedModifiers = state.latchedModifiers xor modMask)
        }
    }

    fun sendHidKey(keyCode: Int, extraModifiers: Int = 0) {
        val mods = _localUi.value.latchedModifiers or extraModifiers
        if (isWifiMode()) {
            wifiController.sendKeyPress(mods, keyCode)
        } else {
            hidController.sendKeyPress(mods, keyCode)
        }
        if (_localUi.value.latchedModifiers != 0) {
            _localUi.update { it.copy(latchedModifiers = 0) }
        }
    }

    fun sendCharacterKey(ch: Char) {
        val shift = (_localUi.value.latchedModifiers and HidKeyMap.MOD_LEFT_SHIFT) != 0
        val actualChar = if (shift && ch.isLetter()) ch.uppercaseChar() else ch
        val nonShiftMods = _localUi.value.latchedModifiers and HidKeyMap.MOD_LEFT_SHIFT.inv()

        if (isWifiMode() && nonShiftMods == 0) {
            wifiController.sendUnicodeText(actualChar.toString())
            if (shift) _localUi.update { it.copy(latchedModifiers = 0) }
            return
        }

        val stroke = HidKeyMap.fromChar(actualChar)
        if (stroke != null) {
            sendHidKey(stroke.keyCode, stroke.modifiers)
        }
    }

    fun sendCombo(modifiers: Int, keyCode: Int) {
        if (isWifiMode()) {
            wifiController.sendKeyPress(modifiers, keyCode)
        } else {
            hidController.sendKeyPress(modifiers, keyCode)
        }
    }

    fun sendConsumerKey(usage: Int) {
        if (isWifiMode()) {
            wifiController.sendConsumerPress(usage)
        } else {
            hidController.sendConsumerPress(usage)
        }
    }

    fun onLiveTypeBufferChanged(newValue: String) {
        val oldValue = _localUi.value.liveTypeBuffer
        if (newValue == oldValue) return

        if (newValue.length < oldValue.length && oldValue.startsWith(newValue)) {
            val deletes = oldValue.length - newValue.length
            repeat(deletes) {
                sendHidKey(HidKeyMap.KEY_BACKSPACE)
            }
        } else if (newValue.length > oldValue.length && newValue.startsWith(oldValue)) {
            val appended = newValue.substring(oldValue.length)
            if (isWifiMode()) {
                wifiController.sendUnicodeText(appended)
            } else {
                hidController.sendTextString(appended)
            }
        } else if (newValue.isNotEmpty()) {
            repeat(oldValue.length) {
                sendHidKey(HidKeyMap.KEY_BACKSPACE)
            }
            if (isWifiMode()) {
                wifiController.sendUnicodeText(newValue)
            } else {
                hidController.sendTextString(newValue)
            }
        }

        _localUi.update { it.copy(liveTypeBuffer = newValue) }
    }

    fun clearLiveTypeBuffer() {
        _localUi.update { it.copy(liveTypeBuffer = "") }
    }

    // ==================== CONNECTION & SETTINGS ====================

    fun connectToHost(address: String) {
        hidController.connectByAddress(address)
    }

    fun scanNearbyBluetooth() {
        hidController.startNearbyScan()
    }

    fun stopNearbyBluetoothScan() {
        hidController.stopNearbyScan()
    }

    fun pairAndConnectBluetooth(address: String) {
        hidController.pairAndConnect(address)
    }

    fun disconnectHost() {
        hidController.disconnectCurrent()
    }

    fun clearDiagnostic() {
        hidController.clearDiagnostic()
    }

    fun onDiscoverableLaunched(durationSeconds: Int = 300) {
        hidController.startDiscoverableCountdown(durationSeconds)
    }

    fun cycleThemeMode() {
        val next = when (uiState.value.settings.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setMouseButtonsPosition(position: MouseButtonsPosition) {
        viewModelScope.launch { settingsRepository.setMouseButtonsPosition(position) }
    }

    fun setLeftHandedMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLeftHandedMode(enabled) }
    }

    fun setPointerSensitivity(value: Float) {
        viewModelScope.launch { settingsRepository.setPointerSensitivity(value) }
    }

    fun setScrollSensitivity(value: Float) {
        viewModelScope.launch { settingsRepository.setScrollSensitivity(value) }
    }

    fun setPointerAcceleration(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPointerAcceleration(enabled) }
    }

    fun setNaturalScroll(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNaturalScroll(enabled) }
    }

    fun setPollingRateHz(hz: Int) {
        viewModelScope.launch { settingsRepository.setPollingRateHz(hz) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticFeedback(enabled) }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoConnect(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        hidController.release()
        wifiController.release()
    }
}
