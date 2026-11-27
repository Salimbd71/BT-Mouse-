package com.slm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LaptopWindows
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slm.bluetooth.BtConnectionState
import com.slm.bluetooth.ConnectionDiagnostic
import com.slm.bluetooth.HostDeviceType
import com.slm.bluetooth.NearbyBtDevice
import com.slm.bluetooth.PairedHostDevice
import com.slm.data.TransportMode
import com.slm.ui.theme.ConnectedEmerald
import com.slm.ui.theme.ErrorCoral
import com.slm.ui.theme.PairingAmber
import com.slm.wifi.WifiConnectionState
import com.slm.wifi.WifiHostDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesBottomSheet(
    transportMode: TransportMode,
    connectionState: BtConnectionState,
    pairedDevices: List<PairedHostDevice>,
    nearbyBtDevices: List<NearbyBtDevice>,
    isBtScanning: Boolean,
    diagnostic: ConnectionDiagnostic?,
    localBluetoothName: String?,
    wifiConnectionState: WifiConnectionState,
    wifiHosts: List<WifiHostDevice>,
    lastWifiIp: String?,
    autoConnect: Boolean,
    onSelectTransport: (TransportMode) -> Unit,
    onDismiss: () -> Unit,
    onDismissDiagnostic: () -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefreshDevices: () -> Unit,
    onScanNearbyBt: () -> Unit,
    onStopBtScan: () -> Unit,
    onPairNearbyBt: (String) -> Unit,
    onConnectDevice: (String) -> Unit,
    onDisconnectDevice: () -> Unit,
    onScanWifi: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onConnectWifi: (String, String?) -> Unit,
    onDisconnectWifi: () -> Unit,
    onAutoConnectChanged: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val connectingAddress = (connectionState as? BtConnectionState.Connecting)?.address
    var manualIp by remember(lastWifiIp) { mutableStateOf(lastWifiIp ?: "") }

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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (transportMode == TransportMode.WIFI) "PC Wi-Fi Connection" else "Bluetooth Auto-Pairing",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = if (transportMode == TransportMode.WIFI) {
                            "Requires BTMouseReceiver.exe on Windows PC"
                        } else if (!localBluetoothName.isNullOrBlank()) {
                            "Phone BT name: $localBluetoothName"
                        } else {
                            "Pair with Smart TVs & Windows Laptops"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (transportMode == TransportMode.BLUETOOTH) {
                    IconButton(
                        onClick = onOpenBluetoothSettings,
                        modifier = Modifier.testTag("open_bt_settings_button"),
                    ) {
                        Icon(Icons.Rounded.SettingsBluetooth, contentDescription = "Open Bluetooth Settings")
                    }
                }
                IconButton(
                    onClick = if (transportMode == TransportMode.WIFI) onScanWifi else onRefreshDevices,
                    modifier = Modifier.testTag("refresh_devices_button"),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh devices")
                }
            }

            // Transport Mode Switcher inside sheet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = transportMode == TransportMode.WIFI,
                    onClick = { onSelectTransport(TransportMode.WIFI) },
                    label = { Text("Wi-Fi (Windows PC)") },
                    leadingIcon = { Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f).testTag("sheet_mode_wifi"),
                )
                FilterChip(
                    selected = transportMode == TransportMode.BLUETOOTH,
                    onClick = { onSelectTransport(TransportMode.BLUETOOTH) },
                    label = { Text("Bluetooth (TV / PC)") },
                    leadingIcon = { Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f).testTag("sheet_mode_bt"),
                )
            }

            if (transportMode == TransportMode.WIFI) {
                // ==================== WI-FI PC MODE ====================
                if (wifiConnectionState is WifiConnectionState.Error) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ErrorCoral.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.55f)),
                    ) {
                        Text(
                            text = wifiConnectionState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                // Manual IP quick connect + Scan button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("wifi_manual_ip_field"),
                        label = { Text("Laptop IP (e.g. 192.168.1.105)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { if (manualIp.isNotBlank()) onConnectWifi(manualIp, null) },
                        ),
                    )
                    Button(
                        onClick = { if (manualIp.isNotBlank()) onConnectWifi(manualIp, null) },
                        enabled = manualIp.isNotBlank(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("wifi_connect_ip_btn"),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Connect")
                    }
                }

                // Auto-Reconnect Toggle
                AutoReconnectCard(autoConnect = autoConnect, onAutoConnectChanged = onAutoConnectChanged)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (wifiConnectionState is WifiConnectionState.Scanning) {
                            "SCANNING SAME WI-FI…"
                        } else {
                            "DISCOVERED PCS (${wifiHosts.size})"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenQrScanner,
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("wifi_scan_qr_btn"),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Scan QR")
                        }
                        OutlinedButton(
                            onClick = onScanWifi,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Scan PCs")
                        }
                    }
                }

                if (wifiHosts.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LaptopWindows,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text("No Windows PC found yet", style = MaterialTheme.typography.titleSmall)
                            Button(
                                onClick = onOpenQrScanner,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Scan PC QR Code")
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        wifiHosts.forEach { host ->
                            val isConnecting = (wifiConnectionState as? WifiConnectionState.Connecting)?.ipAddress == host.ipAddress
                            WifiHostRow(
                                host = host,
                                isConnecting = isConnecting,
                                onConnect = { onConnectWifi(host.ipAddress, host.name) },
                                onDisconnect = onDisconnectWifi,
                            )
                        }
                    }
                }

                ReceiverDownloadCard()
            } else {
                // ==================== BLUETOOTH HID MODE (PRESERVED) ====================
                if (diagnostic != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ErrorCoral.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.55f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorCoral,
                                )
                                Text(
                                    text = "Could not connect to ${diagnostic.deviceName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ErrorCoral,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = onDismissDiagnostic,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss diagnostic",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                text = diagnostic.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (diagnostic.isAlreadyPairedSdpHint) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = onOpenBluetoothSettings,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("diagnostic_bt_settings_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                    ) {
                                        Text("Unpair in BT Settings", maxLines = 1)
                                    }
                                    Button(
                                        onClick = onMakeDiscoverable,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("diagnostic_discoverable_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                    ) {
                                        Text("Re-Pair (Discoverable)", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

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
                                        imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
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
                                        val deviceHint = localBluetoothName ?: "this phone"
                                        Text(
                                            text = "On Windows (Add device) or TV, select '$deviceHint'. For PC, Wi-Fi mode above is also available!",
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

                AutoReconnectCard(autoConnect = autoConnect, onAutoConnectChanged = onAutoConnectChanged)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isBtScanning) {
                            "SCANNING NEARBY TV / LAPTOP…"
                        } else {
                            "NEARBY DEVICES (${nearbyBtDevices.size})"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = if (isBtScanning) onStopBtScan else onScanNearbyBt,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("bt_scan_nearby_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (isBtScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Stop Scan")
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Scan Nearby")
                        }
                    }
                }

                if (nearbyBtDevices.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        nearbyBtDevices.forEach { device ->
                            NearbyDeviceRow(
                                device = device,
                                onPair = { onPairNearbyBt(device.address) },
                            )
                        }
                    }
                } else if (isBtScanning) {
                    Text(
                        text = "Searching… For Windows laptops, keep Settings → Bluetooth & devices open on your laptop.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                            Text("No paired Smart TV or PC found yet", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Tap 'Scan Nearby' to find your TV/laptop, or tap 'Make Discoverable & Auto-Pair' and pair from your TV/Windows Bluetooth menu.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pairedDevices.forEach { device ->
                            PairedDeviceRow(
                                device = device,
                                isConnecting = connectingAddress == device.address,
                                onConnect = { onConnectDevice(device.address) },
                                onDisconnect = onDisconnectDevice,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoReconnectCard(
    autoConnect: Boolean,
    onAutoConnectChanged: (Boolean) -> Unit,
) {
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
}

@Composable
private fun WifiHostRow(
    host: WifiHostDevice,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (host.isConnected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (host.isConnected) ConnectedEmerald else MaterialTheme.colorScheme.outlineVariant,
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
                        imageVector = Icons.Rounded.LaptopWindows,
                        contentDescription = "Windows PC",
                        tint = if (host.isConnected) ConnectedEmerald else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(host.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${host.ipAddress}:${host.port}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (host.isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ConnectedEmerald, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Disconnect")
                }
            } else if (isConnecting) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connecting")
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
private fun NearbyDeviceRow(
    device: NearbyBtDevice,
    onPair: () -> Unit,
) {
    val icon = when (device.deviceType) {
        HostDeviceType.SMART_TV -> Icons.Rounded.Tv
        HostDeviceType.WINDOWS_PC -> Icons.Rounded.LaptopWindows
        HostDeviceType.OTHER -> Icons.Rounded.DevicesOther
    }
    val typeLabel = when (device.deviceType) {
        HostDeviceType.SMART_TV -> "Smart TV"
        HostDeviceType.WINDOWS_PC -> "Windows / PC"
        HostDeviceType.OTHER -> "Bluetooth Device"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        imageVector = icon,
                        contentDescription = typeLabel,
                        tint = MaterialTheme.colorScheme.primary,
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

            if (device.isBonding) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pairing")
                }
            } else {
                Button(
                    onClick = onPair,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Pair & Connect")
                }
            }
        }
    }
}

@Composable
private fun PairedDeviceRow(
    device: PairedHostDevice,
    isConnecting: Boolean,
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
            } else if (isConnecting) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connecting")
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
