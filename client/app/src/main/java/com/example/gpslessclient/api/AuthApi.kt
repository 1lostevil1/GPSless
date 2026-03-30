package com.example.gpslessclient.api

import com.example.gpslessclient.model.http.request.AuthRequest
import com.example.gpslessclient.model.http.request.RefreshTokenRequest
import com.example.gpslessclient.model.http.request.RegistrationRequest
import com.example.gpslessclient.model.http.response.AuthResponse
import com.example.gpslessclient.model.http.response.RefreshTokenResponse
import com.example.gpslessclient.model.http.response.RegistrationResponse
import com.example.gpslessclient.model.http.response.UserInfoResponse
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    @POST("/api/user/signup")
    suspend fun signup(@Body request: RegistrationRequest): Response<RegistrationResponse>

    @POST("/api/user/createAuthToken")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @POST("/api/user/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @GET("/api/user/me")
    suspend fun getCurrentUser(): Response<UserInfoResponse>
}