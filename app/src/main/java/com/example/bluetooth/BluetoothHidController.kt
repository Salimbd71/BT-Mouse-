package com.example.bluetooth

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

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var isAppRegistered = false
    private var receiverRegistered = false
    private var autoConnectEnabled = true
    private var preferredHostAddress: String? = null

    private var activeButtonsMask = AtomicInteger(0)
    private var activePollingHz = 125
    private var motionLoopJob: Job? = null
    private var discoverableTimerJob: Job? = null
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
                        _connectionState.value = if (isAppRegistered) BtConnectionState.Ready else BtConnectionState.Initializing
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
            registerHidApp(hidDevice!!)
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
                registerHidApp(device)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectedHost = null
                isAppRegistered = false
                _connectionState.value = BtConnectionState.Initializing
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("MissingPermission")
    private fun registerHidApp(device: BluetoothHidDevice) {
        if (!hasRequiredPermissions()) return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "BT mouse",
            "Wireless Mouse & Keyboard",
            "BT mouse",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.COMPOSITE_DESCRIPTOR,
        )
        // Low-latency QoS tuning for responsive pointer & wheel frames (4ms max latency)
        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            4000,
            4000,
        )
        device.registerApp(sdp, null, outQos, hidExecutor, hidCallback)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private val hidCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isAppRegistered = registered
            if (registered) {
                if (pluggedDevice != null) {
                    connectedHost = pluggedDevice
                }
                refreshPairedDevices()
                updateConnectionStateFromHost()
                if (connectedHost == null && autoConnectEnabled) {
                    attemptAutoConnect()
                }
            } else {
                connectedHost = null
                _connectionState.value = BtConnectionState.Ready
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    discoverableTimerJob?.cancel()
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
                    if (connectedHost?.address == device.address) {
                        connectedHost = null
                    }
                    refreshPairedDevices()
                    updateConnectionStateFromHost()
                }
            }
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
        if (!adapter.isEnabled) {
            _pairedDevices.value = emptyList()
            return
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
        val device = bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address } ?: return
        connectDevice(device)
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasRequiredPermissions()) return
        val hid = hidDevice ?: return
        if (connectedHost != null && connectedHost?.address != device.address) {
            hid.disconnect(connectedHost)
        }
        _connectionState.value = BtConnectionState.Connecting(
            deviceName = safeDeviceName(device),
            address = device.address,
        )
        hidExecutor.execute {
            hid.connect(device)
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
