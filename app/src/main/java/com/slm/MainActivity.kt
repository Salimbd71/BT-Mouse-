package com.slm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slm.ui.BtMouseScreen
import com.slm.ui.BtMouseViewModel
import com.slm.ui.components.SplashPermissionScreen
import com.slm.ui.theme.BtMouseTheme

class MainActivity : ComponentActivity() {
    private val viewModel: BtMouseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var showSplash by rememberSaveable { mutableStateOf(true) }

            BtMouseTheme(themeMode = uiState.settings.themeMode) {
                if (showSplash) {
                    SplashPermissionScreen(
                        hidController = viewModel.hidController,
                        onPermissionsUpdated = viewModel::onPermissionsUpdated,
                        onContinue = { showSplash = false },
                    )
                } else {
                    BtMouseScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDevices()
    }
}
