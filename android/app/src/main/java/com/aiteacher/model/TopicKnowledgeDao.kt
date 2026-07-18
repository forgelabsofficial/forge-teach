package com.aiteacher.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicKnowledgeDao {
    @Query("SELECT * FROM topic_knowledge WHERE topicId = :topicId")
    suspend fun getTopic(topicId: String): TopicKnowledgeEntity?

    @Query("SELECT * FROM topic_knowledge WHERE subject = :subject")
    fun getSubjectTopics(subject: String): Flow<List<TopicKnowledgeEntity>>

    @Query("SELECT * FROM topic_knowledge")
    fun getAllTopics(): Flow<List<TopicKnowledgeEntity>>

    @Query("SELECT * FROM topic_knowledge WHERE isUnlocked = 1 AND isBlocked = 0 AND nextReviewTimestamp < :now")
    suspend fun getDueReviews(now: Long): List<TopicKnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(topic: TopicKnowledgeEntity)

    @Query("UPDATE topic_knowledge SET mastery = :mastery, confidence = :confidence, avgResponseTimeMs = :avgResponseMs, lastReviewTimestamp = :now WHERE topicId = :topicId")
    suspend fun updateMastery(topicId: String, mastery: Int, confidence: Int, avgResponseMs: Int, now: Long)

    @Query("UPDATE topic_knowledge SET recallStrength = :recall, recognitionStrength = :recog, decayRate = :decay, nextReviewTimestamp = :nextReview WHERE topicId = :topicId")
    suspend fun updateMemoryMetrics(topicId: String, recall: Int, recog: Int, decay: Float, nextReview: Long)

    @Query("DELETE FROM topic_knowledge")
    suspend fun clear()
}
