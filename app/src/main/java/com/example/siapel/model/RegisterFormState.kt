package com.example.siapel.model

data class RegisterFormState(
    val namaLengkap: String = "",
    val nik: String = "",
    val email: String = "",
    val nomorWhatsapp: String = "",
    val kecamatan: String = "",
    val kelurahan: String = "",
    val password: String = "",
    val konfirmasiPassword: String = ""
)
