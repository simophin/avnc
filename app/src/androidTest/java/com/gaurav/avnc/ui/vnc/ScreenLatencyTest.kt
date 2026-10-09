package com.gaurav.avnc.ui.vnc

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.WindowManager
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.TestServer
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Includes the real receiver thread, Renderer, and GLSurfaceView buffer submission. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class ScreenLatencyTest {
    @Test
    fun smallUpdateVisibleInViewerSurface() {
        val server = TestServer(width = 1920, height = 1080)
        var scenario: ActivityScenario<VncActivity>? = null
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            targetPrefs.edit { putBoolean("run_info_has_shown_viewer_help", true) }
            server.start()
            scenario = ActivityScenario.launch(createVncIntent(targetContext,
                ServerProfile(host = server.host, port = server.port)))
            scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            lateinit var view: FrameView
            lateinit var sample: Rect
            pollingAssert {
                scenario.onActivity { activity ->
                    assertTrue(activity.viewModel.connected)
                    view = activity.binding.frameView
                    val state = activity.viewModel.frameState.getSnapshot()
                    assertTrue(state.fbWidth == 1920f && state.scale > 0f)
                    val x = (state.frameX + 976 * state.scale).toInt()
                    val y = (state.frameY + 556 * state.scale).toInt()
                    assertTrue("Sample ($x,$y) outside ${view.width}x${view.height}; state=$state",
                        x in 0 until view.width && y in 0 until view.height)
                    sample = Rect(x, y, x + 1, y + 1)
                }
            }
            for (mode in listOf("small", "full")) {
                val times = mutableListOf<Double>()
                repeat(35) { i ->
                    val rgb = if (i % 2 == 0) 0x2255aa else 0xaa5522
                    if (mode == "small")
                        server.sendRectangle(960, 540, 32, 32, 0xff000000.toInt() or rgb)
                    else
                        server.sendRectangle(0, 0, 1920, 1080, 0xff000000.toInt() or rgb)
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    var observed = false
                    while (System.nanoTime() < deadline) {
                        val done = CountDownLatch(1)
                        var result = -1
                        PixelCopy.request(view, sample, bitmap, { status ->
                            result = status
                            done.countDown()
                        }, Handler(Looper.getMainLooper()))
                        assertTrue("PixelCopy callback timed out", done.await(2, TimeUnit.SECONDS))
                        if (result == PixelCopy.SUCCESS && bitmap.getPixel(0, 0) and 0xffffff == rgb) {
                            observed = true
                            if (i >= 5) times.add((System.nanoTime() - server.lastFrameSentNanos) / 1e6)
                            break
                        }
                    }
                    assertTrue("Updated pixels did not reach the viewer surface", observed)
                    assertEquals(rgb, bitmap.getPixel(0, 0) and 0xffffff)
                }
                val sorted = times.sorted()
                Log.i("RenderingLatency", "viewer-surface $mode server-write-to-PixelCopy " +
                    "p50=%.3fms p95=%.3fms n=%d".format(sorted[sorted.size / 2], sorted[28], sorted.size))
            }
        } finally {
            scenario?.close()
            server.stop()
            bitmap.recycle()
        }
    }
}
