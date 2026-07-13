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

    fun buildAssessment(): Assessment {
        val selected = _topics.value.filter { it.selected }
        val availability = mapOf("mon" to listOf("18:00-19:00"))
        return Assessment(
            user = _name.value.ifBlank { "Student" },
            topics = selected,
            availability = availability,
            prefs = Preferences(sessionLength = 30)
        )
    }
}
