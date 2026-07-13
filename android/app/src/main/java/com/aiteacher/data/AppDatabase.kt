package com.aiteacher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlanEntity::class, SessionEntity::class, StudentProfileEntity::class, ProgressEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun studentDao(): StudentDao
    abstract fun progressDao(): ProgressDao

    companion object {
        private const val DB_NAME = "aiteacher.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // add studentId column to plans (nullable)
                db.execSQL("ALTER TABLE plans ADD COLUMN studentId INTEGER")
                // add status column to sessions with default
                db.execSQL("ALTER TABLE sessions ADD COLUMN status TEXT NOT NULL DEFAULT 'scheduled'")
                // create students table
                db.execSQL("CREATE TABLE IF NOT EXISTS `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `timezone` TEXT, `createdAt` INTEGER, `updatedAt` INTEGER)")
                // create progress table
                db.execSQL("CREATE TABLE IF NOT EXISTS `progress` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `status` TEXT NOT NULL, `notes` TEXT, `score` INTEGER, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON DELETE CASCADE)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                        val inst = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                            .addMigrations(MIGRATION_2_3)
                            .build()
                        INSTANCE = inst
                        inst
                    }
                }
    }
}
