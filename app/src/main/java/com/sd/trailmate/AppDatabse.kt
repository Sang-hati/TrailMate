package com.sd.trailmate

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Hike::class, DailySteps::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hikeDao(): HikeDao
    abstract fun stepsDao(): StepsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Version 1 -> 2: add the daily_steps table
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_steps` (" +
                            "`date` TEXT NOT NULL, `steps` INTEGER NOT NULL, " +
                            "`sample` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        // Version 2 -> 3: add a steps column to hikes; old hikes get 0
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hikes ADD COLUMN steps INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trailmate.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}