package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object AdManager {

    private const val PREFS_NAME = "app_ad_cache_prefs"
    private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
    private const val KEY_CACHED_IMAGE_URL = "cached_image_url"
    private const val KEY_CACHED_TARGET_URL = "cached_target_url"

    private const val TWO_DAYS_MS = 2 * 24 * 60 * 60 * 1000L // 48 hours

    const val DEFAULT_IMAGE_URL = "https://raw.githubusercontent.com/cafenetnetadib24-design/english701/main/16.jpg"
    const val DEFAULT_ADS_TXT_URL = "https://raw.githubusercontent.com/cafenetnetadib24-design/english701/main/ads.txt"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Converts a standard GitHub web blob URL to a raw user content URL if needed.
     */
    private fun toRawUrl(url: String): String {
        var cleanUrl = url.trim()
        if (cleanUrl.contains("github.com") && cleanUrl.contains("/blob/")) {
            cleanUrl = cleanUrl.replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/")
        }
        return cleanUrl
    }

    fun getCachedAdInfo(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val imageUrl = prefs.getString(KEY_CACHED_IMAGE_URL, DEFAULT_IMAGE_URL) ?: DEFAULT_IMAGE_URL
        val targetUrl = prefs.getString(KEY_CACHED_TARGET_URL, "https://github.com/cafenetnetadib24-design/english701") ?: "https://github.com/cafenetnetadib24-design/english701"
        return Pair(imageUrl, targetUrl)
    }

    suspend fun refreshAdInfoIfNeeded(context: Context): Pair<String, String> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetchTime = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
        val currentTime = System.currentTimeMillis()

        var imageUrl = prefs.getString(KEY_CACHED_IMAGE_URL, DEFAULT_IMAGE_URL) ?: DEFAULT_IMAGE_URL
        var targetUrl = prefs.getString(KEY_CACHED_TARGET_URL, "") ?: ""

        if (currentTime - lastFetchTime > TWO_DAYS_MS || targetUrl.isEmpty()) {
            try {
                val rawAdsTxtUrl = toRawUrl(DEFAULT_ADS_TXT_URL)
                val request = Request.Builder()
                    .url(rawAdsTxtUrl)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()?.trim()
                    if (!bodyString.isNullOrBlank()) {
                        targetUrl = toRawUrl(bodyString)
                        imageUrl = toRawUrl(DEFAULT_IMAGE_URL)

                        prefs.edit()
                            .putLong(KEY_LAST_FETCH_TIME, currentTime)
                            .putString(KEY_CACHED_IMAGE_URL, imageUrl)
                            .putString(KEY_CACHED_TARGET_URL, targetUrl)
                            .apply()
                    }
                }
            } catch (e: Exception) {
                Log.e("AdManager", "Error fetching ad target: ${e.message}")
            }
        }

        if (targetUrl.isEmpty()) {
            targetUrl = "https://github.com/cafenetnetadib24-design/english701"
        }

        return@withContext Pair(imageUrl, targetUrl)
    }
}
