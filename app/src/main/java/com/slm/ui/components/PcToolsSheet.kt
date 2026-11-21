package com.slm.ui.components

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slm.ui.theme.ConnectedEmerald
import com.slm.ui.theme.ErrorCoral
import com.slm.wifi.PcSystemAction
import com.slm.wifi.WifiConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcToolsBottomSheet(
    wifiConnectionState: WifiConnectionState,
    lastSyncedClipboard: String?,
    statusMessage: String?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConnectWifiPc: () -> Unit,
    onSendPhoneClipboard: (String) -> Unit,
    onPasteOnPc: (String) -> Unit,
    onFetchPcClipboard: (onFetched: (String) -> Unit) -> Unit,
    onExecuteSystemAction: (PcSystemAction) -> Unit,
    onClearStatus: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isWifiConnected = wifiConnectionState is WifiConnectionState.Connected
    var pendingConfirmAction by remember { mutableStateOf<PcSystemAction?>(null) }

    fun readPhoneClipboard(): String {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        }
        return ""
    }

    fun writePhoneClipboard(text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("PC Clipboard", text))
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
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PC Tools & Clipboard",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (wifiConnectionState is WifiConnectionState.Connected) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ConnectedEmerald.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = wifiConnectionState.hostName,
                            style = MaterialTheme.typography.labelMedium,
                            color = ConnectedEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (!isWifiConnected) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Connect Wi-Fi PC to use Clipboard Sync & Power Controls.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = onConnectWifiPc,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onClearStatus,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            // 1. Clipboard Sync Card
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Clipboard Sync",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onSendPhoneClipboard(readPhoneClipboard()) },
                            enabled = isWifiConnected && !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("clip_send_to_pc_btn"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Phone → PC", maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = {
                                onFetchPcClipboard { pcText ->
                                    if (pcText.isNotEmpty()) {
                                        writePhoneClipboard(pcText)
                                    }
                                }
                            },
                            enabled = isWifiConnected && !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("clip_get_from_pc_btn"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PC → Phone", maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = { onPasteOnPc(readPhoneClipboard()) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("clip_paste_on_pc_btn"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste Phone Clipboard on Active Screen")
                    }

                    if (!lastSyncedClipboard.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = lastSyncedClipboard,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            // 2. Display & Screen Tools Card
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
                        text = "System & Display",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PcActionTile(
                            icon = Icons.Rounded.Lock,
                            label = "Lock",
                            enabled = isWifiConnected && !isBusy,
                            onClick = { onExecuteSystemAction(PcSystemAction.LOCK) },
                            modifier = Modifier.weight(1f).testTag("pc_action_lock"),
                        )
                        PcActionTile(
                            icon = Icons.Rounded.ScreenshotMonitor,
                            label = "Capture",
                            enabled = isWifiConnected && !isBusy,
                            onClick = { onExecuteSystemAction(PcSystemAction.SCREENSHOT) },
                            modifier = Modifier.weight(1f).testTag("pc_action_screenshot"),
                        )
                        PcActionTile(
                            icon = Icons.Rounded.BrightnessLow,
                            label = "Bright -",
                            enabled = isWifiConnected && !isBusy,
                            onClick = { onExecuteSystemAction(PcSystemAction.BRIGHTNESS_DOWN) },
                            modifier = Modifier.weight(1f).testTag("pc_action_bright_down"),
                        )
                        PcActionTile(
                            icon = Icons.Rounded.BrightnessHigh,
                            label = "Bright +",
                            enabled = isWifiConnected && !isBusy,
                            onClick = { onExecuteSystemAction(PcSystemAction.BRIGHTNESS_UP) },
                            modifier = Modifier.weight(1f).testTag("pc_action_bright_up"),
                        )
                    }
                }
            }

            // 3. Power Controls Card (Confirmed)
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
                        text = "PC Power Controls",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { pendingConfirmAction = PcSystemAction.SLEEP },
                            enabled = isWifiConnected && !isBusy,
                            modifier = Modifier.weight(1f).height(46.dp).testTag("pc_power_sleep"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sleep", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { pendingConfirmAction = PcSystemAction.RESTART },
                            enabled = isWifiConnected && !isBusy,
                            modifier = Modifier.weight(1f).height(46.dp).testTag("pc_power_restart"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restart", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { pendingConfirmAction = PcSystemAction.SHUTDOWN },
                            enabled = isWifiConnected && !isBusy,
                            modifier = Modifier.weight(1f).height(46.dp).testTag("pc_power_shutdown"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ErrorCoral.copy(alpha = 0.16f),
                                contentColor = ErrorCoral,
                            ),
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Shut", maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    pendingConfirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingConfirmAction = null },
            title = { Text("Confirm ${action.label}") },
            text = { Text("Do you want to ${action.label.lowercase()} now?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmAction = null
                        onExecuteSystemAction(action)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == PcSystemAction.SHUTDOWN) ErrorCoral else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(action.label)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PcActionTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
