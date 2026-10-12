package com.photodashboard.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photodashboard.app.data.PhotoDb
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Version 1 rows survive the upgrade that adds upload stage and progress. */
@RunWith(AndroidJUnit4::class)
class PhotoDbMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PhotoDb::class.java)

    @Test
    fun migrate1To2() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL(
                "INSERT INTO photos (localId, userId, filePath, mediaStoreUri, capturedAt, utcOffsetMinutes, noLocation, state, attempts) " +
                    "VALUES ('a', 1, '/x.jpg', NULL, 0, 60, 1, 'PENDING', 0)",
            )
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, PhotoDb.MIGRATION_1_2).use { db ->
            db.query("SELECT localId, state, stage, progress FROM photos").use { c ->
                c.moveToFirst()
                assertEquals("a", c.getString(0))
                assertEquals("PENDING", c.getString(1))
                assertEquals(true, c.isNull(2))
                assertEquals(true, c.isNull(3))
            }
        }
    }
}
