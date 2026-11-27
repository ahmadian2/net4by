package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ConnectionMode
import com.example.model.TetherStatus
import com.example.proxy.ProxyProtocolParser
import com.example.ui.theme.AmberSurface
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CoralStop
import com.example.ui.theme.CoralSurface
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.EmeraldSurface
import com.example.ui.theme.InkMuted
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftSlateSurface
import com.example.ui.theme.SubtleBorder
import com.example.ui.viewmodel.TetherUiState

@Composable
fun HomeScreen(
  uiState: TetherUiState,
  onToggleSharing: () -> Unit,
  onCopyText: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val service = uiState.serviceState
  val isConnected = service.status == TetherStatus.CONNECTED
  val isStarting = service.status == TetherStatus.STARTING
  val isActive = isConnected || isStarting
  val selectedMode = if (isActive) service.activeMode else uiState.preferences.connectionMode
  val activePort = if (isActive) service.proxyPort else uiState.preferences.proxyPort

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(PureWhite),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 560.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Minimalist Top Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
          )
          Text(
            text = "پل اشتراک‌گذاری VPN به کامپیوتر",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted
          )
        }

        // Compact Status Pill for VPN
        StatusBadgePill(
          label = if (uiState.networkStatus.isVpnActive) "VPN فعال" else "VPN خاموش",
          icon = Icons.Rounded.Shield,
          containerColor = if (uiState.networkStatus.isVpnActive) EmeraldSurface else AmberSurface,
          contentColor = if (uiState.networkStatus.isVpnActive) EmeraldConnected else AmberWarning
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Connection Status Indicator
      val statusLabel = when (service.status) {
        TetherStatus.CONNECTED -> stringResource(R.string.status_connected)
        TetherStatus.STARTING -> stringResource(R.string.status_connecting)
        TetherStatus.ERROR -> stringResource(R.string.status_error)
        TetherStatus.DISCONNECTED -> stringResource(R.string.status_disconnected)
      }
      val statusBg by animateColorAsState(
        targetValue = if (isConnected) EmeraldSurface else SoftSlateSurface,
        animationSpec = tween(250),
        label = "status_bg"
      )
      val statusFg by animateColorAsState(
        targetValue = if (isConnected) EmeraldConnected else InkMuted,
        animationSpec = tween(250),
        label = "status_fg"
      )

      Surface(
        color = statusBg,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (isConnected) EmeraldConnected.copy(alpha = 0.25f) else SubtleBorder)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(statusFg)
          )
          Text(
            text = "وضعیت اتصال: $statusLabel",
            style = MaterialTheme.typography.labelLarge,
            color = statusFg,
            modifier = Modifier.testTag("connection_status_text")
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Primary Hero Start / Stop Button
      HeroToggleButton(
        isActive = isActive,
        onClick = onToggleSharing
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Quick context hint under button
      val modeBadgeText = if (selectedMode == ConnectionMode.USB) {
        if (uiState.networkStatus.isUsbConnected) {
          "حالت USB • کابل متصل است"
        } else {
          "حالت USB • کابل متصل نیست"
        }
      } else {
        "حالت بی‌سیم Wi-Fi Direct"
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = if (selectedMode == ConnectionMode.USB) Icons.Rounded.Usb else Icons.Rounded.WifiTethering,
          contentDescription = null,
          tint = InkMuted,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = modeBadgeText,
          style = MaterialTheme.typography.bodySmall,
          color = InkMuted
        )
      }

      // Error banner if service encountered an issue
      AnimatedVisibility(visible = !service.errorMessage.isNullOrBlank()) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
          color = CoralSurface,
          shape = MaterialTheme.shapes.medium,
          border = BorderStroke(1.dp, CoralStop.copy(alpha = 0.25f))
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.ErrorOutline,
              contentDescription = null,
              tint = CoralStop,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = service.errorMessage.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = CoralStop
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Active Endpoint / Quick Connection Card
      ConnectionDetailsCard(
        mode = selectedMode,
        port = activePort,
        isConnected = isConnected,
        wifiSsid = service.wifiDirectInfo.ssid,
        wifiPassphrase = service.wifiDirectInfo.passphrase,
        wifiIp = service.wifiDirectInfo.groupOwnerIp,
        onCopyText = onCopyText
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Simple Data Usage Stats
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = stringResource(R.string.stats_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          DataStatTile(
            title = stringResource(R.string.stats_received),
            value = ProxyProtocolParser.formatBytes(service.sessionBytesReceived),
            icon = Icons.Rounded.ArrowDownward,
            accentColor = EmeraldConnected,
            modifier = Modifier.weight(1f)
          )
          DataStatTile(
            title = stringResource(R.string.stats_sent),
            value = ProxyProtocolParser.formatBytes(service.sessionBytesSent),
            icon = Icons.Rounded.ArrowUpward,
            accentColor = BrandBlue,
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Session duration & total lifetime summary bar
        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = SoftSlateSurface,
          shape = MaterialTheme.shapes.medium,
          border = BorderStroke(1.dp, SubtleBorder)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "مدت: ${ProxyProtocolParser.formatDuration(uiState.elapsedMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Text(
              text = "اتصال فعال: ${service.activeConnections}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val totalLifetime = uiState.preferences.lifetimeBytesSent +
              uiState.preferences.lifetimeBytesReceived +
              service.sessionBytesSent +
              service.sessionBytesReceived
            Text(
              text = "کل: ${ProxyProtocolParser.formatBytes(totalLifetime)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HeroToggleButton(
  isActive: Boolean,
  onClick: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (isActive) 1.09f else 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1300),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_scale"
  )

  val buttonColor by animateColorAsState(
    targetValue = if (isActive) CoralStop else BrandBlue,
    animationSpec = tween(250),
    label = "hero_btn_color"
  )

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.size(204.dp)
  ) {
    // Subtle outer breathing halo when active
    Box(
      modifier = Modifier
        .size(190.dp)
        .scale(if (isActive) pulseScale else 1f)
        .clip(CircleShape)
        .background(buttonColor.copy(alpha = if (isActive) 0.12f else 0.08f))
    )

    Surface(
      onClick = onClick,
      shape = CircleShape,
      color = buttonColor,
      shadowElevation = if (isActive) 4.dp else 8.dp,
      modifier = Modifier
        .size(168.dp)
        .testTag("toggle_sharing_button")
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = if (isActive) Icons.Rounded.Stop else Icons.Rounded.PowerSettingsNew,
          contentDescription = stringResource(
            if (isActive) R.string.action_stop_sharing else R.string.action_start_sharing
          ),
          tint = PureWhite,
          modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = stringResource(
            if (isActive) R.string.action_stop_sharing else R.string.action_start_sharing
          ),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = PureWhite,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

@Composable
private fun ConnectionDetailsCard(
  mode: ConnectionMode,
  port: Int,
  isConnected: Boolean,
  wifiSsid: String,
  wifiPassphrase: String,
  wifiIp: String,
  onCopyText: (String) -> Unit
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = SoftSlateSurface,
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, SubtleBorder)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      if (mode == ConnectionMode.USB) {
        val adbCommand = "adb forward tcp:$port tcp:$port"
        Text(
          text = "تنظیم پروکسی روی کامپیوتر (USB)",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "پس از اجرای دستور زیر در کامپیوتر، پروکسی HTTP یا SOCKS5 را روی 127.0.0.1:$port قرار دهید:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        CopyableCodeRow(
          code = adbCommand,
          onCopy = { onCopyText(adbCommand) },
          testTag = "copy_adb_button"
        )
      } else {
        val endpoint = "$wifiIp:$port"
        Text(
          text = "اطلاعات اتصال Wi-Fi Direct",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (!isConnected) {
          Text(
            text = "با فشردن دکمه شروع، شبکه مستقیم ساخته شده و رمز اتصال در اینجا نمایش داده می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          if (wifiSsid.isNotBlank()) {
            Text(
              text = "نام شبکه: $wifiSsid",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onBackground
            )
            if (wifiPassphrase.isNotBlank()) {
              Spacer(modifier = Modifier.height(6.dp))
              CopyableCodeRow(
                code = wifiPassphrase,
                prefixLabel = "رمز:",
                onCopy = { onCopyText(wifiPassphrase) },
                testTag = "copy_wifi_pass_button"
              )
            }
          } else {
            Text(
              text = "در حال آماده‌سازی گروه Wi-Fi Direct…",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          CopyableCodeRow(
            code = endpoint,
            prefixLabel = "پروکسی:",
            onCopy = { onCopyText(endpoint) },
            testTag = "copy_wifi_endpoint_button"
          )
        }
      }
    }
  }
}

@Composable
fun CopyableCodeRow(
  code: String,
  onCopy: () -> Unit,
  testTag: String,
  prefixLabel: String? = null
) {
  Surface(
    color = PureWhite,
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(1.dp, SubtleBorder),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        if (!prefixLabel.isNullOrBlank()) {
          Text(
            text = prefixLabel,
            style = MaterialTheme.typography.labelMedium,
            color = InkMuted,
            modifier = Modifier.padding(end = 8.dp)
          )
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Text(
            text = code,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }
      IconButton(
        onClick = onCopy,
        modifier = Modifier.testTag(testTag)
      ) {
        Icon(
          imageVector = Icons.Rounded.ContentCopy,
          contentDescription = stringResource(R.string.action_copy),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
private fun DataStatTile(
  title: String,
  value: String,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    color = PureWhite,
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, SubtleBorder)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
          )
        }
        Text(
          text = title,
          style = MaterialTheme.typography.bodySmall,
          color = InkMuted
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
          text = value,
          fontFamily = JetBrainsMonoFontFamily,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )
      }
    }
  }
}

@Composable
private fun StatusBadgePill(
  label: String,
  icon: ImageVector,
  containerColor: Color,
  contentColor: Color
) {
  Surface(
    color = containerColor,
    shape = RoundedCornerShape(50)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(14.dp)
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor
      )
    }
  }
}
