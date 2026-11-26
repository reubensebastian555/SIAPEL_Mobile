package com.example.siapel.data.remote.laravel

/**
 * Interface penyedia Bearer Token untuk autentikasi Laravel Sanctum.
 * Implementasi akan menghubungkan DataStore / Session Manager pada tahap berikutnya.
 */
fun interface TokenProvider {
    fun getToken(): String?
}
