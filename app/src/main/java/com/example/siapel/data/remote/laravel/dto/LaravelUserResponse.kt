package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Respons DTO sukses dari GET /api/me Laravel.
 * Struktur JSON root: user { id, name, email, profile { ... } }.
 */
@Serializable
data class LaravelUserResponse(
    @SerialName("user") val user: LaravelUserDto? = null
)
