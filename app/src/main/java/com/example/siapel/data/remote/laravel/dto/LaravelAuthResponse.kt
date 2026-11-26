package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Respons DTO sukses dari POST /api/register dan POST /api/login Laravel.
 * Struktur JSON root: message, user, token_type, access_token.
 */
@Serializable
data class LaravelAuthResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("user") val user: LaravelUserDto? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("access_token") val accessToken: String? = null
)
