package com.aiteacher.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.aiteacher.ai.ModelInfo
import com.aiteacher.ai.ModelRegistry
import com.aiteacher.security.SecureStorage
import android.content.Context

class OnboardingViewModel : ViewModel() {
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _topics = MutableStateFlow(
        listOf(
            Topic("vars", "Variables", true),
            Topic("control", "Control Flow", false),
            Topic("data", "Data Structures", false)
        )
    )
    val topics: StateFlow<List<Topic>> = _topics

    private val _provider = MutableStateFlow("openai")
    val provider: StateFlow<String> = _provider

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel

    // schema-driven onboarding
    private val _schema = MutableStateFlow<com.aiteacher.onboarding.AssessmentSchema?>(null)
    val schema: StateFlow<com.aiteacher.onboarding.AssessmentSchema?> = _schema

    // raw answers mapped by question id
    private val _answers = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val answers: StateFlow<Map<String, Any?>> = _answers

    fun setName(n: String) { _name.value = n }

    fun toggleTopic(id: String) {
        _topics.update { list ->
            list.map { if (it.id == id) it.copy(selected = !it.selected) else it }
        }
    }

    fun setProvider(p: String) { _provider.value = p }
    fun setApiKey(k: String) { _apiKey.value = k }

    fun selectModel(id: String) {
        _selectedModel.value = id
    }

    fun loadModels(context: android.content.Context) {
        val prov = _provider.value
        val key = _apiKey.value
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val loader = ModelRegistry.getLoader(prov)
                val list = loader.listModels(key)
                _models.value = list
                if (list.isNotEmpty()) {
                    _selectedModel.value = list.first().id
                    SecureStorage.saveApiProvider(context, prov)
                    SecureStorage.saveApiKey(context, prov, key)
                    SecureStorage.saveApiModel(context, list.first().id)
                }
            } catch (e: Exception) {
                // ignore – keep empty models
            }
        }
    }

    fun loadAssessmentSchema(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = AssessmentSchemaLoader.loadFromAssets(context)
                _schema.value = s
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun setAnswer(questionId: String, value: Any?) {
        _answers.value = _answers.value.toMutableMap().also { it[questionId] = value }
    }

    fun buildAssessment(): Assessment {
        // If schema/answers present, build from answers
        val s = _schema.value
        val a = _answers.value
        if (s != null && a.isNotEmpty()) {
            val user = (a["q_name"] as? String)?.ifBlank { "Student" } ?: _name.value.ifBlank { "Student" }
            val topics = mutableListOf<Topic>()
            val goals = a["q_goals"] as? List<*>
            if (goals != null) {
                goals.forEach { g ->
                    val id = g as? String ?: return@forEach
                    val opt = s.questions.flatMap { it.options ?: emptyList() }.firstOrNull { it.id == id }
                    topics.add(Topic(id = id, name = opt?.label ?: id, selected = true))
                }
            } else {
                // fallback to selected topics
                topics.addAll(_topics.value.filter { it.selected })
            }
            val availability = (a["q_availability"] as? String)?.let { parseAvailabilityString(it) } ?: mapOf("mon" to listOf("18:00-19:00"))
            val sessionLen = (a["q_session_length"] as? String)?.toIntOrNull() ?: 30
            return Assessment(user = user, topics = topics, availability = availability, prefs = Preferences(sessionLength = sessionLen))
        }

        val selected = _topics.value.filter { it.selected }
        val availability = mapOf("mon" to listOf("18:00-19:00"))
        return Assessment(
            user = _name.value.ifBlank { "Student" },
            topics = selected,
            availability = availability,
            prefs = Preferences(sessionLength = 30)
        )
    }

    // Build a preview Plan using the TimetableEngine. Returns null on error.
    fun generatePreviewPlan(weeks: Int = 4): Plan? {
        return try {
            val a = buildAssessment()
            // check for timezone override
            val tz = (_answers.value["q_timezone"] as? String)?.takeIf { it.isNotBlank() }
            val zone = try { if (tz != null) ZoneId.of(tz) else null } catch (e: Exception) { null }
            if (zone != null) TimetableEngine.generateSchedule(a, weeks = weeks, zone = zone) else TimetableEngine.generateSchedule(a, weeks = weeks)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAvailabilityString(s: String): Map<String, List<String>> {
        // Very small parser: expects lines like "mon: 18:00-19:00, wed: 18:00-19:00"
        return s.split("\n", ",").mapNotNull { part ->
            val p = part.trim()
            if (p.isBlank()) return@mapNotNull null
            val kv = p.split(":").map { it.trim() }
            if (kv.size < 2) return@mapNotNull null
            kv[0] to kv[1].split(";").map { it.trim() }
        }.toMap()
    }
}
