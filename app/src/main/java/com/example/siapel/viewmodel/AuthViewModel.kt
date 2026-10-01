package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.local.entity.UserEntity
import com.example.siapel.data.repository.AuthRepository
import com.example.siapel.model.RegisterFormState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // Login Fields
    private val _emailLogin = MutableStateFlow("")
    val emailLogin: StateFlow<String> = _emailLogin.asStateFlow()

    private val _passwordLogin = MutableStateFlow("")
    val passwordLogin: StateFlow<String> = _passwordLogin.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Register Fields
    private val _registerState = MutableStateFlow(RegisterFormState())
    val registerState: StateFlow<RegisterFormState> = _registerState.asStateFlow()

    private val _registerError = MutableStateFlow<String?>(null)
    val registerError: StateFlow<String?> = _registerError.asStateFlow()

    // Login Actions
    fun onEmailLoginChanged(value: String) {
        _emailLogin.value = value
        _loginError.value = null
    }

    fun onPasswordLoginChanged(value: String) {
        _passwordLogin.value = value
        _loginError.value = null
    }

    fun validateAndLogin(onSuccess: () -> Unit) {
        login(onSuccess)
    }

    fun login(onSuccess: () -> Unit) {
        val email = _emailLogin.value.trim()
        val password = _passwordLogin.value

        if (email.isEmpty()) {
            _loginError.value = "Email tidak boleh kosong"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _loginError.value = "Format email tidak valid"
            return
        }
        if (password.isEmpty()) {
            _loginError.value = "Password tidak boleh kosong"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            authRepository.login(email, password)
                .onSuccess { user ->
                    userPreferencesRepository.saveSession(user.id, user.email)
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _loginError.value = error.message ?: "Terjadi kesalahan"
                }
        }
    }

    // Register Actions
    fun onNamaLengkapChanged(value: String) {
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _registerState.update { it.copy(namaLengkap = filtered) }
        _registerError.value = null
    }

    fun onNikChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(16)
        _registerState.update { it.copy(nik = filtered) }
        _registerError.value = null
    }

    fun onEmailRegisterChanged(value: String) {
        _registerState.update { it.copy(email = value) }
        _registerError.value = null
    }

    fun onNomorWhatsappChanged(value: String) {
        val filtered = value.filter { it.isDigit() }
        _registerState.update { it.copy(nomorWhatsapp = filtered) }
        _registerError.value = null
    }

    fun onKecamatanChanged(value: String) {
        _registerState.update { it.copy(kecamatan = value, kelurahan = "") }
        _registerError.value = null
    }

    fun onKelurahanChanged(value: String) {
        _registerState.update { it.copy(kelurahan = value) }
        _registerError.value = null
    }

    fun onPasswordRegisterChanged(value: String) {
        _registerState.update { it.copy(password = value) }
        _registerError.value = null
    }

    fun onKonfirmasiPasswordChanged(value: String) {
        _registerState.update { it.copy(konfirmasiPassword = value) }
        _registerError.value = null
    }

    fun validateAndRegister(onSuccess: () -> Unit) {
        register(onSuccess)
    }

    fun register(onSuccess: () -> Unit) {
        val state = _registerState.value

        val namaWords = state.namaLengkap.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (state.namaLengkap.trim().isEmpty()) {
            _registerError.value = "Nama tidak boleh kosong"
            return
        }
        if (namaWords.size < 2) {
            _registerError.value = "Nama harus terdiri dari minimal 2 kata"
            return
        }
        if (state.nik.length != 16) {
            _registerError.value = "NIK wajib tepat 16 angka"
            return
        }
        if (state.email.trim().isEmpty()) {
            _registerError.value = "Email tidak boleh kosong"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches()) {
            _registerError.value = "Format email tidak valid"
            return
        }
        if (state.nomorWhatsapp.trim().isEmpty()) {
            _registerError.value = "Nomor WhatsApp tidak boleh kosong"
            return
        }
        if (state.kecamatan.isEmpty()) {
            _registerError.value = "Kecamatan wajib dipilih"
            return
        }
        if (state.kelurahan.isEmpty()) {
            _registerError.value = "Kelurahan wajib dipilih"
            return
        }
        if (state.password.length < 8) {
            _registerError.value = "Password minimal 8 karakter"
            return
        }
        if (state.password != state.konfirmasiPassword) {
            _registerError.value = "Konfirmasi password harus sama"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            val user = UserEntity(
                namaLengkap = state.namaLengkap,
                nik = state.nik,
                email = state.email,
                whatsapp = state.nomorWhatsapp,
                kecamatan = state.kecamatan,
                kelurahan = state.kelurahan,
                passwordHash = authRepository.hashPassword(state.password)
            )
            authRepository.register(user)
                .onSuccess {
                    _isLoading.value = false
                    _registerState.value = RegisterFormState()
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _registerError.value = error.message ?: "Gagal mendaftar"
                }
        }
    }
}
