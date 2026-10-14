package com.smartbluetoothsleeptracker.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration tests validating explicit Room migrations 1 -> 2 and 2 -> 3 without data loss.
 */
@RunWith(RobolectricTestRunner::class)
class RoomMigrationTest {

    private lateinit var openHelper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Schema v1 tables
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS devices (
                            address TEXT NOT NULL,
                            name TEXT NOT NULL,
                            device_type TEXT NOT NULL,
                            is_favorite INTEGER NOT NULL,
                            working_disconnect_method TEXT,
                            last_connected_at INTEGER,
                            PRIMARY KEY(address)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS disconnect_attempts (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            device_address TEXT NOT NULL,
                            android_version INTEGER NOT NULL,
                            manufacturer TEXT NOT NULL,
                            method_id TEXT NOT NULL,
                            succeeded INTEGER NOT NULL,
                            last_tested_at INTEGER NOT NULL,
                            FOREIGN KEY(device_address) REFERENCES devices(address) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS sessions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            device_address TEXT NOT NULL,
                            device_name TEXT NOT NULL,
                            start_time INTEGER NOT NULL,
                            end_time INTEGER,
                            planned_duration_min INTEGER NOT NULL,
                            actual_duration_min INTEGER,
                            disconnect_confirmed INTEGER NOT NULL,
                            extended_minutes INTEGER NOT NULL,
                            date TEXT NOT NULL,
                            FOREIGN KEY(device_address) REFERENCES devices(address) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS daily_usage (
                            date TEXT NOT NULL,
                            device_address TEXT NOT NULL,
                            total_minutes INTEGER NOT NULL,
                            session_count INTEGER NOT NULL,
                            PRIMARY KEY(date, device_address),
                            FOREIGN KEY(device_address) REFERENCES devices(address) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        openHelper.close()
    }

    @Test
    fun testMigration1To2_addsErrorMessageColumnAndPreservesData() {
        // Seed v1 data
        db.execSQL("INSERT INTO devices (address, name, device_type, is_favorite) VALUES ('AA:BB:CC:DD:EE:FF', 'Earbuds', 'EARBUDS', 1)")
        db.execSQL("INSERT INTO disconnect_attempts (device_address, android_version, manufacturer, method_id, succeeded, last_tested_at) VALUES ('AA:BB:CC:DD:EE:FF', 34, 'Google', 'a2dp_reflection', 1, 1000)")

        // Execute MIGRATION_1_2
        AppDatabase.MIGRATION_1_2.migrate(db)

        // Verify error_message column exists and previous data survived
        val cursor = db.query("SELECT id, device_address, error_message FROM disconnect_attempts WHERE device_address = 'AA:BB:CC:DD:EE:FF'")
        assertTrue(cursor.moveToFirst())
        assertEquals("AA:BB:CC:DD:EE:FF", cursor.getString(1))
        assertEquals(null, cursor.getString(2)) // error_message is null for existing rows
        cursor.close()

        // Insert new attempt with error_message
        db.execSQL("INSERT INTO disconnect_attempts (device_address, android_version, manufacturer, method_id, succeeded, last_tested_at, error_message) VALUES ('AA:BB:CC:DD:EE:FF', 34, 'Google', 'hfp_reflection', 0, 2000, 'SecurityException')")
        val cursor2 = db.query("SELECT error_message FROM disconnect_attempts WHERE method_id = 'hfp_reflection'")
        assertTrue(cursor2.moveToFirst())
        assertEquals("SecurityException", cursor2.getString(0))
        cursor2.close()
    }

    @Test
    fun testMigration2To3_addsAudioStoppedColumnWithDefaultZeroAndPreservesData() {
        // Seed v2 data
        db.execSQL("INSERT INTO devices (address, name, device_type, is_favorite) VALUES ('AA:BB:CC:DD:EE:FF', 'Earbuds', 'EARBUDS', 1)")
        db.execSQL("INSERT INTO sessions (id, device_address, device_name, start_time, end_time, planned_duration_min, actual_duration_min, disconnect_confirmed, extended_minutes, date) VALUES (1, 'AA:BB:CC:DD:EE:FF', 'Earbuds', 1000, 2000, 30, 25, 1, 0, '2026-10-09')")

        // Execute MIGRATION_2_3
        AppDatabase.MIGRATION_2_3.migrate(db)

        // Verify audio_stopped column was added with default value 0 and existing session preserved
        val cursor = db.query("SELECT id, device_name, actual_duration_min, audio_stopped FROM sessions WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("Earbuds", cursor.getString(1))
        assertEquals(25, cursor.getInt(2))
        assertEquals(0, cursor.getInt(3)) // Default 0
        cursor.close()

        // Insert new v3 session with audio_stopped = 1
        db.execSQL("INSERT INTO sessions (id, device_address, device_name, start_time, end_time, planned_duration_min, actual_duration_min, audio_stopped, disconnect_confirmed, extended_minutes, date) VALUES (2, 'AA:BB:CC:DD:EE:FF', 'Earbuds', 3000, 4000, 30, 30, 1, 1, 0, '2026-10-09')")
        val cursor2 = db.query("SELECT audio_stopped FROM sessions WHERE id = 2")
        assertTrue(cursor2.moveToFirst())
        assertEquals(1, cursor2.getInt(0))
        cursor2.close()
    }
}
