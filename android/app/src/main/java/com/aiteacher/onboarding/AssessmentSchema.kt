package com.aiteacher.onboarding

import android.content.Context
import com.google.gson.Gson

// ─── Profile schema (assessment_questions.json) ───────────────────────────────

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
    fun loadFromAssets(
        context: Context,
        assetName: String = "assessment_questions.json"
    ): AssessmentSchema? {
        return try {
            val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
            Gson().fromJson(json, AssessmentSchema::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

// ─── Subjects catalogue (subjects_catalogue.json) ────────────────────────────

private data class SubjectEntryRaw(
    val id: String, val label: String, val category: String, val aliases: List<String> = emptyList()
)
private data class SubjectCategoryRaw(val id: String, val label: String, val subjects: List<SubjectEntryRaw>)
private data class SubjectsCatalogueRaw(val version: String, val categories: List<SubjectCategoryRaw>)

object SubjectsCatalogueLoader {
    fun loadFromAssets(context: Context, assetName: String = "subjects_catalogue.json"): SubjectsCatalogue? {
        return try {
            val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
            val raw = Gson().fromJson(json, SubjectsCatalogueRaw::class.java)
            SubjectsCatalogue(
                version = raw.version,
                categories = raw.categories.map { c ->
                    SubjectCategory(
                        id = c.id, label = c.label,
                        subjects = c.subjects.map { s ->
                            SubjectEntry(id = s.id, label = s.label, category = s.category, aliases = s.aliases)
                        }
                    )
                }
            )
        } catch (e: Exception) { null }
    }
}

// ─── Curriculum catalogue (curriculum_catalogue.json) ────────────────────────

// Gson-friendly flat versions for parsing (nested lists need simple field names)
private data class GradeLevelRaw(val id: String, val label: String, val ageRange: String = "")
private data class EducationSystemRaw(
    val id: String,
    val name: String,
    val levels: List<GradeLevelRaw>,
    val keyExams: List<String> = emptyList()
)
private data class CountryRaw(
    val code: String,
    val name: String,
    val flag: String = "",
    val curriculumBody: String = "",
    val systems: List<EducationSystemRaw>
)
private data class CurriculumCatalogueRaw(
    val version: String,
    val countries: List<CountryRaw>
)

object CurriculumLoader {
    fun loadFromAssets(
        context: Context,
        assetName: String = "curriculum_catalogue.json"
    ): CurriculumCatalogue? {
        return try {
            val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
            val raw = Gson().fromJson(json, CurriculumCatalogueRaw::class.java)
            CurriculumCatalogue(
                version = raw.version,
                countries = raw.countries.map { c ->
                    Country(
                        code = c.code,
                        name = c.name,
                        flag = c.flag,
                        curriculumBody = c.curriculumBody,
                        systems = c.systems.map { s ->
                            EducationSystem(
                                id = s.id,
                                name = s.name,
                                levels = s.levels.map { l -> GradeLevel(l.id, l.label, l.ageRange) },
                                keyExams = s.keyExams
                            )
                        }
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }
}
