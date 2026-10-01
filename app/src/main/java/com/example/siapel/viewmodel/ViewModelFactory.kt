package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.repository.ApplicationRepository
import com.example.siapel.data.repository.AuthRepository

class ViewModelFactory(
    private val authRepository: AuthRepository? = null,
    private val applicationRepository: ApplicationRepository? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(authRepository!!, userPreferencesRepository!!) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(authRepository!!, applicationRepository!!, userPreferencesRepository!!) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(authRepository!!, userPreferencesRepository!!) as T
            }
            modelClass.isAssignableFrom(StatusViewModel::class.java) -> {
                StatusViewModel(applicationRepository!!, userPreferencesRepository!!) as T
            }
            modelClass.isAssignableFrom(KiaViewModel::class.java) -> {
                KiaViewModel(applicationRepository!!, userPreferencesRepository!!, authRepository!!) as T
            }
            modelClass.isAssignableFrom(KtpViewModel::class.java) -> {
                KtpViewModel(applicationRepository!!, userPreferencesRepository!!, authRepository!!) as T
            }
            modelClass.isAssignableFrom(KkViewModel::class.java) -> {
                KkViewModel(applicationRepository!!, userPreferencesRepository!!, authRepository!!) as T
            }
            modelClass.isAssignableFrom(AktaKelahiranViewModel::class.java) -> {
                AktaKelahiranViewModel(applicationRepository!!, userPreferencesRepository!!, authRepository!!) as T
            }
            modelClass.isAssignableFrom(DisabilitasViewModel::class.java) -> {
                DisabilitasViewModel(applicationRepository!!, userPreferencesRepository!!, authRepository!!) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
