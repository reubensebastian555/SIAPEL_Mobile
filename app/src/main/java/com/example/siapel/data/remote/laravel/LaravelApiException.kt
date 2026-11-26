package com.example.siapel.data.remote.laravel

/**
 * Exception khusus untuk menangani respons error dari REST API Laravel.
 *
 * @param statusCode Kode status HTTP (misal: 401, 403, 404, 422, 500)
 * @param message Pesan error dari server
 * @param errors Peta error validasi untuk HTTP 422 Unprocessable Content
 */
class LaravelApiException(
    val statusCode: Int,
    override val message: String,
    val errors: Map<String, List<String>>? = null
) : Exception("Laravel API Error [$statusCode]: $message")
