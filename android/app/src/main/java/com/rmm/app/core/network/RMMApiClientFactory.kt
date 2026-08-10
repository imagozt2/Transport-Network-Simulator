package com.rmm.app.core.network

import com.rmm.app.core.environment.RMMApiConfiguration
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RMMApiClientFactory(
    val configuration: RMMApiConfiguration = RMMApiConfiguration.current(),
) {
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .create()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(RMMRequestHeadersInterceptor())
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(configuration.baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val calls: RMMApiCallExecutor by lazy { RMMApiCallExecutor(gson) }

    fun <T : Any> create(service: Class<T>): T = retrofit.create(service)
}
