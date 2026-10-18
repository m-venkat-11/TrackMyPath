package com.example.pathwise.data.local

import android.content.ContentValues
import android.database.Cursor
import com.example.pathwise.data.model.Landmark
import com.example.pathwise.data.model.LandmarkSource

class LandmarkDao(private val dbHelper: PathWiseDatabaseHelper) {

    fun insertLandmark(landmark: Landmark) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", landmark.id)
            put("name", landmark.name)
            put("category", landmark.category)
            put("notes", landmark.notes)
            put("latitude", landmark.latitude)
            put("longitude", landmark.longitude)
            put("source", landmark.source.name)
            put("created_at", landmark.createdAt)
        }
        db.insertWithOnConflict("landmarks", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateLandmark(landmark: Landmark) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", landmark.name)
            put("category", landmark.category)
            put("notes", landmark.notes)
            put("latitude", landmark.latitude)
            put("longitude", landmark.longitude)
            put("source", landmark.source.name)
        }
        db.update("landmarks", values, "id = ?", arrayOf(landmark.id))
    }

    fun deleteLandmark(id: String) {
        val db = dbHelper.writableDatabase
        db.delete("landmarks", "id = ?", arrayOf(id))
    }

    fun getAllLandmarks(): List<Landmark> {
        val list = mutableListOf<Landmark>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "landmarks",
            null,
            null,
            null,
            null,
            null,
            "created_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursorToLandmark(it))
            }
        }
        return list
    }

    fun getLandmarkById(id: String): Landmark? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "landmarks",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) mapCursorToLandmark(it) else null
        }
    }

    private fun mapCursorToLandmark(c: Cursor): Landmark {
        return Landmark(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            category = if (c.isNull(c.getColumnIndexOrThrow("category"))) null else c.getString(c.getColumnIndexOrThrow("category")),
            notes = if (c.isNull(c.getColumnIndexOrThrow("notes"))) null else c.getString(c.getColumnIndexOrThrow("notes")),
            latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
            longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
            source = LandmarkSource.valueOf(c.getString(c.getColumnIndexOrThrow("source"))),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        )
    }
}
