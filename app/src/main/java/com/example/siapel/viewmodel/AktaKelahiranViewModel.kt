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
import com.example.siapel.model.AktaKelahiranFormState
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.SelectedDocument
import com.example.siapel.model.SubmitState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AktaKelahiranViewModel(
    private val applicationRepository: ApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AktaKelahiranFormState())
    val uiState = _uiState.asStateFlow()

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

    private val _submitState = MutableStateFlow(SubmitState.IDLE)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = userPreferencesRepository.userSessionFlow.first()
            if (isEditInitialized) return@launch
            if (session.isLoggedIn) {
                val user = authRepository.getUserById(session.userId)
                if (isEditInitialized) return@launch
                if (user != null) {
                    _uiState.update { 
                        it.copy(
                            email = user.email,
                            whatsapp = user.whatsapp
                        )
                    }
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

                val cat = app.category.uppercase()
                val isTerlambatKiaKkNoKtp = cat.contains("TERLAMBAT + KIA + KK") && !cat.contains("KTP")
                val isTerlambatOrKtp = cat.contains("TERLAMBAT") || cat.contains("KTP")

                _uiState.update {
                    it.copy(
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
                        docKkTerbaru = uriToSelectedDoc(app.doc1),
                        docKtpIbu = uriToSelectedDoc(app.doc2),
                        docKtpBapak = uriToSelectedDoc(app.doc3),
                        docFormF201 = uriToSelectedDoc(app.doc4),
                        docFormF102 = uriToSelectedDoc(app.doc5),
                        docSuratKelahiran = uriToSelectedDoc(app.doc6),
                        docBukuNikah = uriToSelectedDoc(app.doc7),
                        docSptjmKelahiran = if (isTerlambatKiaKkNoKtp) uriToSelectedDoc(app.doc8) else null,
                        docFormPelaporanKelahiran = if (isTerlambatKiaKkNoKtp) uriToSelectedDoc(app.doc9) else null,
                        docKtpAnak = if (isTerlambatOrKtp && !isTerlambatKiaKkNoKtp) uriToSelectedDoc(app.doc8) else null,
                        docSelfie = uriToSelectedDoc(app.docSelfie)
                    )
                }
            }
        }
    }

    fun updateKategori(value: String) {
        _uiState.update { it.copy(kategoriLayanan = value, kategoriError = null) }
    }

    fun updateKecamatan(value: String) {
        _uiState.update { it.copy(kecamatan = value, kelurahan = "", kecamatanError = null, kelurahanError = null) }
    }

    fun updateKelurahan(value: String) {
        _uiState.update { it.copy(kelurahan = value, kelurahanError = null) }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value, emailError = null) }
    }

    fun updateWhatsapp(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update { it.copy(whatsapp = filtered, whatsappError = null) }
    }

    fun updateKeterangan(value: String) {
        _uiState.update { it.copy(keterangan = value) }
    }

    fun updateNikPelapor(value: String) {
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.update { it.copy(nikPelapor = filtered, nikPelaporError = null) }
    }

    fun updateNamaPelapor(value: String) {
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.update { it.copy(namaPelapor = filtered, namaPelaporError = null) }
    }

    fun updateNikBayi(value: String) {
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.update { it.copy(nikBayi = filtered) }
    }

    fun updateNamaBayi(value: String) {
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.update { it.copy(namaBayi = filtered, namaBayiError = null) }
    }

    fun updateNikIbu(value: String) {
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.update { it.copy(nikIbu = filtered, nikIbuError = null) }
    }

    fun updateNamaIbu(value: String) {
        val filtered = value.filter { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' }.take(60)
        _uiState.update { it.copy(namaIbu = filtered, namaIbuError = null) }
    }

    fun updateNomorKk(value: String) {
        val filtered = value.filter { it.isDigit() }.take(16)
        _uiState.update { it.copy(nomorKk = filtered, nomorKkError = null) }
    }

    fun updateDocKkTerbaru(doc: SelectedDocument?) { _uiState.update { it.copy(docKkTerbaru = doc) } }
    fun updateDocKtpIbu(doc: SelectedDocument?) { _uiState.update { it.copy(docKtpIbu = doc) } }
    fun updateDocKtpBapak(doc: SelectedDocument?) { _uiState.update { it.copy(docKtpBapak = doc) } }
    fun updateDocFormF201(doc: SelectedDocument?) { _uiState.update { it.copy(docFormF201 = doc) } }
    fun updateDocFormF102(doc: SelectedDocument?) { _uiState.update { it.copy(docFormF102 = doc) } }
    fun updateDocSuratKelahiran(doc: SelectedDocument?) { _uiState.update { it.copy(docSuratKelahiran = doc) } }
    fun updateDocBukuNikah(doc: SelectedDocument?) { _uiState.update { it.copy(docBukuNikah = doc) } }
    fun updateDocSptjmKelahiran(doc: SelectedDocument?) { _uiState.update { it.copy(docSptjmKelahiran = doc) } }
    fun updateDocFormPelaporanKelahiran(doc: SelectedDocument?) { _uiState.update { it.copy(docFormPelaporanKelahiran = doc) } }
    fun updateDocKtpAnak(doc: SelectedDocument?) { _uiState.update { it.copy(docKtpAnak = doc) } }
    fun updateDocSelfie(doc: SelectedDocument?) { _uiState.update { it.copy(docSelfie = doc) } }

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

    fun submitForm() {
        if (validateForm()) {
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
                            serviceType = "AKTA",
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
                            nikBayi = state.nikBayi,
                            namaBayi = state.namaBayi,
                            nikIbu = state.nikIbu,
                            namaIbu = state.namaIbu,
                            nomorKk = state.nomorKk,
                            doc1 = state.docKkTerbaru?.uri?.toString(),
                            doc2 = state.docKtpIbu?.uri?.toString(),
                            doc3 = state.docKtpBapak?.uri?.toString(),
                            doc4 = state.docFormF201?.uri?.toString(),
                            doc5 = state.docFormF102?.uri?.toString(),
                            doc6 = state.docSuratKelahiran?.uri?.toString(),
                            doc7 = state.docBukuNikah?.uri?.toString(),
                            doc8 = state.docSptjmKelahiran?.uri?.toString() ?: state.docKtpAnak?.uri?.toString(),
                            doc9 = state.docFormPelaporanKelahiran?.uri?.toString(),
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
                            val submissionCode = "AKTA-${System.currentTimeMillis().toString().takeLast(6)}"
                            val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

                            val application = ApplicationEntity(
                                userId = userSession.userId,
                                serviceType = "AKTA",
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
                                nikBayi = state.nikBayi,
                                namaBayi = state.namaBayi,
                                nikIbu = state.nikIbu,
                                namaIbu = state.namaIbu,
                                nomorKk = state.nomorKk,
                                doc1 = state.docKkTerbaru?.uri?.toString(),
                                doc2 = state.docKtpIbu?.uri?.toString(),
                                doc3 = state.docKtpBapak?.uri?.toString(),
                                doc4 = state.docFormF201?.uri?.toString(),
                                doc5 = state.docFormF102?.uri?.toString(),
                                doc6 = state.docSuratKelahiran?.uri?.toString(),
                                doc7 = state.docBukuNikah?.uri?.toString(),
                                doc8 = state.docSptjmKelahiran?.uri?.toString() ?: state.docKtpAnak?.uri?.toString(),
                                doc9 = state.docFormPelaporanKelahiran?.uri?.toString(),
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
