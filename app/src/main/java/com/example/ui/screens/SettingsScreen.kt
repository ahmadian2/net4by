package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ConnectionMode
import com.example.ui.theme.AmberSurface
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandBlueLight
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.EmeraldSurface
import com.example.ui.theme.InkMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftSlateSurface
import com.example.ui.theme.SubtleBorder
import com.example.ui.viewmodel.TetherUiState

@Composable
fun SettingsScreen(
  uiState: TetherUiState,
  onSelectMode: (ConnectionMode) -> Unit,
  onSavePort: (Int) -> Unit,
  onOpenBatterySettings: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onRequestWifiDirectPermission: () -> Unit,
  onResetStats: () -> Unit,
  onCopyCommand: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var portInput by remember(uiState.preferences.proxyPort) {
    mutableStateOf(uiState.preferences.proxyPort.toString())
  }
  val parsedPort = portInput.toIntOrNull()
  val isPortValid = parsedPort != null && parsedPort in 1024..65535

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
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(
        text = stringResource(R.string.nav_settings),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground
      )

      // 1. Connection Mode Section
      SectionHeader(title = "حالت اتصال")

      ModeOptionCard(
        title = stringResource(R.string.mode_usb),
        description = stringResource(R.string.mode_usb_desc),
        icon = Icons.Rounded.Usb,
        selected = uiState.preferences.connectionMode == ConnectionMode.USB,
        onClick = { onSelectMode(ConnectionMode.USB) },
        testTag = "mode_usb_card"
      )

      ModeOptionCard(
        title = stringResource(R.string.mode_wifi_direct),
        description = stringResource(R.string.mode_wifi_direct_desc),
        icon = Icons.Rounded.WifiTethering,
        selected = uiState.preferences.connectionMode == ConnectionMode.WIFI_DIRECT,
        onClick = { onSelectMode(ConnectionMode.WIFI_DIRECT) },
        testTag = "mode_wifi_direct_card"
      )

      // 2. Proxy Port Configuration
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SoftSlateSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, SubtleBorder)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "پورت پروکسی محلی",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "به‌صورت پیش‌فرض 8080 است. پشتیبانی همزمان از HTTP/HTTPS و SOCKS5.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
              OutlinedTextField(
                value = portInput,
                onValueChange = { value ->
                  if (value.length <= 5 && value.all { it.isDigit() }) {
                    portInput = value
                  }
                },
                singleLine = true,
                isError = !isPortValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                  .weight(1f)
                  .testTag("proxy_port_input"),
                label = { Text("Port (1024-65535)") }
              )
            }
            Button(
              onClick = { parsedPort?.let { onSavePort(it) } },
              enabled = isPortValid && parsedPort != uiState.preferences.proxyPort,
              modifier = Modifier
                .height(52.dp)
                .testTag("save_port_button")
            ) {
              Text("ذخیره")
            }
          }
        }
      }

      // 3. Background Stability
      SectionHeader(title = "پایداری در پس‌زمینه")
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (uiState.isBatteryOptimizationIgnored) EmeraldSurface else SoftSlateSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
          1.dp,
          if (uiState.isBatteryOptimizationIgnored) EmeraldConnected.copy(alpha = 0.25f) else SubtleBorder
        )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.BatteryChargingFull,
              contentDescription = null,
              tint = if (uiState.isBatteryOptimizationIgnored) EmeraldConnected else BrandBlue,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = if (uiState.isBatteryOptimizationIgnored) {
                "بهینه‌سازی باتری غیرفعال است (پایدار)"
              } else {
                "جلوگیری از قطع اتصال در پس‌زمینه"
              },
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "برای اینکه سیستم اندروید هنگام قفل صفحه اشتراک‌گذاری را قطع نکند، پیوند نت را در لیست «بدون محدودیت باتری» قرار دهید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (!uiState.isBatteryOptimizationIgnored) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
              onClick = onOpenBatterySettings,
              modifier = Modifier.testTag("battery_optimization_button")
            ) {
              Text("تنظیمات بهینه‌سازی باتری")
            }
          }
        }
      }

      // 4. Driver & Computer Setup Guide
      SectionHeader(title = "راهنمای نصب درایور و اتصال کامپیوتر")
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SoftSlateSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, SubtleBorder)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Computer,
              contentDescription = null,
              tint = BrandBlue,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = "راه‌اندازی سریع روی ویندوز، مک و لینوکس",
              style = MaterialTheme.typography.titleMedium
            )
          }

          GuideStep(
            number = "۱",
            text = "در ویندوز، درایور USB رسمی گوشی (Google USB Driver یا درایور سازنده) و ابزار سبک Android Platform Tools (ADB) را نصب کنید. در مک و لینوکس نیاز به نصب درایور نیست."
          )
          GuideStep(
            number = "۲",
            text = "در گوشی وارد تنظیمات توسعه‌دهنده (Developer Options) شده و گزینه «اشکال‌زدایی USB» (USB Debugging) را روشن کنید."
          )
          GuideStep(
            number = "۳",
            text = "کابل USB را وصل کنید، دستور زیر را در ترمینال کامپیوتر اجرا کنید و پروکسی سیستم یا مرورگر را روی 127.0.0.1 پورت ${uiState.preferences.proxyPort} تنظیم نمایید:"
          )

          val cmd = "adb forward tcp:${uiState.preferences.proxyPort} tcp:${uiState.preferences.proxyPort}"
          CopyableCodeRow(
            code = cmd,
            onCopy = { onCopyCommand(cmd) },
            testTag = "settings_copy_adb_button"
          )

          GuideStep(
            number = "۴",
            text = "در حالت Wi-Fi Direct نیازی به کابل و درایور نیست؛ کامپیوتر را به شبکه وای‌فای نمایش‌داده‌شده در صفحه اصلی وصل کرده و پروکسی را روی 192.168.49.1:${uiState.preferences.proxyPort} قرار دهید."
          )
        }
      }

      // 5. Transparent Permissions Overview
      SectionHeader(title = "مجوزها و دلیل دسترسی")
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SoftSlateSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, SubtleBorder)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          PermissionStatusRow(
            title = "دسترسی به شبکه و اینترنت",
            reason = "برای دریافت وضعیت VPN گوشی و انتقال امن بسته‌های شبکه به کامپیوتر",
            icon = Icons.Rounded.Public,
            isGranted = true,
            onRequest = null
          )
          PermissionStatusRow(
            title = "اعلان پایدار پس‌زمینه",
            reason = "برای نمایش وضعیت اتصال و جلوگیری از بسته شدن سرویس هنگام خاموش شدن صفحه",
            icon = Icons.Rounded.Notifications,
            isGranted = uiState.hasNotificationPermission,
            onRequest = onRequestNotificationPermission
          )
          PermissionStatusRow(
            title = "دستگاه‌های Wi-Fi نزدیک",
            reason = "فقط در حالت Wi-Fi Direct برای ساخت نقطه اتصال بی‌سیم بدون کابل استفاده می‌شود",
            icon = Icons.Rounded.LockOpen,
            isGranted = uiState.hasWifiDirectPermission,
            onRequest = onRequestWifiDirectPermission
          )
        }
      }

      // Reset stats
      TextButton(
        onClick = onResetStats,
        colors = ButtonDefaults.textButtonColors(contentColor = InkMuted),
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .testTag("reset_stats_button")
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("صفر کردن آمار مصرف کل")
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.padding(top = 4.dp)
  )
}

@Composable
private fun ModeOptionCard(
  title: String,
  description: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag),
    color = if (selected) BrandBlueLight.copy(alpha = 0.45f) else PureWhite,
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(
      width = if (selected) 1.5.dp else 1.dp,
      color = if (selected) BrandBlue else SubtleBorder
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) BrandBlue else InkMuted,
        modifier = Modifier.size(24.dp)
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      RadioButton(
        selected = selected,
        onClick = onClick
      )
    }
  }
}

@Composable
private fun GuideStep(
  number: String,
  text: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = "$number.",
      style = MaterialTheme.typography.labelLarge,
      color = BrandBlue
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun PermissionStatusRow(
  title: String,
  reason: String,
  icon: ImageVector,
  isGranted: Boolean,
  onRequest: (() -> Unit)?
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (isGranted) EmeraldConnected else AmberWarning,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground
      )
      Text(
        text = reason,
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    if (isGranted) {
      Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = "تایید شده",
        tint = EmeraldConnected,
        modifier = Modifier.size(20.dp)
      )
    } else if (onRequest != null) {
      Surface(
        onClick = onRequest,
        color = AmberSurface,
        shape = MaterialTheme.shapes.small
      ) {
        Text(
          text = "درخواست",
          style = MaterialTheme.typography.labelMedium,
          color = AmberWarning,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
      }
    }
  }
}
