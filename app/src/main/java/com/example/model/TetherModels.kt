package com.example.model

enum class ConnectionMode(val id: String) {
  USB("usb"),
  WIFI_DIRECT("wifi_direct");

  companion object {
    fun fromId(id: String?): ConnectionMode =
      entries.firstOrNull { it.id == id } ?: USB
  }
}

enum class TetherStatus {
  DISCONNECTED,
  STARTING,
  CONNECTED,
  ERROR
}

data class WifiDirectInfo(
  val ssid: String = "",
  val passphrase: String = "",
  val groupOwnerIp: String = "192.168.49.1",
  val isGroupFormed: Boolean = false
)

data class TetherServiceState(
  val status: TetherStatus = TetherStatus.DISCONNECTED,
  val activeMode: ConnectionMode = ConnectionMode.USB,
  val proxyPort: Int = 8080,
  val sessionBytesSent: Long = 0L,
  val sessionBytesReceived: Long = 0L,
  val activeConnections: Int = 0,
  val startedAtMillis: Long = 0L,
  val wifiDirectInfo: WifiDirectInfo = WifiDirectInfo(),
  val errorMessage: String? = null
)

data class NetworkStatus(
  val isVpnActive: Boolean = false,
  val hasInternet: Boolean = false,
  val isUsbConnected: Boolean = false
)

data class UserPreferences(
  val connectionMode: ConnectionMode = ConnectionMode.USB,
  val proxyPort: Int = 8080,
  val lifetimeBytesSent: Long = 0L,
  val lifetimeBytesReceived: Long = 0L
)
