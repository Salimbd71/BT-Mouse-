package com.slm

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.slm.ads.AdMobIds
import com.slm.bluetooth.BluetoothHidController
import com.slm.bluetooth.HidDescriptor
import com.slm.bluetooth.HidMotionEngine
import com.slm.data.AppSettings
import com.slm.data.MouseButtonsPosition
import com.slm.data.TransportMode
import com.slm.ui.components.requiredLaunchPermissions
import com.slm.wifi.FileTransferRepository
import com.slm.wifi.WifiMouseController
import com.slm.wifi.WifiProtocol
import com.slm.wifi.parseQrConnectPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name, version, and receiver download url from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BT/WiFi Mouse With File Transfer", appName)
        assertEquals("com.slm.btmouse", BuildConfig.APPLICATION_ID)
        assertEquals("1.0", BuildConfig.VERSION_NAME)
        if (BuildConfig.ADMOB_ENABLED) {
            assertEquals("ca-app-pub-3239967742299196~9829622978", AdMobIds.APP_ID)
            assertEquals("ca-app-pub-3239967742299196/4525325407", AdMobIds.BANNER_UNIT_ID)
            assertEquals("ca-app-pub-3239967742299196/6987516412", AdMobIds.INTERSTITIAL_UNIT_ID)
        } else {
            assertEquals("", AdMobIds.APP_ID)
            assertEquals("", AdMobIds.BANNER_UNIT_ID)
            assertEquals("", AdMobIds.INTERSTITIAL_UNIT_ID)
        }
        assertTrue(BuildConfig.WINDOWS_RECEIVER_URL.endsWith("/BTMouseReceiver-Setup.exe"))
        assertTrue(BuildConfig.ANDROID_APK_URL.endsWith("/BT-mouse-release.apk"))
    }

    @Test
    fun `default app settings use wifi mode, bottom buttons, right-handed, and onboarding not completed`() {
        val defaults = AppSettings()
        assertEquals(TransportMode.WIFI, defaults.transportMode)
        assertEquals(MouseButtonsPosition.BOTTOM, defaults.mouseButtonsPosition)
        assertFalse(defaults.leftHandedMode)
        assertFalse(defaults.permissionsOnboardingDone)
    }

    @Test
    fun `parseQrConnectPayload handles multi-ip v1 payload, plain ipv4, and invalid text`() {
        val v1 = parseQrConnectPayload("BTMOUSE_QR_V1|192.168.1.50,192.168.56.1|51920|SALIM-PC")
        assertEquals(listOf("192.168.1.50", "192.168.56.1"), v1?.ips)
        assertEquals(51920, v1?.port)
        assertEquals("SALIM-PC", v1?.hostName)

        val plain = parseQrConnectPayload("192.168.1.10")
        assertEquals(listOf("192.168.1.10"), plain?.ips)
        assertEquals(WifiProtocol.CONTROL_PORT, plain?.port)
        assertNull(plain?.hostName)

        assertNull(parseQrConnectPayload("https://example.com/not-a-mouse-qr"))
    }

    @Test
    fun `splash launch permissions include bluetooth connect and post notifications on api 36`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = BluetoothHidController(context, HidMotionEngine())
        val perms = requiredLaunchPermissions(controller).toSet()
        controller.release()

        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
        assertTrue(perms.contains(Manifest.permission.RECORD_AUDIO))
        assertTrue(perms.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun `file transfer repository cancel updates state cleanly`() {
        FileTransferRepository.requestCancel()
        val cancelledState = FileTransferRepository.state.value
        assertNull(cancelledState.activeTransfer)
        assertEquals("File transfer cancelled", cancelledState.errorMessage)

        FileTransferRepository.clearError()
        FileTransferRepository.resetCancelFlag()
        assertNull(FileTransferRepository.state.value.errorMessage)
    }

    @Test
    fun `wifi auto reconnect matches saved ip or hostname when router ip changes`() {
        assertTrue(
            WifiMouseController.matchesPreferredHost(
                ip = "192.168.1.50",
                hostName = "OTHER-PC",
                preferredIp = "192.168.1.50",
                preferredHostName = "SALIM-PC",
            ),
        )
        assertTrue(
            WifiMouseController.matchesPreferredHost(
                ip = "192.168.1.88",
                hostName = "salim-pc",
                preferredIp = "192.168.1.50",
                preferredHostName = "SALIM-PC",
            ),
        )
        assertFalse(
            WifiMouseController.matchesPreferredHost(
                ip = "192.168.1.88",
                hostName = "GUEST-PC",
                preferredIp = "192.168.1.50",
                preferredHostName = "SALIM-PC",
            ),
        )
    }

    @Test
    fun `hid composite descriptor encodes keyboard max 231 as 16-bit unsigned instead of negative signed byte`() {
        val bytes = HidDescriptor.COMPOSITE_DESCRIPTOR
        // Must NOT contain 0x25 0xE7 (signed -25 rejected by Smart TV/Linux HID parsers)
        for (i in 0 until bytes.size - 1) {
            assertFalse(
                "Found invalid signed 1-byte Logical Maximum 0xE7 at index $i",
                bytes[i] == 0x25.toByte() && bytes[i + 1] == 0xE7.toByte(),
            )
        }
        // Must contain 2-byte positive Logical Maximum (231): 0x26, 0xE7, 0x00
        val has2ByteMax231 = (0 until bytes.size - 2).any { i ->
            bytes[i] == 0x26.toByte() && bytes[i + 1] == 0xE7.toByte() && bytes[i + 2] == 0x00.toByte()
        }
        assertTrue("Descriptor must encode Logical Maximum (231) as 0x26 0xE7 0x00", has2ByteMax231)
    }
}
