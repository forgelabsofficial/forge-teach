package com.aiteacher.onboarding

// ─── Curriculum catalogue models ─────────────────────────────────────────────

data class GradeLevel(
    val id: String,
    val label: String,
    val ageRange: String = ""
)

data class EducationSystem(
    val id: String,
    val name: String,
    val levels: List<GradeLevel>,
    val keyExams: List<String> = emptyList()
)

data class Country(
    val code: String,
    val name: String,
    val flag: String = "",
    val curriculumBody: String = "",
    val systems: List<EducationSystem>
)

data class CurriculumCatalogue(
    val version: String,
    val countries: List<Country>
)

// ─── Student profile ──────────────────────────────────────────────────────────

data class StudentProfile(
    val name: String,
    val countryCode: String,
    val countryName: String,
    val systemId: String,
    val gradeLevelId: String,
    val gradeLevelLabel: String,
    val curriculumBody: String,
    val keyExams: List<String> = emptyList()
)

// ─── Core onboarding domain models ───────────────────────────────────────────

data class Topic(val id: String, val name: String, val selected: Boolean = false)

data class Preferences(val sessionLength: Int = 30)

data class Assessment(
    val user: String,
    val profile: StudentProfile? = null,
    val topics: List<Topic>,
    val availability: Map<String, List<String>>, // e.g. {"mon": ["16:00-17:00"]}
    val prefs: Preferences,
    /** Raw score from the AI-generated capability test (null until test is completed) */
    val capabilityScore: Int? = null,
    /** Per-subject capability level determined by the AI test: subject id -> level 1-5 */
    val subjectLevels: Map<String, Int> = emptyMap()
)

// ─── AI capability test models ────────────────────────────────────────────────

/** A single question produced by the AI for the capability test */
data class CapabilityQuestion(
    val id: String,
    val subject: String,
    val questionText: String,
    val options: List<String>,        // A, B, C, D choices
    val correctIndex: Int,            // 0-based index of correct option
    val difficulty: Int = 1           // 1 easy → 5 hard
)

data class CapabilityTest(
    val questions: List<CapabilityQuestion>
)

/** Per-question result after the student answers */
data class CapabilityAnswer(
    val questionId: String,
    val selectedIndex: Int,
    val correct: Boolean
)

data class CapabilityResult(
    val answers: List<CapabilityAnswer>,
    val totalScore: Int,
    val maxScore: Int,
    /** Subject id → percentage correct for that subject */
    val subjectScores: Map<String, Int>
)

// ─── Subjects catalogue ───────────────────────────────────────────────────────

data class SubjectEntry(
    val id: String,
    val label: String,
    val category: String,
    val aliases: List<String> = emptyList()
)

data class SubjectCategory(
    val id: String,
    val label: String,
    val subjects: List<SubjectEntry>
)

data class SubjectsCatalogue(
    val version: String,
    val categories: List<SubjectCategory>
) {
    val allSubjects: List<SubjectEntry> get() = categories.flatMap { it.subjects }
}

// ─── Plan models ──────────────────────────────────────────────────────────────

data class SessionItem(
    val date: String,
    val topic: String,
    val duration: Int,
    val isoDateTime: String? = null
)

data class Plan(val weeks: Int, val sessions: List<SessionItem>)
