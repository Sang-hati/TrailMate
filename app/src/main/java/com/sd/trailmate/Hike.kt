package com.sd.trailmate

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hikes")
data class Hike(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val date: String,            // e.g. "10-10-2026"
    val distanceKm: Double,
    val durationSec: Long,
    val startPlace: String = "", // from Geocoder
    val endPlace: String = "",
    val notes: String = "",
    val photoPath: String = "",  // file path of camera photo
    val routePoints: String = "", // "lat,lng;lat,lng;..." for the map line
    @ColumnInfo(defaultValue = "0") val steps: Int = 0 // steps counted during the hike (0 = not available)
)