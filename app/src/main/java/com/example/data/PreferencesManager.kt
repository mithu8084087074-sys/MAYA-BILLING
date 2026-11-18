package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.util.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bharatbill_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class PreferencesManager(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val langKey = stringPreferencesKey("app_language")
    private val ownerSessionExpiryKey = longPreferencesKey("owner_session_expiry_ms")
    private val pendingPhoneKey = stringPreferencesKey("pending_login_phone")
    private val pendingGstinKey = stringPreferencesKey("pending_login_gstin")

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[themeKey] ?: ThemeMode.SYSTEM.name
        enumValues<ThemeMode>().firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        val raw = prefs[langKey] ?: AppLanguage.ENGLISH.code
        enumValues<AppLanguage>().firstOrNull { it.code == raw } ?: AppLanguage.ENGLISH
    }

    val ownerSessionExpiryFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[ownerSessionExpiryKey] ?: 0L
    }

    val pendingPhoneFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[pendingPhoneKey] ?: ""
    }

    val pendingGstinFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[pendingGstinKey] ?: ""
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[langKey] = language.code }
    }

    suspend fun setOwnerSessionExpiry(epochMs: Long) {
        context.dataStore.edit { it[ownerSessionExpiryKey] = epochMs }
    }

    suspend fun setPendingLoginInputs(phone: String, gstin: String) {
        context.dataStore.edit {
            it[pendingPhoneKey] = phone.trim()
            it[pendingGstinKey] = gstin.trim().uppercase()
        }
    }

    suspend fun clearPendingLoginInputs() {
        context.dataStore.edit {
            it.remove(pendingPhoneKey)
            it.remove(pendingGstinKey)
        }
    }
}
