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
import com.example.siapel.data.remote.laravel.dto.LaravelAktaApplicationRequest
import com.example.siapel.data.remote.laravel.dto.LaravelAktaDetailRequest
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.AktaKelahiranFormState
import com.example.siapel.model.SelectedDocument
import com.example.siapel.model.SubmitState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private enum class AktaSpecialDocumentGroup {
    NONE,
    SPTJM_AND_FORM,
    KTP_ANAK
}

private fun getAktaSpecialDocumentGroup(category: String): AktaSpecialDocumentGroup {
    return when (category) {
        "Paket Kelahiran Umum + KIA + KK" -> AktaSpecialDocumentGroup.NONE
        "Paket Kelahiran Terlambat + KIA + KK" -> AktaSpecialDocumentGroup.SPTJM_AND_FORM
        "Kelahiran Terlambat Tunggal",
        "Paket Kelahiran Umum + KIA + KK + KTP",
        "Paket Kelahiran Terlambat + KIA + KK + KTP",
        "Paket Kelahiran Terlambat + KK + KTP",
        "Paket Kelahiran Terlambat + KTP" -> AktaSpecialDocumentGroup.KTP_ANAK
        else -> throw IllegalArgumentException("Kategori AKTA tidak valid: $category")
    }
}

class AktaKelahiranViewModel(
    private val applicationRepository: LaravelApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: LaravelAuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AktaKelahiranFormState())
    val uiState = _uiState.asStateFlow()
    private val _isEditMode = mutableStateOf(false)
    val isEditMode: State<Boolean> = _isEditMode
    private val _submissionResult = MutableSharedFlow<Boolean>()
    val submissionResult: SharedFlow<Boolean> = _submissionResult.asSharedFlow()
    private val _submitState = MutableStateFlow(SubmitState.IDLE)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    private val _isEditLoading = MutableStateFlow(false)
    val isEditLoading = _isEditLoading.asStateFlow()
    private val _submissionBlocked = MutableStateFlow(false)
    val submissionBlocked = _submissionBlocked.asStateFlow()

    private fun canChangeForm(): Boolean =
        !_isEditLoading.value && !_submissionBlocked.value &&
                _submitState.value == SubmitState.IDLE &&
                submitJob?.isActive != true

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
                if (canChangeForm()) {
                    _uiState.update { state ->
                        state.copy(
                            email = state.email.ifBlank { user.email },
                            whatsapp = state.whatsapp.ifBlank { user.whatsapp }
                        )
                    }
                }
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
                    _uiState.value = AktaKelahiranFormState()
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
            .firstOrNull { it.id == id && it.serviceType.equals("AKTA", true) })
        check(app.userId == session.localUserId && app.remoteUserUuid == session.remoteUserUuid)
        check(!app.remoteUuid.isNullOrBlank())
        return app
    }

    private fun requireEditable(app: ApplicationEntity) {
        check(app.status == ApplicationStatus.SUBMITTED || app.status == ApplicationStatus.NEED_REVISION)
    }

    fun loadApplication(id: Int) {
        if (submitJob?.isActive == true || loadJob?.isActive == true || reconciliationRequired || sessionInvalidated) return
        isEditInitialized = true
        _isEditMode.value = true
        editingApplication = null
        _isEditLoading.value = true
        _submissionBlocked.value = true
        _operationError.value = null
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
                val originalGroup = getAktaSpecialDocumentGroup(app.category)
                _uiState.value = AktaKelahiranFormState(
                    kategoriLayanan = app.category,
                    kecamatan = app.kecamatan,
                    kelurahan = app.kelurahan,
                    email = app.email,
                    whatsapp = app.whatsapp,
                    keterangan = app.keterangan,
                    nikPelapor = app.nikPelapor,
                    namaPelapor = app.namaPelapor,
                    nikBayi = app.nikBayi ?: "",
                    namaBayi = app.namaBayi ?: "",
                    nikIbu = app.nikIbu ?: "",
                    namaIbu = app.namaIbu ?: "",
                    nomorKk = app.nomorKk ?: "",
                    docKkTerbaru = existingDocument(app.doc1),
                    docKtpIbu = existingDocument(app.doc2),
                    docKtpBapak = existingDocument(app.doc3),
                    docFormF201 = existingDocument(app.doc4),
                    docFormF102 = existingDocument(app.doc5),
                    docSuratKelahiran = existingDocument(app.doc6),
                    docBukuNikah = existingDocument(app.doc7),
                    docSptjmKelahiran = if (originalGroup == AktaSpecialDocumentGroup.SPTJM_AND_FORM) existingDocument(app.doc8) else null,
                    docFormPelaporanKelahiran = if (originalGroup == AktaSpecialDocumentGroup.SPTJM_AND_FORM) existingDocument(app.doc9) else null,
                    docKtpAnak = if (originalGroup == AktaSpecialDocumentGroup.KTP_ANAK) existingDocument(app.doc8) else null,
                    docSelfie = existingDocument(app.docSelfie),
                    docKkTerbaruTouched = false,
                    docKtpIbuTouched = false,
                    docKtpBapakTouched = false,
                    docFormF201Touched = false,
                    docFormF102Touched = false,
                    docSuratKelahiranTouched = false,
                    docBukuNikahTouched = false,
                    docSptjmKelahiranTouched = false,
                    docFormPelaporanKelahiranTouched = false,
                    docKtpAnakTouched = false,
                    docSelfieTouched = false
                )
                editingApplication = app
                _submissionBlocked.value = false
                _operationError.value = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _operationError.value = "Pengajuan tidak dapat dimuat atau tidak dapat diedit."
                editingApplication = null
                _uiState.value = AktaKelahiranFormState()
            } finally {
                _isEditLoading.value = false
            }
        }
    }

    fun updateKategori(value: String) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(kategoriLayanan = value, kategoriError = null) }
    }

    fun updateKecamatan(value: String) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(kecamatan = value, kelurahan = "", kecamatanError = null, kelurahanError = null) }
    }

    fun updateKelurahan(value: String) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(kelurahan = value, kelurahanError = null) }
    }

    fun updateEmail(value: String) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(email = value, emailError = null) }
    }

    fun updateWhatsapp(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isDigit() }
        _uiState.update { it.copy(whatsapp = filtered, whatsappError = null) }
    }

    fun updateKeterangan(value: String) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(keterangan = value) }
    }

    fun updateNikPelapor(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.update { it.copy(nikPelapor = filtered, nikPelaporError = null) }
    }

    fun updateNamaPelapor(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.update { it.copy(namaPelapor = filtered, namaPelaporError = null) }
    }

    fun updateNikBayi(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.update { it.copy(nikBayi = filtered) }
    }

    fun updateNamaBayi(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.update { it.copy(namaBayi = filtered, namaBayiError = null) }
    }

    fun updateNikIbu(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.update { it.copy(nikIbu = filtered, nikIbuError = null) }
    }

    fun updateNamaIbu(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.update { it.copy(namaIbu = filtered, namaIbuError = null) }
    }

    fun updateNomorKk(value: String) {
        if (!canChangeForm()) return
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.update { it.copy(nomorKk = filtered, nomorKkError = null) }
    }

    fun updateDocKkTerbaru(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docKkTerbaru = doc, docKkTerbaruTouched = true) }
    }
    fun updateDocKtpIbu(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docKtpIbu = doc, docKtpIbuTouched = true) }
    }
    fun updateDocKtpBapak(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docKtpBapak = doc, docKtpBapakTouched = true) }
    }
    fun updateDocFormF201(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docFormF201 = doc, docFormF201Touched = true) }
    }
    fun updateDocFormF102(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docFormF102 = doc, docFormF102Touched = true) }
    }
    fun updateDocSuratKelahiran(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docSuratKelahiran = doc, docSuratKelahiranTouched = true) }
    }
    fun updateDocBukuNikah(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docBukuNikah = doc, docBukuNikahTouched = true) }
    }
    fun updateDocSptjmKelahiran(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docSptjmKelahiran = doc, docSptjmKelahiranTouched = true) }
    }
    fun updateDocFormPelaporanKelahiran(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docFormPelaporanKelahiran = doc, docFormPelaporanKelahiranTouched = true) }
    }
    fun updateDocKtpAnak(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docKtpAnak = doc, docKtpAnakTouched = true) }
    }
    fun updateDocSelfie(doc: SelectedDocument?) {
        if (!canChangeForm()) return
        _uiState.update { it.copy(docSelfie = doc, docSelfieTouched = true) }
    }

    fun validateForm(): Boolean {
        var isValid = true
        val currentState = _uiState.value

        val kategoriError = if (currentState.kategoriLayanan.isEmpty()) "Kategori layanan wajib dipilih" else null
        val kecamatanError = if (currentState.kecamatan.isEmpty()) "Kecamatan wajib dipilih" else null
        val kelurahanError = if (currentState.kelurahan.isEmpty()) "Kelurahan wajib dipilih" else null

        val emailError = when {
            currentState.email.isEmpty() -> "Email wajib diisi"
            !Patterns.EMAIL_ADDRESS.matcher(currentState.email).matches() -> "Format email tidak valid"
            else -> null
        }

        val whatsappError = if (currentState.whatsapp.isEmpty()) "Nomor WhatsApp wajib diisi" else null

        val nikPelaporError = when {
            currentState.nikPelapor.isEmpty() -> "NIK Pelapor wajib diisi"
            currentState.nikPelapor.length != 16 -> "NIK harus terdiri dari 16 digit"
            else -> null
        }

        val namaPelaporWords = currentState.namaPelapor.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val namaPelaporError = when {
            currentState.namaPelapor.isEmpty() -> "Nama Pelapor wajib diisi"
            namaPelaporWords.size < 2 -> "Nama harus terdiri dari minimal 2 kata"
            else -> null
        }

        val namaBayiWords = currentState.namaBayi.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val namaBayiError = when {
            currentState.namaBayi.isEmpty() -> "Nama Bayi wajib diisi"
            namaBayiWords.size < 2 -> "Nama harus terdiri dari minimal 2 kata"
            else -> null
        }

        val nikIbuError = when {
            currentState.nikIbu.isEmpty() -> "NIK Ibu wajib diisi"
            currentState.nikIbu.length != 16 -> "NIK harus terdiri dari 16 digit"
            else -> null
        }

        val namaIbuWords = currentState.namaIbu.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val namaIbuError = when {
            currentState.namaIbu.isEmpty() -> "Nama Ibu wajib diisi"
            namaIbuWords.size < 2 -> "Nama harus terdiri dari minimal 2 kata"
            else -> null
        }
        val nomorKkError = when {
            currentState.nomorKk.isEmpty() -> "Nomor KK wajib diisi"
            currentState.nomorKk.length != 16 -> "Nomor KK harus terdiri dari 16 digit"
            else -> null
        }

        if (kategoriError != null || kecamatanError != null || kelurahanError != null ||
            emailError != null || whatsappError != null || nikPelaporError != null ||
            namaPelaporError != null || namaBayiError != null || nikIbuError != null ||
            namaIbuError != null || nomorKkError != null) {
            isValid = false
        }

        _uiState.update {
            it.copy(
                kategoriError = kategoriError,
                kecamatanError = kecamatanError,
                kelurahanError = kelurahanError,
                emailError = emailError,
                whatsappError = whatsappError,
                nikPelaporError = nikPelaporError,
                namaPelaporError = namaPelaporError,
                namaBayiError = namaBayiError,
                nikIbuError = nikIbuError,
                namaIbuError = namaIbuError,
                nomorKkError = nomorKkError
            )
        }

        return isValid
    }

    private fun documents(state: AktaKelahiranFormState): Map<String, SelectedDocument?> {
        val group = getAktaSpecialDocumentGroup(state.kategoriLayanan)
        return linkedMapOf(
            "doc1" to state.docKkTerbaru,
            "doc2" to state.docKtpIbu,
            "doc3" to state.docKtpBapak,
            "doc4" to state.docFormF201,
            "doc5" to state.docFormF102,
            "doc6" to state.docSuratKelahiran,
            "doc7" to state.docBukuNikah,
            "docSelfie" to state.docSelfie,
            "doc8" to when (group) {
                AktaSpecialDocumentGroup.NONE -> null
                AktaSpecialDocumentGroup.SPTJM_AND_FORM -> state.docSptjmKelahiran
                AktaSpecialDocumentGroup.KTP_ANAK -> state.docKtpAnak
            },
            "doc9" to if (group == AktaSpecialDocumentGroup.SPTJM_AND_FORM)
                state.docFormPelaporanKelahiran else null
        )
    }

    private fun touchedSlots(state: AktaKelahiranFormState): Map<String, Boolean> {
        val group = getAktaSpecialDocumentGroup(state.kategoriLayanan)
        return mapOf(
            "doc1" to state.docKkTerbaruTouched,
            "doc2" to state.docKtpIbuTouched,
            "doc3" to state.docKtpBapakTouched,
            "doc4" to state.docFormF201Touched,
            "doc5" to state.docFormF102Touched,
            "doc6" to state.docSuratKelahiranTouched,
            "doc7" to state.docBukuNikahTouched,
            "docSelfie" to state.docSelfieTouched,
            "doc8" to when (group) {
                AktaSpecialDocumentGroup.NONE -> false
                AktaSpecialDocumentGroup.SPTJM_AND_FORM -> state.docSptjmKelahiranTouched
                AktaSpecialDocumentGroup.KTP_ANAK -> state.docKtpAnakTouched
            },
            "doc9" to (group == AktaSpecialDocumentGroup.SPTJM_AND_FORM &&
                    state.docFormPelaporanKelahiranTouched)
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
        if (!validateForm()) return
        if (_uiState.value.nikBayi.isNotBlank() && _uiState.value.nikBayi.length != 16) {
            _operationError.value = "NIK bayi harus terdiri dari 16 digit jika diisi."
            return
        }
        val state = _uiState.value
        _operationError.value = null
        _submitState.value = SubmitState.LOADING
        submitJob = viewModelScope.launch {
            var session: VerifiedLaravelSession? = null
            try {
                val active = requireSession()
                session = active
                require(state.nikBayi.isBlank() || state.nikBayi.length == 16)
                val request = LaravelAktaApplicationRequest(
                    category = state.kategoriLayanan,
                    nikPelapor = state.nikPelapor, namaPelapor = state.namaPelapor,
                    email = state.email, whatsapp = state.whatsapp,
                    kecamatan = state.kecamatan, kelurahan = state.kelurahan,
                    keterangan = state.keterangan,
                    detail = LaravelAktaDetailRequest(
                        nikBayi = state.nikBayi.ifBlank { null },
                        namaBayi = state.namaBayi,
                        nikIbu = state.nikIbu,
                        namaIbu = state.namaIbu,
                        nomorKk = state.nomorKk
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
                        "docSelfie" to cached.docSelfie
                    )
                    val originalGroup = getAktaSpecialDocumentGroup(existing.category)
                    val finalGroup = getAktaSpecialDocumentGroup(state.kategoriLayanan)
                    check(cached.category == existing.category) { "Kategori pengajuan berubah" }
                    val touched = touchedSlots(state)
                    val editReplacements = replacements.filterKeys { touched[it] == true }
                    val deleted = oldPaths.filter { (slot, path) ->
                        val obsolete = when (slot) {
                            "doc8" -> finalGroup == AktaSpecialDocumentGroup.NONE || originalGroup != finalGroup
                            "doc9" -> finalGroup != AktaSpecialDocumentGroup.SPTJM_AND_FORM || originalGroup != finalGroup
                            else -> false
                        }
                        !path.isNullOrBlank() && slot !in editReplacements &&
                                (obsolete || (touched[slot] == true && candidates[slot] == null))
                    }.keys.toList()
                    val result = applicationRepository.updateAktaApplicationWithDocuments(
                        context = context.applicationContext,
                        applicationUuid = requireNotNull(cached.remoteUuid), request = request,
                        replacementDocuments = editReplacements, deletedSlots = deleted,
                        localUserId = active.localUserId, remoteUserUuid = active.remoteUserUuid,
                        token = active.token
                    ).getOrThrow()
                    reconciliationRequired = !result.isFullySuccessful &&
                            (result.isFormUpdated || result.remoteOutcome == LaravelRemoteOutcome.UNCERTAIN ||
                                    result.uncertainSlots.isNotEmpty())
                    result.isFullySuccessful
                } else {
                    check(candidates.values.none { it != null && it.uri == null })
                    val result = applicationRepository.createAktaApplicationWithDocuments(
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
