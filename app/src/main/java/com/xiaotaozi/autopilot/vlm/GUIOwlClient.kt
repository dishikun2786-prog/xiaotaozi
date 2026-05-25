package com.xiaotaozi.autopilot.vlm

import android.graphics.Bitmap
import android.util.Base64
import com.xiaotaozi.autopilot.data.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * GUI-Owl API 客户端
 * 支持直连阿里云 + 云端代理模式
 */
class GUIOwlClient(
    private val apiKey: String = "",
    private val model: String = "pre-gui_owl_7b",
    private val deviceType: String = "mobile",
    private val thoughtLanguage: String = "chinese",
    private val authManager: AuthManager? = null,
    private val cloudProviderId: String = "gui_owl"
) {
    private val isCloudMode: Boolean = authManager != null

    companion object {
        private const val TAG = "GUIOwlClient"
        private const val ENDPOINT = "https://dashscope.aliyuncs.com/api/v2/apps/gui-owl/gui_agent_server"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
        .build()

    private var sessionId: String = ""

    data class GUIOwlResponse(
        val thought: String,
        val operation: String,
        val explanation: String,
        val sessionId: String,
        val rawResponse: String
    )

    data class ParsedAction(
        val type: String,
        val x: Int? = null,
        val y: Int? = null,
        val x2: Int? = null,
        val y2: Int? = null,
        val text: String? = null
    )

    private fun getRequestUrl(): String {
        return if (isCloudMode) {
            authManager!!.getBaseUrl() + "/api/v1/vlm/gui-owl"
        } else {
            ENDPOINT
        }
    }

    private fun getAuthHeader(): String {
        return "Bearer ${if (isCloudMode) (authManager!!.accessToken ?: "") else apiKey}"
    }

    suspend fun predict(
        instruction: String,
        imageUrl: String,
        addInfo: String = ""
    ): Result<GUIOwlResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val requestBody: JSONObject
                val requestUrl: String

                if (isCloudMode) {
                    // 云端代理模式：发送原始参数到服务器
                    val innerPayload = JSONObject().apply {
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("image", imageUrl))
                            put(JSONObject().put("instruction", instruction))
                            put(JSONObject().put("session_id", sessionId))
                            put(JSONObject().put("device_type", deviceType))
                            put(JSONObject().put("pipeline_type", "agent"))
                            put(JSONObject().put("model_name", model))
                            put(JSONObject().put("thought_language", thoughtLanguage))
                            put(JSONObject().put("param_list", JSONArray().apply {
                                put(JSONObject().put("add_info", addInfo))
                            }))
                        })
                    }
                    requestBody = JSONObject().apply {
                        put("providerId", cloudProviderId)
                        put("payload", innerPayload)
                    }
                    requestUrl = getRequestUrl()
                } else {
                    // 直连模式
                    val messagesArray = JSONArray().apply {
                        put(JSONObject().put("image", imageUrl))
                        put(JSONObject().put("instruction", instruction))
                        put(JSONObject().put("session_id", sessionId))
                        put(JSONObject().put("device_type", deviceType))
                        put(JSONObject().put("pipeline_type", "agent"))
                        put(JSONObject().put("model_name", model))
                        put(JSONObject().put("thought_language", thoughtLanguage))
                        put(JSONObject().put("param_list", JSONArray().apply {
                            put(JSONObject().put("add_info", addInfo))
                        }))
                    }

                    val dataObj = JSONObject().apply { put("messages", messagesArray) }
                    val contentArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "data")
                            put("data", dataObj)
                        })
                    }
                    val inputArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", contentArray)
                        })
                    }
                    requestBody = JSONObject().apply {
                        put("app_id", "gui-owl")
                        put("input", inputArray)
                    }
                    requestUrl = ENDPOINT
                }

                val request = Request.Builder()
                    .url(requestUrl)
                    .addHeader("Authorization", getAuthHeader())
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                println("[$TAG] 请求: instruction=$instruction, mode=${if (isCloudMode) "cloud" else "direct"}")
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)

                    if (isCloudMode) {
                        // 云端代理返回格式: { choices: [{ message: { content: "..." } }] }
                        val newSessionId = json.optString("session_id", "")
                        if (newSessionId.isNotEmpty()) sessionId = newSessionId

                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val message = choices.getJSONObject(0).optJSONObject("message")
                            val rawContent = message?.optString("content", "") ?: ""
                            // 尝试解析 content 中的 JSON
                            val innerJson = try {
                                JSONObject(rawContent)
                            } catch (e: Exception) {
                                null
                            }
                            val result = GUIOwlResponse(
                                thought = innerJson?.optString("thought", "") ?: "",
                                operation = innerJson?.optString("operation", "") ?: "",
                                explanation = innerJson?.optString("explanation", "") ?: "",
                                sessionId = sessionId,
                                rawResponse = responseBody
                            )
                            println("[$TAG] 云端响应: operation=${result.operation}")
                            return@withContext Result.success(result)
                        }
                        lastException = Exception("Invalid cloud response format: $responseBody")
                    } else {
                        val newSessionId = json.optString("session_id", "")
                        if (newSessionId.isNotEmpty()) sessionId = newSessionId

                        val outputArray = json.optJSONArray("output")
                        if (outputArray != null && outputArray.length() > 0) {
                            val output = outputArray.getJSONObject(0)
                            val contentArr = output.optJSONArray("content")
                            if (contentArr != null && contentArr.length() > 0) {
                                val content = contentArr.getJSONObject(0)
                                val data = content.optJSONObject("data")
                                if (data != null) {
                                    val result = GUIOwlResponse(
                                        thought = data.optString("Thought", ""),
                                        operation = data.optString("Operation", ""),
                                        explanation = data.optString("Explanation", ""),
                                        sessionId = sessionId,
                                        rawResponse = responseBody
                                    )
                                    println("[$TAG] 响应: operation=${result.operation}")
                                    return@withContext Result.success(result)
                                }
                            }
                        }
                        lastException = Exception("Invalid response format: $responseBody")
                    }
                } else {
                    lastException = Exception("API error: ${response.code} - $responseBody")
                }
            } catch (e: Exception) {
                println("[$TAG] 请求失败 (attempt $attempt): ${e.message}")
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            }
        }

        Result.failure(lastException ?: Exception("Unknown error"))
    }

    suspend fun predict(
        instruction: String,
        image: Bitmap,
        addInfo: String = ""
    ): Result<GUIOwlResponse> {
        val imageUrl = bitmapToDataUrl(image)
        return predict(instruction, imageUrl, addInfo)
    }

    fun parseOperation(operation: String): ParsedAction? {
        val trimmed = operation.trim()

        val clickPattern = Regex("""Click\s*\(\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*\d+\s*,\s*\d+)?\s*\)""", RegexOption.IGNORE_CASE)
        clickPattern.find(trimmed)?.let { match ->
            val x = match.groupValues[1].toIntOrNull() ?: return null
            val y = match.groupValues[2].toIntOrNull() ?: return null
            return ParsedAction(type = "click", x = x, y = y)
        }

        val swipePattern = Regex("""Swipe\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE)
        swipePattern.find(trimmed)?.let { match ->
            val x1 = match.groupValues[1].toIntOrNull() ?: return null
            val y1 = match.groupValues[2].toIntOrNull() ?: return null
            val x2 = match.groupValues[3].toIntOrNull() ?: return null
            val y2 = match.groupValues[4].toIntOrNull() ?: return null
            return ParsedAction(type = "swipe", x = x1, y = y1, x2 = x2, y2 = y2)
        }

        val longPressPattern = Regex("""Long[_\s]?[Pp]ress\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE)
        longPressPattern.find(trimmed)?.let { match ->
            val x = match.groupValues[1].toIntOrNull() ?: return null
            val y = match.groupValues[2].toIntOrNull() ?: return null
            return ParsedAction(type = "long_press", x = x, y = y)
        }

        val typePattern = Regex("""(?:Type|Input)\s*\(\s*["\']?(.+?)["\']?\s*\)""", RegexOption.IGNORE_CASE)
        typePattern.find(trimmed)?.let { match ->
            val text = match.groupValues[1]
            return ParsedAction(type = "type", text = text)
        }

        val scrollPattern = Regex("""Scroll[_\s]?(up|down|left|right)?""", RegexOption.IGNORE_CASE)
        scrollPattern.find(trimmed)?.let { match ->
            val direction = match.groupValues.getOrNull(1)?.lowercase() ?: "down"
            return ParsedAction(type = "scroll", text = direction)
        }

        if (trimmed.contains("Back", ignoreCase = true)) return ParsedAction(type = "system_button", text = "Back")
        if (trimmed.contains("Home", ignoreCase = true)) return ParsedAction(type = "system_button", text = "Home")
        if (trimmed.contains(Regex("FINISH|DONE|COMPLETE|Finished", RegexOption.IGNORE_CASE))) return ParsedAction(type = "finish")

        println("[$TAG] 无法解析操作: $operation")
        return null
    }

    fun resetSession() { sessionId = "" }
    fun getSessionId(): String = sessionId

    private fun bitmapToDataUrl(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()
        println("[$TAG] 图片压缩: ${bitmap.width}x${bitmap.height}, ${bytes.size / 1024}KB")
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    companion object {
        fun fromCloud(authManager: AuthManager, providerId: String = "gui_owl", model: String = "pre-gui_owl_7b") = GUIOwlClient(
            authManager = authManager, cloudProviderId = providerId, model = model
        )
    }
}