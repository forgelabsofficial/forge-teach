package com.aiteacher.ai

import android.content.Context
import android.util.Log
import com.aiteacher.onboarding.CapabilityAnswer
import com.aiteacher.onboarding.CapabilityQuestion
import com.aiteacher.onboarding.CapabilityResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * MisconceptionAgent
 *
 * Role: Analyse wrong answers from quizzes and exams to identify root
 * misconceptions. The vision says: don't store "Wrong", store "Wrong because...".
 *
 * Each misconception tracks:
 *   - topic: the subject/topic area
 *   - misconception: description of the specific misunderstanding
 *   - frequency: how many times this pattern has been observed
 *   - correctionStrategy: a hint or teaching strategy to address it
 *   - resolved: whether the student has overcome this misconception
 *
 * Memory in:  capabilityTest, capabilityResult (or raw Q&A pairs)
 * Memory out: misconceptions stored directly in TopicKnowledgeEntity
 *
 * This agent is NOT part of the onboarding pipeline. It runs as a lightweight
 * analysis after every quiz/exam completion, invoked from the ViewModel.
 */
object MisconceptionAgent {

    private val gson = Gson()
    private val misconceptionPatterns: List<PriorMisconception> = listOf(
        PriorMisconception("fraction_denominator", "Compares denominator size only (ignores numerator)", "fractions", "Use pizza analogy: bigger denominator means smaller slices, not bigger value"),
        PriorMisconception("decimal_place_value", "Treats decimal as whole number (e.g. 0.4 > 0.75)", "decimals", "Align decimal points and compare place values"),
        PriorMisconception("percent_over_100", "Thinks percentages cannot exceed 100%", "percentages", "Show that 200% of 50 = 100 using doubling"),
        PriorMisconception("neg_mult", "Thinks negative × negative = negative", "integers", "Use number line: flip direction twice = back to forward"),
        PriorMisconception("div_by_zero", "Thinks dividing by zero gives zero", "algebra", "Show: 6/0 asks 'what × 0 = 6?' — impossible"),
        PriorMisconception("area_vs_perimeter", "Confuses area and perimeter formulas", "geometry", "Draw grid vs boundary: area = inside, perimeter = edge"),
        PriorMisconception("variable_letter", "Treats variable as letter label not unknown value", "algebra", "x is a mystery box: what number makes the sentence true?"),
        PriorMisconception("order_of_ops", "Always evaluates left-to-right ignoring precedence", "arithmetic", "BODMAS/PEMDAS: brackets, orders, then left-to-right for same level"),
        PriorMisconception("fraction_add_denom", "Adds numerators and denominators directly", "fractions", "You can't add quarters to thirds — they must be same size pieces"),
        PriorMisconception("probability_scaling", "Thinks probability > 1 or scales incorrectly", "probability", "Probability is always between 0 (impossible) and 1 (certain)")
    )

    /**
     * Analyse a completed quiz or exam and extract misconceptions from wrong answers.
     * Returns a list of Misconception instances identified.
     */
    suspend fun analyse(
        questions: List<CapabilityQuestion>,
        selectedAnswers: Map<Int, Int>
    ): List<Misconception> {
        val misconceptions = mutableListOf<Misconception>()

        // Find wrong answers
        val wrongAnswers = questions.mapIndexedNotNull { idx, q ->
            val selected = selectedAnswers[idx]
            if (selected != null && selected != q.correctIndex) {
                WrongAnswerInfo(
                    subject = q.subject,
                    questionText = q.questionText,
                    selectedOption = q.options.getOrElse(selected) { "?" },
                    correctOption = q.options.getOrElse(q.correctIndex) { "?" },
                    difficulty = q.difficulty
                )
            } else null
        }

        if (wrongAnswers.isEmpty()) return misconceptions

        // Group by subject
        val bySubject = wrongAnswers.groupBy { it.subject }

        for ((subject, answers) in bySubject) {
            // Try to match against known patterns first
            val matchedPatterns = matchPatterns(answers)
            misconceptions.addAll(matchedPatterns)

            // If there are unmatched wrong answers, try to extract a custom misconception
            val unmatchedCount = answers.size - matchedPatterns.sumOf { it.frequency }
            if (unmatchedCount > 0) {
                // Generate a generic misconception for unmatched wrong answers
                val sample = answers.first()
                // Check if all wrong answers cluster in same difficulty or same topic area
                val avgDifficulty = answers.map { it.difficulty }.average()
                val desc = if (answers.size >= 3) {
                    if (avgDifficulty <= 2) "Foundational understanding gap in $subject"
                    else if (avgDifficulty <= 4) "Conceptual misunderstanding in $subject"
                    else "Advanced concept struggle in $subject"
                } else {
                    "Possible knowledge gap in $subject"
                }
                misconceptions.add(
                    Misconception(
                        id = "${subject}_custom_${System.currentTimeMillis()}",
                        topic = subject,
                        misconception = desc,
                        frequency = unmatchedCount,
                        correctionStrategy = "Review the core concepts of $subject and practice similar problems",
                        resolved = false
                    )
                )
            }
        }

        return misconceptions
    }

    /**
     * Merge new misconceptions with existing stored misconceptions (from JSON).
     * Existing misconceptions gain frequency, resolved status is checked.
     */
    fun mergeWithExisting(
        newMisconceptions: List<Misconception>,
        existingJson: String
    ): List<Misconception> {
        val existing = parseJson(existingJson)
        val existingMap = existing.associateBy { it.id }.toMutableMap()

        for (new in newMisconceptions) {
            val existing = existingMap[new.id]
            if (existing != null) {
                // Bump frequency, update correction strategy if improved
                existingMap[new.id] = existing.copy(
                    frequency = existing.frequency + new.frequency,
                    correctionStrategy = new.correctionStrategy.takeIf { it.isNotBlank() } ?: existing.correctionStrategy,
                    resolved = false // new evidence means we should re-evaluate
                )
            } else {
                existingMap[new.id] = new
            }
        }

        return existingMap.values.toList()
    }

    /**
     * Convert a list of misconceptions to JSON for storage in TopicKnowledgeEntity.misconceptionsJson
     */
    fun toJson(misconceptions: List<Misconception>): String {
        return try {
            gson.toJson(misconceptions)
        } catch (_: Exception) { "[]" }
    }

    /** Convert misconceptions JSON from storage back to a list */
    fun fromJson(json: String): List<Misconception> {
        return parseJson(json)
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun matchPatterns(wrongAnswers: List<WrongAnswerInfo>): List<Misconception> {
        val matched = mutableListOf<Misconception>()

        // Simple keyword matching: check if question text contains topic keywords
        val combinedText = wrongAnswers.joinToString(" ") { "${it.questionText} ${it.selectedOption}".lowercase() }

        for (pattern in misconceptionPatterns) {
            val keyword = pattern.id.split("_").firstOrNull() ?: continue
            if (combinedText.contains(keyword.lowercase())) {
                val count = wrongAnswers.count {
                    it.questionText.lowercase().contains(keyword.lowercase()) ||
                    it.subject.lowercase().contains(pattern.topic.lowercase())
                }
                if (count > 0) {
                    matched.add(
                        Misconception(
                            id = pattern.id,
                            topic = pattern.topic,
                            misconception = pattern.misconception,
                            frequency = count,
                            correctionStrategy = pattern.correctionStrategy,
                            resolved = false
                        )
                    )
                }
            }
        }

        return matched
    }

    private fun parseJson(json: String): List<Misconception> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val type = object : TypeToken<List<Misconception>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyList() }
    }
}

// ── Data classes ─────────────────────────────────────────────────────────

data class PriorMisconception(
    val id: String,
    val misconception: String,
    val topic: String,
    val correctionStrategy: String
)

data class Misconception(
    val id: String,
    val topic: String,
    val misconception: String,
    val frequency: Int,
    val correctionStrategy: String = "",
    val resolved: Boolean = false
)

private data class WrongAnswerInfo(
    val subject: String,
    val questionText: String,
    val selectedOption: String,
    val correctOption: String,
    val difficulty: Int
)
