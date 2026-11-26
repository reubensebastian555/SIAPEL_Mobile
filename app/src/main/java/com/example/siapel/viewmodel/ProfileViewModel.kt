package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.repository.LaravelAuthRepository
import com.example.siapel.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: LaravelAuthRepository
) : ViewModel() {

    private val _userProfile =
        MutableStateFlow<UserProfile?>(null)

    val userProfile: StateFlow<UserProfile?> =
        _userProfile.asStateFlow()

    private val _isLoading =
        MutableStateFlow(true)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    private val _isDarkMode =
        MutableStateFlow(false)

    val isDarkMode: StateFlow<Boolean> =
        _isDarkMode.asStateFlow()

    private val _logoutError =
        MutableStateFlow<String?>(null)

    val logoutError: StateFlow<String?> =
        _logoutError.asStateFlow()

    private val _isLoggingOut =
        MutableStateFlow(false)

    val isLoggingOut: StateFlow<Boolean> =
        _isLoggingOut.asStateFlow()

    private var profileLoadJob: Job? = null

    init {
        loadUserProfile()
    }

    // ==========================================
    // PROFILE â€” VERIFIED LARAVEL SESSION
    // ==========================================

    fun loadUserProfile() {
        if (_isLoggingOut.value) return

        profileLoadJob?.cancel()

        profileLoadJob = viewModelScope.launch {

            _isLoading.value = true
            _userProfile.value = null

            try {
                val session =
                    authRepository.getVerifiedLaravelSession()

                if (session == null) {
                    return@launch
                }

                val user =
                    authRepository.getVerifiedSessionContract()

                if (
                    user == null ||
                    user.id != session.localUserId ||
                    user.remoteUserUuid != session.remoteUserUuid
                ) {
                    return@launch
                }

                val fallbackProfile = UserProfile(
                    name = user.namaLengkap.ifBlank {
                        "Pengguna Tidak Diketahui"
                    },
                    nik = user.nik,
                    email = user.email,
                    whatsapp = user.whatsapp,
                    kecamatan = user.kecamatan,
                    kelurahan = user.kelurahan
                )

                val profile = try {
                    authRepository.getUserProfile()
                        ?: fallbackProfile

                } catch (e: CancellationException) {
                    throw e

                } catch (e: Exception) {
                    fallbackProfile
                }

                if (
                    authRepository.isVerifiedSessionCurrent(
                        session
                    )
                ) {
                    _userProfile.value = profile
                } else {
                    _userProfile.value = null
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                _userProfile.value = null

            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==========================================
    // THEME â€” EXISTING BEHAVIOR
    // ==========================================

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    // ==========================================
    // LOGOUT â€” LARAVEL SANCTUM
    // ==========================================

    fun clearLogoutError() {
        if (!_isLoggingOut.value) _logoutError.value = null
    }

    fun logout(onSuccess: () -> Unit) {
        if (_isLoggingOut.value) return

        _isLoggingOut.value = true
        _logoutError.value = null

        viewModelScope.launch {

            try {
                profileLoadJob?.cancel()
                profileLoadJob?.join()
                profileLoadJob = null

                authRepository.logout().getOrThrow()

                _userProfile.value = null
                _isLoading.value = false

                // Session cleanup is handled exclusively
                // by LaravelAuthRepository.

                onSuccess()

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                _logoutError.value =
                    e.message ?: "Gagal keluar dari akun"

            } finally {
                _isLoggingOut.value = false
            }
        }
    }
}
