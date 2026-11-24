package com.slm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slm.ads.AdMobManager
import com.slm.ui.BtMouseScreen
import com.slm.ui.BtMouseViewModel
import com.slm.ui.components.AnimatedLaunchScreen
import com.slm.ui.components.SplashPermissionScreen
import com.slm.ui.components.hasAllRequiredLaunchPermissions
import com.slm.ui.theme.BtMouseTheme
import kotlinx.coroutines.delay

private enum class LaunchStage {
    LOADING,
    PERMISSIONS,
    MAIN,
}

class MainActivity : ComponentActivity() {
    private val viewModel: BtMouseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdMobManager.initialize(this)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var stage by rememberSaveable { mutableStateOf(LaunchStage.LOADING) }

            LaunchedEffect(uiState.settingsLoaded) {
                if (!uiState.settingsLoaded || stage != LaunchStage.LOADING) return@LaunchedEffect

                val alreadySavedOrGranted =
                    uiState.settings.permissionsOnboardingDone ||
                        hasAllRequiredLaunchPermissions(this@MainActivity, viewModel.hidController)

                if (alreadySavedOrGranted && !uiState.settings.permissionsOnboardingDone) {
                    viewModel.markPermissionsOnboardingDone()
                }

                delay(750L)
                stage = if (alreadySavedOrGranted) LaunchStage.MAIN else LaunchStage.PERMISSIONS
            }

            BtMouseTheme(themeMode = uiState.settings.themeMode) {
                Crossfade(
                    targetState = stage,
                    animationSpec = tween(durationMillis = 280),
                    label = "launch_stage_crossfade",
                ) { currentStage ->
                    when (currentStage) {
                        LaunchStage.LOADING -> {
                            AnimatedLaunchScreen()
                        }
                        LaunchStage.PERMISSIONS -> {
                            SplashPermissionScreen(
                                hidController = viewModel.hidController,
                                onPermissionsUpdated = viewModel::onPermissionsUpdated,
                                onContinue = {
                                    viewModel.markPermissionsOnboardingDone()
                                    stage = LaunchStage.MAIN
                                },
                            )
                        }
                        LaunchStage.MAIN -> {
                            BtMouseScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDevices()
    }
}
