package com.example.siapel.viewmodel

import android.net.Uri
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

    fun submitForm() {
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
                            doc1 = state.docKkTerbaru?.uri?.toString(),
                            doc2 = state.docKtpLama?.uri?.toString() ?: state.docSuratKeteranganHilang?.uri?.toString(),
                            docSelfie = state.docSelfie?.uri?.toString(),
                            createdAt = originalCreatedAt
                        )

                        applicationRepository.updateApplication(updatedApp)
                        _submitState.value = SubmitState.SUCCESS
                        delay(800)
                        _submissionResult.emit(true)
                    } else {
                        val userSession = userPreferencesRepository.userSessionFlow.first()
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

                            applicationRepository.insertApplication(application)
                            _submitState.value = SubmitState.SUCCESS
                            delay(800)
                            _submissionResult.emit(true)
                        } else {
                            _submitState.value = SubmitState.ERROR
                            delay(1000)
                            _submitState.value = SubmitState.IDLE
                            _submissionResult.emit(false)
                        }
                    }
                } catch (e: Exception) {
                    _submitState.value = SubmitState.ERROR
                    delay(1000)
                    _submitState.value = SubmitState.IDLE
                    _submissionResult.emit(false)
                }
            }
        }
    }
}
