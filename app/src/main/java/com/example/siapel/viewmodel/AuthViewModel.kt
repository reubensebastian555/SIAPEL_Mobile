package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.local.entity.UserEntity
import com.example.siapel.data.repository.LaravelAuthRepository
import com.example.siapel.model.RegisterFormState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: LaravelAuthRepository
) : ViewModel() {

    // ==========================================
    // LOGIN FIELDS
    // ==========================================

    private val _emailLogin = MutableStateFlow("")
    val emailLogin: StateFlow<String> =
        _emailLogin.asStateFlow()

    private val _passwordLogin = MutableStateFlow("")
    val passwordLogin: StateFlow<String> =
        _passwordLogin.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> =
        _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    // ==========================================
    // REGISTER FIELDS
    // ==========================================

    private val _registerState =
        MutableStateFlow(RegisterFormState())

    val registerState: StateFlow<RegisterFormState> =
        _registerState.asStateFlow()

    private val _registerError =
        MutableStateFlow<String?>(null)

    val registerError: StateFlow<String?> =
        _registerError.asStateFlow()

    // ==========================================
    // LOGIN ACTIONS
    // ==========================================

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
        if (_isLoading.value) return

        val email = _emailLogin.value.trim()
        val password = _passwordLogin.value

        if (email.isEmpty()) {
            _loginError.value = "Email tidak boleh kosong"
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS
                .matcher(email).matches()
        ) {
            _loginError.value = "Format email tidak valid"
            return
        }

        if (password.isEmpty()) {
            _loginError.value = "Password tidak boleh kosong"
            return
        }

        _loginError.value = null
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = authRepository.login(
                    email,
                    password
                )

                val user = result.getOrThrow()

                // LaravelAuthRepository owns session persistence.
                // Do not overwrite DataStore from ViewModel.

                val verifiedSession =
                    authRepository.getVerifiedLaravelSession()

                check(
                    verifiedSession != null &&
                            verifiedSession.localUserId == user.id &&
                            verifiedSession.remoteUserUuid ==
                            user.remoteUserUuid &&
                            verifiedSession.email == user.email
                ) {
                    "Sesi autentikasi Laravel tidak valid"
                }

                check(
                    authRepository.isVerifiedSessionCurrent(
                        verifiedSession
                    )
                ) {
                    "Sesi autentikasi telah berubah"
                }

                _passwordLogin.value = ""
                _isLoading.value = false

                onSuccess()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _loginError.value =
                    e.message ?: "Terjadi kesalahan"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==========================================
    // REGISTER ACTIONS
    // ==========================================

    fun onNamaLengkapChanged(value: String) {
        val filtered = value.filter {
            it.isLetter() ||
                    it.isWhitespace() ||
                    it == '\'' ||
                    it == '-'
        }.take(60)

        _registerState.update {
            it.copy(namaLengkap = filtered)
        }

        _registerError.value = null
    }

    fun onNikChanged(value: String) {
        val filtered = value.filter {
            it.isDigit()
        }.take(16)

        _registerState.update {
            it.copy(nik = filtered)
        }

        _registerError.value = null
    }

    fun onEmailRegisterChanged(value: String) {
        _registerState.update {
            it.copy(email = value)
        }

        _registerError.value = null
    }

    fun onNomorWhatsappChanged(value: String) {
        val filtered = value.filter {
            it.isDigit()
        }

        _registerState.update {
            it.copy(nomorWhatsapp = filtered)
        }

        _registerError.value = null
    }

    fun onKecamatanChanged(value: String) {
        _registerState.update {
            it.copy(
                kecamatan = value,
                kelurahan = ""
            )
        }

        _registerError.value = null
    }

    fun onKelurahanChanged(value: String) {
        _registerState.update {
            it.copy(kelurahan = value)
        }

        _registerError.value = null
    }

    fun onPasswordRegisterChanged(value: String) {
        _registerState.update {
            it.copy(password = value)
        }

        _registerError.value = null
    }

    fun onKonfirmasiPasswordChanged(value: String) {
        _registerState.update {
            it.copy(konfirmasiPassword = value)
        }

        _registerError.value = null
    }

    fun validateAndRegister(onSuccess: () -> Unit) {
        register(onSuccess)
    }

    fun register(onSuccess: () -> Unit) {
        if (_isLoading.value) return

        val state = _registerState.value

        val namaWords = state.namaLengkap.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (state.namaLengkap.trim().isEmpty()) {
            _registerError.value = "Nama tidak boleh kosong"
            return
        }

        if (namaWords.size < 2) {
            _registerError.value =
                "Nama harus terdiri dari minimal 2 kata"
            return
        }

        if (state.nik.length != 16) {
            _registerError.value =
                "NIK wajib tepat 16 angka"
            return
        }

        if (state.email.trim().isEmpty()) {
            _registerError.value =
                "Email tidak boleh kosong"
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS
                .matcher(state.email.trim()).matches()
        ) {
            _registerError.value =
                "Format email tidak valid"
            return
        }

        if (state.nomorWhatsapp.trim().isEmpty()) {
            _registerError.value =
                "Nomor WhatsApp tidak boleh kosong"
            return
        }

        if (state.kecamatan.isEmpty()) {
            _registerError.value =
                "Kecamatan wajib dipilih"
            return
        }

        if (state.kelurahan.isEmpty()) {
            _registerError.value =
                "Kelurahan wajib dipilih"
            return
        }

        if (state.password.length < 8) {
            _registerError.value =
                "Password minimal 8 karakter"
            return
        }

        if (state.password != state.konfirmasiPassword) {
            _registerError.value =
                "Konfirmasi password harus sama"
            return
        }

        _registerError.value = null
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val user = UserEntity(
                    namaLengkap = state.namaLengkap.trim(),
                    nik = state.nik,
                    email = state.email.trim(),
                    whatsapp = state.nomorWhatsapp,
                    kecamatan = state.kecamatan,
                    kelurahan = state.kelurahan,
                    passwordHash = "LARAVEL_AUTH"
                )

                authRepository.register(
                    user,
                    state.password
                ).getOrThrow()

                _registerState.value = RegisterFormState()
                _isLoading.value = false

                onSuccess()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _registerError.value =
                    e.message ?: "Gagal mendaftar"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
