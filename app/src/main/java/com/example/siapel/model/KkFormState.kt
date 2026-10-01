package com.example.siapel.model

import android.net.Uri

enum class UploadState {
    READY, UPLOADING, SUCCESS, ERROR
}

data class SelectedDocument(
    val uri: Uri?,
    val displayName: String,
    val fileSize: Long = 0L,
    val mimeType: String? = null,
    val uploadState: UploadState = UploadState.READY,
    val uploadProgress: Float? = null
)

data class KkFormState(
    val kategoriProduk: String = "",
    val kecamatan: String = "",
    val kelurahan: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val keterangan: String = "",
    val nikPelapor: String = "",
    val namaPelapor: String = "",
    val nikAnak: String = "",
    val namaAnak: String = "",
    val nomorKk: String = "",

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
    val nomorKkError: String? = null,

    // Document Picker States
    val docKkTerbaru: SelectedDocument? = null,
    val docF101: SelectedDocument? = null,
    val docF102: SelectedDocument? = null,
    val docF103: SelectedDocument? = null,
    val docF106: SelectedDocument? = null,
    val docSptjm: SelectedDocument? = null,
    val docSuratPernyataanTempatTinggal: SelectedDocument? = null,
    val docSuratKehilanganKkRusak: SelectedDocument? = null,
    val docKtpHilangRusak: SelectedDocument? = null,
    val docPendukung: SelectedDocument? = null,
    val docSelfie: SelectedDocument? = null
)
