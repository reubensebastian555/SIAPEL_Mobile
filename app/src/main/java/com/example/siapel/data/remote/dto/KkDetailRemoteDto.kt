package com.example.siapel.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KkDetailRemoteDto(
    @SerialName("application_id")
    val applicationId: String,

    @SerialName("nik_anak")
    val nikAnak: String? = null,

    @SerialName("nama_anak")
    val namaAnak: String? = null,

    @SerialName("nomor_kk")
    val nomorKk: String? = null,

    @SerialName("doc_1_path")
    val doc1Path: String? = null,

    @SerialName("doc_2_path")
    val doc2Path: String? = null,

    @SerialName("doc_3_path")
    val doc3Path: String? = null,

    @SerialName("doc_4_path")
    val doc4Path: String? = null,

    @SerialName("doc_5_path")
    val doc5Path: String? = null,

    @SerialName("doc_6_path")
    val doc6Path: String? = null,

    @SerialName("doc_7_path")
    val doc7Path: String? = null,

    @SerialName("doc_8_path")
    val doc8Path: String? = null,

    @SerialName("doc_9_path")
    val doc9Path: String? = null,

    @SerialName("doc_10_path")
    val doc10Path: String? = null,

    @SerialName("doc_selfie_path")
    val docSelfiePath: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)
