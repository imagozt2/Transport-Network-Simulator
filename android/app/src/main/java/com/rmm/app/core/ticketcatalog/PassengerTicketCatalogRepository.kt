package com.rmm.app.core.ticketcatalog

import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiClient
import com.rmm.app.core.session.PassengerSession

class PassengerTicketCatalogRepository {
    private val api = RMMApiClient.create(PassengerTicketProductApi::class.java)
    private val calls = RMMApiClient.calls()

    suspend fun products(session: PassengerSession): ApiResult<List<PassengerTicketProduct>> =
        when (val result = calls.execute {
            api.products("Bearer ${session.accessToken}")
        }) {
            is ApiResult.Success -> ApiResult.Success(result.value.items, result.metadata)
            is ApiResult.Failure -> result
        }
}
