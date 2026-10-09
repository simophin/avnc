/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.session

import android.util.Log
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.broadcastWoLPackets
import com.gaurav.avnc.vnc.VncClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Controls a remote session.
 *
 * Session is started with a call to [start]. It will kickoff a background thread
 * to handle the entire session. [observer] is notified about events by this thread.
 *
 * It can be stopped by calling [stop]. It will be automatically stopped when
 * remote server closes the connection  or an error occurs.
 */
class RemoteSession(private val observer: Observer) {

    interface Observer : VncClient.Observer {
        fun onConnecting()
        fun onConnected(vncClient: VncClient, messenger: Messenger)
        fun onDisconnected()

        fun onConnectionError(error: Throwable)
        fun onWakeOnLanBroadcastError(e: Throwable)
    }


    private val id = sessionCounter.incrementAndGet()
    private val tag = "RemoteSession[$id]"

    private var vncClient: VncClient? = null
    private var messenger: Messenger? = null
    private var sessionThread: Thread? = null

    @Volatile
    private var stopRequested = false

    /**
     * Starts remote session for given [profile].
     * For now, [start] can be called only once for an instance.
     */
    @Synchronized
    fun start(profile: ServerProfile) {
        check(sessionThread == null) { "Session re-start is not yet supported" }

        log("Requesting session start")
        stopRequested = false
        sessionThread = startSession(profile)
    }

    @Synchronized
    fun stop() {
        if (sessionThread?.isAlive != true)
            return // Already stopped

        log("Requesting session stop")
        stopRequested = true
    }


    /******************************************************************************************/

    private fun startSession(profile: ServerProfile): Thread {
        return thread(name = tag) {
            log("Session started")

            runCatching {
                startConnection(profile)
            }.let {
                handleConnectionEnd(it)
            }

            stopSession()
        }
    }


    private fun startConnection(profile: ServerProfile) {
        log("Preparing clients")
        vncClient = VncClient(observer)
        configureClient(vncClient!!, profile)

        handleWoL(profile)
        connect(profile, vncClient!!)
    }


    private fun connect(profile: ServerProfile, vncClient: VncClient) {
        log("Connecting to server")
        observer.onConnecting()

        check(profile.channelType == ServerProfile.CHANNEL_TCP) {
            "Unsupported transport: ${profile.channelType}. Edit this profile to use direct VNC."
        }
        vncClient.connect(profile.host, profile.port)

        messenger = Messenger(vncClient)

        log("Connected to server")
        observer.onConnected(vncClient, messenger!!)

        messageLoop(vncClient)
    }

    private fun messageLoop(vncClient: VncClient) {
        log("Running message loop")
        while (!stopRequested)
            vncClient.processServerMessage()
    }

    private fun stopSession() {
        messenger?.shutdown()
        vncClient?.cleanup()

        messenger = null
        vncClient = null
        log("Session stopped")
    }


    /*********************************************************************************/

    private fun configureClient(vncClient: VncClient, profile: ServerProfile) {
        vncClient.configure(profile.securityType, true  /* Hardcoded to true */,
                            profile.imageQuality, profile.useRawEncoding)

        vncClient.setInputDisabled(profile.viewMode == ServerProfile.VIEW_MODE_NO_INPUT)
        vncClient.setFrameBufferUpdatesPaused(profile.viewMode == ServerProfile.VIEW_MODE_NO_VIDEO)
    }

    private fun handleWoL(profile: ServerProfile) {
        if (!profile.enableWol)
            return

        log("Sending WoL magic packet")
        runCatching { broadcastWoLPackets(profile.wolMAC, profile.wolBroadcastAddress, profile.wolPort) }
                .onFailure {
                    logE("WoL broadcast error: ${it.message}", it)
                    observer.onWakeOnLanBroadcastError(it)
                }
    }

    private fun handleConnectionEnd(result: Result<Unit>) {
        runCatching {
            result.onFailure {
                if (!stopRequested) {
                    logE("Connection error", it)
                    observer.onConnectionError(it)
                }
            }

            log("Disconnected from server")
            observer.onDisconnected()

        }.onFailure {
            log("Error handling connection end: $it")
        }
    }

    private fun log(msg: String) = Log.i(tag, msg)
    private fun logE(msg: String, e: Throwable?) = Log.e(tag, msg, e)

    companion object {
        private val sessionCounter = AtomicInteger(0)
    }
}