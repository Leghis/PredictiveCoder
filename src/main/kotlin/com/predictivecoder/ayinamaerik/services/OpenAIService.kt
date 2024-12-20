package com.predictivecoder.ayinamaerik.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.predictivecoder.ayinamaerik.config.PredictiveCoderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.LinkedHashMap

@Service(Service.Level.APP)
class OpenAIService {
    private val logger = Logger.getInstance(OpenAIService::class.java)
    private val baseUrl = "https://api.openai.com/v1/chat/completions"
    private val model = "gpt-4o-mini"

    private val suggestionCache = object : LinkedHashMap<String, CacheEntry>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CacheEntry>): Boolean {
            return size > 100 || System.currentTimeMillis() - eldest.value.timestamp > TimeUnit.SECONDS.toMillis(30)
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor())
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private data class CacheEntry(
        val suggestions: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    )

    init {
        logger.info("Initializing OpenAIService with model: $model")
        if (!getApiKey().isNotBlank()) {
            logger.error("API key not configured")
            showConfigurationError()
        } else {
            logger.info("OpenAI API key configured successfully")
        }
    }

    private fun getApiKey(): String {
        return PredictiveCoderConfig.getInstance().apiKey.also {
            if (it.isBlank()) {
                logger.error("API key not configured")
                throw IllegalStateException("OpenAI API key not configured")
            }
        }
    }

    suspend fun testApiKey(): Boolean {
        try {
            val testRequest = CompletionRequest(
                model = model,
                messages = listOf(Message("user", "Say 'test'")),
                temperature = 0.4,
                max_tokens = 5,
                top_p = 1.0,
                frequency_penalty = 0.0,
                presence_penalty = 0.0,
                stop = null
            )

            val requestBody = gson.toJson(testRequest).toRequestBody(jsonMediaType)
            val httpRequest = Request.Builder()
                .url(baseUrl)
                .header("Authorization", "Bearer ${getApiKey()}")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            return withContext(Dispatchers.IO) {
                client.newCall(httpRequest).execute().use { response ->
                    response.isSuccessful
                }
            }
        } catch (e: Exception) {
            logger.error("API key test failed", e)
            return false
        }
    }

    private fun showConfigurationError() {
        ApplicationManager.getApplication().invokeLater {
            val notification = NotificationGroup
                .balloonGroup("PredictiveCoder Notifications")
                .createNotification(
                    "PredictiveCoder Configuration Required",
                    "OpenAI API key not found. Please configure it in settings.",
                    NotificationType.ERROR
                )
            Notifications.Bus.notify(notification)
        }
    }

    private fun loggingInterceptor() = Interceptor { chain ->
        val request = chain.request()
        logger.debug("Sending request to OpenAI: ${request.url}")
        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            logger.error("Error response from OpenAI: ${response.code} - ${response.body?.string()}")
        }
        response
    }

    data class CompletionRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double,
        val max_tokens: Int,
        val top_p: Double,
        val frequency_penalty: Double,
        val presence_penalty: Double,
        val stop: List<String>?
    )

    data class Message(
        val role: String,
        val content: String
    )

    suspend fun getSuggestions(
        context: String,
        currentLine: String,
        fileType: String,
        isNewLine: Boolean = false
    ): List<String> {
        if (!getApiKey().isNotBlank()) {
            logger.error("OpenAI API key not configured")
            return emptyList()
        }

        try {
            val cacheKey = "$fileType:$currentLine:${context.hashCode()}"
            logger.debug("Processing request for: $fileType")

            if (!isNewLine) {
                suggestionCache[cacheKey]?.let { entry ->
                    if (System.currentTimeMillis() - entry.timestamp < TimeUnit.SECONDS.toMillis(30)) {
                        return entry.suggestions
                    }
                    suggestionCache.remove(cacheKey)
                }
            }

            val cleanContext = context.trim()
            if (cleanContext.isEmpty()) {
                logger.debug("Empty context")
                return emptyList()
            }

            val messages = listOf(
                Message(
                    role = "system",
                    content = buildSystemPrompt(fileType, isNewLine)
                ),
                Message(
                    role = "user",
                    content = "Complete this code:\n$cleanContext"
                )
            )

            val request = CompletionRequest(
                model = model,
                messages = messages,
                temperature = if (isNewLine) 0.4 else 0.4,
                max_tokens = if (isNewLine) 50 else 50,
                top_p = 1.0,
                frequency_penalty = if (isNewLine) 0.2 else 0.0,
                presence_penalty = if (isNewLine) 0.2 else 0.0,
                stop = listOf("```", "\n\n")
            )

            val requestBody = gson.toJson(request).toRequestBody(jsonMediaType)
            val httpRequest = Request.Builder()
                .url(baseUrl)
                .header("Authorization", "Bearer ${getApiKey()}")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            return withContext(Dispatchers.IO) {
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        logger.error("API request failed: ${response.code} - $errorBody")
                        emptyList()
                    } else {
                        val responseBody = response.body?.string()
                        if (responseBody == null) {
                            logger.error("Empty response body")
                            emptyList()
                        } else {
                            val suggestions = processResponse(responseBody)
                            if (suggestions.isNotEmpty() && !isNewLine) {
                                suggestionCache[cacheKey] = CacheEntry(suggestions)
                            }
                            suggestions
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error in getSuggestions", e)
            return emptyList()
        }
    }

    private suspend fun processResponse(responseBody: String): List<String> = withContext(Dispatchers.Default) {
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            jsonResponse.getAsJsonArray("choices")?.mapNotNull { choice ->
                choice.asJsonObject
                    .getAsJsonObject("message")
                    ?.get("content")
                    ?.asString
                    ?.let { cleanSuggestion(it) }
                    ?.takeIf { it.isNotBlank() && !it.contains("```") }
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error processing response: ${e.message}")
            emptyList()
        }
    }

    private fun cleanSuggestion(suggestion: String): String {
        return suggestion
            .replace(Regex("```\\w*\\n?"), "")
            .replace(Regex("\\s+$"), "")
            .trim()
            .let { if (it.startsWith(".")) it.substring(1) else it }
    }

    private fun buildSystemPrompt(fileType: String, isNewLine: Boolean): String {
        return buildString {
            appendLine("You are a code completion assistant for $fileType files.")
            appendLine("Rules:")

            if (isNewLine) {
                appendLine("- Focus on completing the new line based on the context")
                appendLine("- Maintain consistent indentation with the current block")
                appendLine("- Consider the block structure and type")
                appendLine("- Provide complete statement or block continuation")
                appendLine("- Follow the existing code patterns")
                appendLine("- Ensure proper closing of blocks and statements")
                appendLine("- Consider the scope and context of the current block")
            } else {
                appendLine("- Provide only code completions, no explanations")
                appendLine("- Continue from the existing code context")
                appendLine("- Follow the existing code style")
            }

            appendLine("- Be concise and relevant")
            appendLine("- Ensure code is syntactically correct")
            appendLine("- Return only valid code, no markdown formatting")

            if (isNewLine) {
                appendLine("- Prioritize completing control structures and blocks")
                appendLine("- Maintain logical flow with surrounding code")
            }
        }
    }

    companion object {
        fun getInstance(): OpenAIService =
            ApplicationManager.getApplication().getService(OpenAIService::class.java)
    }
}