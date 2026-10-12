package com.example.siapel.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.repository.ApplicationRepository
import com.example.siapel.data.repository.AuthRepository
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.KtpFormState
import com.example.siapel.model.SelectedDocument
import com.example.siapel.model.SubmitState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun isSelectedDocChanged(selected: SelectedDocument?, oldUriStr: String?): Boolean {
    val newUriStr = selected?.uri?.toString()
    return !newUriStr.isNullOrBlank() && newUriStr != oldUriStr
}

class KtpViewModel(
    private val applicationRepository: ApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = mutableStateOf(KtpFormState())
    val uiState: State<KtpFormState> = _uiState

    private val _isEditMode = mutableStateOf(false)
    val isEditMode: State<Boolean> = _isEditMode

    private var isEditInitialized = false
    private var editingApplicationId: Int? = null
    private var originalUserId: Int = 0
    private var originalSubmissionCode: String = ""
    private var originalSubmissionDate: String = ""
    private var originalCreatedAt: Long = System.currentTimeMillis()

    private val _submissionResult = MutableSharedFlow<Boolean>()
    val submissionResult: SharedFlow<Boolean> = _submissionResult

    private val _submitState = mutableStateOf(SubmitState.IDLE)
    val submitState: State<SubmitState> = _submitState

    init {
        viewModelScope.launch {
            val session = userPreferencesRepository.userSessionFlow.first()
            if (isEditInitialized) return@launch
            if (session.isLoggedIn) {
                val user = authRepository.getUserById(session.userId)
                if (isEditInitialized) return@launch
                if (user != null) {
                    _uiState.value = _uiState.value.copy(
                        email = user.email,
                        whatsapp = user.whatsapp
                    )
                }
            }
        }
    }

    private fun uriToSelectedDoc(uriStr: String?): SelectedDocument? {
        return uriStr?.let {
            try {
                val uri = Uri.parse(it)
                SelectedDocument(uri, uri.lastPathSegment ?: "Dokumen")
            } catch (e: Exception) {
                null
            }
        }
    }

    fun loadApplication(id: Int) {
        isEditInitialized = true
        viewModelScope.launch {
            val app = applicationRepository.getApplicationById(id)
            if (app != null) {
                if (app.status == ApplicationStatus.PROCESSING || app.status == ApplicationStatus.COMPLETED) {
                    return@launch
                }
                editingApplicationId = id
                originalUserId = app.userId
                originalSubmissionCode = app.submissionCode
                originalSubmissionDate = app.submissionDate
                originalCreatedAt = app.createdAt
                _isEditMode.value = true

                _uiState.value = _uiState.value.copy(
                    kategoriLayanan = app.category,
                    kecamatan = app.kecamatan,
                    kelurahan = app.kelurahan,
                    email = app.email,
                    whatsapp = app.whatsapp,
                    keterangan = app.keterangan,
                    namaPelapor = app.namaPelapor,
                    nikPelapor = app.nikPelapor,
                    docKkTerbaru = uriToSelectedDoc(app.doc1),
                    docKtpLama = if (app.category.contains("RUSAK", true) || app.category.contains("RUBAH", true)) uriToSelectedDoc(app.doc2) else null,
                    docSuratKeteranganHilang = if (app.category.contains("HILANG", true)) uriToSelectedDoc(app.doc2) else null,
                    docSelfie = uriToSelectedDoc(app.docSelfie)
                )
            }
        }
    }

    fun onKategoriLayananChange(value: String) {
        _uiState.value = _uiState.value.copy(kategoriLayanan = value, kategoriError = null)
    }

    fun onKecamatanChange(value: String) {
        _uiState.value = _uiState.value.copy(
            kecamatan = value,
            kelurahan = "",
            kecamatanError = null,
            kelurahanError = null
        )
    }

    fun onKelurahanChange(value: String) {
        _uiState.value = _uiState.value.copy(kelurahan = value, kelurahanError = null)
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, emailError = null)
    }

    fun onWhatsappChange(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.value = _uiState.value.copy(whatsapp = filtered, whatsappError = null)
    }

    fun onKeteranganChange(value: String) {
        _uiState.value = _uiState.value.copy(keterangan = value)
    }

    fun onNamaPelaporChange(value: String) {
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.value = _uiState.value.copy(namaPelapor = filtered, namaPelaporError = null)
    }

    fun onNikPelaporChange(value: String) {
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.value = _uiState.value.copy(nikPelapor = filtered, nikPelaporError = null)
    }

    fun onDocKkTerbaruChange(doc: SelectedDocument?) {
        _uiState.value = _uiState.value.copy(docKkTerbaru = doc)
    }

    fun onDocKtpLamaChange(doc: SelectedDocument?) {
        _uiState.value = _uiState.value.copy(docKtpLama = doc)
    }

    fun onDocSuratKeteranganHilangChange(doc: SelectedDocument?) {
        _uiState.value = _uiState.value.copy(docSuratKeteranganHilang = doc)
    }

    fun onDocSelfieChange(doc: SelectedDocument?) {
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

    fun submitForm(context: Context) {
        if (validate()) {
            _submitState.value = SubmitState.LOADING
            viewModelScope.launch {
                try {
                    val editId = editingApplicationId
                    if (editId != null) {
                        val existing = applicationRepository.getApplicationById(editId)
                        if (existing == null || existing.status == ApplicationStatus.PROCESSING || existing.status == ApplicationStatus.COMPLETED) {
                            _submitState.value = SubmitState.ERROR
                            delay(1000)
                            _submitState.value = SubmitState.IDLE
                            _submissionResult.emit(false)
                            return@launch
                        }

                        val state = _uiState.value
                        val cat = state.kategoriLayanan.uppercase()
                        val isPerekaman = cat.contains("PEREKAMAN")

                        val doc1Candidate = state.docKkTerbaru
                        val doc2Candidate = when {
                            isPerekaman -> null
                            cat.contains("HILANG") -> state.docSuratKeteranganHilang
                            else -> state.docKtpLama
                        }
                        val docSelfieCandidate = state.docSelfie

                        val doc1Changed = isSelectedDocChanged(doc1Candidate, existing.doc1)
                        val doc2Changed = !isPerekaman && isSelectedDocChanged(doc2Candidate, existing.doc2)
                        val docSelfieChanged = isSelectedDocChanged(docSelfieCandidate, existing.docSelfie)

                        val doc1ForRemote = if (doc1Changed) doc1Candidate else null
                        val doc2ForRemote = if (doc2Changed) doc2Candidate else null
                        val docSelfieForRemote = if (docSelfieChanged) docSelfieCandidate else null

                        Log.d("SIAPEL_EDIT_KTP", "KTP edit submit start code=${existing.submissionCode}")
                        Log.d("SIAPEL_EDIT_KTP", "category=${state.kategoriLayanan}")
                        Log.d("SIAPEL_EDIT_KTP", "doc1 changed=$doc1Changed, doc2 changed=$doc2Changed, docSelfie changed=$docSelfieChanged")

                        val updatedApp = ApplicationEntity(
                            id = editId,
                            userId = originalUserId,
                            serviceType = "KTP",
                            category = state.kategoriLayanan,
                            submissionCode = originalSubmissionCode,
                            submissionDate = originalSubmissionDate,
                            status = ApplicationStatus.SUBMITTED,
                            kecamatan = state.kecamatan,
                            kelurahan = state.kelurahan,
                            email = state.email,
                            whatsapp = state.whatsapp,
                            keterangan = state.keterangan,
                            nikPelapor = state.nikPelapor,
                            namaPelapor = state.namaPelapor,
                            doc1 = if (doc1Changed) doc1Candidate?.uri?.toString() else existing.doc1,
                            doc2 = if (isPerekaman) null else (if (doc2Changed) doc2Candidate?.uri?.toString() else existing.doc2),
                            docSelfie = if (docSelfieChanged) docSelfieCandidate?.uri?.toString() else existing.docSelfie,
                            createdAt = originalCreatedAt
                        )

                        val currentUuid = authRepository.getCurrentSupabaseUser()?.id
                            ?: authRepository.getCurrentSupabaseSessionUserId()

                        if (!currentUuid.isNullOrEmpty()) {
                            val isRemoteUpdateSuccess = applicationRepository.updateKtpApplicationRemoteWithDocuments(
                                context = context,
                                application = updatedApp,
                                currentUuid = currentUuid,
                                newDoc1 = doc1ForRemote,
                                newDoc2 = doc2ForRemote,
                                newDocSelfie = docSelfieForRemote
                            )
                            if (isRemoteUpdateSuccess) {
                                _submitState.value = SubmitState.SUCCESS
                                delay(800)
                                _submissionResult.emit(true)
                            } else {
                                Log.e("SIAPEL_EDIT_KTP", "KTP remote update failed for code ${updatedApp.submissionCode}. Room update skipped.")
                                _submitState.value = SubmitState.ERROR
                                delay(1000)
                                _submitState.value = SubmitState.IDLE
                                _submissionResult.emit(false)
                            }
                        } else {
                            applicationRepository.updateApplication(updatedApp)
                            _submitState.value = SubmitState.SUCCESS
                            delay(800)
                            _submissionResult.emit(true)
                        }
                    } else {
                        val userSession = userPreferencesRepository.userSessionFlow.first()
                        val currentUuid = authRepository.getCurrentSupabaseUser()?.id
                            ?: authRepository.getCurrentSupabaseSessionUserId()

                        if (userSession.isLoggedIn) {
                            val state = _uiState.value
                            val submissionCode = "KTP-${System.currentTimeMillis().toString().takeLast(6)}"
                            val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

                            val application = ApplicationEntity(
                                userId = userSession.userId,
                                serviceType = "KTP",
                                category = state.kategoriLayanan,
                                submissionCode = submissionCode,
                                submissionDate = currentDate,
                                status = ApplicationStatus.SUBMITTED,
                                kecamatan = state.kecamatan,
                                kelurahan = state.kelurahan,
                                email = state.email,
                                whatsapp = state.whatsapp,
                                keterangan = state.keterangan,
                                nikPelapor = state.nikPelapor,
                                namaPelapor = state.namaPelapor,
                                doc1 = state.docKkTerbaru?.uri?.toString(),
                                doc2 = state.docKtpLama?.uri?.toString() ?: state.docSuratKeteranganHilang?.uri?.toString(),
                                docSelfie = state.docSelfie?.uri?.toString()
                            )

                            if (!currentUuid.isNullOrEmpty()) {
                                val cat = state.kategoriLayanan.uppercase()
                                val doc1 = state.docKkTerbaru
                                val doc2 = when {
                                    cat.contains("PEREKAMAN") -> null
                                    cat.contains("HILANG") -> state.docSuratKeteranganHilang
                                    else -> state.docKtpLama
                                }
                                val docSelfie = state.docSelfie

                                val remoteSuccess = applicationRepository.createKtpApplicationRemote(
                                    context = context,
                                    application = application,
                                    currentSupabaseUuid = currentUuid,
                                    doc1 = doc1,
                                    doc2 = doc2,
                                    docSelfie = docSelfie
                                )

                                if (remoteSuccess) {
                                    _submitState.value = SubmitState.SUCCESS
                                    delay(800)
                                    _submissionResult.emit(true)
                                } else {
                                    Log.e("SIAPEL_STORAGE", "KTP remote create failed. Local Room fallback skipped.")
                                    _submitState.value = SubmitState.ERROR
                                    delay(1000)
                                    _submitState.value = SubmitState.IDLE
                                    _submissionResult.emit(false)
                                }
                            } else {
                                applicationRepository.insertApplication(application)
                                _submitState.value = SubmitState.SUCCESS
                                delay(800)
                                _submissionResult.emit(true)
                            }
                        } else {
                            _submitState.value = SubmitState.ERROR
                            delay(1000)
                            _submitState.value = SubmitState.IDLE
                            _submissionResult.emit(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SIAPEL_STORAGE", "KTP submitForm exception: ${e.message}", e)
                    _submitState.value = SubmitState.ERROR
                    delay(1000)
                    _submitState.value = SubmitState.IDLE
                    _submissionResult.emit(false)
                }
            }
        }
    }
}
