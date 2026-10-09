package com.example.siapel.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApplicationWithKiaDetailRemoteDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("service_type")
    val serviceType: String,
    @SerialName("category")
    val category: String,
    @SerialName("submission_code")
    val submissionCode: String,
    @SerialName("submission_date")
    val submissionDate: String,
    @SerialName("status")
    val status: String,
    @SerialName("note")
    val note: String? = null,
    @SerialName("nik_pelapor")
    val nikPelapor: String,
    @SerialName("nama_pelapor")
    val namaPelapor: String,
    @SerialName("email")
    val email: String,
    @SerialName("whatsapp")
    val whatsapp: String,
    @SerialName("kecamatan")
    val kecamatan: String,
    @SerialName("kelurahan")
    val kelurahan: String,
    @SerialName("keterangan")
    val keterangan: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("kia_detail")
    val kiaDetail: KiaDetailRemoteDto? = null
)
