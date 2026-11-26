package com.example.siapel.data.remote.laravel

/**
 * Kontrak alur endpoint REST API Laravel 12 SIAPEL.
 *
 * Base URL default Android Emulator: http://10.0.2.2:8000/api/
 */
object LaravelEndpoints {
    const val BASE_URL = "http://10.0.2.2:8000/api/"

    // Authentication Endpoints
    const val REGISTER = "register"
    const val LOGIN = "login"
    const val LOGOUT = "logout"
    const val ME = "me"

    // Applications Endpoints
    const val APPLICATIONS = "applications"

    /**
     * Endpoint permohonan spesifik berdasarkan UUID Laravel.
     * GET /api/applications/{application}
     * PUT/PATCH /api/applications/{application}
     * DELETE /api/applications/{application}
     */
    fun applicationDetail(applicationUuid: String): String = "applications/$applicationUuid"

    /**
     * Endpoint berkas dokumen privat permohonan.
     * POST /api/applications/{application}/documents/{slot}
     * GET /api/applications/{application}/documents/{slot}
     * DELETE /api/applications/{application}/documents/{slot}
     */
    fun applicationDocument(applicationUuid: String, slot: String): String =
        "applications/$applicationUuid/documents/$slot"
}
