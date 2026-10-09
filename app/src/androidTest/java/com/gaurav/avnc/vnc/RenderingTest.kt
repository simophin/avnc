package com.gaurav.avnc.vnc

import android.opengl.EGL14
import android.opengl.GLES20.*
import android.opengl.Matrix
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.TestServer
import com.gaurav.avnc.ui.vnc.gl.Frame
import com.gaurav.avnc.ui.vnc.gl.Program
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/** Runs the production texture upload and shaders on the device's GPU against a local RFB server. */
@RunWith(AndroidJUnit4::class)
class RenderingTest {
    private class GlSession(val width: Int, val height: Int) : AutoCloseable {
        private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: android.opengl.EGLContext
        private val surface: android.opengl.EGLSurface
        private val frame: Frame
        private val program: Program
        private var initialized = false
        private val cursorFrame: Frame
        private var cursorInitialized = false

        init {
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE)
            check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, IntArray(1), 0))
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0)
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
            glViewport(0, 0, width, height)
            frame = Frame()
            cursorFrame = Frame()
            program = Program()
        }

        fun draw(client: VncClient, fbWidth: Int = width, fbHeight: Int = height) {
            val projection = FloatArray(16)
            Matrix.orthoM(projection, 0, 0f, fbWidth.toFloat(), 0f, fbHeight.toFloat(), -1f, 1f)
            program.useProgram()
            program.setUniforms(projection)
            frame.updateFbSize(fbWidth.toFloat(), fbHeight.toFloat())
            frame.bind(program)
            initialized = client.uploadFrameTexture(force = !initialized)
            frame.draw()
            glFinish() // Include completed GPU work, not just command submission.
            assertEquals(GL_NO_ERROR, glGetError())
        }

        fun drawCursor(client: VncClient) {
            cursorFrame.updateFbSize(width.toFloat(), height.toFloat())
            cursorFrame.bind(program)
            cursorInitialized = client.uploadCursorTexture(force = !cursorInitialized)
            cursorFrame.draw()
            glFinish()
            assertEquals(GL_NO_ERROR, glGetError())
        }

        fun pixel(x: Int, y: Int): Int {
            val pixel = ByteBuffer.allocateDirect(4)
            glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel)
            return (pixel[0].toInt() and 255 shl 16) or
                (pixel[1].toInt() and 255 shl 8) or (pixel[2].toInt() and 255)
        }

        override fun close() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private fun withClient(width: Int, height: Int, block: (TestServer, VncClient) -> Unit) {
        val server = TestServer(width = width, height = height)
        val client = VncClient(VncClientTest.TestObserver())
        try {
            client.configure(0, true, 5, false)
            server.start()
            client.connect(server.host, server.port)
            client.processServerMessage()
            block(server, client)
        } finally {
            client.cleanup()
            server.stop()
        }
    }

    @Test
    fun partialUpdatesAndContextRecreation() = withClient(64, 64) { server, client ->
        GlSession(64, 64).use { gl ->
            gl.draw(client)
            server.sendRectangle(0, 0, 64, 64, 0xff123456.toInt())
            client.processServerMessage()
            gl.draw(client)
            assertEquals(0x123456, gl.pixel(0, 0))
            server.sendRectangle(7, 9, 3, 5, 0xffabcdef.toInt())
            client.processServerMessage()
            gl.draw(client)
            assertEquals(0xabcdef, gl.pixel(8, 10))
            assertEquals(0x123456, gl.pixel(6, 10))
            gl.draw(client) // Redraw with no new pixels.
            assertEquals(0xabcdef, gl.pixel(8, 10))
        }
        GlSession(64, 64).use { gl ->
            gl.draw(client) // A fresh GL context must upload even without a remote update.
            assertEquals(0xabcdef, gl.pixel(8, 10))
        }
    }

    @Test
    fun coalescedUpdatesCopyRectTightAndResize() = withClient(64, 64) { server, client ->
        GlSession(64, 64).use { gl ->
            gl.draw(client)
            server.sendRectangle(3, 5, 4, 6, 0xff112233.toInt())
            client.processServerMessage()
            server.sendRectangle(40, 42, 7, 9, 0xffaabbcc.toInt())
            client.processServerMessage()
            gl.draw(client) // Both updates must survive a single upload.
            assertEquals(0x112233, gl.pixel(4, 6))
            assertEquals(0xaabbcc, gl.pixel(41, 43))
            assertEquals(0, gl.pixel(20, 20))
            server.sendCopyRectangle(3, 5, 15, 17, 4, 6)
            client.processServerMessage()
            gl.draw(client)
            assertEquals(0x112233, gl.pixel(16, 18))
            server.sendTightFill(25, 27, 5, 7, 0x665544)
            client.processServerMessage()
            gl.draw(client)
            assertEquals(0x665544, gl.pixel(26, 28))
            server.sendEmptyUpdate()
            client.processServerMessage()
            gl.draw(client)
            assertEquals(0x665544, gl.pixel(26, 28))
            server.sendResize(32, 32)
            client.processServerMessage()
            client.processServerMessage() // Initial pixels requested for the resized desktop.
            gl.draw(client, 32, 32)
            assertEquals(0, gl.pixel(26, 28))
            server.sendRectangle(0, 0, 32, 32, 0xffabcdef.toInt())
            client.processServerMessage()
            gl.draw(client, 32, 32)
            assertEquals(0xabcdef, gl.pixel(26, 28))
        }
    }

    @Test
    fun cursorShapeChangesAndContextRecreation() = withClient(64, 64) { server, client ->
        GlSession(64, 64).use { gl ->
            gl.draw(client)
            gl.drawCursor(client) // Default cursor
            for ((size, rgb) in listOf(8 to 0x112233, 8 to 0xaabbcc, 16 to 0x665544)) {
                server.sendCursor(size, size, 0xff000000.toInt() or rgb)
                client.processServerMessage()
                gl.drawCursor(client)
                assertEquals(rgb, gl.pixel(32, 32))
                gl.drawCursor(client) // Unchanged shape
                assertEquals(rgb, gl.pixel(32, 32))
            }
        }
        GlSession(64, 64).use { gl ->
            gl.draw(client)
            gl.drawCursor(client)
            assertEquals(0x665544, gl.pixel(32, 32))
        }
    }

    @Test
    fun renderingLatency() = withClient(1920, 1080) { server, client ->
        GlSession(1920, 1080).use { gl ->
            gl.draw(client)
            for (mode in listOf("unchanged", "small", "full")) {
                val renderTimes = mutableListOf<Double>()
                val serverTimes = mutableListOf<Double>()
                repeat(70) { i ->
                    if (mode != "unchanged") {
                        val w = if (mode == "small") 32 else 1920
                        val h = if (mode == "small") 32 else 1080
                        server.sendRectangle(0, 0, w, h, 0xff000000.toInt() or (i * 12345))
                        client.processServerMessage()
                    }
                    val start = System.nanoTime()
                    gl.draw(client)
                    val end = System.nanoTime()
                    if (i >= 10) {
                        renderTimes.add((end - start) / 1e6)
                        if (mode != "unchanged") serverTimes.add((end - server.lastFrameSentNanos) / 1e6)
                    }
                }
                fun stats(values: List<Double>): String {
                    val sorted = values.sorted()
                    return "p50=%.3fms p95=%.3fms".format(sorted[sorted.size / 2], sorted[(sorted.size * 0.95).toInt()])
                }
                Log.i("RenderingLatency", "$mode upload+draw+GPU ${stats(renderTimes)}" +
                    if (serverTimes.isEmpty()) "" else "; server-write-to-GPU ${stats(serverTimes)}")
            }
        }
    }
}
