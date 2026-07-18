package com.aiteacher.ai

import android.content.Context
import com.aiteacher.onboarding.CapabilityResult
import com.aiteacher.onboarding.StudentProfile

sealed class AgentEvent {
    data class Step(val message: String) : AgentEvent()
    data class AgentStarted(val agentName: String) : AgentEvent()
    data class AgentFinished(val agentName: String) : AgentEvent()
    data class PipelineComplete(val memory: AgentMemory) : AgentEvent()
    data class PipelineFailed(val reason: String) : AgentEvent()
}

/**
 * Pipeline: CurriculumAgent → AssessmentAgent → TopicRankerAgent → PlanAgent
 *
 * Phase 1 (runAssessmentPhase): CurriculumAgent + AssessmentAgent + TopicRankerAgent
 *   — runs when student hits "Take the Test"
 *   — TopicRankerAgent runs here so ranked topics are ready before the test is shown
 *     (the ranking doesn't need test results — it uses curriculum context + subject list)
 *
 * Phase 2 (runPlanPhase): PlanAgent
 *   — runs after student submits test answers
 *   — injects real CapabilityResult into memory so PlanAgent can weight by actual scores
 */
object AgentPipeline {

    private val curriculumAgent = CurriculumAgent()
    private val assessmentAgent = AssessmentAgent()
    private val topicRankerAgent = TopicRankerAgent()
    private val planAgent = PlanAgent()

    suspend fun runAssessmentPhase(
        context: Context,
        profile: StudentProfile,
        subjects: List<String>,
        availability: Map<String, List<String>>,
        sessionLengthMinutes: Int,
        weeksToSchedule: Int = 4,
        onEvent: (AgentEvent) -> Unit
    ): AgentMemory {
        var memory = AgentMemory(
            profile = profile,
            subjects = subjects,
            availability = availability,
            sessionLengthMinutes = sessionLengthMinutes,
            weeksToSchedule = weeksToSchedule
        )

        return try {
            onEvent(AgentEvent.AgentStarted("CurriculumAgent"))
            memory = curriculumAgent.run(context, memory) { onEvent(AgentEvent.Step(it)) }
            onEvent(AgentEvent.AgentFinished("CurriculumAgent"))

            onEvent(AgentEvent.AgentStarted("AssessmentAgent"))
            memory = assessmentAgent.run(context, memory) { onEvent(AgentEvent.Step(it)) }
            onEvent(AgentEvent.AgentFinished("AssessmentAgent"))

            // TopicRankerAgent runs here — uses curriculum context + subjects
            // weakness scores will be updated in Phase 2 once test results are in
            onEvent(AgentEvent.AgentStarted("TopicRankerAgent"))
            memory = topicRankerAgent.run(context, memory) { onEvent(AgentEvent.Step(it)) }
            onEvent(AgentEvent.AgentFinished("TopicRankerAgent"))

            memory
        } catch (e: Exception) {
            onEvent(AgentEvent.PipelineFailed("Pipeline error: ${e.message}"))
            memory
        }
    }

    suspend fun runPlanPhase(
        context: Context,
        memory: AgentMemory,
        result: CapabilityResult,
        onEvent: (AgentEvent) -> Unit
    ): AgentMemory {
        // Inject real test results and re-weight ranked topics by actual weakness scores
        val reweightedTopics = memory.rankedTopics.map { topic ->
            val subjectScore = result.subjectScores[topic.subject] ?: 50
            val actualWeakness = 100 - subjectScore
            // Blend original weakness estimate with actual score (60/40 split)
            val blendedWeakness = ((topic.weaknessScore * 0.4) + (actualWeakness * 0.6)).toInt()
            topic.copy(weaknessScore = blendedWeakness)
        }.sortedWith(
            compareByDescending<com.aiteacher.onboarding.RankedTopic> { it.isCurrentTerm }
                .thenByDescending { it.weaknessScore }
                .thenBy { it.rank }
        ).mapIndexed { i, t -> t.copy(rank = i + 1) }

        var updatedMemory = memory.copy(
            capabilityResult = result,
            rankedTopics = reweightedTopics
        )

        return try {
            onEvent(AgentEvent.AgentStarted("PlanAgent"))
            updatedMemory = planAgent.run(context, updatedMemory) { onEvent(AgentEvent.Step(it)) }
            onEvent(AgentEvent.AgentFinished("PlanAgent"))
            onEvent(AgentEvent.PipelineComplete(updatedMemory))
            updatedMemory
        } catch (e: Exception) {
            onEvent(AgentEvent.PipelineFailed("Plan generation error: ${e.message}"))
            updatedMemory
        }
    }
}
