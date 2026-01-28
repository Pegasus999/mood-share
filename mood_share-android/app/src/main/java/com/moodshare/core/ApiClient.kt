package com.moodshare.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ApiClient(private val baseUrlProvider: suspend () -> String) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun register(name: String): Profile = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        val json = JSONObject().put("name", name).toString()
        val request = Request.Builder()
            .url("$baseUrl/register")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(TAG, "POST /register -> ${response.code}")
            if (!response.isSuccessful) {
                throw ApiException(response.code, body)
            }
            parseProfile(body)
        }
    }

    suspend fun getMe(name: String): Profile = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
        val request = Request.Builder()
            .url("$baseUrl/me?name=$encoded")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(TAG, "GET /me -> ${response.code}")
            if (!response.isSuccessful) {
                throw ApiException(response.code, body)
            }
            parseProfile(body)
        }
    }

    suspend fun patchStatus(
        name: String,
        mood: String? = null,
        isFocused: Boolean? = null
    ): Profile = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        val json = JSONObject().put("name", name)
        if (isFocused != null) {
            json.put("isFocused", isFocused)
        }
        if (!mood.isNullOrBlank()) {
            json.put("mood", mood)
        }
        val request = Request.Builder()
            .url("$baseUrl/status")
            .patch(json.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(TAG, "PATCH /status -> ${response.code}")
            if (!response.isSuccessful) {
                throw ApiException(response.code, body)
            }
            parseProfile(body)
        }
    }

    suspend fun searchUsers(query: String): List<Profile> = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val request = Request.Builder()
            .url("$baseUrl/search?q=$encoded")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(TAG, "GET /search -> ${response.code}")
            if (!response.isSuccessful) {
                throw ApiException(response.code, body)
            }
            parseProfileList(body)
        }
    }

    suspend fun setPartner(name: String, partnerId: String?): Profile = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        val json = JSONObject().put("name", name)
        if (partnerId != null) {
            json.put("partner_id", partnerId)
        } else {
            json.put("partner_id", JSONObject.NULL)
        }
        val request = Request.Builder()
            .url("$baseUrl/partner")
            .patch(json.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(TAG, "PATCH /partner -> ${response.code}")
            if (!response.isSuccessful) {
                throw ApiException(response.code, body)
            }
            parseProfile(body)
        }
    }

    suspend fun getPartner(name: String): Profile = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
        val request = Request.Builder()
            .url("$baseUrl/partner?name=$encoded")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(TAG, "GET /partner -> ${response.code}")
            if (!response.isSuccessful) {
                throw ApiException(response.code, body)
            }
            parseProfile(body)
        }
    }

    private fun parseProfile(body: String): Profile {
        val json = JSONObject(body)
        val partnerId = if (json.isNull("partner_id")) null else json.optString("partner_id")
        val partner = if (json.has("partner") && !json.isNull("partner")) {
            val partnerJson = json.getJSONObject("partner")
            PartnerProfile(
                name = partnerJson.optString("name"),
                avatar = partnerJson.optString("avatar"),
                mood = partnerJson.optString("mood"),
                isFocused = partnerJson.optBoolean("isFocused", false)
            )
        } else null

        return Profile(
            name = json.optString("name"),
            avatar = json.optString("avatar"),
            mood = json.optString("mood"),
            isFocused = json.optBoolean("isFocused", false),
            partnerId = partnerId,
            partner = partner
        )
    }

    private fun parseProfileList(body: String): List<Profile> {
        val jsonArray = org.json.JSONArray(body)
        val profiles = mutableListOf<Profile>()
        for (i in 0 until jsonArray.length()) {
            profiles.add(parseProfile(jsonArray.getJSONObject(i).toString()))
        }
        return profiles
    }

    class ApiException(val code: Int, val body: String) : RuntimeException(
        "API error $code: $body"
    )

    companion object {
        private const val TAG = "MoodShareApi"
    }
}
