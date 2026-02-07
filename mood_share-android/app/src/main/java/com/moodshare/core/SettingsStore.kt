package com.moodshare.core

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import com.moodshare.core.Profile

private const val SETTINGS_NAME = "mood_share_settings"

private val Context.dataStore by preferencesDataStore(name = SETTINGS_NAME)

class SettingsStore(private val context: Context) {
    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val lastStatus = stringPreferencesKey("last_status")
        val profileName = stringPreferencesKey("profile_name")
        val profileMood = stringPreferencesKey("profile_mood")
        val profileAvatar = stringPreferencesKey("profile_avatar")
        val profileIsFocused = booleanPreferencesKey("profile_is_focused")
        val profilePartnerId = stringPreferencesKey("profile_partner_id")
        val partnerName = stringPreferencesKey("partner_name")
        val partnerMood = stringPreferencesKey("partner_mood")
        val partnerAvatar = stringPreferencesKey("partner_avatar")
        val partnerIsFocused = booleanPreferencesKey("partner_is_focused")
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.baseUrl] ?: DEFAULT_BASE_URL
    }

    val lastStatus: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.lastStatus]
    }

    val profileName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.profileName]
    }

    val profileMood: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.profileMood]
    }

    val profileAvatar: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.profileAvatar]
    }

    val profileIsFocused: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[Keys.profileIsFocused]
    }

    val profilePartnerId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.profilePartnerId]
    }

    val partnerName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.partnerName]
    }

    val partnerMood: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.partnerMood]
    }

    val partnerAvatar: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.partnerAvatar]
    }

    val partnerIsFocused: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[Keys.partnerIsFocused]
    }

    suspend fun setBaseUrl(value: String) {
        context.dataStore.edit { prefs: MutablePreferences ->
            prefs[Keys.baseUrl] = value
        }
    }

    suspend fun setLastStatus(value: String) {
        context.dataStore.edit { prefs: MutablePreferences ->
            prefs[Keys.lastStatus] = value
        }
    }

    suspend fun saveProfile(profile: Profile) {
        context.dataStore.edit { prefs: MutablePreferences ->
            prefs[Keys.profileName] = profile.name
            prefs[Keys.profileMood] = profile.mood
            prefs[Keys.profileAvatar] = profile.avatar
            prefs[Keys.profileIsFocused] = profile.isFocused
            if (profile.partnerId != null) {
                prefs[Keys.profilePartnerId] = profile.partnerId
            } else {
                prefs.remove(Keys.profilePartnerId)
            }

            // Save partner profile data for widget
            if (profile.partner != null) {
                prefs[Keys.partnerName] = profile.partner.name
                prefs[Keys.partnerMood] = profile.partner.mood
                prefs[Keys.partnerAvatar] = profile.partner.avatar
                prefs[Keys.partnerIsFocused] = profile.partner.isFocused
            } else {
                prefs.remove(Keys.partnerName)
                prefs.remove(Keys.partnerMood)
                prefs.remove(Keys.partnerAvatar)
                prefs.remove(Keys.partnerIsFocused)
            }
        }
    }

    suspend fun storedProfileName(): String? {
        return context.dataStore.data.map { prefs -> prefs[Keys.profileName] }.firstOrNull()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://reflectiondz.online"
    }
}
