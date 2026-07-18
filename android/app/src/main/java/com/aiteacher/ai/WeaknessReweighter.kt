package com.aiteacher.ai

import com.aiteacher.onboarding.RankedTopic

/**
 * Re-weights ranked topics after a quiz or exam result comes in.
 * Blends the existing weakness score with the new result (60/40 split).
 */
object WeaknessReweighter {

    /**
     * @param topics       current ranked topic list
     * @param subject      subject that was just tested
     * @param scorePercent 0-100 score from the quiz/exam
     * @return updated list with re-scored weakness and re-ranked order
     */
    fun reweight(topics: List<RankedTopic>, subject: String, scorePercent: Int): List<RankedTopic> {
        val newWeakness = (100 - scorePercent).coerceIn(0, 100)
        val updated = topics.map { t ->
            if (t.subject.equals(subject, ignoreCase = true)) {
                val blended = (t.weaknessScore * 0.4f + newWeakness * 0.6f).toInt()
                t.copy(weaknessScore = blended)
            } else t
        }
        // Re-rank: higher composite score = lower rank number (teach first)
        return updated
            .sortedByDescending { it.importanceScore * 0.35 + it.weaknessScore * 0.35 + it.dependencyScore * 0.2 + it.currentTermScore * 0.1 }
            .mapIndexed { i, t -> t.copy(rank = i + 1) }
    }
}
