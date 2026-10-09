/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.gaurav.avnc.viewmodel

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteDesktopResizerTest {
    @Test
    fun resizeDragSendsOnlyFinalSize() = runBlocking {
        val sent = mutableListOf<Pair<Int, Int>>()
        val resizer = RemoteDesktopResizer(this, 1) { w, h -> sent.add(w to h) }
        resizer.request(800, 600)
        resizer.request(900, 600)
        resizer.request(1000, 700)
        delay(20)
        assertEquals(listOf(1000 to 700), sent)
        resizer.request(1000, 700)
        delay(20)
        assertEquals(1, sent.size)
    }

    @Test
    fun returningToSentSizeCancelsPendingResize() = runBlocking {
        val sent = mutableListOf<Pair<Int, Int>>()
        val resizer = RemoteDesktopResizer(this, 1) { w, h -> sent.add(w to h) }
        resizer.request(800, 600)
        delay(20)
        resizer.request(900, 600)
        resizer.request(800, 600)
        delay(20)
        assertEquals(listOf(800 to 600), sent)
    }

    @Test
    fun invalidViewportCancelsPendingResize() = runBlocking {
        val sent = mutableListOf<Pair<Int, Int>>()
        val resizer = RemoteDesktopResizer(this, 1) { w, h -> sent.add(w to h) }
        resizer.request(800, 600)
        resizer.request(0, 600)
        delay(20)
        assertEquals(emptyList<Pair<Int, Int>>(), sent)
    }

    @Test
    fun resetCancelsPendingRequestAndAllowsSameSizeInNewSession() = runBlocking {
        val sent = mutableListOf<Pair<Int, Int>>()
        val resizer = RemoteDesktopResizer(this, 1) { w, h -> sent.add(w to h) }
        resizer.request(800, 600)
        delay(20)
        resizer.request(900, 600)
        resizer.reset()
        resizer.request(800, 600)
        delay(20)
        assertEquals(listOf(800 to 600, 800 to 600), sent)
    }
}
