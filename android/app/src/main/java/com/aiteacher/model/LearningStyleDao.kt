package com.aiteacher.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LearningStyleDao {

    @Query("SELECT * FROM learning_styles WHERE studentId = :studentId LIMIT 1")
    suspend fun getStyleForStudent(studentId: String = "default_student"): LearningStyleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStyle(style: LearningStyleEntity)
}
