package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.repository.AuthRepository
import com.example.siapel.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            userPreferencesRepository.userSessionFlow.collectLatest { session ->
                if (session.isLoggedIn) {
                    val user = authRepository.getUserById(session.userId)
                    user?.let {
                        _userProfile.value = UserProfile(
                            name = it.namaLengkap,
                            nik = it.nik,
                            email = it.email,
                            whatsapp = it.whatsapp,
                            kecamatan = it.kecamatan,
                            kelurahan = it.kelurahan
                        )
                    }
                }
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            userPreferencesRepository.clearSession()
            onSuccess()
        }
    }
}
