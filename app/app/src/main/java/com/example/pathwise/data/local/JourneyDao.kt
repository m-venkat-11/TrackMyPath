package com.example.pathwise.data.local

import android.content.ContentValues
import android.database.Cursor
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.model.JourneyStatus

class JourneyDao(private val dbHelper: PathWiseDatabaseHelper) {

    fun insertJourney(journey: Journey) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", journey.id)
            put("title", journey.title)
            put("start_time", journey.startTime)
            put("end_time", journey.endTime)
            put("duration_seconds", journey.durationSeconds)
            put("recorded_distance_meters", journey.recordedDistanceMeters)
            put("point_count", journey.pointCount)
            put("status", journey.status.name)
            put("is_demo", if (journey.isDemo) 1 else 0)
        }
        db.insertWithOnConflict("journeys", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateMetrics(journeyId: String, durationSeconds: Long, distanceMeters: Double, pointCount: Int) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("duration_seconds", durationSeconds)
            put("recorded_distance_meters", distanceMeters)
            put("point_count", pointCount)
        }
        db.update("journeys", values, "id = ?", arrayOf(journeyId))
    }

    fun updateStatus(journeyId: String, status: JourneyStatus, endTime: Long? = null) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", status.name)
            if (endTime != null) {
                put("end_time", endTime)
            }
        }
        db.update("journeys", values, "id = ?", arrayOf(journeyId))
    }

    fun renameJourney(journeyId: String, newTitle: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", newTitle)
        }
        db.update("journeys", values, "id = ?", arrayOf(journeyId))
    }

    fun deleteJourney(journeyId: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("location_points", "journey_id = ?", arrayOf(journeyId))
            db.delete("journeys", "id = ?", arrayOf(journeyId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getJourneyById(id: String): Journey? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "journeys",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) mapCursorToJourney(it) else null
        }
    }

    fun getAllJourneys(): List<Journey> {
        val list = mutableListOf<Journey>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "journeys",
            null,
            null,
            null,
            null,
            null,
            "start_time DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursorToJourney(it))
            }
        }
        return list
    }

    /**
     * Finds any unfinalised journey left in RECORDING or PAUSED state upon restart per PRD FR-10.
     */
    fun getInterruptedJourney(): Journey? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "journeys",
            null,
            "status IN (?, ?)",
            arrayOf(JourneyStatus.RECORDING.name, JourneyStatus.PAUSED.name),
            null,
            null,
            "start_time DESC",
            "1"
        )
        return cursor.use {
            if (it.moveToFirst()) mapCursorToJourney(it) else null
        }
    }

    private fun mapCursorToJourney(c: Cursor): Journey {
        val isDemoIdx = c.getColumnIndex("is_demo")
        val isDemoVal = if (isDemoIdx >= 0 && !c.isNull(isDemoIdx)) c.getInt(isDemoIdx) == 1 else false

        return Journey(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            startTime = c.getLong(c.getColumnIndexOrThrow("start_time")),
            endTime = if (c.isNull(c.getColumnIndexOrThrow("end_time"))) null else c.getLong(c.getColumnIndexOrThrow("end_time")),
            durationSeconds = c.getLong(c.getColumnIndexOrThrow("duration_seconds")),
            recordedDistanceMeters = c.getDouble(c.getColumnIndexOrThrow("recorded_distance_meters")),
            pointCount = c.getInt(c.getColumnIndexOrThrow("point_count")),
            status = try {
                JourneyStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status")))
            } catch (_: Throwable) {
                JourneyStatus.COMPLETED
            },
            isDemo = isDemoVal
        )
    }
}
