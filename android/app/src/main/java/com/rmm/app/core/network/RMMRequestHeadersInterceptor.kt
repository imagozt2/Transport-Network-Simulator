package com.rmm.app.core.network

import com.rmm.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class RMMRequestHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "RMM-App/${BuildConfig.VERSION_NAME}")
            .header("X-RMM-Client", "android")
            .header("X-RMM-Client-Version", BuildConfig.VERSION_NAME)
            .build()
        return chain.proceed(request)
    }
}
