package com.example.siapel.data.remote.laravel

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Provider Singleton HTTP Client Ktor reusable untuk REST API Laravel 12.
 */
object LaravelClientProvider {

    /**
     * Provider Bearer Token global.
     * Token provider menggunakan DataStore untuk autentikasi Laravel.
     */
    var tokenProvider: TokenProvider? = null

    /**
     * Konfigurasi Json serializer kotlinx.
     */
    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = false
        }
    }

    /**
     * Membuat instans Ktor [HttpClient] baru dengan konfigurasi default Laravel.
     */
    fun createClient(customTokenProvider: TokenProvider? = null): HttpClient {
        return HttpClient(Android) {
            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 15_000L
                requestTimeoutMillis = 30_000L
                socketTimeoutMillis = 30_000L
            }

            defaultRequest {
                url(LaravelEndpoints.BASE_URL)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())

                val activeTokenProvider = customTokenProvider ?: tokenProvider
                val token = activeTokenProvider?.getToken()
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }
    }

    /**
     * Instans Client Singleton default.
     */
    val client: HttpClient by lazy {
        createClient()
    }

    /**
     * Helper untuk memvalidasi dan memproses HTTP Response dari Laravel.
     * Menghasilkan [LaravelApiException] jika HTTP status bukan 2xx (Success).
     */
    suspend fun parseOrThrowError(response: HttpResponse): HttpResponse {
        if (response.status.isSuccess()) {
            return response
        }

        val rawResponseBody = try {
            response.bodyAsText()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ""
        }

        val statusCode = response.status.value

        if (statusCode == HttpStatusCode.UnprocessableEntity.value) {
            val validationError = try {
                json.decodeFromString<LaravelValidationErrorResponse>(rawResponseBody)
            } catch (_: Exception) {
                null
            }
            throw LaravelApiException(
                statusCode = statusCode,
                message = validationError?.message ?: "Validasi data gagal",
                errors = validationError?.errors
            )
        }

        val genericError = try {
            json.decodeFromString<LaravelApiResponse<Unit>>(rawResponseBody)
        } catch (_: Exception) {
            null
        }

        val errorMessage = genericError?.message?.ifBlank { null }
            ?: when (statusCode) {
                HttpStatusCode.Unauthorized.value -> "Sesi tidak valid atau telah berakhir (401)"
                HttpStatusCode.Forbidden.value -> "Akses ditolak (403)"
                HttpStatusCode.NotFound.value -> "Sumber daya tidak ditemukan (404)"
                HttpStatusCode.TooManyRequests.value -> "Terlalu banyak permintaan (429). Silakan tunggu beberapa saat."
                HttpStatusCode.InternalServerError.value -> "Kesalahan pada server Laravel (500)"
                else -> "HTTP Request Gagal dengan status [$statusCode]"
            }

        throw LaravelApiException(
            statusCode = statusCode,
            message = errorMessage,
            errors = genericError?.errors
        )
    }
}