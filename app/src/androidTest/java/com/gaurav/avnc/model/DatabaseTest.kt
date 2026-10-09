/*
 * Copyright (c) 2023  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import com.gaurav.avnc.instrumentation
import com.gaurav.avnc.model.db.MainDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DatabaseTest {
    private val dbName = "Bond. James Bond."
    private val minVersion = 1
    private val maxVersion = MainDb.VERSION

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MainDb::class.java)

    @Test
    fun migrations() {
        for (i in minVersion until maxVersion)
            for (j in i + 1..maxVersion)
                runCatching {
                    helper.createDatabase(dbName, i).close()
                    helper.runMigrationsAndValidate(dbName, j, false).close()
                }.onFailure {
                    throw Exception("Failed to migrate MainDb from [$i] to [$j]", it)
                }
    }

    @Test
    fun removingSshPreservesProfilesAndTransport() {
        helper.createDatabase(dbName, 7).use { db ->
            // Populate required legacy columns, including SSH secrets.
            val values = ContentValues()
            db.query("PRAGMA table_info(profiles)").use { columns ->
                while (columns.moveToNext()) {
                    val name = columns.getString(columns.getColumnIndexOrThrow("name"))
                    val type = columns.getString(columns.getColumnIndexOrThrow("type"))
                    if (type == "TEXT") values.put(name, "legacy")
                    else values.put(name, 0)
                }
            }
            for (channel in listOf(1, 24)) {
                values.put("ID", channel)
                values.put("channelType", channel)
                values.put("host", "localhost")
                values.put("port", 5901)
                values.put("password", "vnc-secret")
                db.insert("profiles", SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }

        helper.runMigrationsAndValidate(dbName, 8, true).use { db ->
            db.query("SELECT * FROM profiles ORDER BY ID").use { profiles ->
                assertEquals(2, profiles.count)
                assertFalse(profiles.columnNames.any { it.startsWith("ssh") })
                for (channel in listOf(1, 24)) {
                    assertTrue(profiles.moveToNext())
                    assertEquals(channel, profiles.getInt(profiles.getColumnIndexOrThrow("ID")))
                    assertEquals(channel, profiles.getInt(profiles.getColumnIndexOrThrow("channelType")))
                    assertEquals("localhost", profiles.getString(profiles.getColumnIndexOrThrow("host")))
                    assertEquals(5901, profiles.getInt(profiles.getColumnIndexOrThrow("port")))
                    assertEquals("vnc-secret", profiles.getString(profiles.getColumnIndexOrThrow("password")))
                }
            }
        }
    }

    @Test
    fun removingRepeaterPreservesProfiles() {
        helper.createDatabase(dbName, 8).use { db ->
            val values = ContentValues()
            db.query("PRAGMA table_info(profiles)").use { columns ->
                while (columns.moveToNext()) {
                    val name = columns.getString(columns.getColumnIndexOrThrow("name"))
                    val type = columns.getString(columns.getColumnIndexOrThrow("type"))
                    if (type == "TEXT") values.put(name, "")
                    else values.put(name, 0)
                }
            }
            for (id in 1..2) {
                values.put("ID", id)
                values.put("name", "Saved server $id")
                values.put("host", "server-$id")
                values.put("port", 5901)
                values.put("password", "vnc-secret")
                values.put("useRepeater", id - 1)
                values.put("idOnRepeater", 12345)
                db.insert("profiles", SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }

        helper.runMigrationsAndValidate(dbName, 9, true).use { db ->
            db.query("SELECT * FROM profiles ORDER BY ID").use { profiles ->
                assertEquals(2, profiles.count)
                assertFalse(profiles.columnNames.contains("useRepeater"))
                assertFalse(profiles.columnNames.contains("idOnRepeater"))
                for (id in 1..2) {
                    assertTrue(profiles.moveToNext())
                    assertEquals(id, profiles.getInt(profiles.getColumnIndexOrThrow("ID")))
                    assertEquals("Saved server $id", profiles.getString(profiles.getColumnIndexOrThrow("name")))
                    assertEquals("server-$id", profiles.getString(profiles.getColumnIndexOrThrow("host")))
                    assertEquals(5901, profiles.getInt(profiles.getColumnIndexOrThrow("port")))
                    assertEquals("vnc-secret", profiles.getString(profiles.getColumnIndexOrThrow("password")))
                }
            }
        }
    }

}
