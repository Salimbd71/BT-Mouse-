package com.example.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.bluetooth.HidMotionEngine
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class WifiHostDevice(
    val name: String,
    val ipAddress: String,
    val port: Int = WifiProtocol.CONTROL_PORT,
    val isConnected: Boolean = false,
)

sealed class WifiConnectionState {
    data object Idle : WifiConnectionState()
    data object Scanning : WifiConnectionState()
    data class Connecting(val hostName: String, val ipAddress: String) : WifiConnectionState()
    data class Connected(val hostName: String, val ipAddress: String, val latencyMs: Int) : WifiConnectionState()
    data class Error(val message: String) : WifiConnectionState()
}

object WifiProtocol {
    const val CONTROL_PORT = 51920
    const val DISCOVERY_PORT = 51921
    const val MAGIC_0: Byte = 0x42 // 'B'
    const val MAGIC_1: Byte = 0x54 // 'T'

    const val PKT_HELLO: Byte = 0x01
    const val PKT_HELLO_ACK: Byte = 0x02
    const val PKT_PING: Byte = 0x03
    const val PKT_PONG: Byte = 0x04

    const val PKT_MOUSE_MOVE: Byte = 0x10
    const val PKT_MOUSE_BUTTON: Byte = 0x11
    const val PKT_PINCH_ZOOM: Byte = 0x12
    const val PKT_KEY_PRESS: Byte = 0x20
    const val PKT_UNICODE_TEXT: Byte = 0x21
    const val PKT_CONSUMER_KEY: Byte = 0x30

    const val BTN_ACTION_UP: Byte = 0
    const val BTN_ACTION_DOWN: Byte = 1
    const val BTN_ACTION_CLICK: Byte = 2

    fun buildHelloPacket(phoneName: String): ByteArray {
        val nameBytes = phoneName.take(48).toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4 + nameBytes.size)
            .put(MAGIC_0)
            .put(MAGIC_1)
            .put(PKT_HELLO)
            .put(nameBytes.size.toByte())
            .put(nameBytes)
            .array()
    }

    fun buildPingPacket(seq: Byte): ByteArray = byteArrayOf(MAGIC_0, MAGIC_1, PKT_PING, seq)

    fun buildMouseMovePacket(dx: Int, dy: Int, wheelV: Int, wheelH: Int): ByteArray = byteArrayOf(
        MAGIC_0,
        MAGIC_1,
        PKT_MOUSE_MOVE,
        dx.coerceIn(-127, 127).toByte(),
        dy.coerceIn(-127, 127).toByte(),
        wheelV.coerceIn(-127, 127).toByte(),
        wheelH.coerceIn(-127, 127).toByte(),
    )

    fun buildMouseButtonPacket(seq: Byte, buttonMask: Int, action: Byte): ByteArray = byteArrayOf(
        MAGIC_0,
        MAGIC_1,
        PKT_MOUSE_BUTTON,
        seq,
        (buttonMask and 0x1F).toByte(),
        action,
    )

    fun buildPinchZoomPacket(wheelStep: Int): ByteArray = byteArrayOf(
        MAGIC_0,
        MAGIC_1,
        PKT_PINCH_ZOOM,
        wheelStep.coerceIn(-127, 127).toByte(),
    )

    fun buildKeyPressPacket(seq: Byte, modifiers: Int, hidKeyCode: Int): ByteArray = byteArrayOf(
        MAGIC_0,
        MAGIC_1,
        PKT_KEY_PRESS,
        seq,
        (modifiers and 0xFF).toByte(),
        (hidKeyCode and 0xFF).toByte(),
    )

    fun buildUnicodeTextPacket(seq: Byte, text: String): ByteArray {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        val len = utf8.size.coerceAtMost(512)
        return ByteBuffer.allocate(6 + len)
            .put(MAGIC_0)
            .put(MAGIC_1)
            .put(PKT_UNICODE_TEXT)
            .put(seq)
            .put(((len shr 8) and 0xFF).toByte())
            .put((len and 0xFF).toByte())
            .put(utf8, 0, len)
            .array()
    }

    fun buildConsumerKeyPacket(seq: Byte, usage: Int): ByteArray = byteArrayOf(
        MAGIC_0,
        MAGIC_1,
        PKT_CONSUMER_KEY,
        seq,
        (usage and 0xFF).toByte(),
        ((usage shr 8) and 0xFF).toByte(),
    )
}

class WifiMouseController(
    private val context: Context,
    private val motionEngine: HidMotionEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendExecutor = Executors.newSingleThreadExecutor()

    private val _connectionState = MutableStateFlow<WifiConnectionState>(WifiConnectionState.Idle)
    val connectionState: StateFlow<WifiConnectionState> = _connectionState.asStateFlow()

    private val _discoveredHosts = MutableStateFlow<List<WifiHostDevice>>(emptyList())
    val discoveredHosts: StateFlow<List<WifiHostDevice>> = _discoveredHosts.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private var controlSocket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var discoveryJob: Job? = null
    private var motionLoopJob: Job? = null
    private var txPulseJob: Job? = null
    private var connectTimeoutJob: Job? = null

    private var activePollingHz = 125
    private var autoConnectEnabled = true
    private var preferredIp: String? = null
    private var activeHostAddress: InetAddress? = null
    private var activeHostIp: String? = null
    private var activeHostName: String? = null
    private var activeHostPort: Int = WifiProtocol.CONTROL_PORT
    private var lastPingSentAtMs: Long = 0L
    private var lastPongReceivedAtMs: Long = 0L
    private val seqCounter = AtomicInteger(1)
    private var hasAutoTried = false

    var onHostConnected: ((ip: String, name: String) -> Unit)? = null

    fun updateSettings(pollingRateHz: Int, autoConnect: Boolean, lastIp: String?) {
        activePollingHz = pollingRateHz
        autoConnectEnabled = autoConnect
        preferredIp = lastIp
    }

    fun startDiscovery(autoConnectIfFound: Boolean = false) {
        ensureControlSocketOpen()
        if (_connectionState.value !is WifiConnectionState.Connected &&
            _connectionState.value !is WifiConnectionState.Connecting
        ) {
            _connectionState.value = WifiConnectionState.Scanning
        }

        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            val discSocket = runCatching {
                DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 900
                }
            }.getOrNull() ?: return@launch

            val foundMap = linkedMapOf<String, WifiHostDevice>()
            val phoneLabel = (Build.MODEL ?: "Android").take(32)
            val probeBytes = "BTMOUSE_DISCOVER_V1|$phoneLabel".toByteArray(Charsets.UTF_8)
            val targets = collectBroadcastAddresses()

            for (round in 0 until 3) {
                if (!isActive) return@launch
                for (addr in targets) {
                    runCatching {
                        val pkt = DatagramPacket(probeBytes, probeBytes.size, addr, WifiProtocol.DISCOVERY_PORT)
                        discSocket.send(pkt)
                    }
                }
                val deadline = System.currentTimeMillis() + 850L
                val buf = ByteArray(512)
                while (System.currentTimeMillis() < deadline && isActive) {
                    val reply = DatagramPacket(buf, buf.size)
                    val gotPacket = runCatching {
                        discSocket.receive(reply)
                        true
                    }.getOrDefault(false)
                    if (!gotPacket) break

                    val msg = String(reply.data, 0, reply.length, Charsets.UTF_8).trim()
                    if (msg.startsWith("BTMOUSE_HOST_V1|")) {
                        val parts = msg.split("|")
                        val hostName = parts.getOrNull(1)?.ifBlank { null } ?: "Windows PC"
                        val hostPort = parts.getOrNull(2)?.toIntOrNull() ?: WifiProtocol.CONTROL_PORT
                        val ip = reply.address.hostAddress ?: continue
                        foundMap[ip] = WifiHostDevice(
                            name = hostName,
                            ipAddress = ip,
                            port = hostPort,
                            isConnected = ip == activeHostIp && _connectionState.value is WifiConnectionState.Connected,
                        )
                        _discoveredHosts.value = foundMap.values.toList()

                        if (autoConnectIfFound &&
                            autoConnectEnabled &&
                            !hasAutoTried &&
                            ip == preferredIp &&
                            _connectionState.value !is WifiConnectionState.Connected
                        ) {
                            hasAutoTried = true
                            connectToIp(ip, hostName, hostPort)
                        }
                    }
                }
                if (round == 0 && foundMap.isNotEmpty() && autoConnectIfFound) {
                    // Already found host quickly
                    if (_connectionState.value is WifiConnectionState.Connected ||
                        _connectionState.value is WifiConnectionState.Connecting
                    ) {
                        break
                    }
                }
            }

            discSocket.close()
            if (_connectionState.value is WifiConnectionState.Scanning) {
                _connectionState.value = WifiConnectionState.Idle
            }
        }
    }

    fun connectToIp(ip: String, knownName: String? = null, port: Int = WifiProtocol.CONTROL_PORT) {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return

        ensureControlSocketOpen()
        val displayName = knownName ?: trimmed
        _connectionState.value = WifiConnectionState.Connecting(displayName, trimmed)

        scope.launch {
            val addr = runCatching { InetAddress.getByName(trimmed) }.getOrNull()
            if (addr == null) {
                _connectionState.value = WifiConnectionState.Error("Invalid IP address: $trimmed")
                return@launch
            }

            activeHostAddress = addr
            activeHostIp = trimmed
            activeHostName = displayName
            activeHostPort = port
            lastPongReceivedAtMs = System.currentTimeMillis()

            val hello = WifiProtocol.buildHelloPacket(Build.MODEL ?: "BT mouse")
            sendRawImmediate(hello)

            connectTimeoutJob?.cancel()
            connectTimeoutJob = scope.launch {
                delay(4000L)
                if (_connectionState.value is WifiConnectionState.Connecting) {
                    activeHostAddress = null
                    activeHostIp = null
                    _connectionState.value = WifiConnectionState.Error(
                        "No response from $trimmed. Make sure BTMouseReceiver.exe is running on the same Wi-Fi and allowed in Windows Firewall.",
                    )
                }
            }
        }
    }

    fun disconnect() {
        connectTimeoutJob?.cancel()
        heartbeatJob?.cancel()
        activeHostAddress = null
        activeHostIp = null
        activeHostName = null
        _connectionState.value = WifiConnectionState.Idle
        _discoveredHosts.value = _discoveredHosts.value.map { it.copy(isConnected = false) }
    }

    private fun ensureControlSocketOpen() {
        if (controlSocket != null && controlSocket?.isClosed == false) return
        val socket = runCatching { DatagramSocket() }.getOrNull() ?: return
        controlSocket = socket
        receiveJob?.cancel()
        receiveJob = scope.launch {
            val buf = ByteArray(512)
            while (isActive && !socket.isClosed) {
                val pkt = DatagramPacket(buf, buf.size)
                val ok = runCatching {
                    socket.receive(pkt)
                    true
                }.getOrDefault(false)
                if (!ok) continue

                if (pkt.length >= 3 &&
                    pkt.data[0] == WifiProtocol.MAGIC_0 &&
                    pkt.data[1] == WifiProtocol.MAGIC_1
                ) {
                    val now = System.currentTimeMillis()
                    lastPongReceivedAtMs = now
                    when (pkt.data[2]) {
                        WifiProtocol.PKT_HELLO_ACK -> {
                            connectTimeoutJob?.cancel()
                            val nameLen = if (pkt.length >= 4) (pkt.data[3].toInt() and 0xFF) else 0
                            val serverName = if (nameLen > 0 && pkt.length >= 4 + nameLen) {
                                String(pkt.data, 4, nameLen, Charsets.UTF_8)
                            } else {
                                activeHostName ?: pkt.address.hostAddress ?: "Windows PC"
                            }
                            val ip = pkt.address.hostAddress ?: activeHostIp ?: ""
                            activeHostIp = ip
                            activeHostName = serverName
                            _connectionState.value = WifiConnectionState.Connected(
                                hostName = serverName,
                                ipAddress = ip,
                                latencyMs = 2,
                            )
                            _discoveredHosts.value = _discoveredHosts.value.map {
                                it.copy(isConnected = it.ipAddress == ip)
                            }.let { list ->
                                if (list.any { it.ipAddress == ip }) list
                                else list + WifiHostDevice(serverName, ip, activeHostPort, isConnected = true)
                            }
                            onHostConnected?.invoke(ip, serverName)
                            startHeartbeat()
                        }
                        WifiProtocol.PKT_PONG -> {
                            val rtt = (now - lastPingSentAtMs).toInt().coerceIn(1, 999)
                            val ip = activeHostIp ?: ""
                            val name = activeHostName ?: ip
                            if (_connectionState.value is WifiConnectionState.Connected) {
                                _connectionState.value = WifiConnectionState.Connected(name, ip, rtt)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connectionState.value is WifiConnectionState.Connected) {
                lastPingSentAtMs = System.currentTimeMillis()
                sendRawImmediate(WifiProtocol.buildPingPacket(nextSeq()))
                delay(2500L)
                if (System.currentTimeMillis() - lastPongReceivedAtMs > 9000L) {
                    _connectionState.value = WifiConnectionState.Error("Lost Wi-Fi connection to ${activeHostName ?: "PC"}.")
                    activeHostAddress = null
                    _discoveredHosts.value = _discoveredHosts.value.map { it.copy(isConnected = false) }
                    break
                }
            }
        }
    }

    // ==================== OPTIMIZED UDP TRANSMISSION ====================

    fun notifyMotionEnqueued() {
        if (motionLoopJob?.isActive == true) return
        motionLoopJob = scope.launch(Dispatchers.Default) {
            while (isActive && motionEngine.hasPendingMotion()) {
                val packet = motionEngine.drainNextPacket()
                if (packet != null) {
                    val bytes = WifiProtocol.buildMouseMovePacket(
                        dx = packet.dx,
                        dy = packet.dy,
                        wheelV = packet.wheelV,
                        wheelH = packet.wheelH,
                    )
                    sendRawImmediate(bytes)
                }
                val intervalMs = (1000L / activePollingHz.coerceIn(30, 250)).coerceAtLeast(4L)
                delay(intervalMs)
            }
        }
    }

    fun setMouseButton(buttonMask: Int, pressed: Boolean) {
        val action = if (pressed) WifiProtocol.BTN_ACTION_DOWN else WifiProtocol.BTN_ACTION_UP
        val pkt = WifiProtocol.buildMouseButtonPacket(nextSeq(), buttonMask, action)
        // Send twice 4ms apart; receiver deduplicates by sequence number for reliable UDP clicks
        sendRawImmediate(pkt)
        scope.launch {
            delay(4L)
            sendRawImmediate(pkt)
        }
    }

    fun clickMouseButton(buttonMask: Int) {
        val pkt = WifiProtocol.buildMouseButtonPacket(nextSeq(), buttonMask, WifiProtocol.BTN_ACTION_CLICK)
        sendRawImmediate(pkt)
        scope.launch {
            delay(4L)
            sendRawImmediate(pkt)
        }
    }

    fun sendPinchZoom(wheelStep: Int) {
        if (wheelStep == 0) return
        sendRawImmediate(WifiProtocol.buildPinchZoomPacket(wheelStep))
    }

    fun sendKeyPress(modifiers: Int, hidKeyCode: Int) {
        val pkt = WifiProtocol.buildKeyPressPacket(nextSeq(), modifiers, hidKeyCode)
        sendRawImmediate(pkt)
        scope.launch {
            delay(4L)
            sendRawImmediate(pkt)
        }
    }

    fun sendUnicodeText(text: String) {
        if (text.isEmpty()) return
        val pkt = WifiProtocol.buildUnicodeTextPacket(nextSeq(), text)
        sendRawImmediate(pkt)
        scope.launch {
            delay(5L)
            sendRawImmediate(pkt)
        }
    }

    fun sendConsumerPress(usage: Int) {
        val pkt = WifiProtocol.buildConsumerKeyPacket(nextSeq(), usage)
        sendRawImmediate(pkt)
        scope.launch {
            delay(4L)
            sendRawImmediate(pkt)
        }
    }

    private fun nextSeq(): Byte = (seqCounter.getAndIncrement() and 0xFF).toByte()

    private fun sendRawImmediate(bytes: ByteArray) {
        val target = activeHostAddress ?: return
        val socket = controlSocket ?: return
        val port = activeHostPort
        sendExecutor.execute {
            runCatching {
                val packet = DatagramPacket(bytes, bytes.size, target, port)
                socket.send(packet)
                markTransmitted()
            }
        }
    }

    private fun markTransmitted() {
        _isTransmitting.value = true
        txPulseJob?.cancel()
        txPulseJob = scope.launch {
            delay(160L)
            _isTransmitting.value = false
        }
    }

    private fun collectBroadcastAddresses(): List<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (ifAddr in iface.interfaceAddresses) {
                    val addr = ifAddr.address
                    val bcast = ifAddr.broadcast
                    if (addr is Inet4Address && bcast != null) {
                        result.add(bcast)
                    }
                }
            }
        }
        runCatching { result.add(InetAddress.getByName("255.255.255.255")) }
        return result.toList()
    }

    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun release() {
        connectTimeoutJob?.cancel()
        discoveryJob?.cancel()
        heartbeatJob?.cancel()
        motionLoopJob?.cancel()
        txPulseJob?.cancel()
        receiveJob?.cancel()
        runCatching { controlSocket?.close() }
        sendExecutor.shutdownNow()
        scope.cancel()
    }
}
