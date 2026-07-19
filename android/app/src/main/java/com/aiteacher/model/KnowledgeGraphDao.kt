package com.aiteacher.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KnowledgeGraphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: KnowledgeGraphNode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodes(nodes: List<KnowledgeGraphNode>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEdge(edge: KnowledgeGraphEdge)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEdges(edges: List<KnowledgeGraphEdge>)

    @Query("SELECT * FROM knowledge_graph_nodes WHERE subject = :subject ORDER BY difficulty ASC")
    suspend fun getNodesForSubject(subject: String): List<KnowledgeGraphNode>

    @Query("SELECT * FROM knowledge_graph_nodes ORDER BY subject, difficulty")
    suspend fun getAllNodes(): List<KnowledgeGraphNode>

    @Query("SELECT prerequisiteId FROM knowledge_graph_edges WHERE topicId = :topicId")
    suspend fun getPrerequisites(topicId: String): List<String>

    @Query("SELECT topicId FROM knowledge_graph_edges WHERE prerequisiteId = :topicId")
    suspend fun getDependents(topicId: String): List<String>

    /**
     * Get all topics that are unlocked (all prerequisites met based on mastery).
     * A prerequisite is "met" if the student has mastery >= minMastery in that topic.
     */
    @Query("""
        SELECT n.* FROM knowledge_graph_nodes n
        WHERE n.topicId NOT IN (
            SELECT e.topicId FROM knowledge_graph_edges e
            LEFT JOIN topic_knowledge k ON k.topicId = e.prerequisiteId
            WHERE k.mastery IS NULL OR k.mastery < :minMastery
        )
        ORDER BY n.difficulty ASC
    """)
    suspend fun getUnlockedTopics(minMastery: Int = 60): List<KnowledgeGraphNode>

    @Query("DELETE FROM knowledge_graph_nodes")
    suspend fun clearNodes()

    @Query("DELETE FROM knowledge_graph_edges")
    suspend fun clearEdges()
}
