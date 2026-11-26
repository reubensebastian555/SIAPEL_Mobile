package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO user utama dari REST API Laravel.
 * UUID pengguna Laravel menggunakan tipe String (bukan Auto-Increment Room/Int).
 */
@Serializable
data class LaravelUserDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("email_verified_at") val emailVerifiedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("profile") val profile: LaravelUserProfileDto? = null
)
