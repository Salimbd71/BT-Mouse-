package com.slm.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.slm.BuildConfig
import com.slm.R
import com.slm.bluetooth.BluetoothHidController
import com.slm.ui.theme.ConnectedEmerald
import com.slm.ui.theme.PairingAmber
import kotlinx.coroutines.delay

fun requiredLaunchPermissions(hidController: BluetoothHidController): Array<String> {
    val permissions = linkedSetOf<String>()
    permissions.addAll(hidController.requiredPermissions())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    return permissions.toTypedArray()
}

private fun isPermissionGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun SplashPermissionScreen(
    hidController: BluetoothHidController,
    onPermissionsUpdated: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionCheckTick by remember { mutableIntStateOf(0) }

    val allPermissions = remember(hidController) { requiredLaunchPermissions(hidController) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionCheckTick++
        onPermissionsUpdated()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionCheckTick++
                onPermissionsUpdated()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val btGranted = remember(permissionCheckTick, hidController) {
        hidController.requiredPermissions().all { isPermissionGranted(context, it) }
    }
    val notificationsGranted = remember(permissionCheckTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }
    val storageGranted = remember(permissionCheckTick) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            isPermissionGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            true
        }
    }
    val allGranted = btGranted && notificationsGranted && storageGranted

    LaunchedEffect(allGranted) {
        if (allGranted) {
            delay(750L)
            onPermissionsUpdated()
            onContinue()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Mouse,
                            contentDescription = stringResource(R.string.app_name),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} • By Salim",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (allGranted) {
                            "All required permissions granted. Starting controller…"
                        } else {
                            "Allow required permissions to enable Bluetooth mouse, Wi-Fi PC control, and background file transfer."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PermissionStatusRow(
                            icon = Icons.Rounded.Bluetooth,
                            title = "Bluetooth & Nearby Devices",
                            subtitle = "Pair & control Smart TVs and Windows laptops wirelessly",
                            granted = btGranted,
                        )
                        PermissionStatusRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "Background Transfer Notification",
                            subtitle = "Keep sending/receiving files when closed & show Cancel control",
                            granted = notificationsGranted,
                        )
                        PermissionStatusRow(
                            icon = Icons.Rounded.FolderOpen,
                            title = "Downloads Storage Access",
                            subtitle = "Save files received from PC into Downloads/BT mouse",
                            granted = storageGranted,
                        )
                    }
                }

                if (!allGranted) {
                    Button(
                        onClick = { permissionLauncher.launch(allPermissions) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("splash_allow_permissions_button"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Allow Required Permissions")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("splash_open_settings_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("App Settings", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = onContinue,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("splash_continue_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Continue", maxLines = 1)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                } else {
                    TextButton(
                        onClick = onContinue,
                        modifier = Modifier.testTag("splash_enter_now_button"),
                    ) {
                        Text("Opening app…")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (granted) ConnectedEmerald.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) ConnectedEmerald else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (granted) "Granted" else "Needed",
            tint = if (granted) ConnectedEmerald else PairingAmber,
            modifier = Modifier.size(22.dp),
        )
    }
}
