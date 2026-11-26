package com.example.siapel.data.remote.laravel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Model pembungkus generik untuk merespons struktur JSON standar REST API Laravel.
 */
@Serializable
data class LaravelApiResponse<T>(
    @SerialName("success") val success: Boolean? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("data") val data: T? = null,
    @SerialName("errors") val errors: Map<String, List<String>>? = null
)

/**
 * Model khusus untuk menangani payload error validasi (HTTP 422) dari Laravel.
 */
@Serializable
data class LaravelValidationErrorResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("errors") val errors: Map<String, List<String>>? = null
)
