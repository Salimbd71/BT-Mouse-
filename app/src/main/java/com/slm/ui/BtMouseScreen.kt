package com.slm.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LaptopWindows
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slm.R
import com.slm.bluetooth.BtConnectionState
import com.slm.bluetooth.HidKeyMap
import com.slm.bluetooth.HostDeviceType
import com.slm.data.ThemeMode
import com.slm.data.TransportMode
import com.slm.ui.components.DevicesBottomSheet
import com.slm.ui.components.FileTransferBottomSheet
import com.slm.ui.components.KeyboardDeckSection
import com.slm.ui.components.PcToolsBottomSheet
import com.slm.ui.components.PrecisionTouchpadSection
import com.slm.ui.components.QrScannerDialog
import com.slm.ui.components.SettingsBottomSheet
import com.slm.ui.components.TvRemoteDeckSection
import com.slm.ui.theme.ConnectedEmerald
import com.slm.ui.theme.ErrorCoral
import com.slm.ui.theme.PairingAmber
import com.slm.wifi.TransferDirection
import com.slm.wifi.WifiConnectionState

@Composable
fun BtMouseScreen(
    viewModel: BtMouseViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onPermissionsUpdated()
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onPermissionsUpdated()
    }

    val discoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode > 0) {
            viewModel.onDiscoverableLaunched(result.resultCode)
        } else {
            viewModel.onDiscoverableLaunched(300)
        }
    }

    fun launchPermissions() {
        permissionLauncher.launch(viewModel.hidController.requiredPermissions())
    }

    fun launchEnableBluetooth() {
        runCatching {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    fun launchDiscoverable() {
        if (!viewModel.hidController.hasRequiredPermissions()) {
            launchPermissions()
            return
        }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        runCatching {
            discoverableLauncher.launch(intent)
        }
    }

    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                viewModel.sendVoiceText(spoken)
            }
        }
    }

    fun launchVoiceTyping() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to type on PC or TV")
        }
        runCatching {
            voiceInputLauncher.launch(intent)
        }.onFailure {
            Toast.makeText(context, "Voice typing service unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    fun openBluetoothSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val isWideLayout = maxWidth >= 720.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isWideLayout) 1100.dp else 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. Top Status & Control Header
                val isTransmitting = if (uiState.settings.transportMode == TransportMode.WIFI) {
                    uiState.wifiTransmitting
                } else {
                    uiState.telemetry.isTransmitting
                }
                TopControlHeader(
                    transportMode = uiState.settings.transportMode,
                    connectionState = uiState.connectionState,
                    wifiConnectionState = uiState.wifiConnectionState,
                    pollingRateHz = uiState.settings.pollingRateHz,
                    isTransmitting = isTransmitting,
                    themeMode = uiState.settings.themeMode,
                    onStatusClick = { viewModel.setDevicesSheetVisible(true) },
                    onToggleTransport = {
                        val next = if (uiState.settings.transportMode == TransportMode.BLUETOOTH) {
                            TransportMode.WIFI
                        } else {
                            TransportMode.BLUETOOTH
                        }
                        viewModel.selectTransportMode(next)
                    },
                    onThemeCycle = viewModel::cycleThemeMode,
                    onOpenSettings = { viewModel.setSettingsSheetVisible(true) },
                )

                // 2. Contextual One-Tap Pairing / Permission Banner (when not connected)
                val isConnected = if (uiState.settings.transportMode == TransportMode.WIFI) {
                    uiState.wifiConnectionState is WifiConnectionState.Connected
                } else {
                    uiState.connectionState is BtConnectionState.Connected
                }
                AnimatedVisibility(
                    visible = !isConnected,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    QuickConnectionBanner(
                        transportMode = uiState.settings.transportMode,
                        connectionState = uiState.connectionState,
                        wifiConnectionState = uiState.wifiConnectionState,
                        pairedCount = uiState.pairedDevices.size,
                        wifiHostCount = uiState.wifiHosts.size,
                        hasDiagnostic = uiState.diagnostic != null,
                        onGrantPermission = ::launchPermissions,
                        onEnableBluetooth = ::launchEnableBluetooth,
                        onAutoPair = ::launchDiscoverable,
                        onScanWifi = viewModel::scanWifiHosts,
                        onScanQr = { viewModel.setQrScannerVisible(true) },
                        onOpenDevices = { viewModel.setDevicesSheetVisible(true) },
                    )
                }

                // 3. Quick Shortcuts & Media Action Strip
                QuickShortcutsStrip(
                    showLiveTypeBar = uiState.showLiveTypeBar,
                    incomingFileCount = uiState.fileTransferState.incomingOffers.size,
                    isFileTransferActive = uiState.fileTransferState.isBusy,
                    onToggleTypeBar = viewModel::toggleLiveTypeBar,
                    onOpenFiles = { viewModel.setFileTransferSheetVisible(true) },
                    onOpenPcTools = { viewModel.setPcToolsSheetVisible(true) },
                    onVoiceType = ::launchVoiceTyping,
                    onHome = { viewModel.sendConsumerKey(HidKeyMap.CONSUMER_AC_HOME) },
                    onBack = { viewModel.sendConsumerKey(HidKeyMap.CONSUMER_AC_BACK) },
                    onPlayPause = { viewModel.sendConsumerKey(HidKeyMap.CONSUMER_PLAY_PAUSE) },
                    onVolUp = { viewModel.sendConsumerKey(HidKeyMap.CONSUMER_VOLUME_UP) },
                    onVolDown = { viewModel.sendConsumerKey(HidKeyMap.CONSUMER_VOLUME_DOWN) },
                    onAltTab = { viewModel.sendCombo(HidKeyMap.MOD_LEFT_ALT, HidKeyMap.KEY_TAB) },
                    onWinDesktop = { viewModel.sendCombo(HidKeyMap.MOD_LEFT_GUI, HidKeyMap.KEY_D) },
                )

                val activeTransfer = uiState.fileTransferState.activeTransfer
                AnimatedVisibility(
                    visible = activeTransfer != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    if (activeTransfer != null) {
                        val progress = if (activeTransfer.totalBytes > 0L) {
                            (activeTransfer.bytesTransferred.toFloat() / activeTransfer.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val pct = (progress * 100f).toInt()
                        Surface(
                            onClick = { viewModel.setFileTransferSheetVisible(true) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (activeTransfer.direction == TransferDirection.TO_PC) {
                                            "Sending: ${activeTransfer.fileName} ($pct%)"
                                        } else {
                                            "Downloading: ${activeTransfer.fileName} ($pct%)"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                OutlinedButton(
                                    onClick = viewModel::cancelFileTransfer,
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("mini_banner_cancel_transfer_button"),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.7f)),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = ErrorCoral,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cancel", style = MaterialTheme.typography.labelMedium, color = ErrorCoral)
                                }
                            }
                        }
                    }
                }

                // 4. Inline Live Soft-Keyboard Input Bar (when toggled from Touchpad/TV)
                AnimatedVisibility(
                    visible = uiState.showLiveTypeBar && uiState.activeTab != ControllerTab.KEYBOARD,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    OutlinedTextField(
                        value = uiState.liveTypeBuffer,
                        onValueChange = viewModel::onLiveTypeBufferChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_live_type_field"),
                        placeholder = { Text(stringResource(R.string.hint_type_to_host)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                viewModel.sendHidKey(HidKeyMap.KEY_ENTER)
                                viewModel.clearLiveTypeBuffer()
                            },
                        ),
                        trailingIcon = {
                            Row {
                                if (uiState.liveTypeBuffer.isNotEmpty()) {
                                    IconButton(onClick = viewModel::clearLiveTypeBuffer) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                    }
                                }
                                IconButton(
                                    onClick = ::launchVoiceTyping,
                                    modifier = Modifier.testTag("quick_voice_type_button"),
                                ) {
                                    Icon(
                                        Icons.Rounded.Mic,
                                        contentDescription = "Voice Type",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.sendHidKey(HidKeyMap.KEY_ENTER)
                                        viewModel.clearLiveTypeBuffer()
                                    },
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.KeyboardReturn,
                                        contentDescription = "Send Enter",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        },
                    )
                }

                // 5. Main Ergonomic Controller Area (Adaptive Split on Tablets, Tabs on Phones)
                if (isWideLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PrecisionTouchpadSection(
                            hapticEnabled = uiState.settings.hapticFeedback,
                            buttonsPosition = uiState.settings.mouseButtonsPosition,
                            leftHandedMode = uiState.settings.leftHandedMode,
                            isLeftPressed = uiState.isLeftPressed,
                            isMiddlePressed = uiState.isMiddlePressed,
                            isRightPressed = uiState.isRightPressed,
                            onMove = viewModel::onTouchpadMove,
                            onTwoFingerScroll = viewModel::onTouchpadScroll,
                            onWheelStripScroll = viewModel::onWheelStripScroll,
                            onPinchZoom = viewModel::onPinchZoom,
                            onLeftClick = viewModel::onLeftClick,
                            onRightClick = viewModel::onRightClick,
                            onMiddleClick = viewModel::onMiddleClick,
                            onLeftHoldChanged = viewModel::setLeftButtonHeld,
                            onRightHoldChanged = viewModel::setRightButtonHeld,
                            onMiddleHoldChanged = viewModel::setMiddleButtonHeld,
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                        )

                        Column(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ControllerModeSwitcher(
                                activeTab = if (uiState.activeTab == ControllerTab.TV_REMOTE) {
                                    ControllerTab.TV_REMOTE
                                } else {
                                    ControllerTab.KEYBOARD
                                },
                                includeTouchpad = false,
                                onSelectTab = viewModel::selectTab,
                            )

                            if (uiState.activeTab == ControllerTab.TV_REMOTE) {
                                TvRemoteDeckSection(
                                    hapticEnabled = uiState.settings.hapticFeedback,
                                    onSendHidKey = { viewModel.sendHidKey(it) },
                                    onSendConsumer = viewModel::sendConsumerKey,
                                    onOpenKeyboard = { viewModel.selectTab(ControllerTab.KEYBOARD) },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                KeyboardDeckSection(
                                    hapticEnabled = uiState.settings.hapticFeedback,
                                    latchedModifiers = uiState.latchedModifiers,
                                    liveTypeBuffer = uiState.liveTypeBuffer,
                                    onLiveTypeChanged = viewModel::onLiveTypeBufferChanged,
                                    onClearBuffer = viewModel::clearLiveTypeBuffer,
                                    onToggleModifier = viewModel::toggleModifier,
                                    onSendHidKey = { viewModel.sendHidKey(it) },
                                    onSendCombo = viewModel::sendCombo,
                                    onSendCharacter = viewModel::sendCharacterKey,
                                    onStartVoiceTyping = ::launchVoiceTyping,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                } else {
                    ControllerModeSwitcher(
                        activeTab = uiState.activeTab,
                        includeTouchpad = true,
                        onSelectTab = viewModel::selectTab,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        when (uiState.activeTab) {
                            ControllerTab.TOUCHPAD -> {
                                PrecisionTouchpadSection(
                                    hapticEnabled = uiState.settings.hapticFeedback,
                                    buttonsPosition = uiState.settings.mouseButtonsPosition,
                                    leftHandedMode = uiState.settings.leftHandedMode,
                                    isLeftPressed = uiState.isLeftPressed,
                                    isMiddlePressed = uiState.isMiddlePressed,
                                    isRightPressed = uiState.isRightPressed,
                                    onMove = viewModel::onTouchpadMove,
                                    onTwoFingerScroll = viewModel::onTouchpadScroll,
                                    onWheelStripScroll = viewModel::onWheelStripScroll,
                                    onPinchZoom = viewModel::onPinchZoom,
                                    onLeftClick = viewModel::onLeftClick,
                                    onRightClick = viewModel::onRightClick,
                                    onMiddleClick = viewModel::onMiddleClick,
                                    onLeftHoldChanged = viewModel::setLeftButtonHeld,
                                    onRightHoldChanged = viewModel::setRightButtonHeld,
                                    onMiddleHoldChanged = viewModel::setMiddleButtonHeld,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            ControllerTab.KEYBOARD -> {
                                KeyboardDeckSection(
                                    hapticEnabled = uiState.settings.hapticFeedback,
                                    latchedModifiers = uiState.latchedModifiers,
                                    liveTypeBuffer = uiState.liveTypeBuffer,
                                    onLiveTypeChanged = viewModel::onLiveTypeBufferChanged,
                                    onClearBuffer = viewModel::clearLiveTypeBuffer,
                                    onToggleModifier = viewModel::toggleModifier,
                                    onSendHidKey = { viewModel.sendHidKey(it) },
                                    onSendCombo = viewModel::sendCombo,
                                    onSendCharacter = viewModel::sendCharacterKey,
                                    onStartVoiceTyping = ::launchVoiceTyping,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            ControllerTab.TV_REMOTE -> {
                                TvRemoteDeckSection(
                                    hapticEnabled = uiState.settings.hapticFeedback,
                                    onSendHidKey = { viewModel.sendHidKey(it) },
                                    onSendConsumer = viewModel::sendConsumerKey,
                                    onOpenKeyboard = { viewModel.selectTab(ControllerTab.KEYBOARD) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showDevicesSheet) {
        DevicesBottomSheet(
            transportMode = uiState.settings.transportMode,
            connectionState = uiState.connectionState,
            pairedDevices = uiState.pairedDevices,
            diagnostic = uiState.diagnostic,
            localBluetoothName = uiState.localBluetoothName,
            wifiConnectionState = uiState.wifiConnectionState,
            wifiHosts = uiState.wifiHosts,
            lastWifiIp = uiState.settings.lastWifiHostIp,
            autoConnect = uiState.settings.autoConnect,
            onSelectTransport = viewModel::selectTransportMode,
            onDismiss = { viewModel.setDevicesSheetVisible(false) },
            onDismissDiagnostic = viewModel::clearDiagnostic,
            onRequestPermissions = ::launchPermissions,
            onEnableBluetooth = ::launchEnableBluetooth,
            onMakeDiscoverable = ::launchDiscoverable,
            onOpenBluetoothSettings = ::openBluetoothSettings,
            onRefreshDevices = viewModel::refreshDevices,
            onConnectDevice = viewModel::connectToHost,
            onDisconnectDevice = viewModel::disconnectHost,
            onScanWifi = viewModel::scanWifiHosts,
            onOpenQrScanner = { viewModel.setQrScannerVisible(true) },
            onConnectWifi = viewModel::connectWifiIp,
            onDisconnectWifi = viewModel::disconnectWifi,
            onAutoConnectChanged = viewModel::setAutoConnect,
        )
    }

    if (uiState.showSettingsSheet) {
        SettingsBottomSheet(
            settings = uiState.settings,
            onDismiss = { viewModel.setSettingsSheetVisible(false) },
            onThemeModeChanged = viewModel::setThemeMode,
            onMouseButtonsPositionChanged = viewModel::setMouseButtonsPosition,
            onLeftHandedChanged = viewModel::setLeftHandedMode,
            onPointerSensitivityChanged = viewModel::setPointerSensitivity,
            onScrollSensitivityChanged = viewModel::setScrollSensitivity,
            onAccelerationChanged = viewModel::setPointerAcceleration,
            onNaturalScrollChanged = viewModel::setNaturalScroll,
            onPollingRateChanged = viewModel::setPollingRateHz,
            onHapticChanged = viewModel::setHapticFeedback,
        )
    }

    if (uiState.showFileTransferSheet) {
        FileTransferBottomSheet(
            wifiConnectionState = uiState.wifiConnectionState,
            transferState = uiState.fileTransferState,
            onDismiss = { viewModel.setFileTransferSheetVisible(false) },
            onConnectWifiPc = {
                viewModel.setFileTransferSheetVisible(false)
                viewModel.selectTransportMode(TransportMode.WIFI)
                viewModel.setDevicesSheetVisible(true)
            },
            onSendFilesToPc = viewModel::sendFilesToPc,
            onRefreshOffers = viewModel::refreshPcFileOffers,
            onDownloadOffer = viewModel::downloadPcFile,
            onDownloadAllOffers = viewModel::downloadAllPcFiles,
            onCancelTransfer = viewModel::cancelFileTransfer,
            onDismissError = viewModel::clearFileTransferError,
        )
    }

    if (uiState.showPcToolsSheet) {
        PcToolsBottomSheet(
            wifiConnectionState = uiState.wifiConnectionState,
            lastSyncedClipboard = uiState.lastSyncedClipboard,
            statusMessage = uiState.pcToolsStatus,
            isBusy = uiState.pcToolsBusy,
            onDismiss = { viewModel.setPcToolsSheetVisible(false) },
            onConnectWifiPc = {
                viewModel.setPcToolsSheetVisible(false)
                viewModel.selectTransportMode(TransportMode.WIFI)
                viewModel.setDevicesSheetVisible(true)
            },
            onSendPhoneClipboard = viewModel::sendPhoneClipboardToPc,
            onPasteOnPc = viewModel::pasteClipboardOnActiveHost,
            onFetchPcClipboard = viewModel::fetchPcClipboardToPhone,
            onExecuteSystemAction = viewModel::executePcSystemAction,
            onClearStatus = viewModel::clearPcToolsStatus,
        )
    }

    if (uiState.showQrScannerDialog) {
        QrScannerDialog(
            onDismiss = { viewModel.setQrScannerVisible(false) },
            onQrCodeScanned = viewModel::connectFromQrCode,
        )
    }
}

@Composable
private fun TopControlHeader(
    transportMode: TransportMode,
    connectionState: BtConnectionState,
    wifiConnectionState: WifiConnectionState,
    pollingRateHz: Int,
    isTransmitting: Boolean,
    themeMode: ThemeMode,
    onStatusClick: () -> Unit,
    onToggleTransport: () -> Unit,
    onThemeCycle: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val (dotColor, statusText, hostIcon) = if (transportMode == TransportMode.WIFI) {
            when (wifiConnectionState) {
                is WifiConnectionState.Connected -> Triple(
                    ConnectedEmerald,
                    "${wifiConnectionState.hostName} (${wifiConnectionState.latencyMs}ms)",
                    Icons.Rounded.Wifi,
                )
                is WifiConnectionState.Connecting -> Triple(
                    PairingAmber,
                    "Connecting ${wifiConnectionState.hostName}…",
                    Icons.Rounded.Wifi,
                )
                WifiConnectionState.Scanning -> Triple(
                    PairingAmber,
                    "Scanning Wi-Fi PCs…",
                    Icons.Rounded.Wifi,
                )
                is WifiConnectionState.Error -> Triple(
                    ErrorCoral,
                    "Wi-Fi Tap to Connect",
                    Icons.Rounded.Wifi,
                )
                WifiConnectionState.Idle -> Triple(
                    MaterialTheme.colorScheme.primary,
                    "Wi-Fi Ready • Tap to Connect",
                    Icons.Rounded.Wifi,
                )
            }
        } else {
            when (connectionState) {
                is BtConnectionState.Connected -> {
                    val icon = when (connectionState.deviceType) {
                        HostDeviceType.SMART_TV -> Icons.Rounded.Tv
                        HostDeviceType.WINDOWS_PC -> Icons.Rounded.LaptopWindows
                        HostDeviceType.OTHER -> Icons.Rounded.Bluetooth
                    }
                    Triple(ConnectedEmerald, connectionState.deviceName, icon)
                }
                is BtConnectionState.Connecting -> Triple(
                    PairingAmber,
                    "Connecting ${connectionState.deviceName}…",
                    Icons.AutoMirrored.Rounded.BluetoothSearching,
                )
                is BtConnectionState.Discoverable -> Triple(
                    PairingAmber,
                    "Pairable (${connectionState.secondsRemaining}s)",
                    Icons.AutoMirrored.Rounded.BluetoothSearching,
                )
                BtConnectionState.Ready -> Triple(
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.status_ready),
                    Icons.Rounded.Bluetooth,
                )
                BtConnectionState.PermissionRequired -> Triple(
                    ErrorCoral,
                    stringResource(R.string.status_permission_needed),
                    Icons.Rounded.Bluetooth,
                )
                BtConnectionState.BluetoothDisabled -> Triple(
                    ErrorCoral,
                    stringResource(R.string.status_bt_off),
                    Icons.Rounded.Bluetooth,
                )
                BtConnectionState.Unsupported -> Triple(
                    ErrorCoral,
                    stringResource(R.string.status_unsupported),
                    Icons.Rounded.Bluetooth,
                )
                BtConnectionState.Initializing -> Triple(
                    PairingAmber,
                    "Starting HID…",
                    Icons.Rounded.Bluetooth,
                )
            }
        }

        Surface(
            onClick = onStatusClick,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("connection_status_pill"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Icon(
                    imageVector = hostIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    val appTitle = stringResource(R.string.app_name)
                    Text(
                        text = if (transportMode == TransportMode.WIFI) "$appTitle • Wi-Fi" else "$appTitle • Bluetooth",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isTransmitting) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Text(
                        text = "${pollingRateHz}Hz",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTransmitting) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // 1-Tap BT / Wi-Fi Mode Switch
        Surface(
            onClick = onToggleTransport,
            modifier = Modifier
                .height(48.dp)
                .testTag("transport_toggle_button"),
            shape = RoundedCornerShape(14.dp),
            color = if (transportMode == TransportMode.WIFI) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
                1.dp,
                if (transportMode == TransportMode.WIFI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (transportMode == TransportMode.WIFI) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth,
                    contentDescription = "Switch BT or Wi-Fi mode",
                    tint = if (transportMode == TransportMode.WIFI) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (transportMode == TransportMode.WIFI) "Wi-Fi" else "BT",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (transportMode == TransportMode.WIFI) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        // Quick Dark Mode Toggle
        val themeIcon = when (themeMode) {
            ThemeMode.DARK -> Icons.Rounded.DarkMode
            ThemeMode.LIGHT -> Icons.Rounded.LightMode
            ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
        }
        Surface(
            onClick = onThemeCycle,
            modifier = Modifier
                .size(48.dp)
                .testTag("theme_toggle_button"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = themeIcon,
                    contentDescription = "Toggle Dark Mode",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Settings Sheet Trigger
        Surface(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .testTag("settings_button"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.action_settings),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickConnectionBanner(
    transportMode: TransportMode,
    connectionState: BtConnectionState,
    wifiConnectionState: WifiConnectionState,
    pairedCount: Int,
    wifiHostCount: Int,
    hasDiagnostic: Boolean,
    onGrantPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onAutoPair: () -> Unit,
    onScanWifi: () -> Unit,
    onScanQr: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    val isError = if (transportMode == TransportMode.WIFI) {
        wifiConnectionState is WifiConnectionState.Error
    } else {
        hasDiagnostic
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isError) {
            ErrorCoral.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        },
        border = BorderStroke(
            1.dp,
            if (isError) ErrorCoral.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (transportMode == TransportMode.WIFI) {
                Column(modifier = Modifier.weight(1f)) {
                    val (headline, sub) = when (wifiConnectionState) {
                        WifiConnectionState.Scanning ->
                            "Scanning Wi-Fi…" to "Looking for BTMouseReceiver.exe on same Wi-Fi"
                        is WifiConnectionState.Connecting ->
                            "Connecting to ${wifiConnectionState.hostName}" to wifiConnectionState.ipAddress
                        is WifiConnectionState.Error ->
                            "PC Not Reached" to "Scan QR or tap Connect"
                        else ->
                            "PC Wi-Fi Mode" to if (wifiHostCount > 0) "$wifiHostCount PC(s) found — tap Connect" else "Scan QR on BTMouseReceiver.exe"
                    }
                    Text(headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(
                    onClick = onScanQr,
                    modifier = Modifier.height(40.dp).testTag("banner_scan_qr_btn"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("QR")
                }
                FilledTonalButton(
                    onClick = onScanWifi,
                    modifier = Modifier.height(40.dp).testTag("banner_scan_wifi_btn"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Scan") }
                Button(
                    onClick = onOpenDevices,
                    modifier = Modifier.height(40.dp).testTag("banner_connect_wifi_btn"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Connect") }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    val (headline, sub) = when {
                        connectionState == BtConnectionState.PermissionRequired ->
                            "Bluetooth Permission" to "Tap Grant to connect TV & PC"
                        connectionState == BtConnectionState.BluetoothDisabled ->
                            "Bluetooth Disabled" to "Turn on Bluetooth to connect"
                        connectionState is BtConnectionState.Discoverable ->
                            "Discoverable Mode" to "Pair from your TV or Windows PC"
                        connectionState == BtConnectionState.Unsupported ->
                            "Android 9+ Required" to "HID profile requires Android 9 or newer"
                        hasDiagnostic ->
                            "Connection Declined" to "Tap Devices or switch to Wi-Fi"
                        else ->
                            "Quick Auto-Pair" to if (pairedCount > 0) "$pairedCount paired host(s) ready" else "Pair Smart TV or switch to Wi-Fi for PC"
                    }
                    Text(headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                when (connectionState) {
                    BtConnectionState.PermissionRequired -> {
                        Button(
                            onClick = onGrantPermission,
                            modifier = Modifier.height(40.dp).testTag("banner_grant_btn"),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Grant") }
                    }
                    BtConnectionState.BluetoothDisabled -> {
                        Button(
                            onClick = onEnableBluetooth,
                            modifier = Modifier.height(40.dp).testTag("banner_enable_bt_btn"),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Turn On") }
                    }
                    BtConnectionState.Unsupported -> {}
                    else -> {
                        if (pairedCount > 0) {
                            FilledTonalButton(
                                onClick = onOpenDevices,
                                modifier = Modifier.height(40.dp).testTag("banner_devices_btn"),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Devices") }
                        }
                        Button(
                            onClick = onAutoPair,
                            modifier = Modifier.height(40.dp).testTag("banner_auto_pair_btn"),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Auto-Pair") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickShortcutsStrip(
    showLiveTypeBar: Boolean,
    incomingFileCount: Int,
    isFileTransferActive: Boolean,
    onToggleTypeBar: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenPcTools: () -> Unit,
    onVoiceType: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onVolUp: () -> Unit,
    onVolDown: () -> Unit,
    onAltTab: () -> Unit,
    onWinDesktop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickChip(
            label = when {
                isFileTransferActive -> "Transferring…"
                incomingFileCount > 0 -> "Files ($incomingFileCount)"
                else -> "Files"
            },
            icon = Icons.Rounded.SwapVert,
            active = incomingFileCount > 0 || isFileTransferActive,
            onClick = onOpenFiles,
            testTag = "quick_chip_files",
        )
        QuickChip(
            label = "Tools",
            icon = Icons.Rounded.Build,
            onClick = onOpenPcTools,
            testTag = "quick_chip_pc_tools",
        )
        QuickChip(
            label = "Voice",
            icon = Icons.Rounded.Mic,
            onClick = onVoiceType,
            testTag = "quick_chip_voice",
        )
        QuickChip(
            label = "Type",
            icon = Icons.Rounded.Keyboard,
            active = showLiveTypeBar,
            onClick = onToggleTypeBar,
            testTag = "quick_chip_type",
        )
        QuickChip(label = "Home", icon = Icons.Rounded.Home, onClick = onHome, testTag = "quick_chip_home")
        QuickChip(label = "Back", icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack, testTag = "quick_chip_back")
        QuickChip(label = "Play", icon = Icons.Rounded.PlayArrow, onClick = onPlayPause, testTag = "quick_chip_play")
        QuickChip(label = "Vol+", icon = Icons.AutoMirrored.Rounded.VolumeUp, onClick = onVolUp, testTag = "quick_chip_vol_up")
        QuickChip(label = "Vol-", icon = Icons.AutoMirrored.Rounded.VolumeDown, onClick = onVolDown, testTag = "quick_chip_vol_down")
        QuickChip(label = "Alt+Tab", onClick = onAltTab, testTag = "quick_chip_alt_tab")
        QuickChip(label = "Win+D", onClick = onWinDesktop, testTag = "quick_chip_win_d")
    }
}

@Composable
private fun QuickChip(
    label: String,
    icon: ImageVector? = null,
    active: Boolean = false,
    onClick: () -> Unit,
    testTag: String,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .height(38.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(11.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ControllerModeSwitcher(
    activeTab: ControllerTab,
    includeTouchpad: Boolean,
    onSelectTab: (ControllerTab) -> Unit,
) {
    val tabs = if (includeTouchpad) {
        listOf(
            Triple(ControllerTab.TOUCHPAD, stringResource(R.string.tab_touchpad), Icons.Rounded.Mouse),
            Triple(ControllerTab.KEYBOARD, stringResource(R.string.tab_keyboard), Icons.Rounded.Keyboard),
            Triple(ControllerTab.TV_REMOTE, stringResource(R.string.tab_remote), Icons.Rounded.SettingsRemote),
        )
    } else {
        listOf(
            Triple(ControllerTab.KEYBOARD, stringResource(R.string.tab_keyboard), Icons.Rounded.Keyboard),
            Triple(ControllerTab.TV_REMOTE, stringResource(R.string.tab_remote), Icons.Rounded.SettingsRemote),
        )
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
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { (tab, title, icon) ->
                val selected = activeTab == tab
                Surface(
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("tab_${tab.name.lowercase()}"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
