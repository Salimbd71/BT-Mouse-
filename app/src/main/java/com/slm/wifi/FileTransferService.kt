package com.slm.wifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.slm.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class FileTransferService : Service() {

    private sealed interface TransferTask {
        data class Upload(val hostIp: String, val uris: List<Uri>) : TransferTask
        data class Download(val hostIp: String, val offers: List<PcFileOffer>) : TransferTask
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskQueue = Channel<TransferTask>(Channel.UNLIMITED)
    private var workerJob: Job? = null
    private var lastNotificationUpdateMs = 0L

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                FileTransferRepository.requestCancel()
                while (taskQueue.tryReceive().isSuccess) {
                    // Drain queued items
                }
                workerJob?.cancel()
                workerJob = null
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }

            ACTION_UPLOAD -> {
                val hostIp = intent.getStringExtra(EXTRA_HOST_IP).orEmpty()
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_URIS, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(EXTRA_URIS)
                } ?: emptyList()

                if (hostIp.isNotBlank() && uris.isNotEmpty()) {
                    FileTransferRepository.resetCancelFlag()
                    promoteToForeground("Preparing upload…", "${uris.size} file(s) queued", 0)
                    taskQueue.trySend(TransferTask.Upload(hostIp, uris))
                    ensureWorkerRunning()
                }
            }

            ACTION_DOWNLOAD -> {
                val hostIp = intent.getStringExtra(EXTRA_HOST_IP).orEmpty()
                val ids = intent.getStringArrayListExtra(EXTRA_OFFER_IDS) ?: arrayListOf()
                val names = intent.getStringArrayListExtra(EXTRA_OFFER_NAMES) ?: arrayListOf()
                val sizes = intent.getLongArrayExtra(EXTRA_OFFER_SIZES) ?: LongArray(0)

                val offers = ids.indices.mapNotNull { i ->
                    val id = ids.getOrNull(i) ?: return@mapNotNull null
                    val name = names.getOrNull(i) ?: "file"
                    val size = sizes.getOrNull(i) ?: 0L
                    PcFileOffer(id = id, name = name, sizeBytes = size)
                }

                if (hostIp.isNotBlank() && offers.isNotEmpty()) {
                    FileTransferRepository.resetCancelFlag()
                    promoteToForeground("Preparing download…", "${offers.size} file(s) queued", 0)
                    taskQueue.trySend(TransferTask.Download(hostIp, offers))
                    ensureWorkerRunning()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureWorkerRunning() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch {
            while (true) {
                val next = taskQueue.tryReceive().getOrNull() ?: break
                when (next) {
                    is TransferTask.Upload -> {
                        FileTransferRepository.uploadUrisToPc(
                            context = applicationContext,
                            hostIp = next.hostIp,
                            uris = next.uris,
                            onProgress = ::updateProgressNotificationThrottled,
                        )
                    }
                    is TransferTask.Download -> {
                        FileTransferRepository.downloadOffersFromPc(
                            context = applicationContext,
                            hostIp = next.hostIp,
                            offers = next.offers,
                            onProgress = ::updateProgressNotificationThrottled,
                        )
                    }
                }
            }
            stopForegroundAndSelf()
        }
    }

    private fun promoteToForeground(title: String, text: String, progressPct: Int) {
        val notification = buildNotification(title, text, progressPct)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateProgressNotificationThrottled(active: ActiveFileTransfer) {
        val now = System.currentTimeMillis()
        val isFinished = active.totalBytes in 1..active.bytesTransferred
        if (!isFinished && now - lastNotificationUpdateMs < 350L) return
        lastNotificationUpdateMs = now

        val pct = if (active.totalBytes > 0L) {
            ((active.bytesTransferred * 100L) / active.totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val title = if (active.direction == TransferDirection.TO_PC) {
            "Sending to PC ($pct%)"
        } else {
            "Downloading from PC ($pct%)"
        }
        val sizeText = "${WifiFileProtocol.formatBytes(active.bytesTransferred)} / ${WifiFileProtocol.formatBytes(active.totalBytes)}"
        val mgr = getSystemService(NotificationManager::class.java)
        mgr?.notify(NOTIF_ID, buildNotification(title, "${active.fileName} • $sizeText", pct))
    }

    private fun buildNotification(title: String, content: String, progressPct: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, FileTransferService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .setProgress(100, progressPct.coerceIn(0, 100), progressPct == 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            val existing = mgr.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Wi-Fi File Transfers",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows progress and Cancel control when transferring files in background"
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "bt_mouse_file_transfer"
        private const val NOTIF_ID = 51922

        private const val ACTION_UPLOAD = "com.slm.wifi.action.UPLOAD"
        private const val ACTION_DOWNLOAD = "com.slm.wifi.action.DOWNLOAD"
        private const val ACTION_CANCEL = "com.slm.wifi.action.CANCEL"

        private const val EXTRA_HOST_IP = "extra_host_ip"
        private const val EXTRA_URIS = "extra_uris"
        private const val EXTRA_OFFER_IDS = "extra_offer_ids"
        private const val EXTRA_OFFER_NAMES = "extra_offer_names"
        private const val EXTRA_OFFER_SIZES = "extra_offer_sizes"

        fun startUpload(context: Context, hostIp: String, uris: List<Uri>) {
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = ACTION_UPLOAD
                putExtra(EXTRA_HOST_IP, hostIp)
                putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startDownload(context: Context, hostIp: String, offers: List<PcFileOffer>) {
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_HOST_IP, hostIp)
                putStringArrayListExtra(EXTRA_OFFER_IDS, ArrayList(offers.map { it.id }))
                putStringArrayListExtra(EXTRA_OFFER_NAMES, ArrayList(offers.map { it.name }))
                putExtra(EXTRA_OFFER_SIZES, offers.map { it.sizeBytes }.toLongArray())
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelTransfers(context: Context) {
            FileTransferRepository.requestCancel()
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = ACTION_CANCEL
            }
            runCatching { context.startService(intent) }
        }
    }
}
