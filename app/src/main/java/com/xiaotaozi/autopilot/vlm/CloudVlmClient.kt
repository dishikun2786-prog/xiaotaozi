package com.xiaotaozi.autopilot.vlm

import com.xiaotaozi.autopilot.data.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class CloudVlmResponse(
    val content: String,
    val success: Boolean,
    val error: String? = null
)

class CloudVlmClient(
    private val authManager: AuthManager,
    private val baseUrl: String,
    private val model: String,
    private val providerId: String = "aliyun"
) {
    fun buildChatUrl(): String = authManager.getBaseUrl() + "/api/v1/vlm/chat"

    fun buildGuiOwlUrl(): String = authManager.getBaseUrl() + "/api/v1/vlm/gui-owl"

    fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "Content-Type" to "application/json"
        )
        authManager.accessToken?.let { headers["Authorization"] = "Bearer $it" }
        return headers
    }

    suspend fun chat(
        messages: List<Map<String, Any>>,
        maxTokens: Int = 4096,
        temperature: Double = 0.0
    ): CloudVlmResponse = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("providerId", providerId)
                put("model", model)
                put("messages", JSONArray(messages.map { msg ->
                    JSONObject().apply {
                        put("role", msg["role"] ?: "user")
                        put("content", msg["content"] ?: "")
                    }
                }))
                put("max_tokens", maxTokens)
                put("temperature", temperature)
            }

            val url = URL(buildChatUrl())
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 120000
            conn.doOutput = true
            getHeaders().forEach { (k, v) -> conn.setRequestProperty(k, v) }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }
            stream?.close()
            conn.disconnect()

            if (code in 200..299) {
                val resp = JSONObject(text)
                val choices = resp.optJSONArray("choices")
                val content = if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                } else ""
                CloudVlmResponse(content = content, success = true)
            } else {
                val errorMsg = try { JSONObject(text).optString("error", text) } catch (e: Exception) { text }
                CloudVlmResponse(content = "", success = false, error = "VLM error ($code): $errorMsg")
            }
        } catch (e: Exception) {
            CloudVlmResponse(content = "", success = false, error = e.message ?: "Unknown error")
        }
    }

    suspend fun guiOwlRequest(payload: JSONObject): CloudVlmResponse = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("providerId", providerId)
                put("payload", payload)
            }

            val url = URL(buildGuiOwlUrl())
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 120000
            conn.doOutput = true
            getHeaders().forEach { (k, v) -> conn.setRequestProperty(k, v) }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }
            stream?.close()
            conn.disconnect()

            if (code in 200..299) {
                CloudVlmResponse(content = text, success = true)
            } else {
                val errorMsg = try { JSONObject(text).optString("error", text) } catch (e: Exception) { text }
                CloudVlmResponse(content = "", success = false, error = "GUI-Owl error ($code): $errorMsg")
            }
        } catch (e: Exception) {
            CloudVlmResponse(content = "", success = false, error = e.message ?: "Unknown error")
        }
    }
}
