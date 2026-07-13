package com.aiteacher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlanEntity::class, SessionEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao

    companion object {
        private const val DB_NAME = "aiteacher.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                        val inst = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                            .fallbackToDestructiveMigration() // simple migration strategy for prototype
                            .build()
                        INSTANCE = inst
                        inst
                    }
                }
    }
}
