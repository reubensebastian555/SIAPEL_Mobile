package com.example.siapel.viewmodel

import android.content.Context
import android.util.Patterns
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.repository.*
import com.example.siapel.data.remote.laravel.dto.LaravelKtpApplicationRequest
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.KtpFormState
import com.example.siapel.model.SelectedDocument
import com.example.siapel.model.SubmitState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class KtpViewModel(
    private val applicationRepository: LaravelApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: LaravelAuthRepository
) : ViewModel() {
    private val _uiState = mutableStateOf(KtpFormState())
    val uiState: State<KtpFormState> = _uiState
    private val _isEditMode = mutableStateOf(false)
    val isEditMode: State<Boolean> = _isEditMode
    private val _submissionResult = MutableSharedFlow<Boolean>()
    val submissionResult: SharedFlow<Boolean> = _submissionResult.asSharedFlow()
    private val _submitState = mutableStateOf(SubmitState.IDLE)
    val submitState: State<SubmitState> = _submitState
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    private val _isEditLoading = mutableStateOf(false)
    val isEditLoading: State<Boolean> = _isEditLoading
    private val _submissionBlocked = mutableStateOf(false)
    val submissionBlocked: State<Boolean> = _submissionBlocked
    private var isEditInitialized = false
    private var editingApplication: ApplicationEntity? = null
    private var formSession: VerifiedLaravelSession? = null
    private var loadJob: Job? = null
    private var submitJob: Job? = null
    private var reconciliationRequired = false
    private var sessionInvalidated = false

    init {
        viewModelScope.launch {
            val session = authRepository.getVerifiedLaravelSession() ?: return@launch
            val user = authRepository.getVerifiedSessionContract() ?: return@launch
            if (!isEditInitialized && user.id == session.localUserId &&
                user.remoteUserUuid == session.remoteUserUuid &&
                authRepository.isVerifiedSessionCurrent(session)) {
                formSession = session
                _uiState.value = _uiState.value.copy(
                    email = user.email, whatsapp = user.whatsapp
                )
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.userSessionFlow.collect { stored ->
                val bound = formSession ?: return@collect
                if (!stored.isLoggedIn || stored.generation != bound.generation ||
                    stored.userId != bound.localUserId || stored.remoteUserUuid != bound.remoteUserUuid ||
                    stored.token != bound.token) {
                    sessionInvalidated = true
                    _submissionBlocked.value = true
                    _isEditLoading.value = false
                    loadJob?.cancel()
                    submitJob?.cancel()
                    editingApplication = null
                    _uiState.value = KtpFormState()
                    _isEditMode.value = false
                    _submitState.value = SubmitState.IDLE
                    _operationError.value = "Sesi berubah. Buka kembali formulir."
                }
            }
        }
    }

    // Server paths are metadata, not readable Android URIs.
    private fun existingDocument(path: String?): SelectedDocument? =
        path?.takeIf { it.isNotBlank() }?.let {
            SelectedDocument(uri = null, displayName = "Dokumen tersimpan")
        }

    private suspend fun requireSession(): VerifiedLaravelSession {
        check(!sessionInvalidated) { "Sesi berubah" }
        val current = requireNotNull(authRepository.getVerifiedLaravelSession())
        check(formSession == null || formSession == current) { "Sesi berubah" }
        formSession = current
        check(authRepository.isVerifiedSessionCurrent(current))
        return current
    }

    private suspend fun findOwnedApplication(id: Int, session: VerifiedLaravelSession): ApplicationEntity {
        val app = requireNotNull(applicationRepository.observeCachedApplications(session).first()
            .firstOrNull { it.id == id && it.serviceType.equals("KTP", true) })
        check(app.userId == session.localUserId && app.remoteUserUuid == session.remoteUserUuid)
        check(!app.remoteUuid.isNullOrBlank())
        return app
    }

    private fun requireEditable(app: ApplicationEntity) {
        check(app.status == ApplicationStatus.SUBMITTED || app.status == ApplicationStatus.NEED_REVISION)
    }

    fun loadApplication(id: Int) {
        if (submitJob?.isActive == true || reconciliationRequired || sessionInvalidated) return
        isEditInitialized = true
        _isEditMode.value = true
        editingApplication = null
        loadJob?.cancel()
        _isEditLoading.value = true
        _submissionBlocked.value = true
        _operationError.value = null
        loadJob = viewModelScope.launch {
            try {
                val session = requireSession()
                val cached = findOwnedApplication(id, session)
                applicationRepository.getApplicationDetail(
                    applicationUuid = requireNotNull(cached.remoteUuid),
                    localUserId = session.localUserId, remoteUserUuid = session.remoteUserUuid,
                    token = session.token
                ).getOrThrow()
                val app = findOwnedApplication(id, session)
                requireEditable(app)
                check(authRepository.isVerifiedSessionCurrent(session))
                editingApplication = app
                _uiState.value = KtpFormState(
                    kategoriLayanan = app.category, kecamatan = app.kecamatan,
                    kelurahan = app.kelurahan, email = app.email, whatsapp = app.whatsapp,
                    keterangan = app.keterangan, nikPelapor = app.nikPelapor,
                    namaPelapor = app.namaPelapor,
                    docKkTerbaru = existingDocument(app.doc1),
                    docKtpLama = if (app.category.contains("RUSAK", true) || app.category.contains("RUBAH", true)) existingDocument(app.doc2) else null,
                    docSuratKeteranganHilang = if (app.category.contains("HILANG", true)) existingDocument(app.doc2) else null,
                    docSelfie = existingDocument(app.docSelfie)
                )
                _operationError.value = null
                _submissionBlocked.value = false
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _operationError.value = "Pengajuan tidak dapat dimuat atau tidak dapat diedit."
                _uiState.value = KtpFormState()
            } finally {
                _isEditLoading.value = false
            }
        }
    }

    fun onKategoriLayananChange(value: String) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        _uiState.value = _uiState.value.copy(kategoriLayanan = value, kategoriError = null)
    }

    fun onKecamatanChange(value: String) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        _uiState.value = _uiState.value.copy(
            kecamatan = value,
            kelurahan = "",
            kecamatanError = null,
            kelurahanError = null
        )
    }

    fun onKelurahanChange(value: String) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        _uiState.value = _uiState.value.copy(kelurahan = value, kelurahanError = null)
    }

    fun onEmailChange(value: String) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        _uiState.value = _uiState.value.copy(email = value, emailError = null)
    }

    fun onWhatsappChange(value: String) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        val filtered = value.filter { it.isDigit() }
        _uiState.value = _uiState.value.copy(whatsapp = filtered, whatsappError = null)
    }

    fun onKeteranganChange(value: String) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        _uiState.value = _uiState.value.copy(keterangan = value)
    }

    fun onNamaPelaporChange(value: String) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.value = _uiState.value.copy(namaPelapor = filtered, namaPelaporError = null)
    }

    fun onNikPelaporChange(value: String) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.value = _uiState.value.copy(nikPelapor = filtered, nikPelaporError = null)
    }

    fun onDocKkTerbaruChange(doc: SelectedDocument?) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        _uiState.value = _uiState.value.copy(docKkTerbaru = doc)
    }

    fun onDocKtpLamaChange(doc: SelectedDocument?) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        _uiState.value = _uiState.value.copy(docKtpLama = doc)
    }

    fun onDocSuratKeteranganHilangChange(doc: SelectedDocument?) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        _uiState.value = _uiState.value.copy(docSuratKeteranganHilang = doc)
    }

    fun onDocSelfieChange(doc: SelectedDocument?) {
        if (_isEditLoading.value || _submissionBlocked.value || _submitState.value != SubmitState.IDLE) return
        _uiState.value = _uiState.value.copy(docSelfie = doc)
    }

    fun validate(): Boolean {
        val state = _uiState.value
        val emailPattern = Patterns.EMAIL_ADDRESS

        val kategoriError = if (state.kategoriLayanan.isEmpty()) "Kategori layanan wajib dipilih" else null
        val kecamatanError = if (state.kecamatan.isEmpty()) "Kecamatan wajib dipilih" else null
        val kelurahanError = if (state.kelurahan.isEmpty()) "Kelurahan wajib dipilih" else null
        val emailError = when {
            state.email.isEmpty() -> "Email wajib diisi"
            !emailPattern.matcher(state.email).matches() -> "Format email tidak valid"
            else -> null
        }
        val whatsappError = if (state.whatsapp.isEmpty()) "Nomor WhatsApp wajib diisi" else null
        val namaPelaporWords = state.namaPelapor.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val namaPelaporError = when {
            state.namaPelapor.isEmpty() -> "Nama Pelapor wajib diisi"
            namaPelaporWords.size < 2 -> "Nama harus terdiri dari minimal 2 kata"
            else -> null
        }
        val nikPelaporError = when {
            state.nikPelapor.isEmpty() -> "NIK Pelapor wajib diisi"
            state.nikPelapor.length != 16 -> "NIK harus terdiri dari 16 digit"
            else -> null
        }

        _uiState.value = state.copy(
            kategoriError = kategoriError,
            kecamatanError = kecamatanError,
            kelurahanError = kelurahanError,
            emailError = emailError,
            whatsappError = whatsappError,
            namaPelaporError = namaPelaporError,
            nikPelaporError = nikPelaporError
        )

        return kategoriError == null && kecamatanError == null && kelurahanError == null &&
                emailError == null && whatsappError == null && namaPelaporError == null &&
                nikPelaporError == null
    }

    private fun documents(state: KtpFormState): Map<String, SelectedDocument?> {
        val cat = state.kategoriLayanan.uppercase()
        return linkedMapOf(
            "doc1" to state.docKkTerbaru,
            "doc2" to when {
                cat.contains("PEREKAMAN") -> null
                cat.contains("HILANG") -> state.docSuratKeteranganHilang
                else -> state.docKtpLama
            },
            "docSelfie" to state.docSelfie
        )
    }

    fun submitForm(context: Context) {
        if (submitJob?.isActive == true || loadJob?.isActive == true ||
            _submitState.value == SubmitState.SUCCESS) return
        if (_submissionBlocked.value || sessionInvalidated || reconciliationRequired ||
            (isEditInitialized && editingApplication == null)) {
            _operationError.value = "Periksa status pengajuan sebelum melanjutkan."
            viewModelScope.launch { _submissionResult.emit(false) }
            return
        }
        if (!validate()) return
        val state = _uiState.value
        _operationError.value = null
        _submitState.value = SubmitState.LOADING
        submitJob = viewModelScope.launch {
            var session: VerifiedLaravelSession? = null
            try {
                val active = requireSession()
                session = active
                val request = LaravelKtpApplicationRequest(
                    category = state.kategoriLayanan,
                    nikPelapor = state.nikPelapor, namaPelapor = state.namaPelapor,
                    email = state.email, whatsapp = state.whatsapp,
                    kecamatan = state.kecamatan, kelurahan = state.kelurahan,
                    keterangan = state.keterangan
                )
                val candidates = documents(state)
                val replacements = candidates.filterValues { it?.uri != null }
                val existing = editingApplication
                val success = if (existing != null) {
                    val cached = findOwnedApplication(existing.id, active)
                    check(cached.remoteUuid == existing.remoteUuid)
                    requireEditable(cached)
                    val oldPaths = mapOf("doc1" to cached.doc1, "doc2" to cached.doc2,
                        "docSelfie" to cached.docSelfie)
                    val deleted = oldPaths.filter { (slot, path) ->
                        !path.isNullOrBlank() && candidates[slot] == null
                    }.keys.toList()
                    val result = applicationRepository.updateKtpApplicationWithDocuments(
                        context = context.applicationContext,
                        applicationUuid = requireNotNull(cached.remoteUuid), request = request,
                        replacementDocuments = replacements, deletedSlots = deleted,
                        localUserId = active.localUserId, remoteUserUuid = active.remoteUserUuid,
                        token = active.token
                    ).getOrThrow()
                    reconciliationRequired = !result.isFullySuccessful &&
                            (result.isFormUpdated || result.remoteOutcome == LaravelRemoteOutcome.UNCERTAIN ||
                                    result.uncertainSlots.isNotEmpty())
                    result.isFullySuccessful
                } else {
                    check(candidates.values.none { it != null && it.uri == null })
                    val result = applicationRepository.createKtpApplicationWithDocuments(
                        context = context.applicationContext, request = request, documents = replacements,
                        localUserId = active.localUserId, remoteUserUuid = active.remoteUserUuid,
                        token = active.token
                    ).getOrThrow()
                    reconciliationRequired = !result.isFullySuccessful &&
                            (result.applicationUuid != null || result.remoteOutcome == LaravelRemoteOutcome.UNCERTAIN ||
                                    result.uncertainSlots.isNotEmpty())
                    result.isFullySuccessful
                }
                check(authRepository.isVerifiedSessionCurrent(active))
                if (success) {
                    _submitState.value = SubmitState.SUCCESS
                    delay(800)
                    if (authRepository.isVerifiedSessionCurrent(active)) _submissionResult.emit(true)
                } else {
                    reportFailure(active)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is LaravelMutationUncertainException) reconciliationRequired = true
                reportFailure(session)
            } finally {
                if (_submitState.value == SubmitState.LOADING) _submitState.value = SubmitState.IDLE
            }
        }
    }

    private suspend fun reportFailure(session: VerifiedLaravelSession?) {
        if (sessionInvalidated || (session != null && !authRepository.isVerifiedSessionCurrent(session))) return
        if (reconciliationRequired) _submissionBlocked.value = true
        _operationError.value = if (reconciliationRequired)
            "Pengajuan mungkin telah tersimpan. Periksa status sebelum mengirim ulang."
        else "Pengajuan belum berhasil diproses."
        _submitState.value = SubmitState.ERROR
        delay(1000)
        _submitState.value = SubmitState.IDLE
        if (!sessionInvalidated && (session == null || authRepository.isVerifiedSessionCurrent(session))) {
            _submissionResult.emit(false)
        }
    }
}
