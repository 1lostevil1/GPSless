package com.example.gpslessclient.network

import com.example.gpslessclient.api.TokenManager
import com.example.gpslessclient.model.http.request.RefreshTokenRequest
import com.example.gpslessclient.model.http.response.RefreshTokenResponse
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val baseUrl: String
) : Authenticator {

    private val gson = Gson()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 1) не даём уйти в бесконечные ретраи
        if (responseCount(response) >= 2) return null

        val refreshToken = tokenManager.getRefreshToken() ?: return null

        // 2) Если токен уже обновили в другом потоке, просто повторим запрос с актуальным
        val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val currentToken = tokenManager.getAccessToken()
        if (!currentToken.isNullOrBlank() && currentToken != requestToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .build()
        }

        // 3) Делаем refresh
        val refreshRequest = RefreshTokenRequest(refreshToken)
        val json = gson.toJson(refreshRequest)
        val body = json.toRequestBody("application/json".toMediaType())

        val refreshHttpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/user/refresh")
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        val refreshResponse = try {
            okhttp3.OkHttpClient().newCall(refreshHttpRequest).execute()
        } catch (e: IOException) {
            return null
        }

        refreshResponse.use { rr ->
            if (!rr.isSuccessful) {
                tokenManager.clearTokens()
                return null
            }

            val responseBody = rr.body?.string() ?: return null
            val newAccessToken = gson.fromJson(responseBody, RefreshTokenResponse::class.java).accessToken
                ?: return null

            tokenManager.saveTokens(newAccessToken, refreshToken)

            // 4) Повторяем исходный запрос уже с новым access token
            return response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var count = 1
        while (r?.priorResponse != null) {
            count++
            r = r.priorResponse
        }
        return count
    }
}