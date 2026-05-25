package com.xiaotaozi.autopilot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(private val authManager: AuthManager) {

    data class ApiResult<T>(
        val success: Boolean,
        val data: T? = null,
        val error: String? = null
    )

    private fun get(path: String): JSONObject {
        val conn = openConnection(path, "GET")
        return readResponse(conn)
    }

    private fun post(path: String, body: JSONObject? = null): JSONObject {
        val conn = openConnection(path, "POST")
        if (body != null) {
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        }
        return readResponse(conn)
    }

    private fun openConnection(path: String, method: String): HttpURLConnection {
        val url = URL(authManager.getBaseUrl() + path)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        authManager.accessToken?.let { token ->
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.let {
            BufferedReader(InputStreamReader(it)).use { r -> r.readText() }
        } ?: "{}"
        stream?.close()
        conn.disconnect()
        if (code !in 200..299) {
            throw ApiException(code, try { JSONObject(text).optString("error", text) } catch (e: Exception) { text })
        }
        return JSONObject(text)
    }

    suspend fun register(username: String, password: String, email: String? = null): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
                email?.let { put("email", it) }
            }
            val resp = post("/api/v1/auth/register", body)
            val user = resp.getJSONObject("user")
            authManager.saveTokens(resp.getString("accessToken"), resp.getString("refreshToken"))
            authManager.setUser(UserInfo(
                id = user.getString("id"),
                username = user.getString("username"),
                isActivated = user.optBoolean("isActivated", false)
            ))
            ApiResult(true, resp)
        } catch (e: ApiException) {
            ApiResult(false, error = e.message)
        } catch (e: Exception) {
            ApiResult(false, error = "Network error: ${e.message}")
        }
    }

    suspend fun login(username: String, password: String): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val resp = post("/api/v1/auth/login", body)
            val user = resp.getJSONObject("user")
            authManager.saveTokens(resp.getString("accessToken"), resp.getString("refreshToken"))
            authManager.setUser(UserInfo(
                id = user.getString("id"),
                username = user.getString("username"),
                email = user.optString("email", null),
                isActivated = user.optBoolean("isActivated", false)
            ))
            ApiResult(true, resp)
        } catch (e: ApiException) {
            ApiResult(false, error = e.message)
        } catch (e: Exception) {
            ApiResult(false, error = "Network error: ${e.message}")
        }
    }

    suspend fun activate(cardCode: String): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("cardCode", cardCode) }
            val resp = post("/api/v1/activation", body)
            authManager.setActivated()
            ApiResult(true, resp)
        } catch (e: ApiException) {
            ApiResult(false, error = e.message)
        } catch (e: Exception) {
            ApiResult(false, error = "Network error: ${e.message}")
        }
    }

    suspend fun getProviders(): ApiResult<List<JSONObject>> = withContext(Dispatchers.IO) {
        try {
            val resp = get("/api/v1/providers")
            val arr = resp.getJSONArray("providers") ?: resp.optJSONArray("data") ?: resp.optJSONArray("items")
            val list = mutableListOf<JSONObject>()
            if (arr != null) {
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
            }
            ApiResult(true, list)
        } catch (e: ApiException) {
            ApiResult(false, error = e.message)
        } catch (e: Exception) {
            // If providers list fails (might be an array directly)
            ApiResult(true, emptyList())
        }
    }

    suspend fun getCurrentUser(): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val resp = get("/api/v1/auth/me")
            ApiResult(true, resp)
        } catch (e: ApiException) {
            ApiResult(false, error = e.message)
        } catch (e: Exception) {
            ApiResult(false, error = "Network error: ${e.message}")
        }
    }

    fun buildVlmChatUrl(): String = authManager.getBaseUrl() + "/api/v1/vlm/chat"

    fun buildGuiOwlUrl(): String = authManager.getBaseUrl() + "/api/v1/vlm/gui-owl"
}

class ApiException(val statusCode: Int, message: String) : Exception(message)
