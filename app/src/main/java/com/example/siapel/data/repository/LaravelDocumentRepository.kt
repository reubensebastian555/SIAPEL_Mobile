package com.example.siapel.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.siapel.data.remote.laravel.LaravelClientProvider
import com.example.siapel.data.remote.laravel.LaravelDocumentApiService
import com.example.siapel.data.remote.laravel.dto.LaravelDocumentDownloadResult
import com.example.siapel.data.remote.laravel.dto.LaravelDocumentUploadResponse
import com.example.siapel.data.remote.laravel.dto.LaravelMessageResponse
import com.example.siapel.model.SelectedDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Repository universal untuk mengelola dokumen privat permohonan pada REST API Laravel 12.
 * Mengombinasikan [SelectedDocument], [ContentResolver] Android, Bounded Reading, dan [LaravelDocumentApiService].
 * Berlaku untuk seluruh 5 Layanan SIAPEL: KIA, KTP, KK, AKTA, dan DISABILITAS.
 */
class LaravelDocumentRepository(
    private val documentApiService: LaravelDocumentApiService = LaravelDocumentApiService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private val CANONICAL_UUID_REGEX = Regex(
            "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
        )

        private val VALID_SLOTS = setOf(
            "doc1", "doc2", "doc3", "doc4", "doc5",
            "doc6", "doc7", "doc8", "doc9", "doc10", "docSelfie"
        )

        const val MAX_FILE_SIZE_BYTES = 2_097_152L // 2 MiB

        private val ALLOWED_MIME_TYPES = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "pdf" to "application/pdf",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    }

    /**
     * Membaca byte dokumen dari [ContentResolver] secara aman dengan Bounded Reading di Dispatcher IO.
     */
    suspend fun readDocumentBytes(
        contentResolver: ContentResolver?,
        uri: Uri,
        inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<ByteArray> = withContext(ioDispatcher) {
        var inputStream: InputStream? = null
        val outputStream = ByteArrayOutputStream()

        try {
            inputStream = inputStreamSupplier?.invoke(uri)
                ?: contentResolver?.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("File dokumen tidak dapat dibuka (InputStream null)."))

            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                totalBytesRead += bytesRead
                if (totalBytesRead > MAX_FILE_SIZE_BYTES) {
                    return@withContext Result.failure(
                        Exception("Ukuran berkas file melebihi batas maksimum 2 MiB ($MAX_FILE_SIZE_BYTES bytes).")
                    )
                }
                outputStream.write(buffer, 0, bytesRead)
            }

            val resultBytes = outputStream.toByteArray()
            if (resultBytes.isEmpty()) {
                return@withContext Result.failure(Exception("Berkas file kosong (0 byte)."))
            }

            Result.success(resultBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("Gagal membaca isi berkas dokumen: ${e.message}", e))
        } finally {
            try {
                outputStream.close()
                inputStream?.close()
            } catch (_: Exception) {
                // Ignore stream close exception
            }
        }
    }

    /**
     * Memvalidasi dan menentukan ekstensi serta MIME type berdasarkan filename sebagai dasar utama.
     * Mencegah manipulasi ekstensi atau penyembunyian MIME-extension mismatch.
     */
    fun resolveExtensionAndMime(selectedDocument: SelectedDocument): Pair<String, String> {
        val displayName = selectedDocument.displayName.trim()
        val extension = displayName.substringAfterLast('.', "").lowercase()

        require(extension.isNotBlank() && extension != displayName.lowercase()) {
            "Filename '$displayName' harus memiliki ekstensi berkas yang valid."
        }

        val expectedMime = ALLOWED_MIME_TYPES[extension]
            ?: throw IllegalArgumentException("Format berkas '$extension' tidak didukung. Gunakan JPG, PNG, PDF, DOC, atau DOCX.")

        val rawMime = selectedDocument.mimeType?.lowercase()?.trim()

        if (!rawMime.isNullOrBlank() && rawMime != "application/octet-stream") {
            val isJpgEquivalent = (extension == "jpg" || extension == "jpeg") && rawMime == "image/jpeg"
            if (!isJpgEquivalent && rawMime != expectedMime) {
                throw IllegalArgumentException(
                    "MIME type '$rawMime' tidak sesuai dengan ekstensi berkas '$extension' (seharusnya '$expectedMime')."
                )
            }
        }

        return Pair(extension, expectedMime)
    }

    /**
     * Mengunggah dokumen baru atau memperbarui (replace) dokumen privat pada slot permohonan.
     *
     * @param context Context Android opsional untuk ContentResolver
     * @param applicationUuid UUID canonical pengajuan Laravel (bukan submission_code / Room ID)
     * @param slot Slot dokumen ('doc1'..'doc10', 'docSelfie')
     * @param selectedDocument Data dokumen yang dipilih dari UI ([SelectedDocument])
     * @param token Bearer Token opsional (menggunakan token provider jika null)
     * @param inputStreamSupplier Supplier InputStream opsional khusus pengujian
     */
    suspend fun uploadOrReplaceDocument(
        context: Context?,
        applicationUuid: String,
        slot: String,
        selectedDocument: SelectedDocument,
        token: String? = null,
        inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelDocumentUploadResponse> {
        val activeToken = token ?: LaravelClientProvider.tokenProvider?.getToken()
        if (activeToken.isNullOrBlank()) {
            return Result.failure(Exception("Sesi autentikasi Laravel tidak valid atau belum masuk."))
        }

        if (!CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            return Result.failure(Exception("Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."))
        }

        if (!VALID_SLOTS.contains(slot)) {
            return Result.failure(Exception("Slot dokumen '$slot' tidak valid."))
        }

        val uri = selectedDocument.uri
            ?: return Result.failure(Exception("File dokumen tidak ditemukan (URI null)."))

        // Pre-file-read validation: Format & MIME checked before reading file
        val (extension, mimeType) = try {
            resolveExtensionAndMime(selectedDocument)
        } catch (e: IllegalArgumentException) {
            return Result.failure(e)
        }

        val readResult = readDocumentBytes(context?.contentResolver, uri, inputStreamSupplier)
        if (readResult.isFailure) {
            return Result.failure(readResult.exceptionOrNull() ?: Exception("Gagal membaca berkas dokumen."))
        }

        val fileBytes = readResult.getOrThrow()

        val filename = if (selectedDocument.displayName.endsWith(".$extension", ignoreCase = true)) {
            selectedDocument.displayName
        } else {
            "${selectedDocument.displayName.substringBeforeLast('.')}.$extension"
        }

        return try {
            val response = documentApiService.uploadOrReplaceDocument(
                applicationUuid = applicationUuid,
                slot = slot,
                fileBytes = fileBytes,
                filename = filename,
                declaredMimeType = mimeType,
                token = activeToken
            )
            Result.success(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengunduh/membaca stream biner dokumen privat milik pengguna dari server Laravel.
     *
     * @param applicationUuid UUID canonical pengajuan Laravel
     * @param slot Slot dokumen ('doc1'..'doc10', 'docSelfie')
     * @param token Bearer Token opsional
     */
    suspend fun downloadDocument(
        applicationUuid: String,
        slot: String,
        token: String? = null
    ): Result<LaravelDocumentDownloadResult> {
        val activeToken = token ?: LaravelClientProvider.tokenProvider?.getToken()
        if (activeToken.isNullOrBlank()) {
            return Result.failure(Exception("Sesi autentikasi Laravel tidak valid atau belum masuk."))
        }

        if (!CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            return Result.failure(Exception("Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."))
        }

        if (!VALID_SLOTS.contains(slot)) {
            return Result.failure(Exception("Slot dokumen '$slot' tidak valid."))
        }

        return try {
            val result = documentApiService.downloadDocument(
                applicationUuid = applicationUuid,
                slot = slot,
                token = activeToken
            )
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Menghapus dokumen privat dari server Laravel.
     *
     * @param applicationUuid UUID canonical pengajuan Laravel
     * @param slot Slot dokumen ('doc1'..'doc10', 'docSelfie')
     * @param token Bearer Token opsional
     */
    suspend fun deleteDocument(
        applicationUuid: String,
        slot: String,
        token: String? = null
    ): Result<LaravelMessageResponse> {
        val activeToken = token ?: LaravelClientProvider.tokenProvider?.getToken()
        if (activeToken.isNullOrBlank()) {
            return Result.failure(Exception("Sesi autentikasi Laravel tidak valid atau belum masuk."))
        }

        if (!CANONICAL_UUID_REGEX.matches(applicationUuid)) {
            return Result.failure(Exception("Format UUID pengajuan tidak valid. Harus berformat canonical (8-4-4-4-12 heksadesimal)."))
        }

        if (!VALID_SLOTS.contains(slot)) {
            return Result.failure(Exception("Slot dokumen '$slot' tidak valid."))
        }

        return try {
            val response = documentApiService.deleteDocument(
                applicationUuid = applicationUuid,
                slot = slot,
                token = activeToken
            )
            Result.success(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
