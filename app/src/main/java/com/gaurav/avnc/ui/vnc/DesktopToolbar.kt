/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.gaurav.avnc.ui.vnc

import android.content.res.Configuration
import android.os.Build
import android.view.InputDevice
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible

/** Mouse-accessible controls in desktop windows, kept outside the remote viewport. */
class DesktopToolbar(private val activity: VncActivity) {
    private val binding = activity.binding.desktopToolbar
    private val viewModel = activity.viewModel

    fun initialize(toggleFullscreen: () -> Unit) {
        binding.fitBtn.setOnClickListener { viewModel.fitDesktopToViewport() }
        binding.zoomInBtn.setOnClickListener { zoom(1.25f) }
        binding.zoomOutBtn.setOnClickListener { zoom(0.8f) }
        binding.clipboardBtn.setOnClickListener { viewModel.sendClipboardText() }
        binding.fullscreenBtn.setOnClickListener { toggleFullscreen() }
        binding.moreBtn.setOnClickListener { activity.toolbar.open() }
        binding.disconnectBtn.setOnClickListener { activity.finish() }
        listOf(binding.fitBtn, binding.zoomInBtn, binding.zoomOutBtn, binding.clipboardBtn,
               binding.fullscreenBtn, binding.moreBtn, binding.disconnectBtn).forEach {
            TooltipCompat.setTooltipText(it, it.contentDescription)
            it.isFocusable = true
        }
        viewModel.inPiPMode.observe(activity) { updateVisibility() }
    }

    private fun zoom(factor: Float) {
        val frame = viewModel.frameState
        viewModel.updateZoom(factor, frame.vpWidth / 2, frame.vpHeight / 2)
    }

    fun updateVisibility(insets: WindowInsetsCompat? = null) {
        val config = activity.resources.configuration
        val hasMouse = InputDevice.getDeviceIds().any { id ->
            InputDevice.getDevice(id)?.supportsSource(InputDevice.SOURCE_MOUSE) == true
        }
        val caption = insets?.isVisible(WindowInsetsCompat.Type.captionBar()) == true
        val desktopInput = config.keyboard != Configuration.KEYBOARD_NOKEYS || hasMouse
        val windowed = Build.VERSION.SDK_INT >= 24 && activity.isInMultiWindowMode
        binding.root.isVisible = viewModel.connected && viewModel.inPiPMode.value != true &&
                (caption || (config.screenWidthDp >= 600 && (windowed || desktopInput)))
        activity.binding.openToolbarBtn.isVisible = !binding.root.isVisible &&
                viewModel.connected && viewModel.pref.viewer.toolbarOpenWithButton
    }
}
