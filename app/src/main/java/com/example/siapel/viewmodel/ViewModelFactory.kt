package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.repository.LaravelApplicationRepository
import com.example.siapel.data.repository.LaravelAuthRepository

class ViewModelFactory(
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val laravelAuthRepository: LaravelAuthRepository? = null,
    private val laravelApplicationRepository: LaravelApplicationRepository? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T = when {

        // ==========================================
        // MIGRATED â€” LARAVEL AUTH
        // ==========================================

        modelClass.isAssignableFrom(
            AuthViewModel::class.java
        ) -> AuthViewModel(
            requireNotNull(laravelAuthRepository) {
                "LaravelAuthRepository wajib tersedia untuk AuthViewModel"
            }
        ) as T

        modelClass.isAssignableFrom(
            ProfileViewModel::class.java
        ) -> ProfileViewModel(
            requireNotNull(laravelAuthRepository) {
                "LaravelAuthRepository wajib tersedia untuk ProfileViewModel"
            }
        ) as T

        // ==========================================
        // MIGRATED â€” LARAVEL HOME
        // ==========================================

        modelClass.isAssignableFrom(
            HomeViewModel::class.java
        ) -> HomeViewModel(
            requireNotNull(laravelAuthRepository) {
                "LaravelAuthRepository wajib tersedia untuk HomeViewModel"
            },
            requireNotNull(laravelApplicationRepository) {
                "LaravelApplicationRepository wajib tersedia untuk HomeViewModel"
            },
            requireNotNull(userPreferencesRepository) {
                "UserPreferencesRepository wajib tersedia untuk HomeViewModel"
            }
        ) as T

        // ==========================================
        // MIGRATED â€” LARAVEL STATUS
        // ==========================================

        modelClass.isAssignableFrom(
            StatusViewModel::class.java
        ) -> StatusViewModel(
            applicationRepository =
                requireNotNull(laravelApplicationRepository) {
                    "LaravelApplicationRepository wajib tersedia untuk StatusViewModel"
                },
            userPreferencesRepository =
                requireNotNull(userPreferencesRepository) {
                    "UserPreferencesRepository wajib tersedia untuk StatusViewModel"
                },
            authRepository =
                requireNotNull(laravelAuthRepository) {
                    "LaravelAuthRepository wajib tersedia untuk StatusViewModel"
                }
        ) as T

        // ==========================================
        // MIGRATED â€” LARAVEL KIA
        // ==========================================

        modelClass.isAssignableFrom(
            KiaViewModel::class.java
        ) -> KiaViewModel(
            applicationRepository =
                requireNotNull(laravelApplicationRepository) {
                    "LaravelApplicationRepository wajib tersedia untuk KiaViewModel"
                },
            userPreferencesRepository =
                requireNotNull(userPreferencesRepository) {
                    "UserPreferencesRepository wajib tersedia untuk KiaViewModel"
                },
            authRepository =
                requireNotNull(laravelAuthRepository) {
                    "LaravelAuthRepository wajib tersedia untuk KiaViewModel"
                }
        ) as T

        // ==========================================
        // MIGRATED â€” LARAVEL KTP
        // ==========================================

        modelClass.isAssignableFrom(
            KtpViewModel::class.java
        ) -> KtpViewModel(
            applicationRepository =
                requireNotNull(laravelApplicationRepository) {
                    "LaravelApplicationRepository wajib tersedia untuk KtpViewModel"
                },
            userPreferencesRepository =
                requireNotNull(userPreferencesRepository) {
                    "UserPreferencesRepository wajib tersedia untuk KtpViewModel"
                },
            authRepository =
                requireNotNull(laravelAuthRepository) {
                    "LaravelAuthRepository wajib tersedia untuk KtpViewModel"
                }
        ) as T

        // ==========================================
        // MIGRATED â€” LARAVEL KK
        // ==========================================

        modelClass.isAssignableFrom(
            KkViewModel::class.java
        ) -> KkViewModel(
            applicationRepository =
                requireNotNull(laravelApplicationRepository) {
                    "LaravelApplicationRepository wajib tersedia untuk KkViewModel"
                },
            userPreferencesRepository =
                requireNotNull(userPreferencesRepository) {
                    "UserPreferencesRepository wajib tersedia untuk KkViewModel"
                },
            authRepository =
                requireNotNull(laravelAuthRepository) {
                    "LaravelAuthRepository wajib tersedia untuk KkViewModel"
                }
        ) as T

        // ==========================================
        // MIGRATED â€” LARAVEL AKTA
        // ==========================================

        modelClass.isAssignableFrom(
            AktaKelahiranViewModel::class.java
        ) -> AktaKelahiranViewModel(
            applicationRepository =
                requireNotNull(laravelApplicationRepository) {
                    "LaravelApplicationRepository wajib tersedia untuk AktaKelahiranViewModel"
                },
            userPreferencesRepository =
                requireNotNull(userPreferencesRepository) {
                    "UserPreferencesRepository wajib tersedia untuk AktaKelahiranViewModel"
                },
            authRepository =
                requireNotNull(laravelAuthRepository) {
                    "LaravelAuthRepository wajib tersedia untuk AktaKelahiranViewModel"
                }
        ) as T

        // ==========================================
        // MIGRATED â€” LARAVEL DISABILITAS
        // ==========================================

        modelClass.isAssignableFrom(
            DisabilitasViewModel::class.java
        ) -> DisabilitasViewModel(
            applicationRepository =
                requireNotNull(laravelApplicationRepository) {
                    "LaravelApplicationRepository wajib tersedia untuk DisabilitasViewModel"
                },
            userPreferencesRepository =
                requireNotNull(userPreferencesRepository) {
                    "UserPreferencesRepository wajib tersedia untuk DisabilitasViewModel"
                },
            authRepository =
                requireNotNull(laravelAuthRepository) {
                    "LaravelAuthRepository wajib tersedia untuk DisabilitasViewModel"
                }
        ) as T

        else -> throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}

