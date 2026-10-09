/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gaurav.avnc.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerProfileTest {
    // Same compatibility setting used by PrefsViewModel for backups.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacySshBackupPreservesVncSettingsAndDropsSshSecrets() {
        val profile = json.decodeFromString<ServerProfile>("""{
            "name": "Legacy tunnel", "host": "localhost", "port": 5901,
            "password": "vnc-secret", "channelType": 24,
            "sshHost": "proxy", "sshPort": 22, "sshUsername": "user",
            "sshAuthType": 2, "sshPassword": "ssh-secret", "sshPrivateKey": "key"
        }""")

        assertEquals("Legacy tunnel", profile.name)
        assertEquals("localhost", profile.host)
        assertEquals(5901, profile.port)
        assertEquals("vnc-secret", profile.password)
        assertEquals(24, profile.channelType)
        val exported = json.encodeToString(profile)
        assertFalse(exported.contains("ssh", ignoreCase = true))
    }

    @Test
    fun legacyRepeaterBackupImportsAndDropsRepeaterSettings() {
        val profile = json.decodeFromString<ServerProfile>("""{
            "name": "Saved server", "host": "server", "port": 5901,
            "password": "vnc-secret", "useRepeater": true, "idOnRepeater": 12345
        }""")

        assertEquals("Saved server", profile.name)
        assertEquals("server", profile.host)
        assertEquals(5901, profile.port)
        assertEquals("vnc-secret", profile.password)
        assertFalse(json.encodeToString(profile).contains("repeater", ignoreCase = true))
    }

}
