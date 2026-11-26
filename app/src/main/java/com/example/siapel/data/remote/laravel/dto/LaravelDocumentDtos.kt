package com.example.siapel.data.remote.laravel.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data DTO metadata dokumen setelah berhasil diunggah/diperbarui pada REST API Laravel.
 */
@Serializable
data class LaravelDocumentUploadDataDto(
    @SerialName("slot") val slot: String,
    @SerialName("column") val column: String,
    @SerialName("path") val path: String,
    @SerialName("size") val size: Long,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("replaced") val replaced: Boolean
)

/**
 * Respons tunggal dari endpoint POST /api/applications/{application}/documents/{slot}.
 */
@Serializable
data class LaravelDocumentUploadResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("data") val data: LaravelDocumentUploadDataDto? = null
)

/**
 * Model hasil unduhan file dokumen privat dari endpoint GET /api/applications/{application}/documents/{slot}.
 *
 * @param bytes Data biner file dokumen.
 * @param contentType Header Content-Type dari server (misal: "image/jpeg", "application/pdf").
 * @param contentDisposition Header Content-Disposition dari server (misal: "attachment; filename=\"doc1.jpg\"").
 */
data class LaravelDocumentDownloadResult(
    val bytes: ByteArray,
    val contentType: String? = null,
    val contentDisposition: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LaravelDocumentDownloadResult

        if (!bytes.contentEquals(other.bytes)) return false
        if (contentType != other.contentType) return false
        if (contentDisposition != other.contentDisposition) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (contentDisposition?.hashCode() ?: 0)
        return result
    }
}
