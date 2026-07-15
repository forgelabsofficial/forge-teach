package com.aiteacher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface StudentDao {
    @Insert
    suspend fun insertStudent(student: StudentProfileEntity): Long

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: Long): StudentProfileEntity?

    @Query("SELECT * FROM students ORDER BY id DESC LIMIT 1")
    suspend fun getLatestStudent(): StudentProfileEntity?

    @Update
    suspend fun updateStudent(student: StudentProfileEntity): Int
}
