package com.example.siapel.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KiaDetailRemoteDto(
    @SerialName("application_id")
    val applicationId: String,
    @SerialName("nik_anak")
    val nikAnak: String? = null,
    @SerialName("nama_anak")
    val namaAnak: String? = null,
    @SerialName("doc_1_path")
    val doc1Path: String? = null,
    @SerialName("doc_2_path")
    val doc2Path: String? = null,
    @SerialName("doc_3_path")
    val doc3Path: String? = null,
    @SerialName("doc_selfie_path")
    val docSelfiePath: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
