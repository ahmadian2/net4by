package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ConnectionMode
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tetherDataStore: DataStore<Preferences> by preferencesDataStore(name = "peyvand_settings")

class SettingsRepository(private val context: Context) {

  private object Keys {
    val CONNECTION_MODE = stringPreferencesKey("connection_mode")
    val PROXY_PORT = intPreferencesKey("proxy_port")
    val LIFETIME_SENT = longPreferencesKey("lifetime_bytes_sent")
    val LIFETIME_RECEIVED = longPreferencesKey("lifetime_bytes_received")
  }

  val preferencesFlow: Flow<UserPreferences> =
    context.tetherDataStore.data.map { prefs ->
      UserPreferences(
        connectionMode = ConnectionMode.fromId(prefs[Keys.CONNECTION_MODE]),
        proxyPort = (prefs[Keys.PROXY_PORT] ?: DEFAULT_PORT).coerceIn(1024, 65535),
        lifetimeBytesSent = prefs[Keys.LIFETIME_SENT] ?: 0L,
        lifetimeBytesReceived = prefs[Keys.LIFETIME_RECEIVED] ?: 0L
      )
    }

  suspend fun setConnectionMode(mode: ConnectionMode) {
    context.tetherDataStore.edit { prefs ->
      prefs[Keys.CONNECTION_MODE] = mode.id
    }
  }

  suspend fun setProxyPort(port: Int) {
    val sanitized = port.coerceIn(1024, 65535)
    context.tetherDataStore.edit { prefs ->
      prefs[Keys.PROXY_PORT] = sanitized
    }
  }

  suspend fun addTrafficDelta(sentDelta: Long, receivedDelta: Long) {
    if (sentDelta <= 0L && receivedDelta <= 0L) return
    context.tetherDataStore.edit { prefs ->
      val currentSent = prefs[Keys.LIFETIME_SENT] ?: 0L
      val currentReceived = prefs[Keys.LIFETIME_RECEIVED] ?: 0L
      prefs[Keys.LIFETIME_SENT] = currentSent + sentDelta.coerceAtLeast(0L)
      prefs[Keys.LIFETIME_RECEIVED] = currentReceived + receivedDelta.coerceAtLeast(0L)
    }
  }

  suspend fun resetLifetimeStats() {
    context.tetherDataStore.edit { prefs ->
      prefs[Keys.LIFETIME_SENT] = 0L
      prefs[Keys.LIFETIME_RECEIVED] = 0L
    }
  }

  companion object {
    const val DEFAULT_PORT = 8080
  }
}
