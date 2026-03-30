package com.example.gpslessclient.api

import okhttp3.Interceptor
import okhttp3.Response
import android.util.Log

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenManager.getAccessToken()

        Log.d("AuthInterceptor", "=== INTERCEPTOR CALLED ===")
        Log.d("AuthInterceptor", "URL: ${request.url}")
        Log.d("AuthInterceptor", "Token exists: ${!token.isNullOrEmpty()}, length: ${token?.length ?: 0}")

        val newRequest = if (!token.isNullOrEmpty()) {
            Log.d("AuthInterceptor", "✅ Adding Authorization header")
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            Log.d("AuthInterceptor", "❌ No token, skipping")
            request
        }

        return chain.proceed(newRequest)
    }
}