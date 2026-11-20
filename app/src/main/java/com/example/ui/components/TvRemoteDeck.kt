package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.bluetooth.HidKeyMap

@Composable
fun TvRemoteDeckSection(
    hapticEnabled: Boolean,
    onSendHidKey: (Int) -> Unit,
    onSendConsumer: (Int) -> Unit,
    onOpenKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    fun tap() {
        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Top Smart TV System Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RemotePillButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = "Back",
                onClick = { tap(); onSendConsumer(HidKeyMap.CONSUMER_AC_BACK) },
                modifier = Modifier.weight(1f).testTag("tv_back_button"),
            )
            RemotePillButton(
                icon = Icons.Rounded.Home,
                label = "Home",
                onClick = { tap(); onSendConsumer(HidKeyMap.CONSUMER_AC_HOME) },
                modifier = Modifier.weight(1f).testTag("tv_home_button"),
            )
            RemotePillButton(
                icon = Icons.Rounded.Menu,
                label = "Menu",
                onClick = { tap(); onSendConsumer(HidKeyMap.CONSUMER_MENU) },
                modifier = Modifier.weight(1f).testTag("tv_menu_button"),
            )
            RemotePillButton(
                icon = Icons.Rounded.Search,
                label = "Search",
                onClick = {
                    tap()
                    onSendConsumer(HidKeyMap.CONSUMER_AC_SEARCH)
                    onOpenKeyboard()
                },
                modifier = Modifier.weight(1f).testTag("tv_search_button"),
            )
        }

        // Precision D-Pad Ring
        Surface(
            modifier = Modifier.size(236.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 2.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            ) {
                DPadDirectionButton(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    description = "D-Pad Up",
                    onClick = { tap(); onSendHidKey(HidKeyMap.KEY_UP) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .testTag("tv_dpad_up"),
                )
                DPadDirectionButton(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    description = "D-Pad Down",
                    onClick = { tap(); onSendHidKey(HidKeyMap.KEY_DOWN) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .testTag("tv_dpad_down"),
                )
                DPadDirectionButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    description = "D-Pad Left",
                    onClick = { tap(); onSendHidKey(HidKeyMap.KEY_LEFT) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .testTag("tv_dpad_left"),
                )
                DPadDirectionButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    description = "D-Pad Right",
                    onClick = { tap(); onSendHidKey(HidKeyMap.KEY_RIGHT) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .testTag("tv_dpad_right"),
                )

                // Center OK / Enter Button
                Surface(
                    onClick = {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendHidKey(HidKeyMap.KEY_ENTER)
                    },
                    modifier = Modifier
                        .size(84.dp)
                        .align(Alignment.Center)
                        .testTag("tv_dpad_ok"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "OK",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }

        // Volume & Media Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RemotePillButton(
                icon = Icons.AutoMirrored.Rounded.VolumeDown,
                label = "Vol -",
                onClick = { tap(); onSendConsumer(HidKeyMap.CONSUMER_VOLUME_DOWN) },
                modifier = Modifier.weight(1f).testTag("tv_vol_down"),
            )
            RemotePillButton(
                icon = Icons.AutoMirrored.Rounded.VolumeOff,
                label = "Mute",
                onClick = { tap(); onSendConsumer(HidKeyMap.CONSUMER_MUTE) },
                modifier = Modifier.weight(1f).testTag("tv_mute"),
            )
            RemotePillButton(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = "Vol +",
                onClick = { tap(); onSendConsumer(HidKeyMap.CONSUMER_VOLUME_UP) },
                modifier = Modifier.weight(1f).testTag("tv_vol_up"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RemotePillButton(
                icon = Icons.Rounded.SkipPrevious,
                label = "Prev",
                onClick = { tap(); onSendConsumer(HidKeyMap.CONSUMER_PREV_TRACK) },
                modifier = Modifier.weight(1f).testTag("tv_prev"),
            )
            RemotePillButton(
                icon = Icons.Rounded.PlayArrow,
                label = "Play / Pause",
                isPrimary = true,
                onClick = { tap(); onSendConsumer(HidKeyMap.CONSUMER_PLAY_PAUSE) },
                modifier = Modifier.weight(1.4f).testTag("tv_play_pause"),
            )
            RemotePillButton(
                icon = Icons.Rounded.SkipNext,
                label = "Next",
                onClick = { tap(); onSendConsumer(HidKeyMap.CONSUMER_NEXT_TRACK) },
                modifier = Modifier.weight(1f).testTag("tv_next"),
            )
        }
    }
}

@Composable
private fun DPadDirectionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun RemotePillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
