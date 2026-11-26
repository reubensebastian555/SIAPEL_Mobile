package com.example.siapel

import android.app.Application
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.datastore.dataStore
import com.example.siapel.data.local.database.SiapelDatabase
import com.example.siapel.data.remote.laravel.LaravelClientProvider
import com.example.siapel.data.remote.laravel.LaravelDataStoreTokenProvider
import com.example.siapel.data.repository.LaravelAuthRepository
import com.example.siapel.data.repository.LaravelApplicationRepository
import com.example.siapel.data.repository.LaravelDocumentRepository

class SiapelApplication : Application() {

    // ==========================================
    // SHARED LOCAL INFRASTRUCTURE
    // ==========================================

    val database by lazy {
        SiapelDatabase.getDatabase(this)
    }

    val userPreferencesRepository by lazy {
        UserPreferencesRepository(dataStore)
    }

    // ==========================================
    // LARAVEL TOKEN PROVIDER
    // ==========================================

    val laravelTokenProvider by lazy {
        LaravelDataStoreTokenProvider(
            userPreferencesRepository
        )
    }

    // ==========================================
    // LARAVEL REPOSITORIES
    // ==========================================

    val laravelAuthRepository by lazy {
        LaravelAuthRepository(
            userDao = database.userDao(),
            userPreferencesRepository =
                userPreferencesRepository
        )
    }

    val laravelDocumentRepository by lazy {
        LaravelDocumentRepository()
    }

    val laravelApplicationRepository by lazy {
        LaravelApplicationRepository(
            documentRepository =
                laravelDocumentRepository,
            applicationDao =
                database.applicationDao(),
            authRepository =
                laravelAuthRepository,
            database = database,
            remoteOnly = false
        )
    }

    // ==========================================
    // APPLICATION INITIALIZATION
    // ==========================================

    override fun onCreate() {
        super.onCreate()

        LaravelClientProvider.tokenProvider =
            laravelTokenProvider
    }
}
