/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Accessibility service used to capture system keyboard shortcuts.
 *
 * Android reserves some key combinations (e.g. Alt+Tab, Home, Super/Meta shortcuts)
 * and handles them before they are dispatched to apps. So these shortcuts normally
 * trigger local actions instead of being sent to the VNC server.
 *
 * But key events are offered to accessibility services (with
 * [AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS]) before the system
 * acts on these shortcuts. This service uses that mechanism to capture key events
 * and forward them to the active VNC session.
 *
 * The service is completely passive unless the user explicitly enables it in
 * system Accessibility settings, AND a VNC session is currently in foreground
 * ([keyEventReceiver] is only set while [com.gaurav.avnc.ui.vnc.VncActivity]
 * window has input focus). In all other cases key events are left untouched.
 */
class KeyCaptureService : AccessibilityService() {

    companion object {
        /**
         * Receiver for captured key events.
         * Runs on the main thread. Returning true consumes the event.
         */
        var keyEventReceiver: ((KeyEvent) -> Boolean)? = null

        /**
         * Whether this service is currently enabled in system Accessibility settings.
         */
        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                it.resolveInfo.serviceInfo.let { s ->
                    s.packageName == context.packageName && s.name == KeyCaptureService::class.java.name
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return keyEventReceiver?.invoke(event) ?: false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, we are only interested in key events
    }

    override fun onInterrupt() {
        // Not used
    }
}
