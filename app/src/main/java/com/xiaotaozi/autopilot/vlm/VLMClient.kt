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
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * VLM (Vision Language Model) API 客户端
 * 支持 OpenAI 兼容接口 + 云端代理模式
 */
class VLMClient(
    private val apiKey: String = "",
    baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4-vision-preview",
    private val authManager: AuthManager? = null,
    private val cloudProviderId: String = "aliyun"
) {
    private val isCloudMode: Boolean = authManager != null
    private val baseUrl: String = normalizeUrl(baseUrl)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
        .build()

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L

        private fun normalizeUrl(url: String): String {
            var normalized = url.trim().removeSuffix("/")
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://$normalized"
            }
            return normalized
        }

        suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(Exception("Base URL 不能为空"))
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val cleanBaseUrl = normalizeUrl(baseUrl.removeSuffix("/chat/completions"))

            val request = try {
                Request.Builder()
                    .url("$cleanBaseUrl/models")
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                    .get()
                    .build()
            } catch (e: IllegalArgumentException) {
                return@withContext Result.failure(Exception("Base URL 格式无效: ${e.message}"))
            }

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val json = JSONObject(responseBody)
                        val data = json.optJSONArray("data") ?: JSONArray()
                        val models = mutableListOf<String>()
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i)
                            if (item != null) {
                                val id = item.optString("id", "").trim()
                                if (id.isNotEmpty()) models.add(id)
                            }
                        }
                        Result.success(models)
                    } else {
                        Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun getRequestUrl(): String {
        return if (isCloudMode) {
            authManager!!.getBaseUrl() + "/api/v1/vlm/chat"
        } else {
            "$baseUrl/chat/completions"
        }
    }

    private fun getAuthHeader(): String {
        return if (isCloudMode) {
            "Bearer ${authManager!!.accessToken ?: ""}"
        } else {
            "Bearer $apiKey"
        }
    }

    suspend fun predict(
        prompt: String,
        images: List<Bitmap> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        val encodedImages = images.map { bitmapToBase64Url(it) }

        for (attempt in 1..MAX_RETRIES) {
            try {
                val content = JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                    encodedImages.forEach { imageUrl ->
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", imageUrl)
                            })
                        })
                    }
                }

                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", content)
                    })
                }

                val requestBody = if (isCloudMode) {
                    JSONObject().apply {
                        put("providerId", cloudProviderId)
                        put("model", model)
                        put("messages", messages)
                        put("max_tokens", 4096)
                        put("temperature", 0.0)
                    }
                } else {
                    JSONObject().apply {
                        put("model", model)
                        put("messages", messages)
                        put("max_tokens", 4096)
                        put("temperature", 0.0)
                        put("top_p", 0.85)
                        put("frequency_penalty", 0.2)
                    }
                }

                val request = Request.Builder()
                    .url(getRequestUrl())
                    .addHeader("Authorization", getAuthHeader())
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val responseContent = message.getString("content")
                        return@withContext Result.success(responseContent)
                    } else {
                        lastException = Exception("No response from model")
                    }
                } else {
                    lastException = Exception("API error: ${response.code} - $responseBody")
                }
            } catch (e: UnknownHostException) {
                println("[VLMClient] DNS 解析失败，重试 $attempt/$MAX_RETRIES...")
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: java.net.SocketTimeoutException) {
                println("[VLMClient] 请求超时，重试 $attempt/$MAX_RETRIES...")
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: java.io.IOException) {
                println("[VLMClient] IO 错误: ${e.message}，重试 $attempt/$MAX_RETRIES...")
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        Result.failure(lastException ?: Exception("Unknown error"))
    }

    suspend fun predictWithContext(
        messagesJson: JSONArray
    ): Result<String> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val requestBody = if (isCloudMode) {
                    JSONObject().apply {
                        put("providerId", cloudProviderId)
                        put("model", model)
                        put("messages", messagesJson)
                        put("max_tokens", 4096)
                        put("temperature", 0.0)
                    }
                } else {
                    JSONObject().apply {
                        put("model", model)
                        put("messages", messagesJson)
                        put("max_tokens", 4096)
                        put("temperature", 0.0)
                    }
                }

                val request = Request.Builder()
                    .url(getRequestUrl())
                    .addHeader("Authorization", getAuthHeader())
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val responseContent = message.getString("content")
                        return@withContext Result.success(responseContent)
                    } else {
                        lastException = Exception("No response from model")
                    }
                } else {
                    lastException = Exception("API error: ${response.code} - $responseBody")
                }
            } catch (e: UnknownHostException) {
                println("[VLMClient] DNS 解析失败，重试 $attempt/$MAX_RETRIES...")
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: java.net.SocketTimeoutException) {
                println("[VLMClient] 请求超时，重试 $attempt/$MAX_RETRIES...")
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: java.io.IOException) {
                println("[VLMClient] IO 错误: ${e.message}，重试 $attempt/$MAX_RETRIES...")
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        Result.failure(lastException ?: Exception("Unknown error"))
    }

    private fun bitmapToBase64Url(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()
        println("[VLMClient] 图片压缩: ${bitmap.width}x${bitmap.height}, ${bytes.size / 1024}KB")
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }
}

object VLMConfigs {
    fun gpt4v(apiKey: String) = VLMClient(apiKey = apiKey, baseUrl = "https://api.openai.com/v1", model = "gpt-4-vision-preview")
    fun qwenVL(apiKey: String) = VLMClient(apiKey = apiKey, baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1", model = "qwen-vl-max")
    fun claude(apiKey: String) = VLMClient(apiKey = apiKey, baseUrl = "https://api.anthropic.com/v1", model = "claude-3-5-sonnet-20241022")
    fun custom(apiKey: String, baseUrl: String, model: String) = VLMClient(apiKey = apiKey, baseUrl = baseUrl, model = model)
    fun fromCloud(authManager: AuthManager, providerId: String, model: String) = VLMClient(
        authManager = authManager, cloudProviderId = providerId, model = model
    )
}