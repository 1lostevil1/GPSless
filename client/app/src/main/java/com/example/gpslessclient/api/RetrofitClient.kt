package com.example.gpslessclient.api

import android.content.Context
import android.util.Log
import com.example.gpslessclient.network.TokenAuthenticator
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://158.160.122.227:8080/"

    private var tokenManager: TokenManager? = null
    private var _instance: Retrofit? = null

    fun init(context: Context) {
        tokenManager = TokenManager(context)
        // Сбрасываем кэш при инициализации
        _instance = null
    }

    private val okHttpClient: OkHttpClient
        get() {
            val builder = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)

            val tm = tokenManager
            if (tm != null) {
                Log.d("RetrofitClient", "Adding AuthInterceptor with tokenManager")
                builder.addInterceptor(AuthInterceptor(tm))
                    .authenticator(TokenAuthenticator(tm, BASE_URL))
            } else {
                Log.d("RetrofitClient", "TokenManager is still null, skipping interceptors")
            }

            return builder.build()
        }

    val instance: Retrofit
        get() {
            if (_instance == null) {
                Log.d("RetrofitClient", "Creating Retrofit instance")
                _instance = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return _instance!!
        }

    val authApi: AuthApi
        get() = instance.create(AuthApi::class.java)

    val trackApi: TrackApi
        get() = instance.create(TrackApi::class.java)

    val adminApi: AdminApi by lazy {
        instance.create(AdminApi::class.java)
    }

    val locationApi: LocationApi by lazy {
        instance.create(LocationApi::class.java)
    }
}