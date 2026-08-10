package com.rmm.app.core.network

import com.rmm.app.BuildConfig
import com.rmm.app.core.environment.RMMApiConfiguration
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RMMApiClientFactory(
    val configuration: RMMApiConfiguration = RMMApiConfiguration.current(),
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "RMM-App/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(configuration.baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun <T : Any> create(service: Class<T>): T = retrofit.create(service)
}
