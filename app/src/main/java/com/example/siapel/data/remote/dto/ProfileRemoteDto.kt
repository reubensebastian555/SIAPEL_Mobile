package com.example.siapel.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRemoteDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("nik")
    val nik: String? = null,
    @SerialName("whatsapp")
    val whatsapp: String? = null,
    @SerialName("kecamatan")
    val kecamatan: String? = null,
    @SerialName("kelurahan")
    val kelurahan: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
