package com.aiteacher.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiteacher.ai.CapabilityTestClient
import com.aiteacher.ai.ModelInfo
import com.aiteacher.ai.ModelRegistry
import com.aiteacher.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

class OnboardingViewModel : ViewModel() {

    // ─── AI provider ─────────────────────────────────────────────────────────

    private val _provider = MutableStateFlow("openai")
    val provider: StateFlow<String> = _provider

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading

    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError: StateFlow<String?> = _modelsError

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel

    fun setProvider(p: String) { _provider.value = p }
    fun setApiKey(k: String) { _apiKey.value = k }
    fun selectModel(id: String) { _selectedModel.value = id }

    /** Save credentials immediately so they're available for test generation. */
    fun saveCredentials(context: Context) {
        SecureStorage.saveApiKey(context, _provider.value, _apiKey.value)
        _selectedModel.value?.let { SecureStorage.saveApiModel(context, it) }
    }

    fun clearModels() {
        _models.value = emptyList()
        _selectedModel.value = null
        _modelsError.value = null
    }

    fun loadModels(context: Context) {
        _modelsLoading.value = true
        _modelsError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loader = ModelRegistry.getExtendedLoader(_provider.value)
                val list = loader.listModels(_apiKey.value)
                _models.value = list
                _modelsLoading.value = false
                if (list.isNotEmpty()) {
                    _selectedModel.value = list.first().id
                    SecureStorage.saveApiKey(context, _provider.value, _apiKey.value)
                    SecureStorage.saveApiModel(context, list.first().id)
                } else {
                    _modelsError.value = "No models found. Check your API key."
                }
            } catch (e: Exception) {
                _modelsLoading.value = false
                _modelsError.value = "Failed to load models: ${e.message}"
            }
        }
    }

    // ─── Curriculum catalogue ─────────────────────────────────────────────────

    private val _catalogue = MutableStateFlow<CurriculumCatalogue?>(null)
    val catalogue: StateFlow<CurriculumCatalogue?> = _catalogue

    private val _subjectsCatalogue = MutableStateFlow<SubjectsCatalogue?>(null)
    val subjectsCatalogue: StateFlow<SubjectsCatalogue?> = _subjectsCatalogue

    private val _selectedCountry = MutableStateFlow<Country?>(null)
    val selectedCountry: StateFlow<Country?> = _selectedCountry

    private val _selectedSystem = MutableStateFlow<EducationSystem?>(null)
    val selectedSystem: StateFlow<EducationSystem?> = _selectedSystem

    private val _selectedGrade = MutableStateFlow<GradeLevel?>(null)
    val selectedGrade: StateFlow<GradeLevel?> = _selectedGrade

    fun loadCatalogue(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _catalogue.value = CurriculumLoader.loadFromAssets(context)
            _subjectsCatalogue.value = SubjectsCatalogueLoader.loadFromAssets(context)
        }
    }

    fun selectCountry(country: Country) {
        _selectedCountry.value = country
        // Auto-select first system and clear grade
        _selectedSystem.value = country.systems.firstOrNull()
        _selectedGrade.value = null
    }

    fun selectSystem(system: EducationSystem) {
        _selectedSystem.value = system
        _selectedGrade.value = null
    }

    fun selectGrade(grade: GradeLevel) {
        _selectedGrade.value = grade
    }

    fun buildStudentProfile(): StudentProfile? {
        val c = _selectedCountry.value ?: return null
        val s = _selectedSystem.value ?: return null
        val g = _selectedGrade.value ?: return null
        return StudentProfile(
            name = _name.value.ifBlank { "Student" },
            countryCode = c.code,
            countryName = c.name,
            systemId = s.id,
            gradeLevelId = g.id,
            gradeLevelLabel = g.label,
            curriculumBody = c.curriculumBody,
            keyExams = s.keyExams
        )
    }

    // ─── Student profile form ─────────────────────────────────────────────────

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _schema = MutableStateFlow<AssessmentSchema?>(null)
    val schema: StateFlow<AssessmentSchema?> = _schema

    private val _answers = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val answers: StateFlow<Map<String, Any?>> = _answers

    private val _topics = MutableStateFlow(
        listOf(
            Topic("math",    "Mathematics",            true),
            Topic("science", "Science",                false),
            Topic("english", "English / Language Arts",false),
            Topic("history", "History & Social Studies",false),
            Topic("ict",     "ICT / Computing",         false),
            Topic("arts",    "Arts & Creative",         false)
        )
    )
    val topics: StateFlow<List<Topic>> = _topics

    fun setName(n: String) { _name.value = n }

    fun toggleTopic(id: String) {
        _topics.update { list -> list.map { if (it.id == id) it.copy(selected = !it.selected) else it } }
    }

    fun setAnswer(questionId: String, value: Any?) {
        _answers.value = _answers.value.toMutableMap().also { it[questionId] = value }
    }

    fun loadAssessmentSchema(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _schema.value = AssessmentSchemaLoader.loadFromAssets(context)
        }
    }

    // ─── AI capability test ───────────────────────────────────────────────────

    private val _capabilityTest = MutableStateFlow<CapabilityTest?>(null)
    val capabilityTest: StateFlow<CapabilityTest?> = _capabilityTest

    private val _testGenerating = MutableStateFlow(false)
    val testGenerating: StateFlow<Boolean> = _testGenerating

    private val _testError = MutableStateFlow<String?>(null)
    val testError: StateFlow<String?> = _testError

    /** Student answers keyed by question id → selected option index (0-based) */
    private val _testAnswers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val testAnswers: StateFlow<Map<String, Int>> = _testAnswers

    private val _testResult = MutableStateFlow<CapabilityResult?>(null)
    val testResult: StateFlow<CapabilityResult?> = _testResult

    fun generateCapabilityTest(context: Context) {
        val profile = buildStudentProfile()
            ?: StudentProfile("Student", "xx", "Unknown", "sys", "g1", "Primary", "General", emptyList())
        val selectedSubjects = selectedSubjectIds()
        _testGenerating.value = true
        _testError.value = null
        _capabilityTest.value = null
        _testAnswers.value = emptyMap()
        _testResult.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val test = CapabilityTestClient.generateTest(context, profile, selectedSubjects)
                _capabilityTest.value = test
                _testGenerating.value = false
            } catch (e: Exception) {
                // generateTest never throws (returns fallback), but guard anyway
                val fallback = CapabilityTestClient.builtInAdaptiveFallback(profile, selectedSubjects)
                _capabilityTest.value = CapabilityTest(fallback)
                _testGenerating.value = false
                _testError.value = "Using built-in questions."
            }
        }
    }

    fun answerTestQuestion(questionId: String, selectedIndex: Int) {
        _testAnswers.value = _testAnswers.value.toMutableMap().also { it[questionId] = selectedIndex }
    }

    fun submitTest() {
        val test = _capabilityTest.value ?: return
        val answers = _testAnswers.value
        val resultAnswers = test.questions.map { q ->
            val selected = answers[q.id] ?: -1
            CapabilityAnswer(
                questionId = q.id,
                selectedIndex = selected,
                correct = selected == q.correctIndex
            )
        }
        val totalScore = resultAnswers.count { it.correct }
        val maxScore = test.questions.size

        // Per-subject score as percentage
        val subjectScores = test.questions
            .groupBy { it.subject }
            .mapValues { (_, qs) ->
                val correct = qs.count { q -> answers[q.id] == q.correctIndex }
                if (qs.isEmpty()) 0 else (correct * 100) / qs.size
            }

        _testResult.value = CapabilityResult(
            answers = resultAnswers,
            totalScore = totalScore,
            maxScore = maxScore,
            subjectScores = subjectScores
        )
    }

    // ─── Assessment & plan ────────────────────────────────────────────────────

    fun buildAssessment(): Assessment {
        val s = _schema.value
        val a = _answers.value
        val profile = buildStudentProfile()

        val topics = if (s != null && a.isNotEmpty()) {
            val goals = a["q_goals"] as? List<*>
            if (goals != null) {
                goals.mapNotNull { g ->
                    val id = g as? String ?: return@mapNotNull null
                    val opt = s.questions.flatMap { it.options ?: emptyList() }.firstOrNull { it.id == id }
                    Topic(id = id, name = opt?.label ?: id, selected = true)
                }
            } else _topics.value.filter { it.selected }
        } else _topics.value.filter { it.selected }

        val availability = (a["q_availability"] as? String)
            ?.let { parseAvailabilityString(it) }
            ?: mapOf("mon" to listOf("16:00-17:00"))
        val sessionLen = (a["q_session_length"] as? String)?.toIntOrNull() ?: 30

        val result = _testResult.value
        return Assessment(
            user = profile?.name ?: _name.value.ifBlank { "Student" },
            profile = profile,
            topics = topics.ifEmpty { _topics.value.filter { it.selected } },
            availability = availability,
            prefs = Preferences(sessionLength = sessionLen),
            capabilityScore = result?.totalScore,
            subjectLevels = result?.subjectScores?.mapValues { (_, pct) ->
                when {
                    pct >= 80 -> 5
                    pct >= 60 -> 4
                    pct >= 40 -> 3
                    pct >= 20 -> 2
                    else -> 1
                }
            } ?: emptyMap()
        )
    }

    fun generatePreviewPlan(weeks: Int = 4): Plan? {
        return try {
            val a = buildAssessment()
            val tz = (_answers.value["q_timezone"] as? String)?.takeIf { it.isNotBlank() }
            val zone = try { if (tz != null) ZoneId.of(tz) else null } catch (e: Exception) { null }
            if (zone != null) TimetableEngine.generateSchedule(a, weeks = weeks, zone = zone)
            else TimetableEngine.generateSchedule(a, weeks = weeks)
        } catch (e: Exception) {
            null
        }
    }

    fun calculateScore(): Int {
        val s = _schema.value ?: return 0
        val a = _answers.value
        return s.scoring?.rules?.sumOf { rule ->
            val answer = a[rule.question] as? String
            rule.map[answer] ?: 0
        } ?: 0
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun selectedSubjectIds(): List<String> {
        val fromAnswers = (_answers.value["q_goals"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()
        return fromAnswers.ifEmpty { _topics.value.filter { it.selected }.map { it.id } }
    }

    private fun parseAvailabilityString(s: String): Map<String, List<String>> {
        return s.split("\n", ",").mapNotNull { part ->
            val p = part.trim()
            if (p.isBlank()) return@mapNotNull null
            val kv = p.split(":").map { it.trim() }
            if (kv.size < 2) return@mapNotNull null
            kv[0] to kv[1].split(";").map { it.trim() }
        }.toMap()
    }
}
