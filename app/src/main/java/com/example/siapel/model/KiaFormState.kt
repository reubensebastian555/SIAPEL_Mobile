package com.example.siapel.model

data class KiaFormState(
    val kategoriLayanan: String = "",
    val kecamatan: String = "",
    val kelurahan: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val keterangan: String = "",
    val nikPelapor: String = "",
    val namaPelapor: String = "",
    val nikAnak: String = "",
    val namaAnak: String = "",
    
    // Validation Errors
    val kategoriError: String? = null,
    val kecamatanError: String? = null,
    val kelurahanError: String? = null,
    val emailError: String? = null,
    val whatsappError: String? = null,
    val nikPelaporError: String? = null,
    val namaPelaporError: String? = null,
    val nikAnakError: String? = null,
    val namaAnakError: String? = null,

    // Document Picker States
    val docKkTerbaru: SelectedDocument? = null,
    val docAktaLahirAnak: SelectedDocument? = null,
    val docPasFotoAnak: SelectedDocument? = null,
    val docKiaLama: SelectedDocument? = null,
    val docSuratKehilangan: SelectedDocument? = null,
    val docSelfie: SelectedDocument? = null
)
