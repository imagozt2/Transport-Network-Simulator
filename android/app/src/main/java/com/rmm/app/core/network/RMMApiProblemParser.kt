package com.rmm.app.core.network

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.rmm.app.core.network.model.ApiProblem
import okhttp3.ResponseBody

class RMMApiProblemParser(private val gson: Gson) {
    fun parse(errorBody: ResponseBody?): ApiProblem? {
        if (errorBody == null) return null
        return try {
            errorBody.charStream().use { reader -> gson.fromJson(reader, ApiProblem::class.java) }
        } catch (_: JsonParseException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }
}
