package com.example.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Local multi-protocol proxy server (HTTP/HTTPS CONNECT + SOCKS5) running in Android user-space.
 * All outbound sockets opened by this server are automatically routed by Android OS through
 * the device's active VPN interface, allowing a tethered PC (via USB/ADB or Wi-Fi Direct)
 * to send traffic through the phone's VPN without root access.
 */
class ProxyServer(
  private val port: Int,
  private val onError: (String) -> Unit
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var acceptJob: Job? = null
  private var serverSocket: ServerSocket? = null
  private val openSockets = Collections.synchronizedSet(mutableSetOf<Socket>())

  val activeConnections = AtomicInteger(0)
  val bytesSent = AtomicLong(0L)
  val bytesReceived = AtomicLong(0L)

  @Volatile
  var isRunning: Boolean = false
    private set

  fun start() {
    if (isRunning) return
    val server = ServerSocket().apply {
      reuseAddress = true
      bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 128)
    }
    serverSocket = server
    isRunning = true

    acceptJob = scope.launch {
      while (isActive && !server.isClosed) {
        try {
          val client = server.accept()
          client.tcpNoDelay = true
          client.soTimeout = IDLE_TIMEOUT_MS
          openSockets.add(client)
          launch {
            handleClient(client)
          }
        } catch (e: Exception) {
          if (isRunning && !server.isClosed) {
            onError(e.message ?: "خطا در پذیرش اتصال")
          }
          break
        }
      }
    }
  }

  fun stop() {
    isRunning = false
    runCatching { serverSocket?.close() }
    serverSocket = null
    acceptJob?.cancel()
    acceptJob = null
    synchronized(openSockets) {
      openSockets.forEach { socket -> runCatching { socket.close() } }
      openSockets.clear()
    }
    scope.cancel()
  }

  private suspend fun handleClient(client: Socket) {
    activeConnections.incrementAndGet()
    var remote: Socket? = null
    try {
      val clientIn = client.getInputStream()
      val clientOut = client.getOutputStream()

      val firstByte = clientIn.read()
      if (firstByte == -1) return

      if (firstByte == 0x05) {
        remote = handleSocks5Handshake(clientIn, clientOut) ?: return
      } else {
        remote = handleHttpHandshake(firstByte, clientIn, clientOut) ?: return
      }

      remote.let { targetSocket ->
        openSockets.add(targetSocket)
        relayBidirectional(client, targetSocket)
      }
    } catch (_: Exception) {
      // Client closed or timed out cleanly
    } finally {
      activeConnections.decrementAndGet()
      remote?.let {
        openSockets.remove(it)
        runCatching { it.close() }
      }
      openSockets.remove(client)
      runCatching { client.close() }
    }
  }

  private fun handleSocks5Handshake(
    clientIn: InputStream,
    clientOut: OutputStream
  ): Socket? {
    val nMethods = clientIn.read()
    if (nMethods <= 0) return null
    val methods = readExactBytes(clientIn, nMethods)
    val supportsNoAuth = methods.any { it.toInt() == 0x00 }
    if (!supportsNoAuth) {
      clientOut.write(byteArrayOf(0x05, 0xFF.toByte()))
      clientOut.flush()
      return null
    }
    // Reply: VER=5, METHOD=0 (No authentication required)
    clientOut.write(byteArrayOf(0x05, 0x00))
    clientOut.flush()

    // Read SOCKS5 request: VER, CMD, RSV, ATYP
    val header = readExactBytes(clientIn, 4)
    if (header[0].toInt() != 0x05 || header[1].toInt() != 0x01) {
      // Only CONNECT (0x01) is supported
      clientOut.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
      clientOut.flush()
      return null
    }

    val host: String? = when (header[3].toInt() and 0xFF) {
      0x01 -> ProxyProtocolParser.parseIpv4Bytes(readExactBytes(clientIn, 4))
      0x03 -> {
        val len = clientIn.read()
        if (len <= 0) null else String(readExactBytes(clientIn, len), StandardCharsets.UTF_8)
      }
      0x04 -> ProxyProtocolParser.parseIpv6Bytes(readExactBytes(clientIn, 16))
      else -> null
    }

    val portBytes = readExactBytes(clientIn, 2)
    val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

    if (host.isNullOrBlank() || targetPort !in 1..65535) {
      clientOut.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
      clientOut.flush()
      return null
    }

    val targetSocket = Socket()
    return try {
      targetSocket.tcpNoDelay = true
      targetSocket.soTimeout = IDLE_TIMEOUT_MS
      targetSocket.connect(InetSocketAddress(host, targetPort), CONNECT_TIMEOUT_MS)
      // Success reply
      clientOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
      clientOut.flush()
      targetSocket
    } catch (_: Exception) {
      runCatching { targetSocket.close() }
      runCatching {
        clientOut.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        clientOut.flush()
      }
      null
    }
  }

  private fun handleHttpHandshake(
    firstByte: Int,
    clientIn: InputStream,
    clientOut: OutputStream
  ): Socket? {
    val rawHeaders = readHttpHeaderBlock(firstByte, clientIn) ?: return null
    val lines = rawHeaders.split("\r\n").filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null

    val requestLine = lines.first()
    val headers = lines.drop(1)
    val target = ProxyProtocolParser.parseHttpRequestLine(requestLine, headers) ?: run {
      runCatching {
        clientOut.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".toByteArray())
        clientOut.flush()
      }
      return null
    }

    val isConnect = requestLine.startsWith("CONNECT ", ignoreCase = true)
    val targetSocket = Socket()
    return try {
      targetSocket.tcpNoDelay = true
      targetSocket.soTimeout = IDLE_TIMEOUT_MS
      targetSocket.connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS)

      if (isConnect) {
        clientOut.write(
          "HTTP/1.1 200 Connection Established\r\nProxy-Agent: PeyvandNet/1.0\r\n\r\n"
            .toByteArray(StandardCharsets.US_ASCII)
        )
        clientOut.flush()
      } else {
        // Forward rewritten HTTP request line & clean headers to upstream server
        val rewrittenBlock = buildString {
          append(target.rewrittenRequestLine ?: requestLine).append("\r\n")
          for (h in headers) {
            if (!h.startsWith("Proxy-Connection:", ignoreCase = true)) {
              append(h).append("\r\n")
            }
          }
          append("\r\n")
        }.toByteArray(StandardCharsets.ISO_8859_1)

        val remoteOut = targetSocket.getOutputStream()
        remoteOut.write(rewrittenBlock)
        remoteOut.flush()
        bytesSent.addAndGet(rewrittenBlock.size.toLong())
      }
      targetSocket
    } catch (_: Exception) {
      runCatching { targetSocket.close() }
      runCatching {
        clientOut.write(
          "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n"
            .toByteArray(StandardCharsets.US_ASCII)
        )
        clientOut.flush()
      }
      null
    }
  }

  private suspend fun relayBidirectional(client: Socket, remote: Socket) = coroutineScope {
    val uploadJob = launch(Dispatchers.IO) {
      copyStream(
        input = client.getInputStream(),
        output = remote.getOutputStream(),
        counter = bytesSent
      )
      runCatching { remote.shutdownOutput() }
    }
    val downloadJob = launch(Dispatchers.IO) {
      copyStream(
        input = remote.getInputStream(),
        output = client.getOutputStream(),
        counter = bytesReceived
      )
      runCatching { client.shutdownOutput() }
    }
    uploadJob.join()
    downloadJob.join()
  }

  private fun copyStream(
    input: InputStream,
    output: OutputStream,
    counter: AtomicLong
  ) {
    val buffer = ByteArray(BUFFER_SIZE)
    try {
      while (isRunning) {
        val read = input.read(buffer)
        if (read == -1) break
        if (read > 0) {
          output.write(buffer, 0, read)
          output.flush()
          counter.addAndGet(read.toLong())
        }
      }
    } catch (_: Exception) {
      // Stream terminated
    }
  }

  private fun readExactBytes(input: InputStream, count: Int): ByteArray {
    val result = ByteArray(count)
    var offset = 0
    while (offset < count) {
      val read = input.read(result, offset, count - offset)
      if (read == -1) throw EOFException("Unexpected EOF reading $count bytes")
      offset += read
    }
    return result
  }

  private fun readHttpHeaderBlock(firstByte: Int, input: InputStream): String? {
    val buffer = ByteArrayOutputStream(512)
    buffer.write(firstByte)
    var state = 0
    var totalRead = 1
    while (totalRead < MAX_HEADER_BYTES) {
      val b = input.read()
      if (b == -1) return null
      buffer.write(b)
      totalRead++
      state = when {
        state == 0 && b == '\r'.code -> 1
        state == 1 && b == '\n'.code -> 2
        state == 2 && b == '\r'.code -> 3
        state == 3 && b == '\n'.code -> 4
        b == '\r'.code -> 1
        else -> 0
      }
      if (state == 4) {
        return buffer.toString(StandardCharsets.ISO_8859_1.name())
      }
    }
    return null
  }

  companion object {
    private const val BUFFER_SIZE = 16 * 1024
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val IDLE_TIMEOUT_MS = 120_000
    private const val MAX_HEADER_BYTES = 16 * 1024
  }
}
