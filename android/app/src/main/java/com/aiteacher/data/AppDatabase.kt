package com.aiteacher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlanEntity::class, SessionEntity::class,
        StudentProfileEntity::class, ProgressEntity::class,
        QuizResultEntity::class, ExamResultEntity::class, StudySessionEntity::class
    ],
    version = 4
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun studentDao(): StudentDao
    abstract fun progressDao(): ProgressDao
    abstract fun quizResultDao(): QuizResultDao
    abstract fun examResultDao(): ExamResultDao
    abstract fun studySessionDao(): StudySessionDao

    companion object {
        private const val DB_NAME = "aiteacher.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN isoDateTime TEXT")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `quiz_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subject` TEXT NOT NULL, `topic` TEXT NOT NULL, `totalQuestions` INTEGER NOT NULL, `correctAnswers` INTEGER NOT NULL, `scorePercent` INTEGER NOT NULL, `timeTakenSeconds` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `exam_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `examName` TEXT NOT NULL, `subjects` TEXT NOT NULL, `totalQuestions` INTEGER NOT NULL, `correctAnswers` INTEGER NOT NULL, `scorePercent` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `subjectScoresJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `study_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `topic` TEXT NOT NULL, `subject` TEXT NOT NULL, `plannedDurationMinutes` INTEGER NOT NULL, `actualDurationSeconds` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `xpEarned` INTEGER NOT NULL)")
            }
        }

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
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .build()
                        INSTANCE = inst
                        inst
                    }
                }
    }
}
