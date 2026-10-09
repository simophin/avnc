/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gaurav.avnc.session

import com.gaurav.avnc.TestServer
import com.gaurav.avnc.model.ServerProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RemoteSessionTest {
    @Test
    fun directVncConnectionStillWorks() {
        val server = TestServer()
        val observer = mockk<RemoteSession.Observer>(relaxed = true)
        val connected = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        every { observer.onConnected(any(), any()) } answers { connected.countDown() }
        every { observer.onDisconnected() } answers { disconnected.countDown() }
        val session = RemoteSession(observer)

        server.start()
        try {
            session.start(ServerProfile(host = server.host, port = server.port))
            assertTrue("Direct VNC should connect", connected.await(10, TimeUnit.SECONDS))
        } finally {
            session.stop()
            server.stop()
            assertTrue("Session should stop", disconnected.await(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun legacySshProfileDoesNotConnectDirectly() {
        val observer = mockk<RemoteSession.Observer>(relaxed = true)
        val finished = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        every { observer.onConnectionError(any()) } answers { error.set(firstArg()) }
        every { observer.onDisconnected() } answers { finished.countDown() }

        RemoteSession(observer).start(ServerProfile(host = "localhost", channelType = 24))

        assertTrue("Session should reject the obsolete transport", finished.await(10, TimeUnit.SECONDS))
        assertTrue(error.get() is IllegalStateException)
        assertEquals("Unsupported transport: 24. Edit this profile to use direct VNC.", error.get().message)
        verify(exactly = 0) { observer.onConnected(any(), any()) }
    }
}
