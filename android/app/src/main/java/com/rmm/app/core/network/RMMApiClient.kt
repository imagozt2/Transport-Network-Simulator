package com.rmm.app.core.network

object RMMApiClient {
    private val factory by lazy { RMMApiClientFactory() }

    val health: RMMHealthApi by lazy { factory.create(RMMHealthApi::class.java) }

    fun <T : Any> create(service: Class<T>): T = factory.create(service)
}
