package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothHidController
import com.example.bluetooth.BtConnectionState
import com.example.bluetooth.ConnectionDiagnostic
import com.example.bluetooth.HidKeyMap
import com.example.bluetooth.HidMotionEngine
import com.example.bluetooth.HidTelemetry
import com.example.bluetooth.PairedHostDevice
import com.example.data.AppSettings
import com.example.data.SettingsRepository
import com.example.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val connectionState: BtConnectionState = BtConnectionState.Initializing,
    val pairedDevices: List<PairedHostDevice> = emptyList(),
    val telemetry: HidTelemetry = HidTelemetry(),
    val diagnostic: ConnectionDiagnostic? = null,
    val localBluetoothName: String? = null,
    val activeTab: ControllerTab = ControllerTab.TOUCHPAD,
    val showLiveTypeBar: Boolean = false,
    val liveTypeBuffer: String = "",
    val latchedModifiers: Int = 0,
    val isLeftPressed: Boolean = false,
    val isRightPressed: Boolean = false,
    val isMiddlePressed: Boolean = false,
    val showDevicesSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
)

class BtMouseViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val motionEngine = HidMotionEngine()
    val hidController = BluetoothHidController(application, motionEngine)

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
    )

    private val bluetoothSnapshot = combine(
        hidController.connectionState,
        hidController.pairedDevices,
        hidController.telemetry,
        hidController.diagnostic,
        hidController.localAdapterName,
    ) { connState, devices, telemetry, diagnostic, localName ->
        BluetoothSnapshot(connState, devices, telemetry, diagnostic, localName)
    }

    private data class BluetoothSnapshot(
        val connectionState: BtConnectionState,
        val pairedDevices: List<PairedHostDevice>,
        val telemetry: HidTelemetry,
        val diagnostic: ConnectionDiagnostic?,
        val localBluetoothName: String?,
    )

    val uiState: StateFlow<BtMouseUiState> = combine(
        settingsRepository.settingsFlow,
        bluetoothSnapshot,
        _localUi,
    ) { settings, bt, local ->
        hidController.updateSettings(
            pollingRateHz = settings.pollingRateHz,
            autoConnect = settings.autoConnect,
            preferredAddress = settings.lastConnectedAddress,
        )
        BtMouseUiState(
            settings = settings,
            connectionState = bt.connectionState,
            pairedDevices = bt.pairedDevices,
            telemetry = bt.telemetry,
            diagnostic = bt.diagnostic,
            localBluetoothName = bt.localBluetoothName,
            activeTab = local.activeTab,
            showLiveTypeBar = local.showLiveTypeBar,
            liveTypeBuffer = local.liveTypeBuffer,
            latchedModifiers = local.latchedModifiers,
            isLeftPressed = local.isLeftPressed,
            isRightPressed = local.isRightPressed,
            isMiddlePressed = local.isMiddlePressed,
            showDevicesSheet = local.showDevicesSheet,
            showSettingsSheet = local.showSettingsSheet,
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
        hidController.initialize()
    }

    fun onPermissionsUpdated() {
        hidController.initialize()
    }

    fun refreshDevices() {
        hidController.refreshPairedDevices()
    }

    fun selectTab(tab: ControllerTab) {
        _localUi.update { it.copy(activeTab = tab) }
    }

    fun toggleLiveTypeBar() {
        _localUi.update { it.copy(showLiveTypeBar = !it.showLiveTypeBar) }
    }

    fun setDevicesSheetVisible(visible: Boolean) {
        if (visible) refreshDevices()
        _localUi.update { it.copy(showDevicesSheet = visible) }
    }

    fun setSettingsSheetVisible(visible: Boolean) {
        _localUi.update { it.copy(showSettingsSheet = visible) }
    }

    // ==================== MOUSE & TOUCHPAD ACTIONS ====================

    fun onTouchpadMove(rawDx: Float, rawDy: Float) {
        val s = uiState.value.settings
        motionEngine.enqueuePointerDelta(
            rawDx = rawDx,
            rawDy = rawDy,
            sensitivity = s.pointerSensitivity,
            accelerationEnabled = s.pointerAcceleration,
        )
        hidController.notifyMotionEnqueued()
    }

    fun onTouchpadScroll(rawDx: Float, rawDy: Float) {
        val s = uiState.value.settings
        motionEngine.enqueueScrollDelta(
            rawDx = rawDx,
            rawDy = rawDy,
            scrollSensitivity = s.scrollSensitivity,
            naturalScroll = s.naturalScroll,
        )
        hidController.notifyMotionEnqueued()
    }

    fun onWheelStripScroll(rawDy: Float) {
        val s = uiState.value.settings
        val polarity = if (s.naturalScroll) 1f else -1f
        motionEngine.enqueueWheelNotches(verticalNotches = rawDy * s.scrollSensitivity * 0.16f * polarity)
        hidController.notifyMotionEnqueued()
    }

    fun onPinchZoom(zoomScaleChange: Float) {
        // Windows / Desktop browser zoom via Ctrl + Mouse Wheel
        val delta = (zoomScaleChange - 1f) * 18f
        if (kotlin.math.abs(delta) < 0.35f) return
        val wheelStep = if (delta > 0) 1 else -1
        hidController.sendKeyPress(HidKeyMap.MOD_LEFT_CTRL, HidKeyMap.KEY_NONE)
        motionEngine.enqueueWheelNotches(verticalNotches = wheelStep.toFloat())
        hidController.notifyMotionEnqueued()
    }

    fun onLeftClick() {
        hidController.clickMouseButton(HidKeyMap.MOUSE_LEFT)
    }

    fun onRightClick() {
        hidController.clickMouseButton(HidKeyMap.MOUSE_RIGHT)
    }

    fun onMiddleClick() {
        hidController.clickMouseButton(HidKeyMap.MOUSE_MIDDLE)
    }

    fun setLeftButtonHeld(pressed: Boolean) {
        _localUi.update { it.copy(isLeftPressed = pressed) }
        hidController.setMouseButton(HidKeyMap.MOUSE_LEFT, pressed)
    }

    fun setRightButtonHeld(pressed: Boolean) {
        _localUi.update { it.copy(isRightPressed = pressed) }
        hidController.setMouseButton(HidKeyMap.MOUSE_RIGHT, pressed)
    }

    fun setMiddleButtonHeld(pressed: Boolean) {
        _localUi.update { it.copy(isMiddlePressed = pressed) }
        hidController.setMouseButton(HidKeyMap.MOUSE_MIDDLE, pressed)
    }

    // ==================== KEYBOARD & TV REMOTE ACTIONS ====================

    fun toggleModifier(modMask: Int) {
        _localUi.update { state ->
            state.copy(latchedModifiers = state.latchedModifiers xor modMask)
        }
    }

    fun sendHidKey(keyCode: Int, extraModifiers: Int = 0) {
        val mods = _localUi.value.latchedModifiers or extraModifiers
        hidController.sendKeyPress(mods, keyCode)
        if (_localUi.value.latchedModifiers != 0) {
            _localUi.update { it.copy(latchedModifiers = 0) }
        }
    }

    fun sendCombo(modifiers: Int, keyCode: Int) {
        hidController.sendKeyPress(modifiers, keyCode)
    }

    fun sendConsumerKey(usage: Int) {
        hidController.sendConsumerPress(usage)
    }

    /**
     * Real-time diff bridge for the system soft keyboard: sends Backspace or typed characters
     * immediately to the connected Smart TV or Windows PC as the user types.
     */
    fun onLiveTypeBufferChanged(newValue: String) {
        val oldValue = _localUi.value.liveTypeBuffer
        if (newValue == oldValue) return

        if (newValue.length < oldValue.length && oldValue.startsWith(newValue)) {
            val deletes = oldValue.length - newValue.length
            repeat(deletes) {
                hidController.sendKeyPress(0, HidKeyMap.KEY_BACKSPACE)
            }
        } else if (newValue.length > oldValue.length && newValue.startsWith(oldValue)) {
            val appended = newValue.substring(oldValue.length)
            hidController.sendTextString(appended)
        } else if (newValue.isNotEmpty()) {
            // Fallback for IME autocorrect replacements
            repeat(oldValue.length) {
                hidController.sendKeyPress(0, HidKeyMap.KEY_BACKSPACE)
            }
            hidController.sendTextString(newValue)
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
    }
}
