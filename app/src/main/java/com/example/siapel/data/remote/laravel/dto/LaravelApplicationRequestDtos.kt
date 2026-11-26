package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request DTO detail permohonan KIA.
 */
@Serializable
data class LaravelKiaDetailRequest(
    @SerialName("nik_anak") val nikAnak: String,
    @SerialName("nama_anak") val namaAnak: String
)

/**
 * Request DTO detail permohonan Kartu Keluarga (KK).
 */
@Serializable
data class LaravelKkDetailRequest(
    @SerialName("nik_anak") val nikAnak: String? = null,
    @SerialName("nama_anak") val namaAnak: String? = null,
    @SerialName("nomor_kk") val nomorKk: String? = null
)

/**
 * Request DTO detail permohonan Akta Kelahiran.
 */
@Serializable
data class LaravelAktaDetailRequest(
    @SerialName("nik_bayi") val nikBayi: String? = null,
    @SerialName("nama_bayi") val namaBayi: String,
    @SerialName("nik_ibu") val nikIbu: String,
    @SerialName("nama_ibu") val namaIbu: String,
    @SerialName("nomor_kk") val nomorKk: String
)

/**
 * Request DTO permohonan KIA (Kartu Identitas Anak).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LaravelKiaApplicationRequest(
    @SerialName("service_type") val serviceType: String = "KIA",
    @SerialName("category") val category: String,
    @SerialName("nik_pelapor") val nikPelapor: String,
    @SerialName("nama_pelapor") val namaPelapor: String,
    @SerialName("email") val email: String,
    @SerialName("whatsapp") val whatsapp: String,
    @SerialName("kecamatan") val kecamatan: String,
    @SerialName("kelurahan") val kelurahan: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("keterangan") val keterangan: String? = null,
    @SerialName("detail") val detail: LaravelKiaDetailRequest
)

/**
 * Request DTO permohonan KTP Elektronik (tanpa object detail).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LaravelKtpApplicationRequest(
    @SerialName("service_type") val serviceType: String = "KTP",
    @SerialName("category") val category: String,
    @SerialName("nik_pelapor") val nikPelapor: String,
    @SerialName("nama_pelapor") val namaPelapor: String,
    @SerialName("email") val email: String,
    @SerialName("whatsapp") val whatsapp: String,
    @SerialName("kecamatan") val kecamatan: String,
    @SerialName("kelurahan") val kelurahan: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("keterangan") val keterangan: String? = null
)

/**
 * Request DTO permohonan Kartu Keluarga (KK).
 * [detail] menggunakan @EncodeDefault(NEVER) agar key "detail" ABSENT saat null,
 * memenuhi aturan validasi backend Laravel "sometimes|array".
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LaravelKkApplicationRequest(
    @SerialName("service_type") val serviceType: String = "KK",
    @SerialName("category") val category: String,
    @SerialName("nik_pelapor") val nikPelapor: String,
    @SerialName("nama_pelapor") val namaPelapor: String,
    @SerialName("email") val email: String,
    @SerialName("whatsapp") val whatsapp: String,
    @SerialName("kecamatan") val kecamatan: String,
    @SerialName("kelurahan") val kelurahan: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("keterangan") val keterangan: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("detail") val detail: LaravelKkDetailRequest? = null
)

/**
 * Request DTO permohonan Akta Kelahiran.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LaravelAktaApplicationRequest(
    @SerialName("service_type") val serviceType: String = "AKTA",
    @SerialName("category") val category: String,
    @SerialName("nik_pelapor") val nikPelapor: String,
    @SerialName("nama_pelapor") val namaPelapor: String,
    @SerialName("email") val email: String,
    @SerialName("whatsapp") val whatsapp: String,
    @SerialName("kecamatan") val kecamatan: String,
    @SerialName("kelurahan") val kelurahan: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("keterangan") val keterangan: String? = null,
    @SerialName("detail") val detail: LaravelAktaDetailRequest
)

/**
 * Request DTO permohonan Layanan Disabilitas (tanpa object detail).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LaravelDisabilitasApplicationRequest(
    @SerialName("service_type") val serviceType: String = "DISABILITAS",
    @SerialName("category") val category: String,
    @SerialName("nik_pelapor") val nikPelapor: String,
    @SerialName("nama_pelapor") val namaPelapor: String,
    @SerialName("email") val email: String,
    @SerialName("whatsapp") val whatsapp: String,
    @SerialName("kecamatan") val kecamatan: String,
    @SerialName("kelurahan") val kelurahan: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("keterangan") val keterangan: String? = null
)
