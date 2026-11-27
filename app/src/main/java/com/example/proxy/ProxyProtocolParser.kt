package com.example.proxy

import java.net.InetAddress
import java.net.URI
import java.util.Locale

data class ProxyTarget(
  val host: String,
  val port: Int,
  val rewrittenRequestLine: String? = null
)

object ProxyProtocolParser {

  fun parseConnectTarget(authority: String): ProxyTarget? {
    val trimmed = authority.trim()
    if (trimmed.isEmpty()) return null

    // Handle IPv6 bracketed host e.g. [::1]:443
    if (trimmed.startsWith("[")) {
      val closeBracket = trimmed.indexOf(']')
      if (closeBracket <= 1) return null
      val host = trimmed.substring(1, closeBracket)
      val portPart = trimmed.substring(closeBracket + 1)
      val port = if (portPart.startsWith(":")) {
        portPart.substring(1).toIntOrNull() ?: return null
      } else {
        443
      }
      if (port !in 1..65535) return null
      return ProxyTarget(host = host, port = port)
    }

    val colonIdx = trimmed.lastIndexOf(':')
    return if (colonIdx >= 0) {
      val host = trimmed.substring(0, colonIdx)
      val port = trimmed.substring(colonIdx + 1).toIntOrNull() ?: return null
      if (host.isBlank() || port !in 1..65535) null else ProxyTarget(host = host, port = port)
    } else {
      if (trimmed.contains(" ")) null else ProxyTarget(host = trimmed, port = 443)
    }
  }

  fun parseHttpRequestLine(requestLine: String, headers: List<String>): ProxyTarget? {
    val parts = requestLine.trim().split(" ")
    if (parts.size < 3) return null
    val method = parts[0].uppercase(Locale.US)
    val rawTarget = parts[1]
    val version = parts[2]

    if (method == "CONNECT") {
      return parseConnectTarget(rawTarget)
    }

    // Absolute URI in proxy request: e.g. GET http://example.com:8080/path?a=1 HTTP/1.1
    if (rawTarget.startsWith("http://", ignoreCase = true) ||
      rawTarget.startsWith("https://", ignoreCase = true)
    ) {
      val uri = runCatching { URI(rawTarget) }.getOrNull() ?: return null
      val host = uri.host ?: return null
      val defaultPort = if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
      val port = if (uri.port > 0) uri.port else defaultPort
      val path = buildString {
        append(uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/")
        if (!uri.rawQuery.isNullOrEmpty()) {
          append("?").append(uri.rawQuery)
        }
      }
      return ProxyTarget(
        host = host,
        port = port,
        rewrittenRequestLine = "$method $path $version"
      )
    }

    // Origin-form request with Host header
    val hostHeader = headers.firstOrNull {
      it.startsWith("Host:", ignoreCase = true)
    }?.substringAfter(':')?.trim() ?: return null

    val hostTarget = parseConnectTarget(hostHeader)
    val port = if (hostHeader.contains(":")) {
      hostTarget?.port ?: return null
    } else {
      80
    }
    val host = hostTarget?.host ?: return null
    return ProxyTarget(
      host = host,
      port = port,
      rewrittenRequestLine = "$method $rawTarget $version"
    )
  }

  fun parseIpv4Bytes(bytes: ByteArray): String? {
    if (bytes.size != 4) return null
    return runCatching { InetAddress.getByAddress(bytes).hostAddress }.getOrNull()
  }

  fun parseIpv6Bytes(bytes: ByteArray): String? {
    if (bytes.size != 16) return null
    return runCatching { InetAddress.getByAddress(bytes).hostAddress }.getOrNull()
  }

  fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
      safe < 1024L -> "$safe B"
      safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024.0)
      safe < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f MB", safe / (1024.0 * 1024.0))
      else -> String.format(Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
    }
  }

  fun formatDuration(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
      String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
  }
}
