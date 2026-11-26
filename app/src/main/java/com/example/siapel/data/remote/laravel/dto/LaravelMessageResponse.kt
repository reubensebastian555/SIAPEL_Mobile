package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Respons DTO umum (misal: POST /api/logout) dari Laravel.
 * Struktur JSON root: message.
 */
@Serializable
data class LaravelMessageResponse(
    @SerialName("message") val message: String? = null
)
