package com.slm.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

enum class PcSystemAction(
    val code: Byte,
    val label: String,
    val requiresConfirmation: Boolean,
) {
    LOCK(0x01, "Lock PC", false),
    SLEEP(0x02, "Sleep PC", true),
    RESTART(0x03, "Restart PC", true),
    SHUTDOWN(0x04, "Shutdown PC", true),
    BRIGHTNESS_UP(0x05, "Bright +", false),
    BRIGHTNESS_DOWN(0x06, "Bright -", false),
    SCREENSHOT(0x07, "Screenshot", false),
}

data class QrConnectPayload(
    val ips: List<String>,
    val port: Int,
    val hostName: String?,
)

object WifiPcToolsProtocol {
    const val OP_GET_CLIPBOARD: Byte = 0x05
    const val OP_SET_CLIPBOARD: Byte = 0x06
    const val OP_SYSTEM_ACTION: Byte = 0x07

    const val STATUS_ACK: Byte = 0x06
    private const val MAX_CLIPBOARD_BYTES = 1_048_576
}

fun parseQrConnectPayload(raw: String): QrConnectPayload? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith("BTMOUSE_QR_V1|")) {
        val parts = trimmed.split("|")
        val ips = parts.getOrNull(1)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (ips.isEmpty()) return null
        val port = parts.getOrNull(2)?.toIntOrNull() ?: WifiProtocol.CONTROL_PORT
        val hostName = parts.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
        return QrConnectPayload(ips = ips, port = port, hostName = hostName)
    }

    val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d{1,5})?$""")
    if (ipv4Regex.matches(trimmed)) {
        val segs = trimmed.split(":")
        val ip = segs[0]
        val port = segs.getOrNull(1)?.toIntOrNull() ?: WifiProtocol.CONTROL_PORT
        return QrConnectPayload(ips = listOf(ip), port = port, hostName = null)
    }

    return null
}

object WifiPcToolsClient {

    suspend fun getPcClipboard(hostIp: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(hostIp, WifiFileProtocol.FILE_PORT), 4000)
                socket.soTimeout = 5000

                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())

                out.writeByte(WifiPcToolsProtocol.OP_GET_CLIPBOARD.toInt())
                out.flush()

                val status = inp.readByte()
                if (status != WifiPcToolsProtocol.STATUS_ACK) {
                    error("PC rejected clipboard request")
                }

                val len = inp.readInt()
                if (len < 0 || len > 1_048_576) {
                    error("Invalid clipboard size: $len")
                }
                if (len == 0) return@use ""

                val bytes = ByteArray(len)
                inp.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
        }
    }

    suspend fun sendClipboardToPc(hostIp: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val utf8 = text.toByteArray(Charsets.UTF_8)
            val sendLen = utf8.size.coerceAtMost(1_048_576)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(hostIp, WifiFileProtocol.FILE_PORT), 4000)
                socket.soTimeout = 5000

                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())

                val header = ByteBuffer.allocate(5)
                    .put(WifiPcToolsProtocol.OP_SET_CLIPBOARD)
                    .putInt(sendLen)
                    .array()
                out.write(header)
                if (sendLen > 0) {
                    out.write(utf8, 0, sendLen)
                }
                out.flush()

                val status = inp.readByte()
                if (status != WifiPcToolsProtocol.STATUS_ACK) {
                    error("PC failed to update clipboard")
                }
            }
        }
    }

    suspend fun sendSystemAction(hostIp: String, action: PcSystemAction): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(hostIp, WifiFileProtocol.FILE_PORT), 4000)
                socket.soTimeout = 6000

                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())

                out.writeByte(WifiPcToolsProtocol.OP_SYSTEM_ACTION.toInt())
                out.writeByte(action.code.toInt())
                out.flush()

                val status = inp.readByte()
                if (status != WifiPcToolsProtocol.STATUS_ACK) {
                    error("PC could not run ${action.label}")
                }
            }
        }
    }

    suspend fun pickBestReachableIp(ips: List<String>): String = withContext(Dispatchers.IO) {
        if (ips.size <= 1) return@withContext ips.firstOrNull().orEmpty()
        for (candidate in ips) {
            val reachable = runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(candidate, WifiFileProtocol.FILE_PORT), 550)
                    true
                }
            }.getOrDefault(false)
            if (reachable) return@withContext candidate
        }
        ips.first()
    }
}
