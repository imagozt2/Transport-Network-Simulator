package com.rmm.app.core.environment

import com.rmm.app.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

data class RMMApiConfiguration(
    val environment: RMMEnvironment,
    val baseUrl: HttpUrl,
    val debug: Boolean,
) {
    init {
        require(baseUrl.encodedPath == "/api/rmm-app/v1/") {
            "RMM API URL must end in /api/rmm-app/v1/"
        }
        require(debug || baseUrl.isHttps) {
            "Non-debug RMM environments require HTTPS"
        }
    }

    companion object {
        fun current(): RMMApiConfiguration = RMMApiConfiguration(
            environment = RMMEnvironment.from(BuildConfig.RMM_ENVIRONMENT),
            baseUrl = BuildConfig.RMM_API_BASE_URL.toHttpUrl(),
            debug = BuildConfig.DEBUG,
        )
    }
}
