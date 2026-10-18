package com.example.pathwise.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Native SQLite helper for PathWise local storage per PRD FR-04.
 * Guarantees zero-dependency, crash-resilient persistence and index-optimised queries.
 */
class PathWiseDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "pathwise.db"
        const val DATABASE_VERSION = 2

        @Volatile
        private var INSTANCE: PathWiseDatabaseHelper? = null

        fun getInstance(context: Context): PathWiseDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PathWiseDatabaseHelper(context).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS journeys (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                duration_seconds INTEGER NOT NULL DEFAULT 0,
                recorded_distance_meters REAL NOT NULL DEFAULT 0.0,
                point_count INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                is_demo INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS location_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                journey_id TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL,
                speed REAL,
                accuracy REAL,
                timestamp INTEGER NOT NULL,
                quality_status TEXT NOT NULL DEFAULT 'ACCEPTED',
                FOREIGN KEY (journey_id) REFERENCES journeys(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_points_journey ON location_points(journey_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_points_timestamp ON location_points(timestamp)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS landmarks (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT,
                notes TEXT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                source TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_landmarks_coords ON landmarks(latitude, longitude)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE journeys ADD COLUMN is_demo INTEGER NOT NULL DEFAULT 0")
            } catch (_: Throwable) {}
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL("ALTER TABLE journeys ADD COLUMN is_demo INTEGER NOT NULL DEFAULT 0")
        } catch (_: Throwable) {
            // Already present
        }
    }
}
