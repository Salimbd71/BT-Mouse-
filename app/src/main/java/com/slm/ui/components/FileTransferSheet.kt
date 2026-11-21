package com.slm.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LaptopWindows
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slm.ui.theme.ConnectedEmerald
import com.slm.ui.theme.ErrorCoral
import com.slm.wifi.FileTransferUiState
import com.slm.wifi.PcFileOffer
import com.slm.wifi.TransferDirection
import com.slm.wifi.WifiConnectionState
import com.slm.wifi.WifiFileProtocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferBottomSheet(
    wifiConnectionState: WifiConnectionState,
    transferState: FileTransferUiState,
    onDismiss: () -> Unit,
    onConnectWifiPc: () -> Unit,
    onSendFilesToPc: (List<android.net.Uri>) -> Unit,
    onRefreshOffers: () -> Unit,
    onDownloadOffer: (PcFileOffer) -> Unit,
    onDownloadAllOffers: () -> Unit,
    onCancelTransfer: () -> Unit,
    onDismissError: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val isWifiConnected = wifiConnectionState is WifiConnectionState.Connected

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onSendFilesToPc(uris)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wi-Fi File Transfer",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = if (wifiConnectionState is WifiConnectionState.Connected) {
                            "Connected to ${wifiConnectionState.hostName} (${wifiConnectionState.ipAddress})"
                        } else {
                            "Send & receive any file over same Wi-Fi"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isWifiConnected) {
                    IconButton(
                        onClick = onRefreshOffers,
                        enabled = !transferState.isRefreshingOffers,
                        modifier = Modifier.testTag("files_refresh_offers_button"),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh incoming files")
                    }
                }
            }

            if (!isWifiConnected) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Connect to your Windows PC over Wi-Fi",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = "High-speed file transfer requires Wi-Fi connection to BTMouseReceiver.exe on your laptop/PC (Bluetooth HID stays active for mouse/TV).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onConnectWifiPc,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("files_connect_wifi_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.LaptopWindows, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Switch to Wi-Fi & Connect PC")
                        }
                    }
                }
                ReceiverDownloadCard()
                return@ModalBottomSheet
            }

            if (transferState.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ErrorCoral.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.55f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = transferState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismissError, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss error", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Active transfer progress
            val active = transferState.activeTransfer
            if (active != null) {
                val progress = if (active.totalBytes > 0L) {
                    (active.bytesTransferred.toFloat() / active.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val pct = (progress * 100f).toInt()
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (active.direction == TransferDirection.TO_PC) {
                                    "Sending to PC: ${active.fileName}"
                                } else {
                                    "Downloading: ${active.fileName}"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${WifiFileProtocol.formatBytes(active.bytesTransferred)} / ${WifiFileProtocol.formatBytes(active.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Continues in background if app is closed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = onCancelTransfer,
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("cancel_file_transfer_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.7f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = ErrorCoral,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel", color = ErrorCoral)
                            }
                        }
                    }
                }
            }

            // 1. Mobile -> PC Section
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Phone → PC",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Pick photos, videos, APKs, PDFs, ZIPs, or any file. Saved to PC Downloads\\BT mouse Files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { multiFilePicker.launch(arrayOf("*/*")) },
                        enabled = !transferState.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("send_files_to_pc_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send Any File(s) to PC")
                    }
                }
            }

            // 2. PC -> Mobile Section
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "PC → Phone (${transferState.incomingOffers.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (transferState.incomingOffers.size > 1) {
                            OutlinedButton(
                                onClick = onDownloadAllOffers,
                                enabled = !transferState.isBusy,
                                modifier = Modifier.height(34.dp).testTag("download_all_offers_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Download All")
                            }
                        }
                    }

                    if (transferState.incomingOffers.isEmpty()) {
                        Text(
                            text = "Click 'Send File to Phone' or drag & drop files onto BTMouseReceiver on your PC, then tap Download.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            transferState.incomingOffers.forEach { offer ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.InsertDriveFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = offer.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = WifiFileProtocol.formatBytes(offer.sizeBytes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { onDownloadOffer(offer) },
                                            enabled = !transferState.isBusy,
                                            modifier = Modifier.height(40.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Save")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Recent Completed Transfers
            if (transferState.completedTransfers.isNotEmpty()) {
                Text(
                    text = "RECENT TRANSFERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    transferState.completedTransfers.take(6).forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = if (item.direction == TransferDirection.TO_PC) {
                                        Icons.Rounded.FileUpload
                                    } else {
                                        Icons.Rounded.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = ConnectedEmerald,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${WifiFileProtocol.formatBytes(item.sizeBytes)} • ${item.locationHint}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (item.savedUri != null) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(item.savedUri, item.mimeType ?: "*/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
