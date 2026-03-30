package com.example.gpslessclient.api

import com.example.gpslessclient.model.NetworkSnapshot
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AdminApi {
    @POST("/api/admin/track/save")
    suspend fun saveSnapshot(@Body snapshot: NetworkSnapshot): Response<Unit>
}