package com.slm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slm.R
import com.slm.bluetooth.HidKeyMap

private data class KeyCap(
    val label: String,
    val hidCode: Int,
    val weight: Float = 1f,
)

@Composable
fun KeyboardDeckSection(
    hapticEnabled: Boolean,
    latchedModifiers: Int,
    liveTypeBuffer: String,
    onLiveTypeChanged: (String) -> Unit,
    onClearBuffer: () -> Unit,
    onToggleModifier: (Int) -> Unit,
    onSendHidKey: (Int) -> Unit,
    onSendCombo: (Int, Int) -> Unit,
    onSendCharacter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var showFunctionRow by remember { mutableStateOf(false) }

    fun tapHaptic() {
        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Live System Soft-Keyboard Input Bridge
        OutlinedTextField(
            value = liveTypeBuffer,
            onValueChange = onLiveTypeChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live_keyboard_input"),
            placeholder = {
                Text(
                    text = stringResource(R.string.hint_type_to_host),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (liveTypeBuffer.isNotEmpty()) {
                        IconButton(
                            onClick = onClearBuffer,
                            modifier = Modifier.testTag("clear_live_input_button"),
                        ) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                        }
                    }
                    IconButton(
                        onClick = {
                            tapHaptic()
                            onSendHidKey(HidKeyMap.KEY_ENTER)
                            onClearBuffer()
                        },
                        modifier = Modifier.testTag("send_enter_button"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardReturn,
                            contentDescription = "Send Enter",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    tapHaptic()
                    onSendHidKey(HidKeyMap.KEY_ENTER)
                    onClearBuffer()
                },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // Sticky Modifiers + Windows/TV Shortcuts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModifierChip(
                label = "Ctrl",
                active = (latchedModifiers and HidKeyMap.MOD_LEFT_CTRL) != 0,
                onClick = { tapHaptic(); onToggleModifier(HidKeyMap.MOD_LEFT_CTRL) },
                testTag = "mod_ctrl",
            )
            ModifierChip(
                label = "Alt",
                active = (latchedModifiers and HidKeyMap.MOD_LEFT_ALT) != 0,
                onClick = { tapHaptic(); onToggleModifier(HidKeyMap.MOD_LEFT_ALT) },
                testTag = "mod_alt",
            )
            ModifierChip(
                label = "Shift",
                active = (latchedModifiers and HidKeyMap.MOD_LEFT_SHIFT) != 0,
                onClick = { tapHaptic(); onToggleModifier(HidKeyMap.MOD_LEFT_SHIFT) },
                testTag = "mod_shift",
            )
            ModifierChip(
                label = "Win",
                active = (latchedModifiers and HidKeyMap.MOD_LEFT_GUI) != 0,
                onClick = { tapHaptic(); onToggleModifier(HidKeyMap.MOD_LEFT_GUI) },
                testTag = "mod_win",
            )
            ModifierChip(
                label = if (showFunctionRow) "123" else "F1-F12",
                active = showFunctionRow,
                onClick = { tapHaptic(); showFunctionRow = !showFunctionRow },
                testTag = "toggle_fn_row",
            )
            ShortcutChip("Esc") { tapHaptic(); onSendHidKey(HidKeyMap.KEY_ESC) }
            ShortcutChip("Tab") { tapHaptic(); onSendHidKey(HidKeyMap.KEY_TAB) }
            ShortcutChip("Del") { tapHaptic(); onSendHidKey(HidKeyMap.KEY_DELETE) }
            ShortcutChip("Copy") { tapHaptic(); onSendCombo(HidKeyMap.MOD_LEFT_CTRL, HidKeyMap.KEY_C) }
            ShortcutChip("Paste") { tapHaptic(); onSendCombo(HidKeyMap.MOD_LEFT_CTRL, HidKeyMap.KEY_V) }
            ShortcutChip("Undo") { tapHaptic(); onSendCombo(HidKeyMap.MOD_LEFT_CTRL, HidKeyMap.KEY_Z) }
            ShortcutChip("Alt+Tab") { tapHaptic(); onSendCombo(HidKeyMap.MOD_LEFT_ALT, HidKeyMap.KEY_TAB) }
            ShortcutChip("Win+D") { tapHaptic(); onSendCombo(HidKeyMap.MOD_LEFT_GUI, HidKeyMap.KEY_D) }
            ShortcutChip("Alt+F4") { tapHaptic(); onSendCombo(HidKeyMap.MOD_LEFT_ALT, HidKeyMap.KEY_F4) }
        }

        // Number Row or F1..F12 Row
        if (showFunctionRow) {
            val fKeys1 = listOf(
                KeyCap("F1", HidKeyMap.KEY_F1), KeyCap("F2", HidKeyMap.KEY_F2),
                KeyCap("F3", HidKeyMap.KEY_F3), KeyCap("F4", HidKeyMap.KEY_F4),
                KeyCap("F5", HidKeyMap.KEY_F5), KeyCap("F6", HidKeyMap.KEY_F6),
            )
            val fKeys2 = listOf(
                KeyCap("F7", HidKeyMap.KEY_F7), KeyCap("F8", HidKeyMap.KEY_F8),
                KeyCap("F9", HidKeyMap.KEY_F9), KeyCap("F10", HidKeyMap.KEY_F10),
                KeyCap("F11", HidKeyMap.KEY_F11), KeyCap("F12", HidKeyMap.KEY_F12),
            )
            HidKeyRow(fKeys1) { tapHaptic(); onSendHidKey(it) }
            HidKeyRow(fKeys2) { tapHaptic(); onSendHidKey(it) }
        } else {
            CharKeyRow(
                chars = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0'),
                shiftActive = (latchedModifiers and HidKeyMap.MOD_LEFT_SHIFT) != 0,
            ) { ch -> tapHaptic(); onSendCharacter(ch) }
        }

        // Built-in Low-Latency QWERTY Deck
        CharKeyRow(
            chars = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
            shiftActive = (latchedModifiers and HidKeyMap.MOD_LEFT_SHIFT) != 0,
        ) { ch -> tapHaptic(); onSendCharacter(ch) }

        CharKeyRow(
            chars = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
            shiftActive = (latchedModifiers and HidKeyMap.MOD_LEFT_SHIFT) != 0,
            horizontalPadding = 12.dp,
        ) { ch -> tapHaptic(); onSendCharacter(ch) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shiftActive = (latchedModifiers and HidKeyMap.MOD_LEFT_SHIFT) != 0
            Surface(
                onClick = { tapHaptic(); onToggleModifier(HidKeyMap.MOD_LEFT_SHIFT) },
                modifier = Modifier
                    .weight(1.35f)
                    .height(48.dp)
                    .testTag("key_shift"),
                shape = RoundedCornerShape(10.dp),
                color = if (shiftActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (shiftActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "⇧",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (shiftActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            listOf('z', 'x', 'c', 'v', 'b', 'n', 'm').forEach { ch ->
                val display = if (shiftActive) ch.uppercaseChar() else ch
                Surface(
                    onClick = { tapHaptic(); onSendCharacter(ch) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = display.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Surface(
                onClick = { tapHaptic(); onSendHidKey(HidKeyMap.KEY_BACKSPACE) },
                modifier = Modifier
                    .weight(1.35f)
                    .height(48.dp)
                    .testTag("key_backspace"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = "Backspace",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Bottom Space + Punctuation + Enter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(
                onClick = { tapHaptic(); onSendCharacter(',') },
                modifier = Modifier
                    .weight(0.9f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(",", style = MaterialTheme.typography.titleMedium)
                }
            }

            Surface(
                onClick = { tapHaptic(); onSendHidKey(HidKeyMap.KEY_SPACE) },
                modifier = Modifier
                    .weight(3.2f)
                    .height(48.dp)
                    .testTag("key_space"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SpaceBar,
                        contentDescription = "Space",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                onClick = { tapHaptic(); onSendCharacter('.') },
                modifier = Modifier
                    .weight(0.9f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(".", style = MaterialTheme.typography.titleMedium)
                }
            }

            Surface(
                onClick = { tapHaptic(); onSendHidKey(HidKeyMap.KEY_ENTER) },
                modifier = Modifier
                    .weight(1.4f)
                    .height(48.dp)
                    .testTag("key_enter"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardReturn,
                        contentDescription = "Enter",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Compact Navigation & Arrow Pad
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortcutChip("Home") { tapHaptic(); onSendHidKey(HidKeyMap.KEY_HOME) }
            ShortcutChip("End") { tapHaptic(); onSendHidKey(HidKeyMap.KEY_END) }
            Spacer(modifier = Modifier.weight(1f))
            ArrowKeyButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left Arrow") {
                tapHaptic(); onSendHidKey(HidKeyMap.KEY_LEFT)
            }
            ArrowKeyButton(Icons.Rounded.KeyboardArrowUp, "Up Arrow") {
                tapHaptic(); onSendHidKey(HidKeyMap.KEY_UP)
            }
            ArrowKeyButton(Icons.Rounded.KeyboardArrowDown, "Down Arrow") {
                tapHaptic(); onSendHidKey(HidKeyMap.KEY_DOWN)
            }
            ArrowKeyButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right Arrow") {
                tapHaptic(); onSendHidKey(HidKeyMap.KEY_RIGHT)
            }
        }
    }
}

@Composable
private fun CharKeyRow(
    chars: List<Char>,
    shiftActive: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onCharClick: (Char) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        chars.forEach { ch ->
            val display = if (shiftActive && ch.isLetter()) ch.uppercaseChar() else ch
            Surface(
                onClick = { onCharClick(ch) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = display.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun HidKeyRow(
    keys: List<KeyCap>,
    onKeyClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        keys.forEach { cap ->
            Surface(
                onClick = { onKeyClick(cap.hidCode) },
                modifier = Modifier
                    .weight(cap.weight)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = cap.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModifierChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ShortcutChip(
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ArrowKeyButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description)
        }
    }
}
