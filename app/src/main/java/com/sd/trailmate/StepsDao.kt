package com.sd.trailmate

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StepsDao {
    // REPLACE: saving today again overwrites today's earlier number
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(day: DailySteps)

    // IGNORE: demo data never overwrites a real day
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(days: List<DailySteps>)

    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: String, to: String): List<DailySteps>

    @Query("SELECT COUNT(*) FROM daily_steps WHERE sample = 1")
    suspend fun sampleCount(): Int

    @Query("DELETE FROM daily_steps WHERE sample = 1")
    suspend fun deleteSamples()
}