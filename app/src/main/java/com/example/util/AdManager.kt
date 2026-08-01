package com.example.util

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AdItem(
    val imageUrl: String,
    val clickUrl: String
)

object AdManager {

    private const val PREFS_NAME = "app_ad_cache_prefs"
    private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
    private const val KEY_CACHED_ADS_JSON = "cached_ads_json"
    private const val KEY_HAS_SHOWN_FIRST_AD = "has_shown_first_ad"
    private const val KEY_LAST_AD_SHOWN_TIME = "last_ad_shown_time"
    private const val KEY_LAST_SHOWN_IMAGE_URL = "last_shown_image_url"
    private const val KEY_SHOWN_ADS_HISTORY = "shown_ads_history"

    private const val KEY_CACHED_SOURCE_URL = "cached_source_url"

    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L // 24 hours (daily refresh)
    private const val FOUR_MINUTES_MS = 4 * 60 * 1000L // 4 minutes

    const val DEFAULT_IMAGE_URL = "https://raw.githubusercontent.com/cafenetnetadib24-design/english701/main/16.jpg"
    const val DEFAULT_CLICK_URL = "https://github.com/cafenetnetadib24-design/english701"
    const val DEFAULT_ADS_TXT_URL = "https://raw.githubusercontent.com/cafenetnetadib24-design/ads/refs/heads/main/ads.html"

    private var hasShownInSession = false

    fun shouldShowAd(context: Context): Boolean {
        if (!hasShownInSession) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastShown = prefs.getLong(KEY_LAST_AD_SHOWN_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastShown) >= FOUR_MINUTES_MS
    }

    fun markAdShown(context: Context) {
        hasShownInSession = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_AD_SHOWN_TIME, System.currentTimeMillis()).apply()
    }

    fun hasShownFirstAd(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_SHOWN_FIRST_AD, false)
    }

    fun markFirstAdShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HAS_SHOWN_FIRST_AD, true).apply()
    }

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

    /**
     * Parses JSON string into a list of AdItem objects.
     * Supports extracting JSON arrays/objects even if wrapped inside HTML tags or text.
     */
    fun parseAdsJson(jsonString: String?): List<AdItem> {
        if (jsonString.isNullOrBlank()) return emptyList()
        val list = mutableListOf<AdItem>()
        try {
            var trimmed = jsonString.trim()
            val startBracket = trimmed.indexOf('[').let { if (it != -1) it else Int.MAX_VALUE }
            val startBrace = trimmed.indexOf('{').let { if (it != -1) it else Int.MAX_VALUE }
            val firstJsonChar = minOf(startBracket, startBrace)
            if (firstJsonChar != Int.MAX_VALUE && firstJsonChar > 0) {
                trimmed = trimmed.substring(firstJsonChar)
            }
            val endBracket = trimmed.lastIndexOf(']').let { if (it != -1) it else -1 }
            val endBrace = trimmed.lastIndexOf('}').let { if (it != -1) it else -1 }
            val lastJsonChar = maxOf(endBracket, endBrace)
            if (lastJsonChar != -1 && lastJsonChar < trimmed.length - 1) {
                trimmed = trimmed.substring(0, lastJsonChar + 1)
            }

            if (trimmed.startsWith("[")) {
                val jsonArray = JSONArray(trimmed)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val img = obj.optString("imageUrl", obj.optString("image_url", obj.optString("image", "")))
                    val click = obj.optString("clickUrl", obj.optString("click_url", obj.optString("targetUrl", obj.optString("url", obj.optString("link", "")))))
                    if (img.isNotBlank() && click.isNotBlank() && !img.contains("YOUR_GITHUB_PAGES_URL") && !img.contains("example.com")) {
                        list.add(AdItem(toRawUrl(img), toRawUrl(click)))
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val jsonObject = JSONObject(trimmed)
                val jsonArray = jsonObject.optJSONArray("ads")
                    ?: jsonObject.optJSONArray("advertisements")
                    ?: jsonObject.optJSONArray("items")
                if (jsonArray != null) {
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val img = obj.optString("imageUrl", obj.optString("image_url", obj.optString("image", "")))
                        val click = obj.optString("clickUrl", obj.optString("click_url", obj.optString("targetUrl", obj.optString("url", obj.optString("link", "")))))
                        if (img.isNotBlank() && click.isNotBlank() && !img.contains("YOUR_GITHUB_PAGES_URL") && !img.contains("example.com")) {
                            list.add(AdItem(toRawUrl(img), toRawUrl(click)))
                        }
                    }
                } else {
                    val img = jsonObject.optString("imageUrl", jsonObject.optString("image_url", ""))
                    val click = jsonObject.optString("clickUrl", jsonObject.optString("click_url", ""))
                    if (img.isNotBlank() && click.isNotBlank()) {
                        list.add(AdItem(toRawUrl(img), toRawUrl(click)))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AdManager", "Error parsing ads JSON: ${e.message}")
        }
        return list
    }

    /**
     * Selects a random non-repeating ad from cached ads.
     * Ensures that today's ad differs from yesterday's/recently shown ads.
     */
    fun getRandomAdInfo(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString(KEY_CACHED_ADS_JSON, null)
        val adsList = parseAdsJson(cachedJson)

        if (adsList.isEmpty()) {
            return Pair(DEFAULT_IMAGE_URL, DEFAULT_CLICK_URL)
        }

        if (adsList.size == 1) {
            return Pair(adsList[0].imageUrl, adsList[0].clickUrl)
        }

        // Get history of recently shown ad image URLs
        val lastShownImage = prefs.getString(KEY_LAST_SHOWN_IMAGE_URL, "") ?: ""
        val historySet = prefs.getStringSet(KEY_SHOWN_ADS_HISTORY, emptySet())?.toMutableSet() ?: mutableSetOf()

        // Filter candidates that haven't been shown recently
        var candidates = adsList.filter { ad ->
            ad.imageUrl != lastShownImage && !historySet.contains(ad.imageUrl)
        }

        // If all ads have been shown in history, reset history except for the last shown ad
        if (candidates.isEmpty()) {
            historySet.clear()
            if (lastShownImage.isNotEmpty()) {
                historySet.add(lastShownImage)
            }
            candidates = adsList.filter { ad -> ad.imageUrl != lastShownImage }
        }

        // Fallback if still empty
        if (candidates.isEmpty()) {
            candidates = adsList
        }

        val selectedAd = candidates.random()

        // Update history in SharedPreferences
        historySet.add(selectedAd.imageUrl)
        prefs.edit()
            .putString(KEY_LAST_SHOWN_IMAGE_URL, selectedAd.imageUrl)
            .putStringSet(KEY_SHOWN_ADS_HISTORY, historySet)
            .apply()

        return Pair(selectedAd.imageUrl, selectedAd.clickUrl)
    }

    fun getCachedAdInfo(context: Context): Pair<String, String> {
        return getRandomAdInfo(context)
    }

    /**
     * Pre-loads and caches image URLs in background using Coil ImageLoader
     * so ad display is instant and responsive.
     */
    private fun prefetchAdImages(context: Context, adsList: List<AdItem>) {
        try {
            val imageLoader = ImageLoader(context)
            for (ad in adsList) {
                if (ad.imageUrl.isNotBlank()) {
                    val request = ImageRequest.Builder(context)
                        .data(ad.imageUrl)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        } catch (e: Exception) {
            Log.e("AdManager", "Error prefetching ad images: ${e.message}")
        }
    }

    /**
     * Refreshes ads JSON once every 24 hours from network and caches images locally.
     */
    suspend fun refreshAdInfoIfNeeded(context: Context): Pair<String, String> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetchTime = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        val cachedJson = prefs.getString(KEY_CACHED_ADS_JSON, null)
        val cachedSourceUrl = prefs.getString(KEY_CACHED_SOURCE_URL, "") ?: ""
        val cachedList = parseAdsJson(cachedJson)

        // Check if 24 hours passed, no cache exists, source URL changed, or cache list is empty
        if (currentTime - lastFetchTime > ONE_DAY_MS || cachedJson.isNullOrBlank() || cachedSourceUrl != DEFAULT_ADS_TXT_URL || cachedList.isEmpty()) {
            try {
                val rawAdsTxtUrl = toRawUrl(DEFAULT_ADS_TXT_URL)
                val request = Request.Builder()
                    .url(rawAdsTxtUrl)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()?.trim()
                    if (!bodyString.isNullOrBlank()) {
                        val parsedList = parseAdsJson(bodyString)
                        if (parsedList.isNotEmpty()) {
                            prefs.edit()
                                .putLong(KEY_LAST_FETCH_TIME, currentTime)
                                .putString(KEY_CACHED_ADS_JSON, bodyString)
                                .putString(KEY_CACHED_SOURCE_URL, DEFAULT_ADS_TXT_URL)
                                .apply()

                            prefetchAdImages(context, parsedList)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AdManager", "Error fetching ads JSON daily: ${e.message}")
            }
        } else {
            // Pre-fetch images from existing cache in background to ensure readiness
            prefetchAdImages(context, cachedList)
        }

        return@withContext getRandomAdInfo(context)
    }
}

