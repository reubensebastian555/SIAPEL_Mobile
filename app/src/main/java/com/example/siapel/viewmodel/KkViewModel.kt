package com.example.siapel.viewmodel

import android.content.Context
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.repository.*
import com.example.siapel.data.remote.laravel.dto.LaravelKkApplicationRequest
import com.example.siapel.data.remote.laravel.dto.LaravelKkDetailRequest
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.KkFormState
import com.example.siapel.model.SelectedDocument
import com.example.siapel.model.SubmitState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class KkViewModel(
    private val applicationRepository: LaravelApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: LaravelAuthRepository
) : ViewModel() {
    private val _uiState = mutableStateOf(KkFormState())
    val uiState: State<KkFormState> = _uiState
    private val _isEditMode = mutableStateOf(false)
    val isEditMode: State<Boolean> = _isEditMode
    private val _submissionResult = MutableSharedFlow<Boolean>()
    val submissionResult: SharedFlow<Boolean> = _submissionResult.asSharedFlow()
    private val _submitState = mutableStateOf(SubmitState.IDLE)
    val submitState: State<SubmitState> = _submitState
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    private var isEditInitialized = false
    private var editingApplication: ApplicationEntity? = null
    private var formSession: VerifiedLaravelSession? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var submitJob: Job? = null
    private var reconciliationRequired by mutableStateOf(false)
    private var sessionInvalidated by mutableStateOf(false)
    private val _isEditLoading = mutableStateOf(false)
    val isEditLoading: State<Boolean> = _isEditLoading
    private var editReady by mutableStateOf(true)
    val canSubmit: Boolean
        get() = !sessionInvalidated && !reconciliationRequired &&
                !_isEditLoading.value && editReady && _submitState.value == SubmitState.IDLE
    private fun canChangeForm(): Boolean = canSubmit && submitJob?.isActive != true
    private val touchedSlots = mutableSetOf<String>()

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
                    _isEditLoading.value = false
                    editReady = false
                    loadJob?.cancel()
                    submitJob?.cancel()
                    editingApplication = null
                    _uiState.value = KkFormState()
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
            .firstOrNull { it.id == id && it.serviceType.equals("KK", true) })
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
        editReady = false
        touchedSlots.clear()
        _isEditLoading.value = true
        _operationError.value = null
        val generation = ++loadGeneration
        loadJob?.cancel()
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
                _uiState.value = KkFormState(
                    kategoriProduk = app.category,
                    kecamatan = app.kecamatan,
                    kelurahan = app.kelurahan,
                    email = app.email,
                    whatsapp = app.whatsapp,
                    keterangan = app.keterangan,
                    nikPelapor = app.nikPelapor,
                    namaPelapor = app.namaPelapor,
                    nikAnak = app.nikAnak ?: "",
                    namaAnak = app.namaAnak ?: "",
                    nomorKk = app.nomorKk ?: "",
                    docKkTerbaru = existingDocument(app.doc1),
                    docF101 = existingDocument(app.doc2),
                    docF102 = existingDocument(app.doc3),
                    docF103 = existingDocument(app.doc4),
                    docF106 = existingDocument(app.doc5),
                    docSptjm = existingDocument(app.doc6),
                    docSuratPernyataanTempatTinggal = existingDocument(app.doc7),
                    docSuratKehilanganKkRusak = existingDocument(app.doc8),
                    docKtpHilangRusak = existingDocument(app.doc9),
                    docPendukung = existingDocument(app.doc10),
                    docSelfie = existingDocument(app.docSelfie)
                )
                editReady = true
                _operationError.value = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (!sessionInvalidated && generation == loadGeneration) {
                    _operationError.value = "Pengajuan tidak dapat dimuat atau tidak dapat diedit. Buka kembali formulir."
                    _uiState.value = KkFormState()
                }
            } finally {
                if (generation == loadGeneration) _isEditLoading.value = false
            }
        }
    }

    fun onKategoriProdukChange(value: String) {
        if (!canChangeForm()) return
        _uiState.value = _uiState.value.copy(kategoriProduk = value, kategoriError = null)
    }

    fun onKecamatanChange(value: String) {
        if (!canChangeForm()) return
        _uiState.value = _uiState.value.copy(
            kecamatan = value,
            kelurahan = "",
            kecamatanError = null,
            kelurahanError = null
        )
    }

    fun onKelurahanChange(value: String) {
        if (!canChangeForm()) return
        _uiState.value = _uiState.value.copy(kelurahan = value, kelurahanError = null)
    }

    fun onEmailChange(value: String) {
        if (!canChangeForm()) return
        _uiState.value = _uiState.value.copy(email = value, emailError = null)
    }

    fun onWhatsappChange(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isDigit() }
        _uiState.value = _uiState.value.copy(whatsapp = filtered, whatsappError = null)
    }

    fun onKeteranganChange(value: String) {
        if (!canChangeForm()) return
        _uiState.value = _uiState.value.copy(keterangan = value)
    }

    fun onNikPelaporChange(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.value = _uiState.value.copy(nikPelapor = filtered, nikPelaporError = null)
    }

    fun onNamaPelaporChange(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.value = _uiState.value.copy(namaPelapor = filtered, namaPelaporError = null)
    }

    fun onNikAnakChange(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.value = _uiState.value.copy(nikAnak = filtered, nikAnakError = null)
    }

    fun onNamaAnakChange(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.value = _uiState.value.copy(namaAnak = filtered, namaAnakError = null)
    }

    fun onNomorKkChange(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.value = _uiState.value.copy(nomorKk = filtered, nomorKkError = null)
    }

    // Document Picker setters
    fun onDocKkTerbaruChange(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc1")
        _uiState.value = _uiState.value.copy(docKkTerbaru = doc)
    }

    fun onDocF101Change(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc2")
        _uiState.value = _uiState.value.copy(docF101 = doc)
    }

    fun onDocF102Change(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc3")
        _uiState.value = _uiState.value.copy(docF102 = doc)
    }

    fun onDocF103Change(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc4")
        _uiState.value = _uiState.value.copy(docF103 = doc)
    }

    fun onDocF106Change(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc5")
        _uiState.value = _uiState.value.copy(docF106 = doc)
    }

    fun onDocSptjmChange(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc6")
        _uiState.value = _uiState.value.copy(docSptjm = doc)
    }

    fun onDocSuratPernyataanTempatTinggalChange(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc7")
        _uiState.value = _uiState.value.copy(docSuratPernyataanTempatTinggal = doc)
    }

    fun onDocSuratKehilanganKkRusakChange(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc8")
        _uiState.value = _uiState.value.copy(docSuratKehilanganKkRusak = doc)
    }

    fun onDocKtpHilangRusakChange(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc9")
        _uiState.value = _uiState.value.copy(docKtpHilangRusak = doc)
    }

    fun onDocPendukungChange(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("doc10")
        _uiState.value = _uiState.value.copy(docPendukung = doc)
    }

    fun onDocSelfieChange(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        touchedSlots.add("docSelfie")
        _uiState.value = _uiState.value.copy(docSelfie = doc)
    }

    fun validate(): Boolean {
        val state = _uiState.value
        val emailPattern = Patterns.EMAIL_ADDRESS

        val kategoriError = if (state.kategoriProduk.isEmpty()) "Kategori produk wajib dipilih" else null
        val kecamatanError = if (state.kecamatan.isEmpty()) "Kecamatan wajib dipilih" else null
        val kelurahanError = if (state.kelurahan.isEmpty()) "Kelurahan wajib dipilih" else null
        val emailError = when {
            state.email.isEmpty() -> "Email wajib diisi"
            !emailPattern.matcher(state.email).matches() -> "Format email tidak valid"
            else -> null
        }
        val whatsappError = if (state.whatsapp.isEmpty()) "Nomor WhatsApp wajib diisi" else null
        val nikPelaporError = when {
            state.nikPelapor.isEmpty() -> "NIK Pelapor wajib diisi"
            state.nikPelapor.length != 16 -> "NIK harus terdiri dari 16 digit"
            else -> null
        }
        val namaPelaporWords = state.namaPelapor.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val namaPelaporError = when {
            state.namaPelapor.isEmpty() -> "Nama Pelapor wajib diisi"
            namaPelaporWords.size < 2 -> "Nama harus terdiri dari minimal 2 kata"
            else -> null
        }
        val nikAnakError = when {
            state.nikAnak.isNotEmpty() && state.nikAnak.length != 16 -> "NIK harus terdiri dari 16 digit jika diisi"
            else -> null
        }
        val namaAnakWords = state.namaAnak.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val namaAnakError = when {
            state.namaAnak.isNotEmpty() && namaAnakWords.size < 2 -> "Nama harus terdiri dari minimal 2 kata jika diisi"
            else -> null
        }
        val nomorKkError = when {
            state.nomorKk.isNotEmpty() && state.nomorKk.length != 16 -> "Nomor KK harus terdiri dari 16 digit jika diisi"
            else -> null
        }

        _uiState.value = state.copy(
            kategoriError = kategoriError,
            kecamatanError = kecamatanError,
            kelurahanError = kelurahanError,
            emailError = emailError,
            whatsappError = whatsappError,
            nikPelaporError = nikPelaporError,
            namaPelaporError = namaPelaporError,
            nikAnakError = nikAnakError,
            namaAnakError = namaAnakError,
            nomorKkError = nomorKkError
        )

        return kategoriError == null && kecamatanError == null && kelurahanError == null &&
                emailError == null && whatsappError == null && nikPelaporError == null &&
                namaPelaporError == null && nikAnakError == null && namaAnakError == null && nomorKkError == null
    }

    private fun documents(state: KkFormState): Map<String, SelectedDocument?> {
        return linkedMapOf(
            "doc1" to state.docKkTerbaru,
            "doc2" to state.docF101,
            "doc3" to state.docF102,
            "doc4" to state.docF103,
            "doc5" to state.docF106,
            "doc6" to state.docSptjm,
            "doc7" to state.docSuratPernyataanTempatTinggal,
            "doc8" to state.docSuratKehilanganKkRusak,
            "doc9" to state.docKtpHilangRusak,
            "doc10" to state.docPendukung,
            "docSelfie" to state.docSelfie
        )
    }

    fun submitForm(context: Context) {
        if (!canSubmit || submitJob?.isActive == true || loadJob?.isActive == true) return
        if (sessionInvalidated || reconciliationRequired ||
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
                val request = LaravelKkApplicationRequest(
                    category = state.kategoriProduk,
                    nikPelapor = state.nikPelapor, namaPelapor = state.namaPelapor,
                    email = state.email, whatsapp = state.whatsapp,
                    kecamatan = state.kecamatan, kelurahan = state.kelurahan,
                    keterangan = state.keterangan,
                    detail = LaravelKkDetailRequest(
                        nikAnak = state.nikAnak.ifBlank { null },
                        namaAnak = state.namaAnak.ifBlank { null },
                        nomorKk = state.nomorKk.ifBlank { null }
                    )
                )
                val candidates = documents(state)
                val replacements = candidates.filterValues { it?.uri != null }
                val existing = editingApplication
                val success = if (existing != null) {
                    val cached = findOwnedApplication(existing.id, active)
                    check(cached.remoteUuid == existing.remoteUuid)
                    requireEditable(cached)
                    val oldPaths = mapOf(
                        "doc1" to cached.doc1,
                        "doc2" to cached.doc2,
                        "doc3" to cached.doc3,
                        "doc4" to cached.doc4,
                        "doc5" to cached.doc5,
                        "doc6" to cached.doc6,
                        "doc7" to cached.doc7,
                        "doc8" to cached.doc8,
                        "doc9" to cached.doc9,
                        "doc10" to cached.doc10,
                        "docSelfie" to cached.docSelfie
                    )
                    val deleted = oldPaths.filter { (slot, path) ->
                        slot in touchedSlots && !path.isNullOrBlank() && candidates[slot] == null
                    }.keys.toList()
                    val result = applicationRepository.updateKkApplicationWithDocuments(
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
                    val result = applicationRepository.createKkApplicationWithDocuments(
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
