package com.aiteacher.onboarding

// Core data models for onboarding and assessment

data class Topic(val id: String, val name: String, val selected: Boolean = false)

data class Preferences(val sessionLength: Int = 30)

data class Assessment(
    val user: String,
    val topics: List<Topic>,
    val availability: Map<String, List<String>>, // e.g., {"mon": ["18:00-19:00"]}
    val prefs: Preferences
)

// Plan models (used by AI client mock)
data class SessionItem(val date: String, val topic: String, val duration: Int, val isoDateTime: String? = null)
data class Plan(val weeks: Int, val sessions: List<SessionItem>)
