package com.slm.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.slm.data.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
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
                    Text(
                        text = "Higher Hz coalesces sub-pixel frames faster for lag-free 4K TV & laptop control.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                title = "Pointer Acceleration",
                subtitle = "Boost cursor travel speed on fast swipes",
                checked = settings.pointerAcceleration,
                onCheckedChange = onAccelerationChanged,
                testTag = "acceleration_switch",
            )
            SettingsSwitchRow(
                title = "Natural Scrolling",
                subtitle = "Content follows finger direction",
                checked = settings.naturalScroll,
                onCheckedChange = onNaturalScrollChanged,
                testTag = "natural_scroll_switch",
            )
            SettingsSwitchRow(
                title = "Haptic Feedback",
                subtitle = "Tactile pulse on clicks, wheel notches & keys",
                checked = settings.hapticFeedback,
                onCheckedChange = onHapticChanged,
                testTag = "haptic_switch",
            )

            AboutDeveloperCard()
        }
    }
}

@Composable
private fun AboutDeveloperCard() {
    val context = LocalContext.current
    val phoneNumber = "01737462871"
    val whatsappUri = "https://wa.me/8801737462871"
    val facebookUri = "https://fb.com/salim.naogaon"

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
                    text = "Salim",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("about_developer_text"),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("about_call_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Call me", maxLines = 1)
                }

                FilledTonalButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUri)))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("about_whatsapp_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1B4332).copy(alpha = 0.22f),
                        contentColor = Color(0xFF25D366),
                    ),
                ) {
                    WhatsAppGlyph(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp", maxLines = 1)
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
    subtitle: String,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
            )
        }
    }
}
