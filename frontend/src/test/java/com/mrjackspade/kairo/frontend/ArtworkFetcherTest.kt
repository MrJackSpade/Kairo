package com.mrjackspade.kairo.frontend

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

/** Kairo#35: a throttled artwork request is told apart from a failed one, with when to resume. */
class ArtworkFetcherTest {
    // A one-connection-at-a-time HTTP server answering every request with status, headers and,
    // for 200, body.
    private lateinit var server: ServerSocket
    @Volatile private var status = 200
    @Volatile private var headers = mapOf<String, String>()
    private val body = byteArrayOf(1, 2, 3)

    @Before
    fun start() {
        server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        Thread {
            while (true) {
                // Closing the server in stop() ends accept() with a SocketException.
                val socket = try { server.accept() } catch (closed: SocketException) { break }
                socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    while (reader.readLine().orEmpty().isNotEmpty()) {}
                    val payload = if (status == 200) body else ByteArray(0)
                    val head = buildString {
                        append("HTTP/1.1 $status Status\r\n")
                        headers.forEach { (name, value) -> append("$name: $value\r\n") }
                        append("Content-Length: ${payload.size}\r\nConnection: close\r\n\r\n")
                    }
                    it.getOutputStream().apply { write(head.toByteArray()); write(payload); flush() }
                }
            }
        }.apply { isDaemon = true; start() }
    }

    @After
    fun stop() = server.close()

    private val url get() = "http://127.0.0.1:${server.localPort}/image.png"

    private fun fetch() = ArtworkFetcher(1024).fetch(url, AtomicBoolean(false))

    private fun throttled(): ArtworkFetcher.Throttled {
        try {
            fetch()
        } catch (throttled: ArtworkFetcher.Throttled) {
            return throttled
        }
        fail("the request was not throttled")
        throw AssertionError()
    }

    @Test
    fun anImageIsFetched() = assertArrayEquals(body, fetch())

    @Test
    fun tooManyRequestsResumesWhenRetryAfterSays() {
        status = 429
        headers = mapOf("Retry-After" to "30")
        val before = System.currentTimeMillis()
        val retryAt = throttled().retryAt!!
        assertTrue("$retryAt is about 30 s on", retryAt in before + 29_000..System.currentTimeMillis() + 31_000)
    }

    @Test
    fun tooManyRequestsWithoutRetryAfterLeavesTheWaitToTheCaller() {
        status = 429
        assertNull(throttled().retryAt)
    }

    @Test
    fun anExhaustedRateLimitResumesAtItsReset() {
        val reset = System.currentTimeMillis() / 1000 + 60
        status = 403
        headers = mapOf("x-ratelimit-remaining" to "0", "x-ratelimit-reset" to "$reset")
        assertEquals(reset * 1000, throttled().retryAt)
    }

    @Test
    fun aForbiddenImageIsAFailureNotAThrottle() {
        status = 403
        try {
            fetch()
            fail("a 403 without rate-limit headers was fetched")
        } catch (throttled: ArtworkFetcher.Throttled) {
            fail("a 403 without rate-limit headers was taken for a throttle")
        } catch (failure: IllegalArgumentException) {
            assertEquals("Image server returned 403", failure.message)
        }
    }

    @Test
    fun theRemainingRateLimitPacesTheNextRequest() {
        // 2 requests left over the next 2 s: the next one waits about 1 s.
        val fetcher = ArtworkFetcher(1024)
        headers = mapOf("x-ratelimit-remaining" to "2", "x-ratelimit-reset" to "${System.currentTimeMillis() / 1000 + 2}")
        fetcher.fetch(url, AtomicBoolean(false))
        headers = mapOf()
        val before = System.currentTimeMillis()
        fetcher.fetch(url, AtomicBoolean(false))
        assertTrue("waited ${System.currentTimeMillis() - before} ms", System.currentTimeMillis() - before >= 400)
    }
}
