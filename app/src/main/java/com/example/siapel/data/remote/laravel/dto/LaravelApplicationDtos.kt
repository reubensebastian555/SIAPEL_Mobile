package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO respons utama permohonan dari REST API Laravel 12.
 */
@Serializable
data class LaravelApplicationDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("service_type") val serviceType: String,
    @SerialName("category") val category: String,
    @SerialName("submission_code") val submissionCode: String,
    @SerialName("submission_date") val submissionDate: String,
    @SerialName("status") val status: String,
    @SerialName("note") val note: String? = null,
    @SerialName("nik_pelapor") val nikPelapor: String,
    @SerialName("nama_pelapor") val namaPelapor: String,
    @SerialName("email") val email: String,
    @SerialName("whatsapp") val whatsapp: String,
    @SerialName("kecamatan") val kecamatan: String,
    @SerialName("kelurahan") val kelurahan: String,
    @SerialName("keterangan") val keterangan: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,

    // Relasi Detail Layanan (Serialized dari Eloquent camelCase relations)
    @SerialName("kia_detail") val kiaDetail: LaravelKiaDetailDto? = null,
    @SerialName("ktp_detail") val ktpDetail: LaravelKtpDetailDto? = null,
    @SerialName("kk_detail") val kkDetail: LaravelKkDetailDto? = null,
    @SerialName("akta_detail") val aktaDetail: LaravelAktaDetailDto? = null,
    @SerialName("disabilitas_detail") val disabilitasDetail: LaravelDisabilitasDetailDto? = null
)

/**
 * DTO detail permohonan KIA (Kartu Identitas Anak).
 */
@Serializable
data class LaravelKiaDetailDto(
    @SerialName("application_id") val applicationId: String,
    @SerialName("nik_anak") val nikAnak: String? = null,
    @SerialName("nama_anak") val namaAnak: String? = null,
    @SerialName("doc_1_path") val doc1Path: String? = null,
    @SerialName("doc_2_path") val doc2Path: String? = null,
    @SerialName("doc_3_path") val doc3Path: String? = null,
    @SerialName("doc_selfie_path") val docSelfiePath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * DTO detail permohonan KTP Elektronik.
 */
@Serializable
data class LaravelKtpDetailDto(
    @SerialName("application_id") val applicationId: String,
    @SerialName("doc_1_path") val doc1Path: String? = null,
    @SerialName("doc_2_path") val doc2Path: String? = null,
    @SerialName("doc_selfie_path") val docSelfiePath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * DTO detail permohonan Kartu Keluarga (KK).
 */
@Serializable
data class LaravelKkDetailDto(
    @SerialName("application_id") val applicationId: String,
    @SerialName("nik_anak") val nikAnak: String? = null,
    @SerialName("nama_anak") val namaAnak: String? = null,
    @SerialName("nomor_kk") val nomorKk: String? = null,
    @SerialName("doc_1_path") val doc1Path: String? = null,
    @SerialName("doc_2_path") val doc2Path: String? = null,
    @SerialName("doc_3_path") val doc3Path: String? = null,
    @SerialName("doc_4_path") val doc4Path: String? = null,
    @SerialName("doc_5_path") val doc5Path: String? = null,
    @SerialName("doc_6_path") val doc6Path: String? = null,
    @SerialName("doc_7_path") val doc7Path: String? = null,
    @SerialName("doc_8_path") val doc8Path: String? = null,
    @SerialName("doc_9_path") val doc9Path: String? = null,
    @SerialName("doc_10_path") val doc10Path: String? = null,
    @SerialName("doc_selfie_path") val docSelfiePath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * DTO detail permohonan Akta Kelahiran.
 */
@Serializable
data class LaravelAktaDetailDto(
    @SerialName("application_id") val applicationId: String,
    @SerialName("nik_bayi") val nikBayi: String? = null,
    @SerialName("nama_bayi") val namaBayi: String? = null,
    @SerialName("nik_ibu") val nikIbu: String? = null,
    @SerialName("nama_ibu") val namaIbu: String? = null,
    @SerialName("nomor_kk") val nomorKk: String? = null,
    @SerialName("doc_1_path") val doc1Path: String? = null,
    @SerialName("doc_2_path") val doc2Path: String? = null,
    @SerialName("doc_3_path") val doc3Path: String? = null,
    @SerialName("doc_4_path") val doc4Path: String? = null,
    @SerialName("doc_5_path") val doc5Path: String? = null,
    @SerialName("doc_6_path") val doc6Path: String? = null,
    @SerialName("doc_7_path") val doc7Path: String? = null,
    @SerialName("doc_8_path") val doc8Path: String? = null,
    @SerialName("doc_9_path") val doc9Path: String? = null,
    @SerialName("doc_selfie_path") val docSelfiePath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * DTO detail permohonan Layanan Disabilitas.
 */
@Serializable
data class LaravelDisabilitasDetailDto(
    @SerialName("application_id") val applicationId: String,
    @SerialName("doc_1_path") val doc1Path: String? = null,
    @SerialName("doc_2_path") val doc2Path: String? = null,
    @SerialName("doc_selfie_path") val docSelfiePath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * Pembungkus respons tunggal untuk Create (POST) dan Detail (GET show).
 */
@Serializable
data class LaravelApplicationSingleResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("data") val data: LaravelApplicationDto? = null
)

/**
 * Pembungkus respons paginasi untuk List (GET index).
 * Sesuai dengan struktur LengthAwarePaginator Laravel.
 */
@Serializable
data class LaravelApplicationPaginatedResponse(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("data") val data: List<LaravelApplicationDto> = emptyList(),
    @SerialName("first_page_url") val firstPageUrl: String? = null,
    @SerialName("from") val from: Int? = null,
    @SerialName("last_page") val lastPage: Int = 1,
    @SerialName("last_page_url") val lastPageUrl: String? = null,
    @SerialName("next_page_url") val nextPageUrl: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("per_page") val perPage: Int = 20,
    @SerialName("prev_page_url") val prevPageUrl: String? = null,
    @SerialName("to") val to: Int? = null,
    @SerialName("total") val total: Int = 0
)
