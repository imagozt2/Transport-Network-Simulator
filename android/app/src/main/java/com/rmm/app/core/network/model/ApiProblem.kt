package com.rmm.app.core.network.model

data class ApiProblem(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val requestId: String? = null,
    val fieldErrors: List<ApiFieldError> = emptyList(),
)

data class ApiFieldError(
    val field: String,
    val code: String,
)
