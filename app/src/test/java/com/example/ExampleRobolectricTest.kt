package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.proxy.ProxyProtocolParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("پیوند نت", appName)
  }

  @Test
  fun `parse HTTP CONNECT authority`() {
    val target = ProxyProtocolParser.parseConnectTarget("example.com:443")
    assertNotNull(target)
    assertEquals("example.com", target?.host)
    assertEquals(443, target?.port)

    val ipv6 = ProxyProtocolParser.parseConnectTarget("[2001:db8::1]:8443")
    assertNotNull(ipv6)
    assertEquals("2001:db8::1", ipv6?.host)
    assertEquals(8443, ipv6?.port)

    assertNull(ProxyProtocolParser.parseConnectTarget(":443"))
  }

  @Test
  fun `parse HTTP absolute URI request line`() {
    val target = ProxyProtocolParser.parseHttpRequestLine(
      "GET http://api.example.com:8080/v1/ping?ok=1 HTTP/1.1",
      listOf("Host: api.example.com:8080")
    )
    assertNotNull(target)
    assertEquals("api.example.com", target?.host)
    assertEquals(8080, target?.port)
    assertEquals("GET /v1/ping?ok=1 HTTP/1.1", target?.rewrittenRequestLine)
  }

  @Test
  fun `format bytes and duration`() {
    assertEquals("512 B", ProxyProtocolParser.formatBytes(512))
    assertEquals("2.0 KB", ProxyProtocolParser.formatBytes(2048))
    assertEquals("01:05", ProxyProtocolParser.formatDuration(65_000))
    assertEquals("01:00:05", ProxyProtocolParser.formatDuration(3_605_000))
  }
}
