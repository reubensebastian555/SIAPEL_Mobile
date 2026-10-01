package com.example.siapel.model

data class KtpFormState(
    val kategoriLayanan: String = "",
    val kecamatan: String = "",
    val kelurahan: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val keterangan: String = "",
    val namaPelapor: String = "",
    val nikPelapor: String = "",
    
    // Validation Errors
    val kategoriError: String? = null,
    val kecamatanError: String? = null,
    val kelurahanError: String? = null,
    val emailError: String? = null,
    val whatsappError: String? = null,
    val namaPelaporError: String? = null,
    val nikPelaporError: String? = null,

    // Document Picker States
    val docKkTerbaru: SelectedDocument? = null,
    val docKtpLama: SelectedDocument? = null,
    val docSuratKeteranganHilang: SelectedDocument? = null,
    val docSelfie: SelectedDocument? = null
)
