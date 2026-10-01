package com.example.siapel.model

data class AktaKelahiranFormState(
    val kategoriLayanan: String = "",
    val kecamatan: String = "",
    val kelurahan: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val keterangan: String = "",
    val nikPelapor: String = "",
    val namaPelapor: String = "",
    val nikBayi: String = "",
    val namaBayi: String = "",
    val nikIbu: String = "",
    val namaIbu: String = "",
    val nomorKk: String = "",

    // Validation Errors
    val kategoriError: String? = null,
    val kecamatanError: String? = null,
    val kelurahanError: String? = null,
    val emailError: String? = null,
    val whatsappError: String? = null,
    val nikPelaporError: String? = null,
    val namaPelaporError: String? = null,
    val namaBayiError: String? = null,
    val nikIbuError: String? = null,
    val namaIbuError: String? = null,
    val nomorKkError: String? = null,

    // Document Picker States
    val docKkTerbaru: SelectedDocument? = null,
    val docKtpIbu: SelectedDocument? = null,
    val docKtpBapak: SelectedDocument? = null,
    val docFormF201: SelectedDocument? = null,
    val docFormF102: SelectedDocument? = null,
    val docSuratKelahiran: SelectedDocument? = null,
    val docBukuNikah: SelectedDocument? = null,
    val docSptjmKelahiran: SelectedDocument? = null,
    val docFormPelaporanKelahiran: SelectedDocument? = null,
    val docKtpAnak: SelectedDocument? = null,
    val docSelfie: SelectedDocument? = null
)
