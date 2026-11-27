package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.SettingsRepository
import com.example.model.ConnectionMode
import com.example.model.TetherStatus
import com.example.model.WifiDirectInfo
import com.example.proxy.ProxyProtocolParser
import com.example.proxy.ProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TetherForegroundService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var settingsRepository: SettingsRepository
  private var proxyServer: ProxyServer? = null
  private var statsJob: Job? = null

  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null

  private var wifiP2pManager: WifiP2pManager? = null
  private var wifiP2pChannel: WifiP2pManager.Channel? = null
  private var wifiP2pReceiver: BroadcastReceiver? = null

  private var activeMode: ConnectionMode = ConnectionMode.USB
  private var activePort: Int = SettingsRepository.DEFAULT_PORT
  private var lastPersistedSent: Long = 0L
  private var lastPersistedReceived: Long = 0L

  override fun onCreate() {
    super.onCreate()
    settingsRepository = SettingsRepository(applicationContext)
    createNotificationChannel()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopTethering(errorMessage = null)
        return START_NOT_STICKY
      }
      ACTION_START -> {
        val modeId = intent.getStringExtra(EXTRA_MODE)
        val port = intent.getIntExtra(EXTRA_PORT, SettingsRepository.DEFAULT_PORT).coerceIn(1024, 65535)
        val requestedMode = ConnectionMode.fromId(modeId)
        startTethering(requestedMode, port)
      }
      else -> {
        // Restarted by system sticky policy
        startTethering(activeMode, activePort)
      }
    }
    return START_STICKY
  }

  private fun startTethering(mode: ConnectionMode, port: Int) {
    // Enter foreground immediately to comply with Android 8+ / 14+ foreground service timing rules
    promoteToForeground(buildNotification(mode, port, 0L, 0L))

    // Clean up any prior session cleanly
    tearDownResourcesOnly()

    activeMode = mode
    activePort = port
    lastPersistedSent = 0L
    lastPersistedReceived = 0L

    TetherStateHolder.update {
      it.copy(
        status = TetherStatus.STARTING,
        activeMode = mode,
        proxyPort = port,
        sessionBytesSent = 0L,
        sessionBytesReceived = 0L,
        activeConnections = 0,
        startedAtMillis = System.currentTimeMillis(),
        wifiDirectInfo = WifiDirectInfo(),
        errorMessage = null
      )
    }

    if (mode == ConnectionMode.WIFI_DIRECT && !hasWifiDirectPermission()) {
      stopTethering("برای فعال‌سازی Wi-Fi Direct ابتدا مجوز دستگاه‌های وای‌فای نزدیک را تایید کنید.")
      return
    }

    acquireLocks(enableWifiLock = mode == ConnectionMode.WIFI_DIRECT)

    try {
      val server = ProxyServer(port = port) { err ->
        serviceScope.launch {
          stopTethering("خطا در سرور اشتراک‌گذاری: $err")
        }
      }
      server.start()
      proxyServer = server

      TetherStateHolder.update {
        it.copy(
          status = TetherStatus.CONNECTED,
          errorMessage = null
        )
      }

      if (mode == ConnectionMode.WIFI_DIRECT) {
        startWifiDirectGroup()
      }

      startStatsLoop()
    } catch (e: Exception) {
      stopTethering("پورت $port در دسترس نیست یا خطایی رخ داد: ${e.localizedMessage ?: "نامشخص"}")
    }
  }

  private fun startStatsLoop() {
    statsJob?.cancel()
    statsJob = serviceScope.launch {
      var tick = 0
      while (isActive) {
        val server = proxyServer ?: break
        val sent = server.bytesSent.get()
        val received = server.bytesReceived.get()
        val conns = server.activeConnections.get().coerceAtLeast(0)

        TetherStateHolder.update {
          it.copy(
            sessionBytesSent = sent,
            sessionBytesReceived = received,
            activeConnections = conns
          )
        }

        // Update persistent notification every 2 seconds and flush DataStore every 6 seconds
        if (tick % 2 == 0) {
          val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          nm.notify(NOTIFICATION_ID, buildNotification(activeMode, activePort, sent, received))
        }
        if (tick % 6 == 0) {
          flushTrafficDelta(sent, received)
        }
        tick++
        delay(1000L)
      }
    }
  }

  private suspend fun flushTrafficDelta(currentSent: Long, currentReceived: Long) {
    val sentDelta = (currentSent - lastPersistedSent).coerceAtLeast(0L)
    val receivedDelta = (currentReceived - lastPersistedReceived).coerceAtLeast(0L)
    if (sentDelta > 0L || receivedDelta > 0L) {
      lastPersistedSent = currentSent
      lastPersistedReceived = currentReceived
      settingsRepository.addTrafficDelta(sentDelta, receivedDelta)
    }
  }

  @SuppressLint("MissingPermission")
  private fun startWifiDirectGroup() {
    val manager = applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    if (manager == null) {
      TetherStateHolder.update {
        it.copy(errorMessage = "سخت‌افزار Wi-Fi Direct در این دستگاه پشتیبانی نمی‌شود.")
      }
      return
    }
    wifiP2pManager = manager
    val channel = manager.initialize(applicationContext, Looper.getMainLooper(), null)
    wifiP2pChannel = channel

    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
          queryWifiDirectDetails()
        }
      }
    }
    wifiP2pReceiver = receiver
    ContextCompat.registerReceiver(
      applicationContext,
      receiver,
      IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )

    try {
      manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
        override fun onSuccess() = createGroupNow(manager, channel)
        override fun onFailure(reason: Int) = createGroupNow(manager, channel)
      })
    } catch (_: SecurityException) {
      TetherStateHolder.update {
        it.copy(errorMessage = "دسترسی Wi-Fi Direct توسط سیستم رد شد.")
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun createGroupNow(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
    try {
      manager.createGroup(channel, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
          queryWifiDirectDetails()
        }

        override fun onFailure(reason: Int) {
          val reasonLabel = when (reason) {
            WifiP2pManager.BUSY -> "سیستم وای‌فای مشغول است. وای‌فای گوشی را روشن و دوباره تلاش کنید."
            WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct روی این دستگاه پشتیبانی نمی‌شود."
            else -> "ایجاد شبکه Wi-Fi Direct ناموفق بود (کد $reason)."
          }
          TetherStateHolder.update { it.copy(errorMessage = reasonLabel) }
        }
      })
    } catch (_: SecurityException) {
      TetherStateHolder.update {
        it.copy(errorMessage = "دسترسی Wi-Fi Direct توسط سیستم رد شد.")
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun queryWifiDirectDetails() {
    val manager = wifiP2pManager ?: return
    val channel = wifiP2pChannel ?: return
    if (!hasWifiDirectPermission()) return

    try {
      manager.requestGroupInfo(channel) { group ->
        if (group != null) {
          TetherStateHolder.update { state ->
            state.copy(
              wifiDirectInfo = state.wifiDirectInfo.copy(
                ssid = group.networkName.orEmpty(),
                passphrase = group.passphrase.orEmpty(),
                isGroupFormed = true
              )
            )
          }
        }
      }
      manager.requestConnectionInfo(channel) { info ->
        val ip = info?.groupOwnerAddress?.hostAddress
        if (!ip.isNullOrBlank()) {
          TetherStateHolder.update { state ->
            state.copy(
              wifiDirectInfo = state.wifiDirectInfo.copy(
                groupOwnerIp = ip,
                isGroupFormed = info.groupFormed
              )
            )
          }
        }
      }
    } catch (_: SecurityException) {
      // Permission revoked externally
    }
  }

  private fun hasWifiDirectPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.NEARBY_WIFI_DEVICES
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  @SuppressLint("WakelockTimeout")
  private fun acquireLocks(enableWifiLock: Boolean) {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    if (wakeLock == null) {
      wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PeyvandNet::TetherWakeLock").apply {
        setReferenceCounted(false)
        acquire()
      }
    }

    if (enableWifiLock && wifiLock == null) {
      val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
      val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
      } else {
        @Suppress("DEPRECATION")
        WifiManager.WIFI_MODE_FULL_HIGH_PERF
      }
      wifiLock = wm.createWifiLock(lockMode, "PeyvandNet::WifiLock").apply {
        setReferenceCounted(false)
        acquire()
      }
    }
  }

  private fun releaseLocks() {
    runCatching {
      if (wakeLock?.isHeld == true) wakeLock?.release()
    }
    wakeLock = null
    runCatching {
      if (wifiLock?.isHeld == true) wifiLock?.release()
    }
    wifiLock = null
  }

  @SuppressLint("MissingPermission")
  private fun tearDownResourcesOnly() {
    statsJob?.cancel()
    statsJob = null

    val server = proxyServer
    if (server != null) {
      val finalSent = server.bytesSent.get()
      val finalReceived = server.bytesReceived.get()
      server.stop()
      proxyServer = null
      serviceScope.launch(Dispatchers.IO) {
        flushTrafficDelta(finalSent, finalReceived)
      }
    }

    wifiP2pReceiver?.let { receiver ->
      runCatching { applicationContext.unregisterReceiver(receiver) }
    }
    wifiP2pReceiver = null

    val manager = wifiP2pManager
    val channel = wifiP2pChannel
    if (manager != null && channel != null && hasWifiDirectPermission()) {
      runCatching { manager.removeGroup(channel, null) }
    }
    wifiP2pManager = null
    wifiP2pChannel = null

    releaseLocks()
  }

  private fun stopTethering(errorMessage: String?) {
    tearDownResourcesOnly()
    TetherStateHolder.resetToDisconnected(errorMessage)
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun promoteToForeground(notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceCompat.startForeground(
        this,
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun buildNotification(
    mode: ConnectionMode,
    port: Int,
    sentBytes: Long,
    receivedBytes: Long
  ): Notification {
    val openAppIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val stopIntent = PendingIntent.getService(
      this,
      1,
      Intent(this, TetherForegroundService::class.java).apply { action = ACTION_STOP },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val modeLabel = if (mode == ConnectionMode.USB) "USB" else "Wi-Fi Direct"
    val downStr = ProxyProtocolParser.formatBytes(receivedBytes)
    val upStr = ProxyProtocolParser.formatBytes(sentBytes)

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.notification_title))
      .setContentText("$modeLabel • پورت $port • دریافت $downStr / ارسال $upStr")
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setContentIntent(openAppIntent)
      .addAction(0, getString(R.string.notification_stop), stopIntent)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = getString(R.string.notification_channel_desc)
        setShowBadge(false)
      }
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      nm.createNotificationChannel(channel)
    }
  }

  override fun onDestroy() {
    tearDownResourcesOnly()
    TetherStateHolder.resetToDisconnected(TetherStateHolder.state.value.errorMessage)
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_START = "com.example.action.START_TETHER"
    const val ACTION_STOP = "com.example.action.STOP_TETHER"
    const val EXTRA_MODE = "extra_mode"
    const val EXTRA_PORT = "extra_port"

    private const val CHANNEL_ID = "peyvand_tether_channel"
    private const val NOTIFICATION_ID = 1001

    fun start(context: Context, mode: ConnectionMode, port: Int) {
      val intent = Intent(context, TetherForegroundService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_MODE, mode.id)
        putExtra(EXTRA_PORT, port)
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      val intent = Intent(context, TetherForegroundService::class.java).apply {
        action = ACTION_STOP
      }
      context.startService(intent)
    }
  }
}
