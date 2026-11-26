package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO profil pengguna dari REST API Laravel.
 */
@Serializable
data class LaravelUserProfileDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("nik") val nik: String? = null,
    @SerialName("whatsapp") val whatsapp: String? = null,
    @SerialName("kecamatan") val kecamatan: String? = null,
    @SerialName("kelurahan") val kelurahan: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
