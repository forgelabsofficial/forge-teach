package com.aiteacher.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiteacher.onboarding.Assessment
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("aiteacher_prefs")

object DataStoreUtils {
    private val ASSESSMENT_KEY = stringPreferencesKey("assessment_json")
    private val ONBOARDING_STEP_KEY = intPreferencesKey("onboarding_step")

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
}
