/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

/**
 * Generic wrapper for login information.
 * This can be used to hold different [Type]s of credentials.
 */
data class LoginInfo(
        val type: Type,
        var username: String,
        var password: String,
) {
    enum class Type {
        VNC_PASSWORD,
        VNC_CREDENTIAL  // Username & Password
    }

    companion object {
        /**
         * Extracts given login [type] from [profile]
         */
        fun fromProfile(profile: ServerProfile, type: Type): LoginInfo {
            return when (type) {
                Type.VNC_PASSWORD -> LoginInfo(type, "", profile.password)
                Type.VNC_CREDENTIAL -> LoginInfo(type, profile.username, profile.password)
            }
        }
    }

    /**
     * Applies this login info to [profile]
     */
    fun applyTo(profile: ServerProfile) {
        when (type) {
            Type.VNC_PASSWORD -> {
                profile.password = password
            }
            Type.VNC_CREDENTIAL -> {
                profile.username = username
                profile.password = password
            }
        }
    }
}