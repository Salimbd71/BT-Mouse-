package com.slm

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.slm.bluetooth.BluetoothHidController
import com.slm.bluetooth.HidMotionEngine
import com.slm.data.AppSettings
import com.slm.data.MouseButtonsPosition
import com.slm.data.TransportMode
import com.slm.ui.components.requiredLaunchPermissions
import com.slm.wifi.FileTransferRepository
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
@Config(sdk = [34], manifest = Config.NONE)
class ExampleRobolectricTest {

    @Test
    fun `read app name, version, and receiver download url from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BT/WiFi Mouse With File Transfer", appName)
        assertEquals("1.0", BuildConfig.VERSION_NAME)
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
}
