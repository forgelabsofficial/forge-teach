package com.aiteacher.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStorage {
    private const val PREFS_NAME = "aiteacher_secure_prefs"
    private const val KEY_API_PROVIDER = "api_provider"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_API_MODEL = "api_model"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(context: Context, provider: String, apiKey: String) {
        prefs(context).edit()
            .putString(KEY_API_PROVIDER, provider)
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    fun getApiProvider(context: Context): String? = prefs(context).getString(KEY_API_PROVIDER, null)
    fun getApiKey(context: Context): String? = prefs(context).getString(KEY_API_KEY, null)
    fun saveApiModel(context: Context, modelId: String) { prefs(context).edit().putString(KEY_API_MODEL, modelId).apply() }
    fun getApiModel(context: Context): String? = prefs(context).getString(KEY_API_MODEL, null)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
