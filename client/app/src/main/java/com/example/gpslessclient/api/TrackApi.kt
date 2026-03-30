package com.example.gpslessclient.api

import com.example.gpslessclient.model.NetworkSnapshot
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TrackApi {
    @POST("/api/track/save")
    suspend fun saveTrack(@Body snapshots: List<NetworkSnapshot>): Response<Boolean>
}