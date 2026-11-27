package com.slm.bluetooth

enum class HostDeviceType {
    SMART_TV,
    WINDOWS_PC,
    OTHER,
}

data class PairedHostDevice(
    val name: String,
    val address: String,
    val deviceType: HostDeviceType,
    val isConnected: Boolean = false,
)

data class NearbyBtDevice(
    val name: String,
    val address: String,
    val deviceType: HostDeviceType,
    val isBonding: Boolean = false,
)

sealed class BtConnectionState {
    data object Unsupported : BtConnectionState()
    data object PermissionRequired : BtConnectionState()
    data object BluetoothDisabled : BtConnectionState()
    data object Initializing : BtConnectionState()
    data object Ready : BtConnectionState()
    data class Discoverable(val secondsRemaining: Int) : BtConnectionState()
    data class Connecting(val deviceName: String, val address: String) : BtConnectionState()
    data class Connected(val deviceName: String, val address: String, val deviceType: HostDeviceType) : BtConnectionState()
}

data class HidTelemetry(
    val packetsSent: Long = 0L,
    val activeRateHz: Int = 125,
    val isTransmitting: Boolean = false,
)

data class ConnectionDiagnostic(
    val deviceName: String,
    val message: String,
    val isAlreadyPairedSdpHint: Boolean = false,
)
