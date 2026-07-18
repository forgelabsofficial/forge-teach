package com.aiteacher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlanEntity::class, SessionEntity::class,
        StudentProfileEntity::class, ProgressEntity::class,
        QuizResultEntity::class, ExamResultEntity::class, StudySessionEntity::class,
        com.aiteacher.model.TopicKnowledgeEntity::class
    ],
    version = 5
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun studentDao(): StudentDao
    abstract fun progressDao(): ProgressDao
    abstract fun quizResultDao(): QuizResultDao
    abstract fun examResultDao(): ExamResultDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun topicKnowledgeDao(): com.aiteacher.model.TopicKnowledgeDao

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
                db.execSQL("ALTER TABLE plans ADD COLUMN studentId INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN status TEXT NOT NULL DEFAULT 'scheduled'")
                db.execSQL("CREATE TABLE IF NOT EXISTS `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `timezone` TEXT, `createdAt` INTEGER, `updatedAt` INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `progress` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `status` TEXT NOT NULL, `notes` TEXT, `score` INTEGER, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON DELETE CASCADE)")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `topic_knowledge` (`topicId` TEXT NOT NULL PRIMARY KEY, `subject` TEXT NOT NULL, `mastery` INTEGER NOT NULL DEFAULT 0, `confidence` INTEGER NOT NULL DEFAULT 0, `recallStrength` INTEGER NOT NULL DEFAULT 0, `recognitionStrength` INTEGER NOT NULL DEFAULT 0, `decayRate` REAL NOT NULL DEFAULT 0.5, `lastReviewTimestamp` INTEGER NOT NULL DEFAULT 0, `nextReviewTimestamp` INTEGER NOT NULL DEFAULT 0, `totalAttempts` INTEGER NOT NULL DEFAULT 0, `correctAttempts` INTEGER NOT NULL DEFAULT 0, `avgResponseTimeMs` INTEGER NOT NULL DEFAULT 0, `guessingTendency` INTEGER NOT NULL DEFAULT 0, `misconceptionsJson` TEXT NOT NULL DEFAULT '[]', `isUnlocked` INTEGER NOT NULL DEFAULT 1, `isBlocked` INTEGER NOT NULL DEFAULT 0, `transferScore` INTEGER NOT NULL DEFAULT 0, `learningVelocity` REAL NOT NULL DEFAULT 0.0, `stressScore` INTEGER NOT NULL DEFAULT 0, `lastSessionDurationSec` INTEGER NOT NULL DEFAULT 0, `streakCorrect` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                        val inst = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .build()
                        INSTANCE = inst
                        inst
                    }
                }
    }
}
