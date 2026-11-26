package com.example.siapel.data.remote.laravel

import com.example.siapel.data.remote.laravel.dto.LaravelAuthResponse
import com.example.siapel.data.remote.laravel.dto.LaravelLoginRequest
import com.example.siapel.data.remote.laravel.dto.LaravelMessageResponse
import com.example.siapel.data.remote.laravel.dto.LaravelRegisterRequest
import com.example.siapel.data.remote.laravel.dto.LaravelUserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * API Service untuk menangani autentikasi Laravel Sanctum 12.
 */
class LaravelAuthApiService(
    private val client: HttpClient = LaravelClientProvider.client
) {

    /**
     * Mendaftarkan pengguna baru via POST /api/register
     */
    suspend fun register(request: LaravelRegisterRequest): LaravelAuthResponse {
        val httpResponse = client.post(LaravelEndpoints.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val validatedResponse = LaravelClientProvider.parseOrThrowError(httpResponse)
        return validatedResponse.body()
    }

    /**
     * Autentikasi dan login pengguna via POST /api/login
     */
    suspend fun login(request: LaravelLoginRequest): LaravelAuthResponse {
        val httpResponse = client.post(LaravelEndpoints.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val validatedResponse = LaravelClientProvider.parseOrThrowError(httpResponse)
        return validatedResponse.body()
    }

    /**
     * Mengambil data pengguna aktif & profil via GET /api/me
     */
    suspend fun getMe(token: String? = null): LaravelUserResponse {
        val httpResponse = client.get(LaravelEndpoints.ME) {
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        val validatedResponse = LaravelClientProvider.parseOrThrowError(httpResponse)
        return validatedResponse.body()
    }

    /**
     * Mengakhiri sesi pengguna (revoke token) via POST /api/logout
     */
    suspend fun logout(token: String? = null): LaravelMessageResponse {
        val httpResponse = client.post(LaravelEndpoints.LOGOUT) {
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        val validatedResponse = LaravelClientProvider.parseOrThrowError(httpResponse)
        return validatedResponse.body()
    }
}
