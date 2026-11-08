package com.sd.trailmate

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_steps")
data class DailySteps(
    @PrimaryKey val date: String,   // "2026-10-11": year-month-day sorts correctly as text
    val steps: Int,
    val sample: Int = 0             // 1 = demo data, 0 = real
)