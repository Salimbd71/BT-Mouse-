package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.LaptopWindows
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bluetooth.BtConnectionState
import com.example.bluetooth.HostDeviceType
import com.example.bluetooth.PairedHostDevice
import com.example.ui.theme.ConnectedEmerald
import com.example.ui.theme.PairingAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesBottomSheet(
    connectionState: BtConnectionState,
    pairedDevices: List<PairedHostDevice>,
    autoConnect: Boolean,
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onRefreshDevices: () -> Unit,
    onConnectDevice: (String) -> Unit,
    onDisconnectDevice: () -> Unit,
    onAutoConnectChanged: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Devices & Auto-Pairing",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Pair with Smart TVs & Windows Laptops",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRefreshDevices,
                    modifier = Modifier.testTag("refresh_devices_button"),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh paired devices")
                }
            }

            // Action Banner based on current Bluetooth / Permission state
            when (connectionState) {
                BtConnectionState.PermissionRequired -> {
                    StatusActionCard(
                        title = "Bluetooth Permission Needed",
                        subtitle = "Allow nearby device access so BT mouse can connect to your TV or Windows PC.",
                        buttonText = "Grant Permission",
                        onClick = onRequestPermissions,
                        testTag = "sheet_grant_permission_button",
                    )
                }
                BtConnectionState.BluetoothDisabled -> {
                    StatusActionCard(
                        title = "Bluetooth is Turned Off",
                        subtitle = "Enable Bluetooth to auto-connect or pair with a new host.",
                        buttonText = "Turn On Bluetooth",
                        onClick = onEnableBluetooth,
                        testTag = "sheet_enable_bt_button",
                    )
                }
                BtConnectionState.Unsupported -> {
                    StatusActionCard(
                        title = "Android 9+ Required for HID",
                        subtitle = "Bluetooth HID Device profile requires Android 9 (API 28) or higher.",
                        buttonText = "Close",
                        onClick = onDismiss,
                        testTag = "sheet_unsupported_button",
                    )
                }
                else -> {
                    // Auto-Pair Discoverable Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BluetoothSearching,
                                    contentDescription = null,
                                    tint = if (connectionState is BtConnectionState.Discoverable) {
                                        PairingAmber
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (connectionState is BtConnectionState.Discoverable) {
                                            "Discoverable (${connectionState.secondsRemaining}s)"
                                        } else {
                                            "Auto-Pair New TV or Laptop"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Open Bluetooth on your Windows PC or Smart TV and select this phone to auto-connect.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Button(
                                onClick = onMakeDiscoverable,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("auto_pair_discoverable_button"),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(
                                    text = if (connectionState is BtConnectionState.Discoverable) {
                                        "Extend Discoverable Window"
                                    } else {
                                        "Make Discoverable & Auto-Pair"
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Auto-Reconnect Toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Reconnect Last Host", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Connect automatically when BT mouse opens",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = onAutoConnectChanged,
                        modifier = Modifier.testTag("auto_connect_switch"),
                    )
                }
            }

            Text(
                text = "PAIRED DEVICES (${pairedDevices.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (pairedDevices.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = "No paired Smart TV or PC found yet",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Tap 'Make Discoverable & Auto-Pair' above, then pair from your TV or Windows Bluetooth settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pairedDevices, key = { it.address }) { device ->
                        PairedDeviceRow(
                            device = device,
                            onConnect = { onConnectDevice(device.address) },
                            onDisconnect = onDisconnectDevice,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairedDeviceRow(
    device: PairedHostDevice,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val icon = when (device.deviceType) {
        HostDeviceType.SMART_TV -> Icons.Rounded.Tv
        HostDeviceType.WINDOWS_PC -> Icons.Rounded.LaptopWindows
        HostDeviceType.OTHER -> Icons.Rounded.DevicesOther
    }
    val typeLabel = when (device.deviceType) {
        HostDeviceType.SMART_TV -> "Smart TV"
        HostDeviceType.WINDOWS_PC -> "Windows / PC"
        HostDeviceType.OTHER -> "Bluetooth Host"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (device.isConnected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (device.isConnected) ConnectedEmerald else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (device.isConnected) Icons.Rounded.BluetoothConnected else icon,
                        contentDescription = typeLabel,
                        tint = if (device.isConnected) ConnectedEmerald else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$typeLabel • ${device.address}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (device.isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = ConnectedEmerald,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun StatusActionCard(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(testTag),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(buttonText)
            }
        }
    }
}
