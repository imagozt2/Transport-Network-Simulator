package com.rmm.app.core.networkcatalog

import com.rmm.app.core.network.ApiFailure
import com.rmm.app.core.network.ApiResult
import com.rmm.app.core.network.RMMApiClient
import com.rmm.app.core.session.PassengerSession

class PassengerNetworkRepository {
    private val api = RMMApiClient.create(PassengerNetworkApi::class.java)
    private val calls = RMMApiClient.calls()

    suspend fun catalog(session: PassengerSession): NetworkCatalogResult {
        val authorization = "Bearer ${session.accessToken}"
        val lines = calls.execute { api.lines(authorization) }
        if (lines is ApiResult.Failure) {
            return NetworkCatalogResult.Failure(lines.reason)
        }

        val stations = calls.execute { api.stations(authorization) }
        if (stations is ApiResult.Failure) {
            return NetworkCatalogResult.Failure(stations.reason)
        }

        return NetworkCatalogResult.Success(
            NetworkCatalog(
                lines = (lines as ApiResult.Success).value.items.filter { it.active },
                stations = (stations as ApiResult.Success).value.items.filter { it.active },
            ),
        )
    }
}

data class NetworkCatalog(
    val lines: List<PassengerNetworkLine>,
    val stations: List<PassengerNetworkStation>,
)

sealed interface NetworkCatalogResult {
    data class Success(val catalog: NetworkCatalog) : NetworkCatalogResult
    data class Failure(val reason: ApiFailure) : NetworkCatalogResult
}
