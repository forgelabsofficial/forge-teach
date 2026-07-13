package com.aiteacher.onboarding

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStreamReader

// Data classes representing the JSON-driven assessment schema
data class AssessmentSchema(
    val version: String,
    val title: String,
    val description: String,
    val questions: List<Question>,
    val scoring: Scoring? = null
)

data class Question(
    val id: String,
    val type: String,
    val prompt: String,
    val required: Boolean = false,
    val options: List<QuestionOption>? = null
)

data class QuestionOption(
    val id: String,
    val label: String
)

data class Scoring(val description: String?, val rules: List<ScoringRule>?)
data class ScoringRule(val question: String, val map: Map<String, Int>)

object AssessmentSchemaLoader {
    fun loadFromAssets(context: Context, assetName: String = "assessment_questions.json"): AssessmentSchema? {
        return try {
            val am = context.assets.open(assetName)
            val reader = InputStreamReader(am)
            val gson = Gson()
            val map = gson.fromJson(reader, Map::class.java)
            // Simple reparse into the typed model to allow flexible schema fields
            reader.close()
            am.close()
            val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
            gson.fromJson(json, AssessmentSchema::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
