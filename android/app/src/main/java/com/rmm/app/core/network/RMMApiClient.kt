package com.rmm.app.core.network

object RMMApiClient {
    private val factory by lazy { RMMApiClientFactory() }
    private val healthService: RMMHealthApi by lazy { factory.create(RMMHealthApi::class.java) }

    suspend fun health() = factory.calls.execute { healthService.health() }

    fun <T : Any> create(service: Class<T>): T = factory.create(service)

    fun calls(): RMMApiCallExecutor = factory.calls
}
