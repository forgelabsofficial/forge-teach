package com.aiteacher.data

import androidx.room.*

data class PlanWithSessions(
    @Embedded val plan: PlanEntity,
    @Relation(parentColumn = "id", entityColumn = "planId") val sessions: List<SessionEntity>
)

@Dao
interface PlanDao {
    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Transaction
    @Query("SELECT * FROM plans ORDER BY id DESC LIMIT 1")
    suspend fun getLatestPlan(): PlanWithSessions?

    @Query("SELECT * FROM sessions WHERE planId = :planId")
    suspend fun getSessionsForPlan(planId: Long): List<SessionEntity>
}
