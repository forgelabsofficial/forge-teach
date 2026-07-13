package com.aiteacher.ui

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiteacher.onboarding.Assessment
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("aiteacher_prefs")

object DataStoreUtils {
    private val ASSESSMENT_KEY = preferencesKey<String>("assessment_json")

    suspend fun saveAssessment(context: Context, assessment: Assessment) {
        val json = Gson().toJson(assessment)
        context.dataStore.edit { prefs ->
            prefs[ASSESSMENT_KEY] = json
        }
    }

    suspend fun loadAssessment(context: Context): Assessment? {
        val prefs = context.dataStore.data.first()
        val json = prefs[ASSESSMENT_KEY] ?: return null
        return Gson().fromJson(json, Assessment::class.java)
    }
}
