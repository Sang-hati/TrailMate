package com.sd.trailmate

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface HikeDao {
    @Insert
    suspend fun insert(hike: Hike): Long

    @Update
    suspend fun update(hike: Hike)

    @Delete
    suspend fun delete(hike: Hike)

    @Query("SELECT * FROM hikes ORDER BY id DESC")
    fun getAll(): LiveData<List<Hike>>

    @Query("SELECT * FROM hikes WHERE id = :hikeId")
    suspend fun getById(hikeId: Int): Hike?

    // Used on the Stats screen
    @Query("SELECT COUNT(*) FROM hikes")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(distanceKm), 0) FROM hikes")
    suspend fun totalDistance(): Double

    @Query("SELECT COALESCE(SUM(durationSec), 0) FROM hikes")
    suspend fun totalDuration(): Long

    @Query("SELECT COALESCE(MAX(distanceKm), 0) FROM hikes")
    suspend fun longest(): Double

    @Query("SELECT * FROM hikes ORDER BY id DESC LIMIT 3")
    fun getRecent(): LiveData<List<Hike>>
}