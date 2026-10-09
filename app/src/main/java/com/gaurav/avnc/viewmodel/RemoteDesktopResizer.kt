/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.gaurav.avnc.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Coalesces layout changes without postponing an unchanged pending size. */
internal class RemoteDesktopResizer(
        private val scope: CoroutineScope,
        private val settleMillis: Long = 150,
        private val send: (Int, Int) -> Unit
) {
    private var job: Job? = null
    private var pending: Pair<Int, Int>? = null
    private var sent: Pair<Int, Int>? = null

    fun request(width: Int, height: Int) {
        val size = width to height
        if (size == pending) return
        job?.cancel()
        pending = null
        if (width <= 0 || height <= 0 || size == sent) return
        pending = size
        job = scope.launch {
            delay(settleMillis)
            send(width, height)
            sent = size
            pending = null
        }
    }

    fun reset() {
        job?.cancel()
        pending = null
        sent = null
    }
}
