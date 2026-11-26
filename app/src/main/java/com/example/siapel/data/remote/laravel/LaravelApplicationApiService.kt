package com.example.siapel.data.remote.laravel

import com.example.siapel.data.remote.laravel.dto.LaravelAktaApplicationRequest
import com.example.siapel.data.remote.laravel.dto.LaravelApplicationPaginatedResponse
import com.example.siapel.data.remote.laravel.dto.LaravelApplicationSingleResponse
import com.example.siapel.data.remote.laravel.dto.LaravelDisabilitasApplicationRequest
import com.example.siapel.data.remote.laravel.dto.LaravelKiaApplicationRequest
import com.example.siapel.data.remote.laravel.dto.LaravelKkApplicationRequest
import com.example.siapel.data.remote.laravel.dto.LaravelKtpApplicationRequest
import com.example.siapel.data.remote.laravel.dto.LaravelMessageResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * API Service Ktor untuk membaca, membuat, memperbarui, dan menghapus permohonan dari REST API Laravel 12.
 */
class LaravelApplicationApiService(
    private val client: HttpClient = LaravelClientProvider.client
) {

    companion object {
        /**
         * Regex untuk memvalidasi format canonical UUID (8-4-4-4-12 heksadesimal).
         * Menerima karakter heksadesimal huruf besar, huruf kecil, maupun mixed-case.
         */
        private val CANONICAL_UUID_REGEX = Regex(
            "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
        )
    }

    /**
     * Mengambil daftar permohonan milik pengguna via GET /api/applications.
     *
     * @param serviceType Filter jenis layanan (misal: 'KIA', 'KTP', 'KK', 'AKTA', 'DISABILITAS')
     * @param status Filter status permohonan (misal: 'SUBMITTED', 'PROCESSING', 'COMPLETED', 'NEED_REVISION')
     * @param perPage Jumlah item per halaman (1..100)
     * @param page Nomor halaman paginasi
     * @param token Bearer Token opsional (menggunakan default provider jika null)
     */
    suspend fun getApplications(
        serviceType: String? = null,
        status: String? = null,
        perPage: Int? = null,
        page: Int? = null,
        token: String? = null
    ): LaravelApplicationPaginatedResponse {
        try {
            val httpResponse = client.get(LaravelEndpoints.APPLICATIONS) {
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                url {
                    serviceType?.let { if (it.isNotBlank()) parameter("service_type", it) }
                    status?.let { if (it.isNotBlank()) parameter("status", it) }
                    perPage?.let { if (it > 0) parameter("per_page", it) }
                    page?.let { if (it > 0) parameter("page", it) }
                }
            }
            val validatedResponse = LaravelClientProvider.parseOrThrowError(httpResponse)
            return validatedResponse.body()
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Mengambil satu permohonan spesifik berdasarkan UUID remote Laravel via GET /api/applications/{application}.
     *
     * @param applicationUuid UUID String permohonan Laravel (bukan Int ID Room dan bukan submission_code)
     * @param token Bearer Token opsional
     */
    suspend fun getApplicationDetail(
        applicationUuid: String,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        try {
            val httpResponse = client.get(LaravelEndpoints.applicationDetail(applicationUuid)) {
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

    /**
     * Membuat permohonan baru khusus KIA via POST /api/applications.
     *
     * @param request Payload permohonan KIA ([LaravelKiaApplicationRequest])
     * @param token Bearer Token opsional (menggunakan default provider jika null)
     * @return [LaravelApplicationSingleResponse] berisi data permohonan yang baru dibuat (HTTP 201)
     */
    suspend fun createKiaApplication(
        request: LaravelKiaApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        try {
            val httpResponse = client.post(LaravelEndpoints.APPLICATIONS) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Membuat permohonan baru khusus KTP Elektronik via POST /api/applications.
     *
     * @param request Payload permohonan KTP ([LaravelKtpApplicationRequest])
     * @param token Bearer Token opsional (menggunakan default provider jika null)
     * @return [LaravelApplicationSingleResponse] berisi data permohonan yang baru dibuat (HTTP 201)
     */
    suspend fun createKtpApplication(
        request: LaravelKtpApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        try {
            val httpResponse = client.post(LaravelEndpoints.APPLICATIONS) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Membuat permohonan baru khusus Kartu Keluarga (KK) via POST /api/applications.
     *
     * @param request Payload permohonan KK ([LaravelKkApplicationRequest])
     * @param token Bearer Token opsional (menggunakan default provider jika null)
     * @return [LaravelApplicationSingleResponse] berisi data permohonan yang baru dibuat (HTTP 201)
     */
    suspend fun createKkApplication(
        request: LaravelKkApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        try {
            val httpResponse = client.post(LaravelEndpoints.APPLICATIONS) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Membuat permohonan baru khusus Akta Kelahiran via POST /api/applications.
     *
     * @param request Payload permohonan Akta Kelahiran ([LaravelAktaApplicationRequest])
     * @param token Bearer Token opsional (menggunakan default provider jika null)
     * @return [LaravelApplicationSingleResponse] berisi data permohonan yang baru dibuat (HTTP 201)
     */
    suspend fun createAktaApplication(
        request: LaravelAktaApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        try {
            val httpResponse = client.post(LaravelEndpoints.APPLICATIONS) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Membuat permohonan baru khusus Layanan Disabilitas via POST /api/applications.
     *
     * @param request Payload permohonan Disabilitas ([LaravelDisabilitasApplicationRequest])
     * @param token Bearer Token opsional (menggunakan default provider jika null)
     * @return [LaravelApplicationSingleResponse] berisi data permohonan yang baru dibuat (HTTP 201)
     */
    suspend fun createDisabilitasApplication(
        request: LaravelDisabilitasApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        try {
            val httpResponse = client.post(LaravelEndpoints.APPLICATIONS) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Memperbarui permohonan KIA via PUT /api/applications/{application}.
     *
     * @param applicationUuid UUID String permohonan Laravel
     * @param request Payload permohonan KIA ([LaravelKiaApplicationRequest])
     * @param token Bearer Token opsional
     * @return [LaravelApplicationSingleResponse] (HTTP 200)
     */
    suspend fun updateKiaApplication(
        applicationUuid: String,
        request: LaravelKiaApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        try {
            val httpResponse = client.put(LaravelEndpoints.applicationDetail(applicationUuid)) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Memperbarui permohonan KTP Elektronik via PUT /api/applications/{application}.
     *
     * @param applicationUuid UUID String permohonan Laravel
     * @param request Payload permohonan KTP ([LaravelKtpApplicationRequest])
     * @param token Bearer Token opsional
     * @return [LaravelApplicationSingleResponse] (HTTP 200)
     */
    suspend fun updateKtpApplication(
        applicationUuid: String,
        request: LaravelKtpApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        try {
            val httpResponse = client.put(LaravelEndpoints.applicationDetail(applicationUuid)) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Memperbarui permohonan Kartu Keluarga (KK) via PUT /api/applications/{application}.
     *
     * @param applicationUuid UUID String permohonan Laravel
     * @param request Payload permohonan KK ([LaravelKkApplicationRequest])
     * @param token Bearer Token opsional
     * @return [LaravelApplicationSingleResponse] (HTTP 200)
     */
    suspend fun updateKkApplication(
        applicationUuid: String,
        request: LaravelKkApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        try {
            val httpResponse = client.put(LaravelEndpoints.applicationDetail(applicationUuid)) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Memperbarui permohonan Akta Kelahiran via PUT /api/applications/{application}.
     *
     * @param applicationUuid UUID String permohonan Laravel
     * @param request Payload permohonan Akta Kelahiran ([LaravelAktaApplicationRequest])
     * @param token Bearer Token opsional
     * @return [LaravelApplicationSingleResponse] (HTTP 200)
     */
    suspend fun updateAktaApplication(
        applicationUuid: String,
        request: LaravelAktaApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        try {
            val httpResponse = client.put(LaravelEndpoints.applicationDetail(applicationUuid)) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Memperbarui permohonan Layanan Disabilitas via PUT /api/applications/{application}.
     *
     * @param applicationUuid UUID String permohonan Laravel
     * @param request Payload permohonan Disabilitas ([LaravelDisabilitasApplicationRequest])
     * @param token Bearer Token opsional
     * @return [LaravelApplicationSingleResponse] (HTTP 200)
     */
    suspend fun updateDisabilitasApplication(
        applicationUuid: String,
        request: LaravelDisabilitasApplicationRequest,
        token: String? = null
    ): LaravelApplicationSingleResponse {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        try {
            val httpResponse = client.put(LaravelEndpoints.applicationDetail(applicationUuid)) {
                contentType(ContentType.Application.Json)
                setBody(request)
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

    /**
     * Menghapus permohonan via DELETE /api/applications/{application}.
     *
     * @param applicationUuid UUID String permohonan Laravel
     * @param token Bearer Token opsional
     * @return [LaravelMessageResponse] (HTTP 200)
     */
    suspend fun deleteApplication(
        applicationUuid: String,
        token: String? = null
    ): LaravelMessageResponse {
        require(CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            "Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."
        }

        try {
            val httpResponse = client.delete(LaravelEndpoints.applicationDetail(applicationUuid)) {
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
