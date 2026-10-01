package com.example.siapel

import android.app.Application
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.datastore.dataStore
import com.example.siapel.data.local.database.SiapelDatabase
import com.example.siapel.data.repository.ApplicationRepository
import com.example.siapel.data.repository.AuthRepository

class SiapelApplication : Application() {
    val database by lazy { SiapelDatabase.getDatabase(this) }
    val authRepository by lazy { AuthRepository(database.userDao()) }
    val applicationRepository by lazy { ApplicationRepository(database.applicationDao()) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(dataStore) }
}
