package com.example.pathwise.data.local

import android.content.ContentValues
import android.database.Cursor
import com.example.pathwise.data.model.LocationPoint
import com.example.pathwise.data.model.PointQuality

class LocationPointDao(private val dbHelper: PathWiseDatabaseHelper) {

    fun insertPoint(point: LocationPoint): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("journey_id", point.journeyId)
            put("latitude", point.latitude)
            put("longitude", point.longitude)
            put("altitude", point.altitude)
            put("speed", point.speed)
            put("accuracy", point.accuracy)
            put("timestamp", point.timestamp)
            put("quality_status", point.qualityStatus.name)
        }
        return db.insert("location_points", null, values)
    }

    fun insertBatch(points: List<LocationPoint>) {
        if (points.isEmpty()) return
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (p in points) {
                val values = ContentValues().apply {
                    put("journey_id", p.journeyId)
                    put("latitude", p.latitude)
                    put("longitude", p.longitude)
                    put("altitude", p.altitude)
                    put("speed", p.speed)
                    put("accuracy", p.accuracy)
                    put("timestamp", p.timestamp)
                    put("quality_status", p.qualityStatus.name)
                }
                db.insert("location_points", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPointsForJourney(journeyId: String): List<LocationPoint> {
        val list = mutableListOf<LocationPoint>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "location_points",
            null,
            "journey_id = ?",
            arrayOf(journeyId),
            null,
            null,
            "timestamp ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursorToPoint(it))
            }
        }
        return list
    }

    fun getPointCountForJourney(journeyId: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM location_points WHERE journey_id = ?",
            arrayOf(journeyId)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun getLatestPointForJourney(journeyId: String): LocationPoint? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "location_points",
            null,
            "journey_id = ?",
            arrayOf(journeyId),
            null,
            null,
            "timestamp DESC",
            "1"
        )
        return cursor.use {
            if (it.moveToFirst()) mapCursorToPoint(it) else null
        }
    }

    private fun mapCursorToPoint(c: Cursor): LocationPoint {
        return LocationPoint(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            journeyId = c.getString(c.getColumnIndexOrThrow("journey_id")),
            latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
            longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
            altitude = if (c.isNull(c.getColumnIndexOrThrow("altitude"))) null else c.getDouble(c.getColumnIndexOrThrow("altitude")),
            speed = if (c.isNull(c.getColumnIndexOrThrow("speed"))) null else c.getFloat(c.getColumnIndexOrThrow("speed")),
            accuracy = if (c.isNull(c.getColumnIndexOrThrow("accuracy"))) null else c.getFloat(c.getColumnIndexOrThrow("accuracy")),
            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
            qualityStatus = PointQuality.valueOf(c.getString(c.getColumnIndexOrThrow("quality_status")))
        )
    }
}
