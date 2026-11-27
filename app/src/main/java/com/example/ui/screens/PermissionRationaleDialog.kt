package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.viewmodel.PendingPermissionRequest

@Composable
fun PermissionRationaleDialog(
  requestType: PendingPermissionRequest,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  if (requestType == PendingPermissionRequest.NONE) return

  val isNotification = requestType == PendingPermissionRequest.NOTIFICATIONS
  val title = stringResource(
    if (isNotification) R.string.perm_title_notifications else R.string.perm_title_wifi_direct
  )
  val description = stringResource(
    if (isNotification) R.string.perm_desc_notifications else R.string.perm_desc_wifi_direct
  )
  val icon = if (isNotification) Icons.Rounded.NotificationsActive else Icons.Rounded.WifiTethering

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    icon = {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(26.dp)
        )
      }
    },
    title = {
      Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center
        )
      }
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        modifier = Modifier.testTag("permission_confirm_button")
      ) {
        Text(text = stringResource(R.string.perm_grant))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("permission_dismiss_button")
      ) {
        Text(text = stringResource(R.string.perm_cancel))
      }
    }
  )
}
