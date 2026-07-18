package com.aiteacher.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiteacher.onboarding.Assessment
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("aiteacher_prefs")

object DataStoreUtils {
    private val ASSESSMENT_KEY      = stringPreferencesKey("assessment_json")
    private val ONBOARDING_STEP_KEY  = intPreferencesKey("onboarding_step")
    private val DASHBOARD_MODE_KEY   = stringPreferencesKey("dashboard_mode")
    private val STREAK_KEY           = intPreferencesKey("streak_days")
    private val LAST_STUDY_DAY_KEY   = longPreferencesKey("last_study_day_epoch")
    private val TOTAL_XP_KEY         = intPreferencesKey("total_xp")

    suspend fun saveAssessment(context: Context, assessment: Assessment) {
        val json = Gson().toJson(assessment)
        context.dataStore.edit { prefs -> prefs[ASSESSMENT_KEY] = json }
    }

    suspend fun loadAssessment(context: Context): Assessment? {
        val prefs = context.dataStore.data.first()
        val json = prefs[ASSESSMENT_KEY] ?: return null
        return Gson().fromJson(json, Assessment::class.java)
    }

    suspend fun saveOnboardingStep(context: Context, step: Int) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_STEP_KEY] = step }
    }

    suspend fun getOnboardingStep(context: Context): Int {
        return context.dataStore.data.first()[ONBOARDING_STEP_KEY] ?: 0
    }

    suspend fun clearOnboardingStep(context: Context) {
        context.dataStore.edit { prefs -> prefs.remove(ONBOARDING_STEP_KEY) }
    }

    suspend fun saveDashboardMode(context: Context, mode: String) {
        context.dataStore.edit { it[DASHBOARD_MODE_KEY] = mode }
    }

    suspend fun getDashboardMode(context: Context): String {
        return context.dataStore.data.first()[DASHBOARD_MODE_KEY] ?: "study"
    }

    fun dashboardModeFlow(context: Context): Flow<String> =
        context.dataStore.data.map { it[DASHBOARD_MODE_KEY] ?: "study" }

    suspend fun getStreak(context: Context): Int =
        context.dataStore.data.first()[STREAK_KEY] ?: 0

    suspend fun getTotalXp(context: Context): Int =
        context.dataStore.data.first()[TOTAL_XP_KEY] ?: 0

    /** Call when a study session, quiz, or exam is completed. Updates streak + XP. */
    suspend fun recordActivity(context: Context, xpGained: Int) {
        val todayEpochDay = java.time.LocalDate.now().toEpochDay()
        context.dataStore.edit { prefs ->
            val lastDay = prefs[LAST_STUDY_DAY_KEY] ?: -2L
            val streak = prefs[STREAK_KEY] ?: 0
            prefs[STREAK_KEY] = when (todayEpochDay - lastDay) {
                0L   -> streak                  // already recorded today
                1L   -> streak + 1              // consecutive day
                else -> 1                       // streak broken
            }
            prefs[LAST_STUDY_DAY_KEY] = todayEpochDay
            prefs[TOTAL_XP_KEY] = (prefs[TOTAL_XP_KEY] ?: 0) + xpGained
        }
    }
}
