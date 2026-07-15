package com.aiteacher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timezone: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)
