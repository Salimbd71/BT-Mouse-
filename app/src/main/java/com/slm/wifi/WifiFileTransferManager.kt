package com.slm.wifi

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object WifiFileProtocol {
    const val FILE_PORT = 51922

    const val OP_UPLOAD_TO_PC: Byte = 0x01
    const val OP_LIST_OFFERS: Byte = 0x02
    const val OP_DOWNLOAD_FROM_PC: Byte = 0x03
    const val OP_CLEAR_OFFER: Byte = 0x04

    const val STATUS_ACK: Byte = 0x06

    fun buildUploadHeader(fileName: String, fileSize: Long): ByteArray {
        val nameBytes = fileName.toByteArray(Charsets.UTF_8)
        val safeLen = nameBytes.size.coerceAtMost(4096)
        return ByteBuffer.allocate(1 + 2 + safeLen + 8)
            .put(OP_UPLOAD_TO_PC)
            .putShort(safeLen.toShort())
            .put(nameBytes, 0, safeLen)
            .putLong(fileSize)
            .array()
    }

    fun buildOfferIdRequest(opCode: Byte, offerId: String): ByteArray {
        val idBytes = offerId.toByteArray(Charsets.UTF_8)
        val safeLen = idBytes.size.coerceAtMost(256)
        return ByteBuffer.allocate(1 + 2 + safeLen)
            .put(opCode)
            .putShort(safeLen.toShort())
            .put(idBytes, 0, safeLen)
            .array()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }
}

data class PcFileOffer(
    val id: String,
    val name: String,
    val sizeBytes: Long,
)

enum class TransferDirection {
    TO_PC,
    FROM_PC,
}

data class ActiveFileTransfer(
    val fileName: String,
    val direction: TransferDirection,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val queueRemaining: Int = 0,
)

data class CompletedFileTransfer(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val direction: TransferDirection,
    val sizeBytes: Long,
    val savedUri: Uri? = null,
    val mimeType: String? = null,
    val locationHint: String,
)

data class FileTransferUiState(
    val incomingOffers: List<PcFileOffer> = emptyList(),
    val activeTransfer: ActiveFileTransfer? = null,
    val completedTransfers: List<CompletedFileTransfer> = emptyList(),
    val isRefreshingOffers: Boolean = false,
    val errorMessage: String? = null,
) {
    val isBusy: Boolean get() = activeTransfer != null
}

/**
 * Shared singleton transfer engine so transfers continue seamlessly in FileTransferService
 * even if the user closes MainActivity, while ViewModel & notifications stay synchronized.
 */
object FileTransferRepository {
    private val _state = MutableStateFlow(FileTransferUiState())
    val state: StateFlow<FileTransferUiState> = _state.asStateFlow()

    @Volatile
    private var activeSocket: Socket? = null
    private val cancelRequested = AtomicBoolean(false)

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun requestCancel() {
        cancelRequested.set(true)
        runCatching { activeSocket?.close() }
        activeSocket = null
        _state.update {
            it.copy(
                activeTransfer = null,
                errorMessage = "File transfer cancelled",
            )
        }
    }

    fun resetCancelFlag() {
        cancelRequested.set(false)
    }

    suspend fun refreshIncomingOffers(hostIp: String) = withContext(Dispatchers.IO) {
        _state.update { it.copy(isRefreshingOffers = true, errorMessage = null) }
        val result = runCatching {
            Socket().use { socket ->
                socket.soTimeout = 8_000
                socket.connect(InetSocketAddress(hostIp, WifiFileProtocol.FILE_PORT), 4_000)
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                out.writeByte(WifiFileProtocol.OP_LIST_OFFERS.toInt())
                out.flush()

                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val count = input.readInt().coerceIn(0, 500)
                val offers = ArrayList<PcFileOffer>(count)
                repeat(count) {
                    val idLen = input.readUnsignedShort()
                    val idBytes = ByteArray(idLen)
                    input.readFully(idBytes)
                    val id = String(idBytes, Charsets.UTF_8)

                    val nameLen = input.readUnsignedShort()
                    val nameBytes = ByteArray(nameLen)
                    input.readFully(nameBytes)
                    val name = String(nameBytes, Charsets.UTF_8)

                    val size = input.readLong()
                    offers.add(PcFileOffer(id = id, name = name, sizeBytes = size))
                }
                offers
            }
        }

        result.onSuccess { offers ->
            _state.update { it.copy(incomingOffers = offers, isRefreshingOffers = false) }
        }.onFailure { err ->
            _state.update {
                it.copy(
                    isRefreshingOffers = false,
                    errorMessage = "Could not check PC files: ${err.message ?: "Connection failed"}",
                )
            }
        }
    }

    suspend fun uploadUrisToPc(
        context: Context,
        hostIp: String,
        uris: List<Uri>,
        onProgress: (ActiveFileTransfer) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext false
        _state.update { it.copy(errorMessage = null) }

        for ((index, uri) in uris.withIndex()) {
            if (cancelRequested.get()) return@withContext false
            val remainingCount = uris.size - index - 1
            val res = runCatching {
                val (fileName, knownSize) = queryUriMeta(context, uri)
                val exactSize = if (knownSize >= 0L) knownSize else measureUriSize(context, uri)
                if (exactSize < 0L) error("Unable to read file size for $fileName")

                Socket().use { socket ->
                    activeSocket = socket
                    socket.soTimeout = 25_000
                    socket.tcpNoDelay = true
                    socket.sendBufferSize = 65536
                    socket.connect(InetSocketAddress(hostIp, WifiFileProtocol.FILE_PORT), 5_000)

                    val out = BufferedOutputStream(socket.getOutputStream(), 65536)
                    out.write(WifiFileProtocol.buildUploadHeader(fileName, exactSize))

                    val initial = ActiveFileTransfer(
                        fileName = fileName,
                        direction = TransferDirection.TO_PC,
                        bytesTransferred = 0L,
                        totalBytes = exactSize,
                        queueRemaining = remainingCount,
                    )
                    _state.update { it.copy(activeTransfer = initial) }
                    onProgress(initial)

                    val buffer = ByteArray(65536)
                    var sent = 0L
                    var lastUiUpdate = 0L

                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: error("Cannot open $fileName")
                    inputStream.use { stream ->
                        while (true) {
                            if (cancelRequested.get()) throw CancellationException("Cancelled by user")
                            val n = stream.read(buffer)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                            sent += n
                            if (sent - lastUiUpdate >= 131072L || sent == exactSize) {
                                lastUiUpdate = sent
                                val active = ActiveFileTransfer(
                                    fileName = fileName,
                                    direction = TransferDirection.TO_PC,
                                    bytesTransferred = sent,
                                    totalBytes = exactSize,
                                    queueRemaining = remainingCount,
                                )
                                _state.update { it.copy(activeTransfer = active) }
                                onProgress(active)
                            }
                        }
                    }
                    out.flush()

                    val ack = socket.getInputStream().read()
                    if (ack != WifiFileProtocol.STATUS_ACK.toInt()) {
                        error("PC failed to save $fileName")
                    }

                    CompletedFileTransfer(
                        fileName = fileName,
                        direction = TransferDirection.TO_PC,
                        sizeBytes = exactSize,
                        locationHint = "PC Downloads\\BT mouse Files",
                    )
                }
            }

            activeSocket = null

            if (cancelRequested.get() || res.exceptionOrNull() is CancellationException) {
                _state.update { it.copy(activeTransfer = null, errorMessage = "File transfer cancelled") }
                return@withContext false
            }

            res.onSuccess { completed ->
                _state.update { current ->
                    current.copy(
                        activeTransfer = null,
                        completedTransfers = (listOf(completed) + current.completedTransfers).take(25),
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        activeTransfer = null,
                        errorMessage = if (cancelRequested.get()) {
                            "File transfer cancelled"
                        } else {
                            "Send failed: ${err.message ?: "Transfer interrupted"}"
                        },
                    )
                }
                return@withContext false
            }
        }
        true
    }

    suspend fun downloadOffersFromPc(
        context: Context,
        hostIp: String,
        offers: List<PcFileOffer>,
        onProgress: (ActiveFileTransfer) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        if (offers.isEmpty()) return@withContext false
        _state.update { it.copy(errorMessage = null) }

        for ((index, offer) in offers.withIndex()) {
            if (cancelRequested.get()) return@withContext false
            val remainingCount = offers.size - index - 1
            val result = runCatching {
                Socket().use { socket ->
                    activeSocket = socket
                    socket.soTimeout = 25_000
                    socket.tcpNoDelay = true
                    socket.receiveBufferSize = 65536
                    socket.connect(InetSocketAddress(hostIp, WifiFileProtocol.FILE_PORT), 5_000)

                    val out = BufferedOutputStream(socket.getOutputStream())
                    out.write(WifiFileProtocol.buildOfferIdRequest(WifiFileProtocol.OP_DOWNLOAD_FROM_PC, offer.id))
                    out.flush()

                    val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 65536))
                    val status = input.readUnsignedByte()
                    val fileSize = input.readLong()
                    if (status != 1 || fileSize < 0L) {
                        error("File is no longer available on PC")
                    }

                    val initial = ActiveFileTransfer(
                        fileName = offer.name,
                        direction = TransferDirection.FROM_PC,
                        bytesTransferred = 0L,
                        totalBytes = fileSize,
                        queueRemaining = remainingCount,
                    )
                    _state.update { it.copy(activeTransfer = initial) }
                    onProgress(initial)

                    val mimeType = guessMimeType(offer.name)
                    val (savedUri, locationHint) = writeToDownloads(
                        context = context,
                        fileName = offer.name,
                        mimeType = mimeType,
                        fileSize = fileSize,
                        queueRemaining = remainingCount,
                        input = input,
                        onProgress = onProgress,
                    )
                    clearOfferOnPc(hostIp, offer.id)

                    CompletedFileTransfer(
                        fileName = offer.name,
                        direction = TransferDirection.FROM_PC,
                        sizeBytes = fileSize,
                        savedUri = savedUri,
                        mimeType = mimeType,
                        locationHint = locationHint,
                    )
                }
            }

            activeSocket = null

            if (cancelRequested.get() || result.exceptionOrNull() is CancellationException) {
                _state.update { it.copy(activeTransfer = null, errorMessage = "File transfer cancelled") }
                return@withContext false
            }

            result.onSuccess { completed ->
                _state.update { current ->
                    current.copy(
                        activeTransfer = null,
                        incomingOffers = current.incomingOffers.filterNot { it.id == offer.id },
                        completedTransfers = (listOf(completed) + current.completedTransfers).take(25),
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        activeTransfer = null,
                        errorMessage = if (cancelRequested.get()) {
                            "File transfer cancelled"
                        } else {
                            "Download failed: ${err.message ?: "Transfer interrupted"}"
                        },
                    )
                }
                return@withContext false
            }
        }
        true
    }

    private fun clearOfferOnPc(hostIp: String, offerId: String) {
        runCatching {
            Socket().use { socket ->
                socket.soTimeout = 4_000
                socket.connect(InetSocketAddress(hostIp, WifiFileProtocol.FILE_PORT), 3_000)
                val out = socket.getOutputStream()
                out.write(WifiFileProtocol.buildOfferIdRequest(WifiFileProtocol.OP_CLEAR_OFFER, offerId))
                out.flush()
                socket.getInputStream().read()
            }
        }
    }

    private fun writeToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        fileSize: Long,
        queueRemaining: Int,
        input: DataInputStream,
        onProgress: (ActiveFileTransfer) -> Unit,
    ): Pair<Uri?, String> {
        val buffer = ByteArray(65536)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BT mouse")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create file in Downloads")

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    streamExactToOutput(fileName, fileSize, queueRemaining, input, out, buffer, onProgress)
                } ?: error("Could not open Downloads output stream")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(itemUri, publishValues, null, null)
                return itemUri to "Phone Downloads/BT mouse"
            } catch (t: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw t
            }
        } else {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BT mouse",
            )
            val targetDir = if (publicDir.exists() || publicDir.mkdirs()) {
                publicDir
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "BT mouse").apply {
                    mkdirs()
                }
            }
            val targetFile = uniqueLegacyFile(targetDir, fileName)
            try {
                FileOutputStream(targetFile).use { out ->
                    streamExactToOutput(fileName, fileSize, queueRemaining, input, out, buffer, onProgress)
                }
            } catch (t: Throwable) {
                runCatching { targetFile.delete() }
                throw t
            }
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
            return null to "Downloads/BT mouse/${targetFile.name}"
        }
    }

    private fun streamExactToOutput(
        fileName: String,
        fileSize: Long,
        queueRemaining: Int,
        input: DataInputStream,
        out: OutputStream,
        buffer: ByteArray,
        onProgress: (ActiveFileTransfer) -> Unit,
    ) {
        var remaining = fileSize
        var received = 0L
        var lastUiUpdate = 0L
        while (remaining > 0L) {
            if (cancelRequested.get()) throw CancellationException("Cancelled by user")
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val n = input.read(buffer, 0, toRead)
            if (n <= 0) error("Connection closed during download")
            out.write(buffer, 0, n)
            remaining -= n
            received += n
            if (received - lastUiUpdate >= 131072L || remaining == 0L) {
                lastUiUpdate = received
                val active = ActiveFileTransfer(
                    fileName = fileName,
                    direction = TransferDirection.FROM_PC,
                    bytesTransferred = received,
                    totalBytes = fileSize,
                    queueRemaining = queueRemaining,
                )
                _state.update { it.copy(activeTransfer = active) }
                onProgress(active)
            }
        }
        out.flush()
    }

    private fun queryUriMeta(context: Context, uri: Uri): Pair<String, Long> {
        var name = "shared_file"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    c.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) {
                    size = c.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun measureUriSize(context: Context, uri: Uri): Long {
        var total = 0L
        val buf = ByteArray(16384)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                total += n
            }
            return total
        }
        return -1L
    }

    private fun uniqueLegacyFile(dir: File, rawName: String): File {
        val safe = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
        var candidate = File(dir, safe)
        if (!candidate.exists()) return candidate
        val dot = safe.lastIndexOf('.')
        val base = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isBlank()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
