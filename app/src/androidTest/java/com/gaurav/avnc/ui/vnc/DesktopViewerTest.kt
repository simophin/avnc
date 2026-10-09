/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.gaurav.avnc.ui.vnc

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.VncSessionTest
import com.gaurav.avnc.util.EdgeToEdgeWrapperLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopViewerTest : VncSessionTest() {
    private fun captionInsets() = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.captionBar(), Insets.of(0, 40, 0, 0))
            .setVisible(WindowInsetsCompat.Type.captionBar(), true)
            .build()

    @Test
    fun fitUsesViewportWithDifferentRemoteOrientation() {
        vncSession.run {
            vncSession.onActivity { activity ->
                val vm = activity.viewModel
                vm.frameState.setWindowSize(1000f, 600f)
                vm.frameState.setViewportSize(1000f, 552f)
                vm.frameState.setFramebufferSize(600f, 1000f)
                vm.fitDesktopToViewport()
                assertEquals(0.552f, vm.frameState.scale, 0.001f)
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun fullscreenWrapperReservesCaptionExactlyOnce() {
        vncSession.run {
            vncSession.onActivity { activity ->
                val wrapper = activity.findViewById<View>(android.R.id.content)
                        .let { (it as android.view.ViewGroup).getChildAt(0) as EdgeToEdgeWrapperLayout }
                val insets = captionInsets()
                wrapper.onApplyWindowInsets(insets.toWindowInsets()!!)
                assertEquals(40, wrapper.paddingTop)
                activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets()!!)
                // Caption padding already belongs to the wrapper; viewer must not duplicate it.
                assertEquals(0, activity.binding.viewerRoot.paddingTop)
            }
        }
    }
}
