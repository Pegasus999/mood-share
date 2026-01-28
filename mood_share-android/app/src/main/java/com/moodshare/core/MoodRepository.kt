package com.moodshare.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class MoodRepository(context: Context) {
    private val settings = SettingsStore(context.applicationContext)
    private val api = ApiClient { settings.baseUrl.first() }

    suspend fun register(name: String): Profile {
        val profile = api.register(name)
        settings.saveProfile(profile)
        Log.d(TAG, "Registered profile: $profile")
        return profile
    }

    suspend fun refreshProfile(): Profile? {
        val name = settings.profileName.firstOrNull()
        if (name.isNullOrBlank()) {
            return null
        }
        val profile = api.getMe(name)
        settings.saveProfile(profile)
        Log.d(TAG, "Fetched profile: $profile")
        return profile
    }

    suspend fun updateFocus(isFocused: Boolean): Profile? {
        val name = settings.profileName.firstOrNull()
        if (name.isNullOrBlank()) {
            return null
        }
        val profile = api.patchStatus(name, isFocused = isFocused)
        settings.saveProfile(profile)
        Log.d(TAG, "Updated status: $profile")
        return profile
    }

    suspend fun updateMood(mood: String): Profile? {
        val name = settings.profileName.firstOrNull()
        if (name.isNullOrBlank()) {
            return null
        }
        val profile = api.patchStatus(name, mood = mood)
        settings.saveProfile(profile)
        Log.d(TAG, "Updated mood: $profile")
        return profile
    }

    suspend fun getStoredName(): String? {
        return settings.profileName.firstOrNull()
    }

    suspend fun setBaseUrl(url: String) {
        settings.setBaseUrl(url)
    }

    suspend fun searchUsers(query: String): List<Profile> {
        return api.searchUsers(query)
    }

    suspend fun setPartner(partnerId: String?): Profile? {
        val name = settings.profileName.firstOrNull()
        if (name.isNullOrBlank()) {
            return null
        }
        val profile = api.setPartner(name, partnerId)
        settings.saveProfile(profile)
        Log.d(TAG, "Set partner: $profile")
        return profile
    }

    suspend fun refreshPartner(): Profile? {
        val name = settings.profileName.firstOrNull()
        if (name.isNullOrBlank()) {
            return null
        }
        val profile = api.getPartner(name)
        settings.saveProfile(profile)
        Log.d(TAG, "Refreshed partner: $profile")
        return profile
    }

    companion object {
        private const val TAG = "MoodShareRepo"
    }
}
