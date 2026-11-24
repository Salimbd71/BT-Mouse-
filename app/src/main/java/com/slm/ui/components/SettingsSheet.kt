package com.slm.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slm.BuildConfig
import com.slm.data.AppSettings
import com.slm.data.MouseButtonsPosition
import com.slm.data.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMouseButtonsPositionChanged: (MouseButtonsPosition) -> Unit,
    onLeftHandedChanged: (Boolean) -> Unit,
    onPointerSensitivityChanged: (Float) -> Unit,
    onScrollSensitivityChanged: (Float) -> Unit,
    onAccelerationChanged: (Boolean) -> Unit,
    onNaturalScrollChanged: (Boolean) -> Unit,
    onPollingRateChanged: (Int) -> Unit,
    onHapticChanged: (Boolean) -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Touchpad & Protocol Settings",
                style = MaterialTheme.typography.headlineSmall,
            )

            // Appearance / Dark Mode Selector
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Appearance (Dark Mode)", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.LIGHT -> "Light"
                            }
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeModeChanged(mode) },
                                label = { Text(label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_${mode.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            // Mouse Buttons Position Selector (Above / Below Touchpad)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Mouse Buttons Position", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = settings.mouseButtonsPosition == MouseButtonsPosition.TOP,
                            onClick = { onMouseButtonsPositionChanged(MouseButtonsPosition.TOP) },
                            label = { Text("Above Touchpad") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mouse_buttons_pos_top"),
                        )
                        FilterChip(
                            selected = settings.mouseButtonsPosition == MouseButtonsPosition.BOTTOM,
                            onClick = { onMouseButtonsPositionChanged(MouseButtonsPosition.BOTTOM) },
                            label = { Text("Below Touchpad") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mouse_buttons_pos_bottom"),
                        )
                    }
                }
            }

            // Optimized Data Transmission Protocol Rate
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("HID Packet Polling Rate", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(60, 125, 250).forEach { hz ->
                            FilterChip(
                                selected = settings.pollingRateHz == hz,
                                onClick = { onPollingRateChanged(hz) },
                                label = { Text("$hz Hz") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("polling_${hz}hz"),
                            )
                        }
                    }
                }
            }

            // Pointer & Scroll Sliders
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
                    ) {
                        Text("Pointer Sensitivity", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${(settings.pointerSensitivity * 10).roundToInt() / 10f}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = settings.pointerSensitivity,
                        onValueChange = onPointerSensitivityChanged,
                        valueRange = 0.5f..3.0f,
                        modifier = Modifier.testTag("pointer_sensitivity_slider"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Scroll Speed", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${(settings.scrollSensitivity * 10).roundToInt() / 10f}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = settings.scrollSensitivity,
                        onValueChange = onScrollSensitivityChanged,
                        valueRange = 0.5f..2.5f,
                        modifier = Modifier.testTag("scroll_sensitivity_slider"),
                    )
                }
            }

            // Toggles
            SettingsSwitchRow(
                title = "Left-Handed Mode",
                checked = settings.leftHandedMode,
                onCheckedChange = onLeftHandedChanged,
                testTag = "left_handed_switch",
            )
            SettingsSwitchRow(
                title = "Pointer Acceleration",
                checked = settings.pointerAcceleration,
                onCheckedChange = onAccelerationChanged,
                testTag = "acceleration_switch",
            )
            SettingsSwitchRow(
                title = "Natural Scrolling",
                checked = settings.naturalScroll,
                onCheckedChange = onNaturalScrollChanged,
                testTag = "natural_scroll_switch",
            )
            SettingsSwitchRow(
                title = "Haptic Feedback",
                checked = settings.hapticFeedback,
                onCheckedChange = onHapticChanged,
                testTag = "haptic_switch",
            )

            LegalPoliciesCard()

            AboutDeveloperCard()
        }
    }
}

@Composable
private fun LegalPoliciesCard() {
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = { showPrivacyDialog = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().testTag("settings_privacy_policy_card"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Privacy Policy", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Data protection & permission usage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Surface(
            onClick = { showTermsDialog = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().testTag("settings_terms_card"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Terms & Conditions", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Usage guidelines & compliance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPrivacyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "BT/WiFi Mouse With File Transfer ('App') respects your privacy and is committed to protecting your personal data in compliance with Google Play Developer Policies.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "1. Local Network & Bluetooth Connectivity:\nAll wireless mouse cursor movements, keyboard keystrokes, remote control commands, and file transfers operate strictly over your local Wi-Fi network (UDP/TCP) or direct Bluetooth HID profile between your Android device and Windows PC / Smart TV. No data is sent or stored on external cloud servers.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "2. Required Permissions & Clarification:\n• Bluetooth / Nearby Devices (BLUETOOTH_CONNECT, BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE): Used exclusively to pair and communicate with Windows PCs and Android Smart TVs as a standard Bluetooth HID mouse/keyboard peripheral.\n• Microphone (RECORD_AUDIO): Used strictly on-device for the optional Voice-to-Text typing feature (RecognizerIntent) to convert spoken speech into text sent to your PC. Audio is never recorded, saved, or uploaded.\n• Notifications (POST_NOTIFICATIONS): Required on Android 13+ to display ongoing foreground service notification during Wi-Fi file transfer so you can monitor progress and cancel transfers.\n• Storage / Files: Used exclusively to save files received from your Windows PC into your local Downloads/BT mouse Files folder.\n• Network & Internet (INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE): Used for local Wi-Fi PC mouse/keyboard control, local file transfers, and loading Google AdMob ads.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "3. Zero Keystroke/File Collection & Advertising (Google AdMob):\nWe do not collect, log, sell, or upload your mouse movements, keystrokes, clipboard contents, or transferred files to any server. To keep the app free, this app uses Google AdMob (Banner & Interstitial ads). Google AdMob may process your device's Advertising ID (AD_ID), IP address, and ad interaction diagnostics in accordance with Google's Privacy Policy to serve and measure advertisements.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showTermsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms & Conditions") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "By downloading, installing, or using BT/WiFi Mouse With File Transfer, you agree to be bound by these Terms & Conditions.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "1. Acceptance of Terms:\nYou agree to use this application only for lawful purposes related to controlling your personal computers and smart TVs, and transferring files within your local network.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "2. Intellectual Property & Licensing:\nThis software is governed by the terms of the accompanying LICENSE file included in the project distribution. Unauthorized redistribution of modified binaries for malicious purposes is prohibited.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "3. Limitation of Liability:\nThe application is provided 'as is' without warranties of any kind, either express or implied. The developer shall not be held liable for any incidental or consequential damages resulting from the use or inability to use the app.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showTermsDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun AboutDeveloperCard() {
    val context = LocalContext.current
    val phoneNumber = "01737462871"
    val whatsappUri = "https://wa.me/8801737462871"
    val facebookUri = "https://fb.com/salim.naogaon"

    val shareLatestLinks = {
        val shareText = buildString {
            appendLine("BT/WiFi Mouse With File Transfer (v${BuildConfig.VERSION_NAME})")
            appendLine("Wireless PC touchpad, keyboard, Smart TV remote & background Wi-Fi file transfer.")
            appendLine()
            appendLine("Android Release APK:")
            appendLine(BuildConfig.ANDROID_APK_URL)
            appendLine()
            appendLine("Windows PC Setup (.exe):")
            appendLine(BuildConfig.WINDOWS_RECEIVER_URL)
            appendLine()
            appendLine("Quick Instructions:")
            appendLine("1. Install the APK on your phone and run BTMouseReceiver-Setup.exe on your Windows PC.")
            appendLine("2. Keep both devices on the same Wi-Fi network (or pair via Bluetooth).")
            appendLine("3. Open both apps to control mouse/keyboard & transfer files in background.")
            appendLine()
            append("Developed By Salim")
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "BT/WiFi Mouse With File Transfer (APK + Windows EXE)")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent, "Share App & PC Receiver Links"))
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.testTag("settings_about_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("about_version_text"),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Developer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Developed By Salim",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("about_developer_text"),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("about_call_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = "Call $phoneNumber",
                        modifier = Modifier.size(22.dp),
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUri)))
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("about_whatsapp_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF1B4332).copy(alpha = 0.22f),
                        contentColor = Color(0xFF25D366),
                    ),
                ) {
                    WhatsAppGlyph(modifier = Modifier.size(22.dp))
                }

                FilledTonalIconButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(facebookUri)))
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("about_facebook_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF1877F2).copy(alpha = 0.16f),
                        contentColor = Color(0xFF1877F2),
                    ),
                ) {
                    FacebookGlyph(modifier = Modifier.size(22.dp))
                }

                FilledTonalIconButton(
                    onClick = { shareLatestLinks() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("about_share_links_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share APK and Windows EXE download links",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsAppGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = w * 0.42f
        val center = Offset(w * 0.52f, h * 0.48f)

        drawCircle(
            color = Color(0xFF25D366),
            radius = radius,
            center = center,
            style = Stroke(width = w * 0.12f),
        )
        val tail = Path().apply {
            moveTo(w * 0.20f, h * 0.72f)
            lineTo(w * 0.10f, h * 0.92f)
            lineTo(w * 0.34f, h * 0.84f)
            close()
        }
        drawPath(tail, color = Color(0xFF25D366))

        val handset = Path().apply {
            moveTo(w * 0.38f, h * 0.34f)
            lineTo(w * 0.44f, h * 0.44f)
            quadraticTo(w * 0.52f, h * 0.56f, w * 0.64f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.56f)
        }
        drawPath(
            path = handset,
            color = Color(0xFF25D366),
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun FacebookGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = Color(0xFF1877F2),
            radius = w * 0.48f,
        )
        // Crisp "f" glyph inside Facebook blue badge
        val stemWidth = w * 0.15f
        val stemX = w * 0.48f
        drawRect(
            color = Color.White,
            topLeft = Offset(stemX, h * 0.30f),
            size = Size(stemWidth, h * 0.68f),
        )
        val topHook = Path().apply {
            moveTo(stemX, h * 0.33f)
            quadraticTo(stemX, h * 0.19f, w * 0.68f, h * 0.20f)
            lineTo(w * 0.71f, h * 0.20f)
            lineTo(w * 0.71f, h * 0.31f)
            lineTo(w * 0.63f, h * 0.31f)
            close()
        }
        drawPath(topHook, color = Color.White)
        // Horizontal crossbar
        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.35f, h * 0.46f),
            size = Size(w * 0.34f, h * 0.12f),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
            )
        }
    }
}
