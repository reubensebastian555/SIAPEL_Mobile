package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload DTO untuk login pengguna ke POST /api/login
 */
@Serializable
data class LaravelLoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String
)
