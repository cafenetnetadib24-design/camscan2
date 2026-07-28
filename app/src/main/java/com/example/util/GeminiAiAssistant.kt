package com.example.util

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenConfig? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null
)

data class GeminiGenConfig(
    val temperature: Float? = 0.4f
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null
)

interface GeminiRestService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiAiAssistant {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val service: GeminiRestService = retrofit.create(GeminiRestService::class.java)

    suspend fun generateDocumentTitle(ocrText: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || ocrText.isBlank()) {
            return@withContext fallbackTitle(ocrText)
        }

        val prompt = "Based on the following document OCR text, generate a short 2-4 word clean title for this document (e.g., 'Invoice Acme Corp', 'Receipt Target', 'Contract Agreement'). Return ONLY the title text, with no quotes or punctuation:\n\n$ocrText"

        try {
            val response = service.generateContent(
                apiKey,
                GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    )
                )
            )
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (!text.isNullOrBlank()) text.take(40) else fallbackTitle(ocrText)
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackTitle(ocrText)
        }
    }

    suspend fun summarizeDocument(ocrText: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || ocrText.isBlank()) return@withContext "No AI summary available."

        val prompt = "Analyze and summarize the key information, dates, amounts, and bullet points from this scanned document text:\n\n$ocrText"

        try {
            val response = service.generateContent(
                apiKey,
                GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    )
                )
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "Unable to generate summary."
        } catch (e: Exception) {
            e.printStackTrace()
            "Error generating summary: ${e.message}"
        }
    }

    suspend fun translateText(ocrText: String, targetLanguage: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || ocrText.isBlank()) return@withContext ocrText

        val prompt = "Translate the following scanned document text accurately into $targetLanguage. Maintain formatting and line breaks where appropriate:\n\n$ocrText"

        try {
            val response = service.generateContent(
                apiKey,
                GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    )
                )
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: ocrText
        } catch (e: Exception) {
            e.printStackTrace()
            "Translation error: ${e.message}"
        }
    }

    suspend fun askQuestionAboutDocument(ocrText: String, question: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || ocrText.isBlank()) return@withContext "Document text is empty."

        val prompt = "You are an AI Document Assistant. Based ONLY on the following scanned document content, answer the user's question clearly and concisely.\n\nDOCUMENT CONTENT:\n$ocrText\n\nUSER QUESTION:\n$question"

        try {
            val response = service.generateContent(
                apiKey,
                GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    )
                )
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "No response generated."
        } catch (e: Exception) {
            e.printStackTrace()
            "Error answering question: ${e.message}"
        }
    }

    suspend fun generateTags(ocrText: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || ocrText.isBlank()) return@withContext "scanned, document"

        val prompt = "Analyze the text of this document and generate 2 to 4 comma-separated relevant tags/categories (e.g., 'Invoice, Taxes, 2026' or 'Contract, Legal'). Return ONLY the comma-separated string:\n\n$ocrText"

        try {
            val response = service.generateContent(
                apiKey,
                GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    )
                )
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "Scanned"
        } catch (e: Exception) {
            e.printStackTrace()
            "Scanned"
        }
    }

    private fun fallbackTitle(ocrText: String): String {
        val firstLine = ocrText.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Scanned Doc"
        val words = firstLine.split(" ").take(3).joinToString(" ")
        return if (words.isNotBlank()) words.take(25) else "Scanned Doc"
    }
}
