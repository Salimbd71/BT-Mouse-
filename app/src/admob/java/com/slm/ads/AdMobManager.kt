package com.slm.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdMobIds {
    const val APP_ID = "ca-app-pub-3239967742299196~9829622978"
    const val BANNER_UNIT_ID = "ca-app-pub-3239967742299196/4525325407"
    const val INTERSTITIAL_UNIT_ID = "ca-app-pub-3239967742299196/6987516412"
}

object AdMobManager {
    private const val TAG = "AdMobManager"

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        runCatching {
            MobileAds.initialize(context.applicationContext) { status ->
                Log.d(TAG, "MobileAds initialized: ${status.adapterStatusMap.keys}")
            }
        }.onFailure { err ->
            initialized = false
            Log.w(TAG, "MobileAds init skipped/failed: ${err.message}")
        }
    }
}

class InterstitialAdManager(
    private val minIntervalMs: Long = 90_000L,
    private val initialGraceMs: Long = 25_000L,
) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private val createdAtMs = SystemClock.elapsedRealtime()
    private var lastShownMs = 0L

    fun loadAd(context: Context) {
        if (interstitialAd != null || isLoading) return
        isLoading = true
        val appContext = context.applicationContext
        runCatching {
            val request = AdRequest.Builder().build()
            InterstitialAd.load(
                appContext,
                AdMobIds.INTERSTITIAL_UNIT_ID,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoading = false
                        interstitialAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoading = false
                        interstitialAd = null
                    }
                },
            )
        }.onFailure {
            isLoading = false
            interstitialAd = null
        }
    }

    fun showIfReady(activity: Activity?, onContinue: () -> Unit = {}) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onContinue()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val pastGrace = now - createdAtMs >= initialGraceMs
        val pastCooldown = lastShownMs == 0L || (now - lastShownMs >= minIntervalMs)
        val readyAd = interstitialAd

        if (readyAd == null || !pastGrace || !pastCooldown) {
            if (readyAd == null) loadAd(activity)
            onContinue()
            return
        }

        interstitialAd = null
        lastShownMs = now
        readyAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadAd(activity)
                onContinue()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadAd(activity)
                onContinue()
            }
        }
        runCatching {
            readyAd.show(activity)
        }.onFailure {
            loadAd(activity)
            onContinue()
        }
    }
}

@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    val isInspection = LocalInspectionMode.current
    val context = LocalContext.current
    var adLoaded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admob_banner_container"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (adLoaded) 50.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )

            if (!isInspection) {
                val widthDp = maxWidth.value.toInt().coerceIn(320, 900)
                val adSize = remember(widthDp, context) {
                    runCatching {
                        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
                    }.getOrElse {
                        AdSize.BANNER
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { viewContext ->
                        AdView(viewContext).apply {
                            adUnitId = AdMobIds.BANNER_UNIT_ID
                            setAdSize(adSize)
                            adListener = object : AdListener() {
                                override fun onAdLoaded() {
                                    adLoaded = true
                                }

                                override fun onAdFailedToLoad(error: LoadAdError) {
                                    adLoaded = false
                                }
                            }
                            runCatching {
                                loadAd(AdRequest.Builder().build())
                            }
                        }
                    },
                    onRelease = { adView ->
                        runCatching { adView.destroy() }
                    },
                )
            }
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
