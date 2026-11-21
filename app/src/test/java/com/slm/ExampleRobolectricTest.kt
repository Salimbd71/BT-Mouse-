package com.slm

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.slm.bluetooth.BluetoothHidController
import com.slm.bluetooth.HidMotionEngine
import com.slm.ui.components.requiredLaunchPermissions
import com.slm.wifi.FileTransferRepository
import org.junit.Assert.assertEquals
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
        assertEquals("1.0", BuildConfig.VERSION_NAME)
        assertTrue(BuildConfig.WINDOWS_RECEIVER_URL.endsWith("/BTMouseReceiver-Setup.exe"))
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
