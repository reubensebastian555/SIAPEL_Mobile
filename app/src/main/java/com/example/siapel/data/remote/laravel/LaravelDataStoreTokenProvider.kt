package com.example.siapel.data.remote.laravel

import com.example.siapel.data.datastore.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Adapter yang mengimplementasikan [TokenProvider] dengan membaca token Laravel Sanctum dari [UserPreferencesRepository].
 *
 * Menggunakan [StateFlow] untuk mengamati [UserPreferencesRepository.laravelTokenFlow] secara asinkron
 * sehingga [getToken] dapat mengembalikan token terbaru secara instan tanpa memblokir thread HTTP.
 */
class LaravelDataStoreTokenProvider(
    userPreferencesRepository: UserPreferencesRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : TokenProvider {

    private val tokenState: StateFlow<String?> = userPreferencesRepository.laravelTokenFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    override fun getToken(): String? {
        val currentToken = tokenState.value
        return if (!currentToken.isNullOrBlank()) currentToken else null
    }
}
