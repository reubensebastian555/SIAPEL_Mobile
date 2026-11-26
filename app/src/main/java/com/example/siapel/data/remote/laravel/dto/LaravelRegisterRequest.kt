package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload DTO untuk pendaftaran akun pengguna baru ke POST /api/register
 */
@Serializable
data class LaravelRegisterRequest(
    @SerialName("name") val name: String,
    @SerialName("nik") val nik: String,
    @SerialName("email") val email: String,
    @SerialName("whatsapp") val whatsapp: String,
    @SerialName("kecamatan") val kecamatan: String,
    @SerialName("kelurahan") val kelurahan: String,
    @SerialName("password") val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String
)
