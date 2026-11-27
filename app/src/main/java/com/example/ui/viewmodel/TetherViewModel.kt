package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.NetworkMonitor
import com.example.data.SettingsRepository
import com.example.model.ConnectionMode
import com.example.model.NetworkStatus
import com.example.model.TetherServiceState
import com.example.model.TetherStatus
import com.example.model.UserPreferences
import com.example.service.TetherForegroundService
import com.example.service.TetherStateHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PendingPermissionRequest {
  NONE,
  NOTIFICATIONS,
  WIFI_DIRECT
}

data class TetherUiState(
  val serviceState: TetherServiceState = TetherServiceState(),
  val networkStatus: NetworkStatus = NetworkStatus(),
  val preferences: UserPreferences = UserPreferences(),
  val elapsedMillis: Long = 0L,
  val isBatteryOptimizationIgnored: Boolean = false,
  val hasNotificationPermission: Boolean = true,
  val hasWifiDirectPermission: Boolean = true,
  val pendingPermissionDialog: PendingPermissionRequest = PendingPermissionRequest.NONE
)

class TetherViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val settingsRepository = SettingsRepository(appContext)
  private val networkMonitor = NetworkMonitor(appContext)

  private val elapsedMillisFlow = MutableStateFlow(0L)
  private val permissionTrigger = MutableStateFlow(0)
  private val pendingPermissionFlow = MutableStateFlow(PendingPermissionRequest.NONE)
  private var startAfterPermission = false

  val uiState: StateFlow<TetherUiState> = combine(
    TetherStateHolder.state,
    networkMonitor.observeNetworkStatus(),
    settingsRepository.preferencesFlow,
    elapsedMillisFlow,
    combine(permissionTrigger, pendingPermissionFlow) { tick, pending -> tick to pending }
  ) { serviceState, networkStatus, prefs, elapsed, (_, pendingDialog) ->
    TetherUiState(
      serviceState = serviceState,
      networkStatus = networkStatus,
      preferences = prefs,
      elapsedMillis = elapsed,
      isBatteryOptimizationIgnored = checkBatteryOptimizationIgnored(),
      hasNotificationPermission = checkNotificationPermission(),
      hasWifiDirectPermission = checkWifiDirectPermission(),
      pendingPermissionDialog = pendingDialog
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000L),
    initialValue = TetherUiState(
      isBatteryOptimizationIgnored = checkBatteryOptimizationIgnored(),
      hasNotificationPermission = checkNotificationPermission(),
      hasWifiDirectPermission = checkWifiDirectPermission()
    )
  )

  init {
    viewModelScope.launch {
      while (isActive) {
        val current = TetherStateHolder.state.value
        elapsedMillisFlow.value =
          if (current.status == TetherStatus.CONNECTED && current.startedAtMillis > 0L) {
            (System.currentTimeMillis() - current.startedAtMillis).coerceAtLeast(0L)
          } else {
            0L
          }
        delay(1000L)
      }
    }
  }

  fun onToggleSharing() {
    val current = uiState.value
    if (current.serviceState.status == TetherStatus.CONNECTED ||
      current.serviceState.status == TetherStatus.STARTING
    ) {
      TetherForegroundService.stop(appContext)
      return
    }

    // Transparently request runtime permissions with clear explanation before starting
    if (!checkNotificationPermission()) {
      startAfterPermission = true
      pendingPermissionFlow.value = PendingPermissionRequest.NOTIFICATIONS
      return
    }

    if (current.preferences.connectionMode == ConnectionMode.WIFI_DIRECT &&
      !checkWifiDirectPermission()
    ) {
      startAfterPermission = true
      pendingPermissionFlow.value = PendingPermissionRequest.WIFI_DIRECT
      return
    }

    startServiceNow(current.preferences.connectionMode, current.preferences.proxyPort)
  }

  fun dismissPermissionDialog() {
    startAfterPermission = false
    pendingPermissionFlow.value = PendingPermissionRequest.NONE
  }

  fun requestNotificationPermissionRationale() {
    startAfterPermission = false
    pendingPermissionFlow.value = PendingPermissionRequest.NOTIFICATIONS
  }

  fun requestWifiDirectPermissionRationale() {
    startAfterPermission = false
    pendingPermissionFlow.value = PendingPermissionRequest.WIFI_DIRECT
  }

  fun onPermissionPromptConfirmed(): PendingPermissionRequest {
    val type = pendingPermissionFlow.value
    pendingPermissionFlow.value = PendingPermissionRequest.NONE
    return type
  }

  fun onPermissionResult(granted: Boolean) {
    refreshSystemChecks()
    val shouldAutoStart = startAfterPermission
    startAfterPermission = false
    if (!shouldAutoStart) return

    val current = uiState.value
    // Even if notification permission was denied, check if Wifi Direct needs permission or start
    if (current.preferences.connectionMode == ConnectionMode.WIFI_DIRECT &&
      !checkWifiDirectPermission()
    ) {
      if (granted) {
        startAfterPermission = true
        pendingPermissionFlow.value = PendingPermissionRequest.WIFI_DIRECT
      }
      return
    }
    if (current.preferences.connectionMode == ConnectionMode.USB || checkWifiDirectPermission()) {
      startServiceNow(current.preferences.connectionMode, current.preferences.proxyPort)
    }
  }

  private fun startServiceNow(mode: ConnectionMode, port: Int) {
    TetherForegroundService.start(appContext, mode, port)
  }

  fun setConnectionMode(mode: ConnectionMode) {
    viewModelScope.launch {
      settingsRepository.setConnectionMode(mode)
      if (mode == ConnectionMode.WIFI_DIRECT && !checkWifiDirectPermission()) {
        startAfterPermission = false
        pendingPermissionFlow.value = PendingPermissionRequest.WIFI_DIRECT
      }
      // If currently sharing, restart cleanly on the new mode
      val status = TetherStateHolder.state.value.status
      if (status == TetherStatus.CONNECTED &&
        (mode == ConnectionMode.USB || checkWifiDirectPermission())
      ) {
        TetherForegroundService.start(appContext, mode, uiState.value.preferences.proxyPort)
      }
    }
  }

  fun setProxyPort(port: Int) {
    viewModelScope.launch {
      settingsRepository.setProxyPort(port)
    }
  }

  fun resetLifetimeStats() {
    viewModelScope.launch {
      settingsRepository.resetLifetimeStats()
    }
  }

  fun refreshSystemChecks() {
    permissionTrigger.value += 1
  }

  private fun checkBatteryOptimizationIgnored(): Boolean {
    val pm = appContext.getSystemService(PowerManager::class.java) ?: return false
    return pm.isIgnoringBatteryOptimizations(appContext.packageName)
  }

  private fun checkNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  private fun checkWifiDirectPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.NEARBY_WIFI_DEVICES
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
    }
  }
}
