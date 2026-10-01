package com.example.siapel.model

data class DisabilitasFormState(
    val kategoriLayanan: String = "",
    val kecamatan: String = "",
    val kelurahan: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val keterangan: String = "",
    val nikPelapor: String = "",
    val namaPelapor: String = "",

    // Validation Errors
    val kategoriError: String? = null,
    val kecamatanError: String? = null,
    val kelurahanError: String? = null,
    val emailError: String? = null,
    val whatsappError: String? = null,
    val nikPelaporError: String? = null,
    val namaPelaporError: String? = null,

    // Document Picker States
    val docKk: SelectedDocument? = null,
    val docKtp: SelectedDocument? = null,
    val docSelfie: SelectedDocument? = null,
    val docKkError: String? = null,
    val docKtpError: String? = null,
    val docSelfieError: String? = null
)
