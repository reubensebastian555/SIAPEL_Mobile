package com.example.siapel.data.remote.laravel

import com.example.siapel.data.remote.laravel.dto.LaravelDocumentDownloadResult
import com.example.siapel.data.remote.laravel.dto.LaravelDocumentUploadResponse
import com.example.siapel.data.remote.laravel.dto.LaravelMessageResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException

/**
 * API Service Ktor universal untuk mengelola berkas dokumen privat permohonan pada REST API Laravel 12.
 * Mendukung lima layanan: KIA, KTP, KK, AKTA, dan DISABILITAS.
 */
class LaravelDocumentApiService(
    private val client: HttpClient = LaravelClientProvider.client
) {

    companion object {
        /**
         * Regex untuk memvalidasi format canonical UUID (8-4-4-4-12 heksadesimal).
         */
        private val CANONICAL_UUID_REGEX = Regex(
            "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
        )

        /**
         * Slot dokumen resmi yang didukung backend SIAPEL.
         */
        private val VALID_SLOTS = setOf(
            "doc1", "doc2", "doc3", "doc4", "doc5",
            "doc6", "doc7", "doc8", "doc9", "doc10", "docSelfie"
        )

        /**
         * Batas maksimum ukuran berkas dokumen (2 MiB = 2.097.152 bytes).
         */
        const val MAX_FILE_SIZE_BYTES = 2_097_152L

        /**
         * Peta kesesuaian ekstensi file dan MIME type resmi backend.
         */
        private val ALLOWED_MIME_TYPES = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "pdf" to "application/pdf",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    }

    /**
     * Memvalidasi file dokumen pada client-side sebelum dikirim ke server.
     */
    private fun validateFileClientSide(
        fileBytes: ByteArray,
        filename: String,
        declaredMimeType: String
    ) {
        require(fileBytes.isNotEmpty()) {
            "Dokumen tidak boleh kosong (0 byte)."
        }

        require(fileBytes.size <= MAX_FILE_SIZE_BYTES) {
            "Ukuran berkas dokumen (${fileBytes.size} bytes) melebihi batas maksimum 2 MiB ($MAX_FILE_SIZE_BYTES bytes)."
        }

        val extension = filename.substringAfterLast('.', "").lowercase()
        require(extension.isNotBlank()) {
            "Filename '$filename' harus memiliki ekstensi berkas yang valid."
        }

        val expectedMime = ALLOWED_MIME_TYPES[extension]
        require(expectedMime != null) {
            "Ekstensi berkas '$extension' tidak didukung. Gunakan JPG, PNG, PDF, DOC, atau DOCX."
        }

        require(declaredMimeType.equals(expectedMime, ignoreCase = true)) {
            "Declared MIME type '$declaredMimeType' tidak sesuai dengan ekstensi berkas '$extension' (seharusnya '$expectedMime')."
        }
    }

    /**
     * Mengunggah dokumen baru atau mengganti (replace) dokumen privat existing.
     * Endpoint: POST /api/applications/{application}/documents/{slot}
     *
     * @param applicationUuid UUID String permohonan Laravel
     * @param slot Slot dokumen (misal: 'doc1', 'docSelfie')
     * @param fileBytes Data biner dokumen
     * @param filename Nama file beserta ekstensi (misal: 'ktp.jpg')
     * @param declaredMimeType Declared MIME type (misal: 'image/jpeg')
     * @param token Bearer Token opsional
     * @return [LaravelDocumentUploadResponse] berisi metadata dokumen yang diunggah/diperbarui
     */
    suspend fun uploadOrReplaceDocument(
        applicationUuid: String,
        slot: String,
        fileBytes: ByteArray,
        filename: String,
        declaredMimeType: String,
        token: String? = null
    ): LaravelDocumentUploadResponse {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        require(VALID_SLOTS.contains(slot)) {
            "Slot dokumen '$slot' tidak valid. Slot resmi: $VALID_SLOTS"
        }

        validateFileClientSide(fileBytes, filename, declaredMimeType)

        val safeFilename = filename.replace("\"", "\\\"").replace("\r", "").replace("\n", "")

        try {
            val httpResponse = client.post(LaravelEndpoints.applicationDocument(applicationUuid, slot)) {
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = fileBytes,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, declaredMimeType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"$safeFilename\"")
                                }
                            )
                        }
                    )
                )
            }

            val validatedResponse = LaravelClientProvider.parseOrThrowError(httpResponse)
            val uploadResponse = validatedResponse.body<LaravelDocumentUploadResponse>()

            if (uploadResponse.data == null) {
                throw LaravelApiException(
                    statusCode = httpResponse.status.value,
                    message = "Respons upload dokumen tidak memuat metadata 'data' yang valid"
                )
            }

            return uploadResponse
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Mengunduh/membaca stream biner dokumen privat dari server.
     * Endpoint: GET /api/applications/{application}/documents/{slot}
     *
     * @param applicationUuid UUID String permohonan Laravel
     * @param slot Slot dokumen (misal: 'doc1', 'docSelfie')
     * @param token Bearer Token opsional
     * @return [LaravelDocumentDownloadResult] berisi byte content dan header HTTP
     */
    suspend fun downloadDocument(
        applicationUuid: String,
        slot: String,
        token: String? = null
    ): LaravelDocumentDownloadResult {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        require(VALID_SLOTS.contains(slot)) {
            "Slot dokumen '$slot' tidak valid. Slot resmi: $VALID_SLOTS"
        }

        try {
            val httpResponse = client.get(LaravelEndpoints.applicationDocument(applicationUuid, slot)) {
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            val validatedResponse = LaravelClientProvider.parseOrThrowError(httpResponse)
            val bytes = validatedResponse.body<ByteArray>()
            val contentType = validatedResponse.headers[HttpHeaders.ContentType]
            val contentDisposition = validatedResponse.headers[HttpHeaders.ContentDisposition]

            return LaravelDocumentDownloadResult(
                bytes = bytes,
                contentType = contentType,
                contentDisposition = contentDisposition
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Menghapus dokumen privat dari server.
     * Endpoint: DELETE /api/applications/{application}/documents/{slot}
     *
     * @param applicationUuid UUID String permohonan Laravel
     * @param slot Slot dokumen (misal: 'doc1', 'docSelfie')
     * @param token Bearer Token opsional
     * @return [LaravelMessageResponse] (HTTP 200)
     */
    suspend fun deleteDocument(
        applicationUuid: String,
        slot: String,
        token: String? = null
    ): LaravelMessageResponse {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        require(VALID_SLOTS.contains(slot)) {
            "Slot dokumen '$slot' tidak valid. Slot resmi: $VALID_SLOTS"
        }

        try {
            val httpResponse = client.delete(LaravelEndpoints.applicationDocument(applicationUuid, slot)) {
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            val validatedResponse = LaravelClientProvider.parseOrThrowError(httpResponse)
            return validatedResponse.body()
        } catch (e: CancellationException) {
            throw e
        }
    }
}
