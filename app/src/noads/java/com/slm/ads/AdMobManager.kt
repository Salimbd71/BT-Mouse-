package com.slm.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object AdMobIds {
    const val APP_ID = ""
    const val BANNER_UNIT_ID = ""
    const val INTERSTITIAL_UNIT_ID = ""
}

object AdMobManager {
    fun initialize(context: Context) = Unit
}

class InterstitialAdManager(
    private val minIntervalMs: Long = 90_000L,
    private val initialGraceMs: Long = 25_000L,
) {
    fun loadAd(context: Context) = Unit

    fun showIfReady(activity: Activity?, onContinue: () -> Unit = {}) {
        onContinue()
    }
}

@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    // Ads disabled when includeAdmobAds=false
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
