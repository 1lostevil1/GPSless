package com.example.gpslessclient.api

import com.example.gpslessclient.model.NetworkSnapshot
import com.example.gpslessclient.model.UserLocation
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LocationApi {
    @POST("/api/location/current")
    suspend fun getLocation(@Body snapshot: NetworkSnapshot): Response<UserLocation>
}