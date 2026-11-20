package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.ThemeMode
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
        }
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
