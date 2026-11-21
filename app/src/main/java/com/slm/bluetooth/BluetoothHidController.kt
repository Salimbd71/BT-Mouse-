package com.slm.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class BluetoothHidController(
    private val context: Context,
    private val motionEngine: HidMotionEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val hidExecutor = Executors.newSingleThreadExecutor()
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<BtConnectionState>(BtConnectionState.Initializing)
    val connectionState: StateFlow<BtConnectionState> = _connectionState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedHostDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedHostDevice>> = _pairedDevices.asStateFlow()

    private val _telemetry = MutableStateFlow(HidTelemetry())
    val telemetry: StateFlow<HidTelemetry> = _telemetry.asStateFlow()

    private val _diagnostic = MutableStateFlow<ConnectionDiagnostic?>(null)
    val diagnostic: StateFlow<ConnectionDiagnostic?> = _diagnostic.asStateFlow()

    private val _localAdapterName = MutableStateFlow<String?>(null)
    val localAdapterName: StateFlow<String?> = _localAdapterName.asStateFlow()

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var isAppRegistered = false
    private var attemptedQosFallback = false
    private var receiverRegistered = false
    private var autoConnectEnabled = true
    private var preferredHostAddress: String? = null
    private var pendingConnectAddress: String? = null
    private var activeConnectAttemptAddress: String? = null

    private var activeButtonsMask = AtomicInteger(0)
    private var activePollingHz = 125
    private var motionLoopJob: Job? = null
    private var discoverableTimerJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var txPulseJob: Job? = null
    private var packetCounter = 0L

    var onHostConnected: ((address: String, name: String) -> Unit)? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        initialize()
                    } else if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        connectTimeoutJob?.cancel()
                        activeConnectAttemptAddress = null
                        connectedHost = null
                        isAppRegistered = false
                        _connectionState.value = BtConnectionState.BluetoothDisabled
                        _pairedDevices.value = emptyList()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refreshPairedDevices()
                    val device = getParcelableDevice(intent)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    if (bondState == BluetoothDevice.BOND_BONDED && device != null && autoConnectEnabled) {
                        _diagnostic.value = null
                        connectDevice(device)
                    }
                }
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {
                    val scanMode = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_SCAN_MODE,
                        BluetoothAdapter.SCAN_MODE_NONE,
                    )
                    if (scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE &&
                        _connectionState.value is BtConnectionState.Discoverable
                    ) {
                        discoverableTimerJob?.cancel()
                        updateConnectionStateFromHost()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
            )
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun updateSettings(pollingRateHz: Int, autoConnect: Boolean, preferredAddress: String?) {
        activePollingHz = pollingRateHz
        autoConnectEnabled = autoConnect
        preferredHostAddress = preferredAddress
        _telemetry.value = _telemetry.value.copy(activeRateHz = pollingRateHz)
    }

    fun clearDiagnostic() {
        _diagnostic.value = null
    }

    @SuppressLint("MissingPermission")
    fun initialize() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            _connectionState.value = BtConnectionState.Unsupported
            return
        }
        if (!hasRequiredPermissions()) {
            _connectionState.value = BtConnectionState.PermissionRequired
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _connectionState.value = BtConnectionState.Unsupported
            return
        }
        _localAdapterName.value = runCatching { adapter.name }.getOrNull()
        registerReceiversIfNeeded()

        if (!adapter.isEnabled) {
            _connectionState.value = BtConnectionState.BluetoothDisabled
            return
        }

        refreshPairedDevices()

        if (hidDevice != null && isAppRegistered) {
            updateConnectionStateFromHost()
            return
        }

        _connectionState.value = BtConnectionState.Initializing
        if (hidDevice == null) {
            adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } else {
            attemptedQosFallback = false
            registerHidApp(hidDevice!!, useLowLatencyQos = true)
        }
    }

    private fun registerReceiversIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val device = proxy as BluetoothHidDevice
                hidDevice = device
                attemptedQosFallback = false
                registerHidApp(device, useLowLatencyQos = true)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                connectTimeoutJob?.cancel()
                activeConnectAttemptAddress = null
                hidDevice = null
                connectedHost = null
                isAppRegistered = false
                _connectionState.value = BtConnectionState.Initializing
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("MissingPermission")
    private fun registerHidApp(device: BluetoothHidDevice, useLowLatencyQos: Boolean) {
        if (!hasRequiredPermissions()) return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "BT mouse",
            "Wireless Mouse & Keyboard",
            "BT mouse",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.COMPOSITE_DESCRIPTOR,
        )
        val outQos = if (useLowLatencyQos) {
            BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800,
                9,
                0,
                4000,
                4000,
            )
        } else {
            null
        }
        val accepted = runCatching {
            device.registerApp(sdp, null, outQos, hidExecutor, hidCallback)
        }.getOrDefault(false)

        if (!accepted && useLowLatencyQos && !attemptedQosFallback) {
            attemptedQosFallback = true
            registerHidApp(device, useLowLatencyQos = false)
        } else if (!accepted) {
            _diagnostic.value = ConnectionDiagnostic(
                deviceName = "Bluetooth HID",
                message = "Another Bluetooth HID app may be active, or your phone's Bluetooth stack rejected HID registration. Toggle Bluetooth off and on, then retry.",
                isAlreadyPairedSdpHint = false,
            )
            _connectionState.value = BtConnectionState.Ready
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private val hidCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isAppRegistered = registered
            if (registered) {
                val currentConnected = pluggedDevice ?: runCatching {
                    hidDevice?.connectedDevices?.firstOrNull()
                }.getOrNull()
                if (currentConnected != null) {
                    connectedHost = currentConnected
                }
                refreshPairedDevices()
                updateConnectionStateFromHost()

                val pendingAddr = pendingConnectAddress
                pendingConnectAddress = null
                if (pendingAddr != null) {
                    connectByAddress(pendingAddr)
                } else if (connectedHost == null && autoConnectEnabled) {
                    attemptAutoConnect()
                }
            } else {
                connectedHost = null
                if (!attemptedQosFallback && hidDevice != null) {
                    attemptedQosFallback = true
                    registerHidApp(hidDevice!!, useLowLatencyQos = false)
                } else {
                    _connectionState.value = BtConnectionState.Ready
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectTimeoutJob?.cancel()
                    discoverableTimerJob?.cancel()
                    activeConnectAttemptAddress = null
                    _diagnostic.value = null
                    connectedHost = device
                    val name = safeDeviceName(device)
                    val type = classifyDevice(device)
                    _connectionState.value = BtConnectionState.Connected(
                        deviceName = name,
                        address = device.address,
                        deviceType = type,
                    )
                    refreshPairedDevices()
                    onHostConnected?.invoke(device.address, name)
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = BtConnectionState.Connecting(
                        deviceName = safeDeviceName(device),
                        address = device.address,
                    )
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectTimeoutJob?.cancel()
                    val wasAttemptingThisDevice = activeConnectAttemptAddress == device.address
                    val wasPreviouslyConnected = connectedHost?.address == device.address
                    activeConnectAttemptAddress = null
                    if (wasPreviouslyConnected) {
                        connectedHost = null
                    }
                    if (wasAttemptingThisDevice && !wasPreviouslyConnected) {
                        val name = safeDeviceName(device)
                        _diagnostic.value = ConnectionDiagnostic(
                            deviceName = name,
                            message = "$name declined the HID Mouse/Keyboard connection. If this laptop or TV was paired before installing BT mouse, unpair/remove it on both devices and pair again using Auto-Pair.",
                            isAlreadyPairedSdpHint = true,
                        )
                    }
                    refreshPairedDevices()
                    updateConnectionStateFromHost()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val hid = hidDevice ?: return
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                val payload = when (id.toInt()) {
                    HidDescriptor.REPORT_ID_MOUSE -> ByteArray(5)
                    HidDescriptor.REPORT_ID_KEYBOARD -> ByteArray(8)
                    HidDescriptor.REPORT_ID_CONSUMER -> ByteArray(2)
                    else -> null
                }
                if (payload != null) {
                    hid.replyReport(device, type, id, payload)
                } else {
                    hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                }
            } else {
                hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray?) {
            // Host (Windows/TV) may send Keyboard Output LED state (NumLock/CapsLock); accept cleanly.
        }

        @SuppressLint("MissingPermission")
        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            // Acknowledge Windows/TV protocol selection (Report vs Boot mode).
        }

        @SuppressLint("MissingPermission")
        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            connectTimeoutJob?.cancel()
            activeConnectAttemptAddress = null
            if (connectedHost?.address == device.address) {
                connectedHost = null
            }
            _diagnostic.value = ConnectionDiagnostic(
                deviceName = safeDeviceName(device),
                message = "The host removed or unplugged the Bluetooth HID link. Re-pair from Auto-Pair if needed.",
                isAlreadyPairedSdpHint = true,
            )
            refreshPairedDevices()
            updateConnectionStateFromHost()
        }
    }

    @SuppressLint("MissingPermission")
    private fun attemptAutoConnect() {
        if (!hasRequiredPermissions()) return
        val adapter = bluetoothAdapter ?: return
        val targetAddr = preferredHostAddress ?: return
        val targetDevice = adapter.bondedDevices?.firstOrNull { it.address == targetAddr } ?: return
        connectDevice(targetDevice)
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (!hasRequiredPermissions()) {
            _pairedDevices.value = emptyList()
            return
        }
        val adapter = bluetoothAdapter ?: return
        _localAdapterName.value = runCatching { adapter.name }.getOrNull()
        if (!adapter.isEnabled) {
            _pairedDevices.value = emptyList()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && connectedHost == null) {
            val active = runCatching { hidDevice?.connectedDevices?.firstOrNull() }.getOrNull()
            if (active != null) {
                connectedHost = active
            }
        }
        val connectedAddr = connectedHost?.address
        val list = adapter.bondedDevices
            ?.map { device ->
                PairedHostDevice(
                    name = safeDeviceName(device),
                    address = device.address,
                    deviceType = classifyDevice(device),
                    isConnected = device.address == connectedAddr,
                )
            }
            ?.sortedWith(compareByDescending<PairedHostDevice> { it.isConnected }.thenBy { it.name })
            ?: emptyList()
        _pairedDevices.value = list
    }

    @SuppressLint("MissingPermission")
    fun connectByAddress(address: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasRequiredPermissions()) return
        if (!isAppRegistered || hidDevice == null) {
            pendingConnectAddress = address
            initialize()
            return
        }
        val device = bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address } ?: return
        _diagnostic.value = null
        connectDevice(device)
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasRequiredPermissions()) return
        val hid = hidDevice ?: return
        if (!isAppRegistered) {
            pendingConnectAddress = device.address
            return
        }
        val targetName = safeDeviceName(device)
        if (connectedHost != null && connectedHost?.address != device.address) {
            hid.disconnect(connectedHost)
        }
        activeConnectAttemptAddress = device.address
        _connectionState.value = BtConnectionState.Connecting(
            deviceName = targetName,
            address = device.address,
        )

        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(8500L)
            if (activeConnectAttemptAddress == device.address && connectedHost?.address != device.address) {
                activeConnectAttemptAddress = null
                _diagnostic.value = ConnectionDiagnostic(
                    deviceName = targetName,
                    message = "Could not connect to $targetName. Make sure Bluetooth is open on the laptop/TV—or if it was paired before installing BT mouse, remove the old pairing on both devices and pair again via Auto-Pair.",
                    isAlreadyPairedSdpHint = true,
                )
                updateConnectionStateFromHost()
            }
        }

        hidExecutor.execute {
            runCatching { device.fetchUuidsWithSdp() }
            val initiated = runCatching { hid.connect(device) }.getOrDefault(false)
            if (!initiated) {
                connectTimeoutJob?.cancel()
                activeConnectAttemptAddress = null
                _diagnostic.value = ConnectionDiagnostic(
                    deviceName = targetName,
                    message = "Android could not start an HID link to $targetName. Remove the old pairing on both your phone and laptop, then tap Make Discoverable & Auto-Pair.",
                    isAlreadyPairedSdpHint = true,
                )
                updateConnectionStateFromHost()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectCurrent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasRequiredPermissions()) return
        val hid = hidDevice ?: return
        val host = connectedHost ?: return
        hidExecutor.execute {
            hid.disconnect(host)
        }
    }

    fun startDiscoverableCountdown(durationSeconds: Int = 300) {
        _diagnostic.value = null
        discoverableTimerJob?.cancel()
        discoverableTimerJob = scope.launch {
            var remaining = durationSeconds
            while (remaining > 0 && isActive && connectedHost == null) {
                _connectionState.value = BtConnectionState.Discoverable(remaining)
                delay(1000L)
                remaining--
            }
            if (connectedHost == null) {
                updateConnectionStateFromHost()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateConnectionStateFromHost() {
        val host = connectedHost
        if (host != null) {
            _connectionState.value = BtConnectionState.Connected(
                deviceName = safeDeviceName(host),
                address = host.address,
                deviceType = classifyDevice(host),
            )
        } else if (isAppRegistered) {
            _connectionState.value = BtConnectionState.Ready
        } else if (bluetoothAdapter?.isEnabled == false) {
            _connectionState.value = BtConnectionState.BluetoothDisabled
        } else {
            _connectionState.value = BtConnectionState.Initializing
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        return runCatching {
            device.name?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: device.address
    }

    @SuppressLint("MissingPermission")
    private fun classifyDevice(device: BluetoothDevice): HostDeviceType {
        val majorClass = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
        val nameLower = safeDeviceName(device).lowercase()
        return when {
            majorClass == BluetoothClass.Device.Major.COMPUTER ||
                nameLower.contains("laptop") ||
                nameLower.contains("pc") ||
                nameLower.contains("windows") ||
                nameLower.contains("desktop") ||
                nameLower.contains("mac") -> HostDeviceType.WINDOWS_PC

            majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                nameLower.contains("tv") ||
                nameLower.contains("bravia") ||
                nameLower.contains("webos") ||
                nameLower.contains("tizen") ||
                nameLower.contains("samsung") ||
                nameLower.contains("lg") ||
                nameLower.contains("android tv") ||
                nameLower.contains("google tv") ||
                nameLower.contains("shield") ||
                nameLower.contains("fire") -> HostDeviceType.SMART_TV

            else -> HostDeviceType.OTHER
        }
    }

    // ==================== OPTIMIZED DATA TRANSMISSION PROTOCOL ====================

    fun notifyMotionEnqueued() {
        if (motionLoopJob?.isActive == true) return
        motionLoopJob = scope.launch {
            while (isActive && motionEngine.hasPendingMotion()) {
                val packet = motionEngine.drainNextPacket()
                if (packet != null) {
                    sendMouseReportImmediate(
                        buttonsMask = activeButtonsMask.get(),
                        dx = packet.dx,
                        dy = packet.dy,
                        wheelV = packet.wheelV,
                        wheelH = packet.wheelH,
                    )
                }
                val intervalMs = (1000L / activePollingHz.coerceIn(30, 250)).coerceAtLeast(4L)
                delay(intervalMs)
            }
        }
    }

    fun setMouseButton(buttonMask: Int, pressed: Boolean) {
        val updated = if (pressed) {
            activeButtonsMask.updateAndGet { it or buttonMask }
        } else {
            activeButtonsMask.updateAndGet { it and buttonMask.inv() }
        }
        sendMouseReportImmediate(buttonsMask = updated, dx = 0, dy = 0, wheelV = 0, wheelH = 0)
    }

    fun clickMouseButton(buttonMask: Int) {
        val downMask = activeButtonsMask.get() or buttonMask
        sendMouseReportImmediate(buttonsMask = downMask, dx = 0, dy = 0, wheelV = 0, wheelH = 0)
        scope.launch {
            delay(28L)
            val upMask = activeButtonsMask.get() and buttonMask.inv()
            activeButtonsMask.set(upMask)
            sendMouseReportImmediate(buttonsMask = upMask, dx = 0, dy = 0, wheelV = 0, wheelH = 0)
        }
    }

    fun sendKeyPress(modifiers: Int, keyCode: Int) {
        sendKeyboardReportImmediate(modifiers, keyCode)
        scope.launch {
            delay(18L)
            sendKeyboardReportImmediate(0, 0)
        }
    }

    fun sendTextString(text: String) {
        if (text.isEmpty()) return
        scope.launch {
            for (ch in text) {
                val stroke = HidKeyMap.fromChar(ch) ?: continue
                sendKeyboardReportImmediate(stroke.modifiers, stroke.keyCode)
                delay(12L)
                sendKeyboardReportImmediate(0, 0)
                delay(12L)
            }
        }
    }

    fun sendConsumerPress(usage: Int) {
        sendConsumerReportImmediate(usage)
        scope.launch {
            delay(35L)
            sendConsumerReportImmediate(0)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendMouseReportImmediate(
        buttonsMask: Int,
        dx: Int,
        dy: Int,
        wheelV: Int,
        wheelH: Int,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasRequiredPermissions()) return
        val hid = hidDevice ?: return
        val host = connectedHost ?: return
        val payload = HidDescriptor.buildMouseReport(buttonsMask, dx, dy, wheelV, wheelH)
        hidExecutor.execute {
            val ok = hid.sendReport(host, HidDescriptor.REPORT_ID_MOUSE, payload)
            if (ok) markPacketSent()
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendKeyboardReportImmediate(modifiers: Int, keyCode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasRequiredPermissions()) return
        val hid = hidDevice ?: return
        val host = connectedHost ?: return
        val payload = HidDescriptor.buildKeyboardReport(modifiers, keyCode)
        hidExecutor.execute {
            val ok = hid.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, payload)
            if (ok) markPacketSent()
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendConsumerReportImmediate(usage: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasRequiredPermissions()) return
        val hid = hidDevice ?: return
        val host = connectedHost ?: return
        val payload = HidDescriptor.buildConsumerReport(usage)
        hidExecutor.execute {
            val ok = hid.sendReport(host, HidDescriptor.REPORT_ID_CONSUMER, payload)
            if (ok) markPacketSent()
        }
    }

    private fun markPacketSent() {
        packetCounter++
        _telemetry.value = _telemetry.value.copy(
            packetsSent = packetCounter,
            isTransmitting = true,
        )
        txPulseJob?.cancel()
        txPulseJob = scope.launch {
            delay(160L)
            _telemetry.value = _telemetry.value.copy(isTransmitting = false)
        }
    }

    @SuppressLint("MissingPermission")
    fun release() {
        connectTimeoutJob?.cancel()
        motionLoopJob?.cancel()
        discoverableTimerJob?.cancel()
        txPulseJob?.cancel()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(bluetoothReceiver) }
            receiverRegistered = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasRequiredPermissions()) {
            runCatching {
                hidDevice?.unregisterApp()
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
            }
        }
        hidExecutor.shutdownNow()
        scope.cancel()
    }
}
