package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PermissionRationaleDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandBlueLight
import com.example.ui.theme.InkMuted
import com.example.ui.theme.PeyvandNetTheme
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SubtleBorder
import com.example.ui.viewmodel.PendingPermissionRequest
import com.example.ui.viewmodel.TetherViewModel
import kotlinx.coroutines.launch

private enum class AppTab {
  HOME,
  SETTINGS
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PeyvandNetTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          PeyvandNetApp()
        }
      }
    }
  }
}

@Composable
fun PeyvandNetApp(
  viewModel: TetherViewModel = viewModel()
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  BackHandler(enabled = selectedTab == AppTab.SETTINGS) {
    selectedTab = AppTab.HOME
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSystemChecks()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    viewModel.onPermissionResult(granted)
  }

  val copySuccessMessage = stringResource(R.string.copied_to_clipboard)
  val copyToClipboard: (String) -> Unit = { value ->
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("peyvand_net", value))
    coroutineScope.launch {
      snackbarHostState.showSnackbar(copySuccessMessage)
    }
  }

  val openBatterySettings: () -> Unit = {
    runCatching {
      context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
  }

  PermissionRationaleDialog(
    requestType = uiState.pendingPermissionDialog,
    onConfirm = {
      val type = viewModel.onPermissionPromptConfirmed()
      when (type) {
        PendingPermissionRequest.NOTIFICATIONS -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            viewModel.onPermissionResult(true)
          }
        }
        PendingPermissionRequest.WIFI_DIRECT -> {
          val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
          } else {
            Manifest.permission.ACCESS_FINE_LOCATION
          }
          permissionLauncher.launch(permission)
        }
        PendingPermissionRequest.NONE -> Unit
      }
    },
    onDismiss = { viewModel.dismissPermissionDialog() }
  )

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      containerColor = PureWhite,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useRail) {
          Column {
            HorizontalDivider(thickness = 1.dp, color = SubtleBorder)
            NavigationBar(
              containerColor = PureWhite,
              tonalElevation = 0.dp
            ) {
              NavigationBarItem(
                selected = selectedTab == AppTab.HOME,
                onClick = { selectedTab = AppTab.HOME },
                icon = {
                  Icon(
                    imageVector = if (selectedTab == AppTab.HOME) Icons.Rounded.Home else Icons.Outlined.Home,
                    contentDescription = stringResource(R.string.nav_home)
                  )
                },
                label = { Text(stringResource(R.string.nav_home)) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = BrandBlue,
                  selectedTextColor = BrandBlue,
                  indicatorColor = BrandBlueLight,
                  unselectedIconColor = InkMuted,
                  unselectedTextColor = InkMuted
                ),
                modifier = Modifier.testTag("nav_home_tab")
              )
              NavigationBarItem(
                selected = selectedTab == AppTab.SETTINGS,
                onClick = { selectedTab = AppTab.SETTINGS },
                icon = {
                  Icon(
                    imageVector = if (selectedTab == AppTab.SETTINGS) Icons.Rounded.Settings else Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.nav_settings)
                  )
                },
                label = { Text(stringResource(R.string.nav_settings)) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = BrandBlue,
                  selectedTextColor = BrandBlue,
                  indicatorColor = BrandBlueLight,
                  unselectedIconColor = InkMuted,
                  unselectedTextColor = InkMuted
                ),
                modifier = Modifier.testTag("nav_settings_tab")
              )
            }
          }
        }
      }
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        if (useRail) {
          NavigationRail(containerColor = PureWhite) {
            NavigationRailItem(
              selected = selectedTab == AppTab.HOME,
              onClick = { selectedTab = AppTab.HOME },
              icon = {
                Icon(
                  imageVector = if (selectedTab == AppTab.HOME) Icons.Rounded.Home else Icons.Outlined.Home,
                  contentDescription = stringResource(R.string.nav_home)
                )
              },
              label = { Text(stringResource(R.string.nav_home)) },
              colors = NavigationRailItemDefaults.colors(
                selectedIconColor = BrandBlue,
                selectedTextColor = BrandBlue,
                indicatorColor = BrandBlueLight,
                unselectedIconColor = InkMuted,
                unselectedTextColor = InkMuted
              ),
              modifier = Modifier.testTag("nav_home_rail")
            )
            NavigationRailItem(
              selected = selectedTab == AppTab.SETTINGS,
              onClick = { selectedTab = AppTab.SETTINGS },
              icon = {
                Icon(
                  imageVector = if (selectedTab == AppTab.SETTINGS) Icons.Rounded.Settings else Icons.Outlined.Settings,
                  contentDescription = stringResource(R.string.nav_settings)
                )
              },
              label = { Text(stringResource(R.string.nav_settings)) },
              colors = NavigationRailItemDefaults.colors(
                selectedIconColor = BrandBlue,
                selectedTextColor = BrandBlue,
                indicatorColor = BrandBlueLight,
                unselectedIconColor = InkMuted,
                unselectedTextColor = InkMuted
              ),
              modifier = Modifier.testTag("nav_settings_rail")
            )
          }
        }

        Box(modifier = Modifier.weight(1f)) {
          when (selectedTab) {
            AppTab.HOME -> HomeScreen(
              uiState = uiState,
              onToggleSharing = viewModel::onToggleSharing,
              onCopyText = copyToClipboard
            )
            AppTab.SETTINGS -> SettingsScreen(
              uiState = uiState,
              onSelectMode = viewModel::setConnectionMode,
              onSavePort = { port ->
                viewModel.setProxyPort(port)
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("پورت $port ذخیره شد")
                }
              },
              onOpenBatterySettings = openBatterySettings,
              onRequestNotificationPermission = viewModel::requestNotificationPermissionRationale,
              onRequestWifiDirectPermission = viewModel::requestWifiDirectPermissionRationale,
              onResetStats = {
                viewModel.resetLifetimeStats()
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("آمار مصرف کل صفر شد")
                }
              },
              onCopyCommand = copyToClipboard
            )
          }
        }
      }
    }
  }
}
