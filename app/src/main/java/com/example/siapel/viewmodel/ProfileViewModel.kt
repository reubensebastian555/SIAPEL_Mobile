package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.repository.AuthRepository
import com.example.siapel.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (authRepository.isUserLoggedIn()) {
                    val profile = authRepository.getUserProfile()
                    _userProfile.value = profile ?: UserProfile(
                        name = authRepository.getSupabaseFullName() ?: "Pengguna Tidak Diketahui",
                        nik = "",
                        email = authRepository.getCurrentUserEmail() ?: "",
                        whatsapp = "",
                        kecamatan = "",
                        kelurahan = ""
                    )
                } else {
                    _userProfile.value = null
                }
            } catch (e: Exception) {
                if (authRepository.isUserLoggedIn()) {
                    _userProfile.value = UserProfile(
                        name = authRepository.getSupabaseFullName() ?: "Pengguna Tidak Diketahui",
                        nik = "",
                        email = authRepository.getCurrentUserEmail() ?: "",
                        whatsapp = "",
                        kecamatan = "",
                        kelurahan = ""
                    )
                } else {
                    _userProfile.value = null
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            userPreferencesRepository.clearSession()
            onSuccess()
        }
    }
}
