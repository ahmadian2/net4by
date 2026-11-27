package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import com.example.model.NetworkStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(private val context: Context) {

  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
    var latestUsbConnected = readStickyUsbState()

    fun emitCurrent() {
      val (vpnActive, hasInternet) = inspectNetworks()
      trySend(
        NetworkStatus(
          isVpnActive = vpnActive,
          hasInternet = hasInternet,
          isUsbConnected = latestUsbConnected
        )
      )
    }

    val vpnRequest = NetworkRequest.Builder()
      .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
      .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
      .build()

    val vpnCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) = emitCurrent()
      override fun onLost(network: Network) = emitCurrent()
      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) = emitCurrent()
    }

    val defaultCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) = emitCurrent()
      override fun onLost(network: Network) = emitCurrent()
      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) = emitCurrent()
    }

    val usbReceiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent?.action == ACTION_USB_STATE) {
          latestUsbConnected = intent.getBooleanExtra("connected", false) ||
            intent.getBooleanExtra("configured", false)
          emitCurrent()
        }
      }
    }

    try {
      connectivityManager.registerNetworkCallback(vpnRequest, vpnCallback)
      connectivityManager.registerDefaultNetworkCallback(defaultCallback)
    } catch (_: SecurityException) {
      // Fallback: initial emission still works via inspectNetworks if permission restricted
    } catch (_: Exception) {
      // Ignore callback registration issues on constrained devices
    }

    ContextCompat.registerReceiver(
      context,
      usbReceiver,
      IntentFilter(ACTION_USB_STATE),
      ContextCompat.RECEIVER_EXPORTED
    )

    emitCurrent()

    awaitClose {
      runCatching { connectivityManager.unregisterNetworkCallback(vpnCallback) }
      runCatching { connectivityManager.unregisterNetworkCallback(defaultCallback) }
      runCatching { context.unregisterReceiver(usbReceiver) }
    }
  }.distinctUntilChanged()

  @Suppress("DEPRECATION")
  private fun inspectNetworks(): Pair<Boolean, Boolean> {
    var vpnActive = false
    var hasInternet = false
    try {
      val networks = connectivityManager.allNetworks
      for (network in networks) {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
          vpnActive = true
        }
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
          hasInternet = true
        }
      }
    } catch (_: Exception) {
      // Ignore inspection errors
    }
    return vpnActive to hasInternet
  }

  private fun readStickyUsbState(): Boolean {
    val sticky = runCatching {
      ContextCompat.registerReceiver(
        context,
        null,
        IntentFilter(ACTION_USB_STATE),
        ContextCompat.RECEIVER_EXPORTED
      )
    }.getOrNull()
    return sticky?.let {
      it.getBooleanExtra("connected", false) || it.getBooleanExtra("configured", false)
    } ?: false
  }

  companion object {
    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
  }
}
