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

    private var originalCategory: String = ""
    private var originalDoc1: String? = null
    private var originalDoc2: String? = null
    private var originalDoc3: String? = null
    private var originalDoc4: String? = null
    private var originalDoc5: String? = null
    private var originalDoc6: String? = null
    private var originalDoc7: String? = null
    private var originalDoc8: String? = null
    private var originalDoc9: String? = null
    private var originalDocSelfie: String? = null

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
                originalCategory = app.category
                originalDoc1 = app.doc1
                originalDoc2 = app.doc2
                originalDoc3 = app.doc3
                originalDoc4 = app.doc4
                originalDoc5 = app.doc5
                originalDoc6 = app.doc6
                originalDoc7 = app.doc7
                originalDoc8 = app.doc8
                originalDoc9 = app.doc9
                originalDocSelfie = app.docSelfie
                _isEditMode.value = true

                val originalGroup = getAktaSpecialDocumentGroup(app.category)

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
                        docSptjmKelahiran = if (originalGroup == AktaSpecialDocumentGroup.SPTJM_AND_FORM) uriToSelectedDoc(app.doc8) else null,
                        docFormPelaporanKelahiran = if (originalGroup == AktaSpecialDocumentGroup.SPTJM_AND_FORM) uriToSelectedDoc(app.doc9) else null,
                        docKtpAnak = if (originalGroup == AktaSpecialDocumentGroup.KTP_ANAK) uriToSelectedDoc(app.doc8) else null,
                        docSelfie = uriToSelectedDoc(app.docSelfie),
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

    fun updateDocKkTerbaru(doc: SelectedDocument?) { _uiState.update { it.copy(docKkTerbaru = doc, docKkTerbaruTouched = true) } }
    fun updateDocKtpIbu(doc: SelectedDocument?) { _uiState.update { it.copy(docKtpIbu = doc, docKtpIbuTouched = true) } }
    fun updateDocKtpBapak(doc: SelectedDocument?) { _uiState.update { it.copy(docKtpBapak = doc, docKtpBapakTouched = true) } }
    fun updateDocFormF201(doc: SelectedDocument?) { _uiState.update { it.copy(docFormF201 = doc, docFormF201Touched = true) } }
    fun updateDocFormF102(doc: SelectedDocument?) { _uiState.update { it.copy(docFormF102 = doc, docFormF102Touched = true) } }
    fun updateDocSuratKelahiran(doc: SelectedDocument?) { _uiState.update { it.copy(docSuratKelahiran = doc, docSuratKelahiranTouched = true) } }
    fun updateDocBukuNikah(doc: SelectedDocument?) { _uiState.update { it.copy(docBukuNikah = doc, docBukuNikahTouched = true) } }
    fun updateDocSptjmKelahiran(doc: SelectedDocument?) { _uiState.update { it.copy(docSptjmKelahiran = doc, docSptjmKelahiranTouched = true) } }
    fun updateDocFormPelaporanKelahiran(doc: SelectedDocument?) { _uiState.update { it.copy(docFormPelaporanKelahiran = doc, docFormPelaporanKelahiranTouched = true) } }
    fun updateDocKtpAnak(doc: SelectedDocument?) { _uiState.update { it.copy(docKtpAnak = doc, docKtpAnakTouched = true) } }
    fun updateDocSelfie(doc: SelectedDocument?) { _uiState.update { it.copy(docSelfie = doc, docSelfieTouched = true) } }

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

    fun submitForm(context: Context) {
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

                        val removeDoc1 = state.docKkTerbaruTouched && state.docKkTerbaru == null
                        val newDoc1 = if (state.docKkTerbaruTouched && state.docKkTerbaru != null) state.docKkTerbaru else null

                        val removeDoc2 = state.docKtpIbuTouched && state.docKtpIbu == null
                        val newDoc2 = if (state.docKtpIbuTouched && state.docKtpIbu != null) state.docKtpIbu else null

                        val removeDoc3 = state.docKtpBapakTouched && state.docKtpBapak == null
                        val newDoc3 = if (state.docKtpBapakTouched && state.docKtpBapak != null) state.docKtpBapak else null

                        val removeDoc4 = state.docFormF201Touched && state.docFormF201 == null
                        val newDoc4 = if (state.docFormF201Touched && state.docFormF201 != null) state.docFormF201 else null

                        val removeDoc5 = state.docFormF102Touched && state.docFormF102 == null
                        val newDoc5 = if (state.docFormF102Touched && state.docFormF102 != null) state.docFormF102 else null

                        val removeDoc6 = state.docSuratKelahiranTouched && state.docSuratKelahiran == null
                        val newDoc6 = if (state.docSuratKelahiranTouched && state.docSuratKelahiran != null) state.docSuratKelahiran else null

                        val removeDoc7 = state.docBukuNikahTouched && state.docBukuNikah == null
                        val newDoc7 = if (state.docBukuNikahTouched && state.docBukuNikah != null) state.docBukuNikah else null

                        val removeDocSelfie = state.docSelfieTouched && state.docSelfie == null
                        val newDocSelfie = if (state.docSelfieTouched && state.docSelfie != null) state.docSelfie else null

                        val originalGroup = getAktaSpecialDocumentGroup(originalCategory)
                        val finalGroup = getAktaSpecialDocumentGroup(state.kategoriLayanan)

                        var newDoc8: SelectedDocument? = null
                        var removeDoc8 = false

                        var newDoc9: SelectedDocument? = null
                        var removeDoc9 = false

                        when (finalGroup) {
                            AktaSpecialDocumentGroup.NONE -> {
                                removeDoc8 = true
                                removeDoc9 = true
                            }
                            AktaSpecialDocumentGroup.SPTJM_AND_FORM -> {
                                if (originalGroup == AktaSpecialDocumentGroup.SPTJM_AND_FORM) {
                                    if (state.docSptjmKelahiranTouched) {
                                        if (state.docSptjmKelahiran != null) {
                                            newDoc8 = state.docSptjmKelahiran
                                        } else {
                                            removeDoc8 = true
                                        }
                                    }
                                } else {
                                    if (state.docSptjmKelahiranTouched && state.docSptjmKelahiran != null) {
                                        newDoc8 = state.docSptjmKelahiran
                                    } else {
                                        removeDoc8 = true
                                    }
                                }

                                if (originalGroup == AktaSpecialDocumentGroup.SPTJM_AND_FORM) {
                                    if (state.docFormPelaporanKelahiranTouched) {
                                        if (state.docFormPelaporanKelahiran != null) {
                                            newDoc9 = state.docFormPelaporanKelahiran
                                        } else {
                                            removeDoc9 = true
                                        }
                                    }
                                } else {
                                    if (state.docFormPelaporanKelahiranTouched && state.docFormPelaporanKelahiran != null) {
                                        newDoc9 = state.docFormPelaporanKelahiran
                                    } else {
                                        removeDoc9 = true
                                    }
                                }
                            }
                            AktaSpecialDocumentGroup.KTP_ANAK -> {
                                if (originalGroup == AktaSpecialDocumentGroup.KTP_ANAK) {
                                    if (state.docKtpAnakTouched) {
                                        if (state.docKtpAnak != null) {
                                            newDoc8 = state.docKtpAnak
                                        } else {
                                            removeDoc8 = true
                                        }
                                    }
                                } else {
                                    if (state.docKtpAnakTouched && state.docKtpAnak != null) {
                                        newDoc8 = state.docKtpAnak
                                    } else {
                                        removeDoc8 = true
                                    }
                                }
                                removeDoc9 = true
                            }
                        }

                        val finalRoomDoc1 = when {
                            removeDoc1 -> null
                            newDoc1 != null -> newDoc1.uri?.toString()
                            else -> originalDoc1
                        }
                        val finalRoomDoc2 = when {
                            removeDoc2 -> null
                            newDoc2 != null -> newDoc2.uri?.toString()
                            else -> originalDoc2
                        }
                        val finalRoomDoc3 = when {
                            removeDoc3 -> null
                            newDoc3 != null -> newDoc3.uri?.toString()
                            else -> originalDoc3
                        }
                        val finalRoomDoc4 = when {
                            removeDoc4 -> null
                            newDoc4 != null -> newDoc4.uri?.toString()
                            else -> originalDoc4
                        }
                        val finalRoomDoc5 = when {
                            removeDoc5 -> null
                            newDoc5 != null -> newDoc5.uri?.toString()
                            else -> originalDoc5
                        }
                        val finalRoomDoc6 = when {
                            removeDoc6 -> null
                            newDoc6 != null -> newDoc6.uri?.toString()
                            else -> originalDoc6
                        }
                        val finalRoomDoc7 = when {
                            removeDoc7 -> null
                            newDoc7 != null -> newDoc7.uri?.toString()
                            else -> originalDoc7
                        }
                        val finalRoomDoc8 = when {
                            removeDoc8 -> null
                            newDoc8 != null -> newDoc8.uri?.toString()
                            originalGroup == finalGroup -> originalDoc8
                            else -> null
                        }
                        val finalRoomDoc9 = when {
                            removeDoc9 -> null
                            newDoc9 != null -> newDoc9.uri?.toString()
                            originalGroup == AktaSpecialDocumentGroup.SPTJM_AND_FORM && finalGroup == AktaSpecialDocumentGroup.SPTJM_AND_FORM -> originalDoc9
                            else -> null
                        }
                        val finalRoomDocSelfie = when {
                            removeDocSelfie -> null
                            newDocSelfie != null -> newDocSelfie.uri?.toString()
                            else -> originalDocSelfie
                        }

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
                            doc1 = finalRoomDoc1,
                            doc2 = finalRoomDoc2,
                            doc3 = finalRoomDoc3,
                            doc4 = finalRoomDoc4,
                            doc5 = finalRoomDoc5,
                            doc6 = finalRoomDoc6,
                            doc7 = finalRoomDoc7,
                            doc8 = finalRoomDoc8,
                            doc9 = finalRoomDoc9,
                            docSelfie = finalRoomDocSelfie,
                            createdAt = originalCreatedAt
                        )

                        var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                            ?: authRepository.getCurrentSupabaseSessionUserId()

                        if (currentUuid.isNullOrEmpty()) {
                            authRepository.validateSessionWithServer()
                            currentUuid = authRepository.getCurrentSupabaseUser()?.id
                                ?: authRepository.getCurrentSupabaseSessionUserId()
                        }

                        if (!currentUuid.isNullOrBlank()) {
                            val isRemoteUpdateSuccess = applicationRepository.updateAktaApplicationRemoteWithDocuments(
                                context = context,
                                application = updatedApp,
                                currentUuid = currentUuid,
                                newDoc1 = newDoc1,
                                newDoc2 = newDoc2,
                                newDoc3 = newDoc3,
                                newDoc4 = newDoc4,
                                newDoc5 = newDoc5,
                                newDoc6 = newDoc6,
                                newDoc7 = newDoc7,
                                newDoc8 = newDoc8,
                                newDoc9 = newDoc9,
                                newDocSelfie = newDocSelfie,
                                removeDoc1 = removeDoc1,
                                removeDoc2 = removeDoc2,
                                removeDoc3 = removeDoc3,
                                removeDoc4 = removeDoc4,
                                removeDoc5 = removeDoc5,
                                removeDoc6 = removeDoc6,
                                removeDoc7 = removeDoc7,
                                removeDoc8 = removeDoc8,
                                removeDoc9 = removeDoc9,
                                removeDocSelfie = removeDocSelfie
                            )

                            if (isRemoteUpdateSuccess) {
                                _submitState.value = SubmitState.SUCCESS
                                delay(800)
                                _submissionResult.emit(true)
                            } else {
                                Log.e("SIAPEL_EDIT_AKTA", "AKTA remote update failed for code ${updatedApp.submissionCode}.")
                                _submitState.value = SubmitState.ERROR
                                delay(1000)
                                _submitState.value = SubmitState.IDLE
                                _submissionResult.emit(false)
                            }
                        } else {
                            Log.e("SIAPEL_EDIT_AKTA", "AKTA remote update failed: UUID is null or blank.")
                            _submitState.value = SubmitState.ERROR
                            delay(1000)
                            _submitState.value = SubmitState.IDLE
                            _submissionResult.emit(false)
                        }
                    } else {
                        val userSession = userPreferencesRepository.userSessionFlow.first()
                        val currentUuid = authRepository.getCurrentSupabaseUser()?.id
                            ?: authRepository.getCurrentSupabaseSessionUserId()

                        if (userSession.isLoggedIn) {
                            val state = _uiState.value
                            val submissionCode = "AKTA-${System.currentTimeMillis().toString().takeLast(6)}"
                            val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

                            val doc1 = state.docKkTerbaru
                            val doc2 = state.docKtpIbu
                            val doc3 = state.docKtpBapak
                            val doc4 = state.docFormF201
                            val doc5 = state.docFormF102
                            val doc6 = state.docSuratKelahiran
                            val doc7 = state.docBukuNikah
                            val docSelfie = state.docSelfie

                            val doc8: SelectedDocument?
                            val doc9: SelectedDocument?

                            when (state.kategoriLayanan) {
                                "Paket Kelahiran Umum + KIA + KK" -> {
                                    doc8 = null
                                    doc9 = null
                                }
                                "Paket Kelahiran Terlambat + KIA + KK" -> {
                                    doc8 = state.docSptjmKelahiran
                                    doc9 = state.docFormPelaporanKelahiran
                                }
                                "Kelahiran Terlambat Tunggal",
                                "Paket Kelahiran Umum + KIA + KK + KTP",
                                "Paket Kelahiran Terlambat + KIA + KK + KTP",
                                "Paket Kelahiran Terlambat + KK + KTP",
                                "Paket Kelahiran Terlambat + KTP" -> {
                                    doc8 = state.docKtpAnak
                                    doc9 = null
                                }
                                else -> {
                                    _submitState.value = SubmitState.ERROR
                                    delay(1000)
                                    _submitState.value = SubmitState.IDLE
                                    _submissionResult.emit(false)
                                    return@launch
                                }
                            }

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
                                doc1 = doc1?.uri?.toString(),
                                doc2 = doc2?.uri?.toString(),
                                doc3 = doc3?.uri?.toString(),
                                doc4 = doc4?.uri?.toString(),
                                doc5 = doc5?.uri?.toString(),
                                doc6 = doc6?.uri?.toString(),
                                doc7 = doc7?.uri?.toString(),
                                doc8 = doc8?.uri?.toString(),
                                doc9 = doc9?.uri?.toString(),
                                docSelfie = docSelfie?.uri?.toString()
                            )

                            if (!currentUuid.isNullOrBlank()) {
                                val remoteSuccess = applicationRepository.createAktaApplicationRemote(
                                    context = context,
                                    application = application,
                                    currentSupabaseUuid = currentUuid,
                                    doc1 = doc1,
                                    doc2 = doc2,
                                    doc3 = doc3,
                                    doc4 = doc4,
                                    doc5 = doc5,
                                    doc6 = doc6,
                                    doc7 = doc7,
                                    doc8 = doc8,
                                    doc9 = doc9,
                                    docSelfie = docSelfie
                                )

                                if (remoteSuccess) {
                                    _submitState.value = SubmitState.SUCCESS
                                    delay(800)
                                    _submissionResult.emit(true)
                                } else {
                                    Log.e("SIAPEL_STORAGE", "AKTA remote create failed.")
                                    _submitState.value = SubmitState.ERROR
                                    delay(1000)
                                    _submitState.value = SubmitState.IDLE
                                    _submissionResult.emit(false)
                                }
                            } else {
                                Log.e("SIAPEL_STORAGE", "AKTA remote create failed: UUID is null or blank.")
                                _submitState.value = SubmitState.ERROR
                                delay(1000)
                                _submitState.value = SubmitState.IDLE
                                _submissionResult.emit(false)
                            }
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
