package com.aiteacher.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A node in the curriculum knowledge graph.
 * Represents one teachable topic with its metadata.
 */
@Entity(
    tableName = "knowledge_graph_nodes",
    indices = [Index(value = ["subject", "gradeLevel"])]
)
data class KnowledgeGraphNode(
    @PrimaryKey val topicId: String,          // e.g. "math_fractions"
    val subject: String,                      // e.g. "math"
    val title: String,                        // e.g. "Fractions"
    val description: String = "",             // one-line summary
    val gradeLevel: String = "",              // e.g. "grade_5", "jss1"
    val difficulty: Int = 1,                  // 1-5
    val category: String = "",                // e.g. "Number & Operations"
    val estimatedMinutes: Int = 30,           // typical time to master
    val sourceUrl: String = ""                // where this was sourced from
)

/**
 * A directed edge: topicA must be mastered before topicB.
 * Multiple edges form the prerequisite graph.
 */
@Entity(
    tableName = "knowledge_graph_edges",
    primaryKeys = ["prerequisiteId", "topicId"],
    indices = [Index("topicId"), Index("prerequisiteId")]
)
data class KnowledgeGraphEdge(
    val prerequisiteId: String,               // must know this first
    val topicId: String,                      // then can learn this
    val strength: Float = 1.0f                // 0-1 how critical this dependency is
)
