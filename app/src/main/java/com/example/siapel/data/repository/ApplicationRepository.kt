package com.example.siapel.data.repository

import android.content.Context
import android.util.Log
import com.example.siapel.data.local.dao.ApplicationDao
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.remote.SupabaseClientProvider
import com.example.siapel.data.remote.dto.ApplicationRemoteDto
import com.example.siapel.data.remote.dto.ApplicationWithKiaDetailRemoteDto
import com.example.siapel.data.remote.dto.ApplicationWithKtpDetailRemoteDto
import com.example.siapel.data.remote.dto.ApplicationWithKkDetailRemoteDto
import com.example.siapel.data.remote.dto.ApplicationWithAktaDetailRemoteDto
import com.example.siapel.data.remote.dto.KiaDetailRemoteDto
import com.example.siapel.data.remote.dto.KtpDetailRemoteDto
import com.example.siapel.data.remote.dto.KkDetailRemoteDto
import com.example.siapel.data.remote.dto.AktaDetailRemoteDto
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.SelectedDocument
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SIAPEL_SUPABASE"

private fun isKiaPlusKk(category: String): Boolean {
    val normalized = category
        .uppercase()
        .replace(" ", "")
        .replace("&", "+")
        .replace("DAN", "+")

    return normalized.contains("KIA+KK")
}

private fun isNewSelectedDocument(newDoc: SelectedDocument?): Boolean {
    val newUri = newDoc?.uri?.toString()
    return !newUri.isNullOrBlank()
}

class ApplicationRepository(private val applicationDao: ApplicationDao) {

    private val postgrest = SupabaseClientProvider.postgrest
    private val storage = SupabaseClientProvider.storage

    fun getApplicationsByUser(userId: Int): Flow<List<ApplicationEntity>> {
        return applicationDao.getApplicationsByUser(userId)
    }

    suspend fun getApplicationById(id: Int): ApplicationEntity? {
        return applicationDao.getApplicationById(id)
    }

    suspend fun getApplicationByCode(code: String): ApplicationEntity? {
        return applicationDao.getApplicationByCode(code)
    }

    fun getApplicationByCodeFlow(code: String): Flow<ApplicationEntity?> {
        return applicationDao.getApplicationByCodeFlow(code)
    }

    suspend fun insertApplication(application: ApplicationEntity): Long {
        return applicationDao.insertApplication(application)
    }

    suspend fun updateApplication(application: ApplicationEntity) {
        applicationDao.updateApplication(application)
    }

    suspend fun deleteApplication(application: ApplicationEntity) {
        applicationDao.deleteApplication(application)
    }

    private suspend fun cleanupRemoteApplication(submissionCode: String, currentUuid: String) {
        try {
            Log.d(TAG, "Attempting cleanup of remote application row for code $submissionCode")
            postgrest.from("applications").delete {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentUuid)
                }
            }
            Log.d(TAG, "Cleanup remote application row success for code $submissionCode")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup remote application row failed for code $submissionCode: ${e.message}")
        }
    }

    private suspend fun cleanupRemoteAktaApplication(submissionCode: String, currentUuid: String) {
        try {
            Log.d(TAG, "Attempting cleanup of remote AKTA application row for code $submissionCode")
            postgrest.from("applications").delete {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentUuid)
                    eq("service_type", "AKTA")
                }
            }
            Log.d(TAG, "Cleanup remote AKTA application row success for code $submissionCode")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup remote AKTA application row failed for code $submissionCode: ${e.message}")
        }
    }

    private suspend fun cleanupNewlyUploadedFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        val editTag = "SIAPEL_EDIT"
        try {
            Log.d(editTag, "cleaning up newly uploaded files due to failure: ${paths.size} files")
            storage.from("siapel-documents").delete(paths)
            Log.d(editTag, "cleanup newly uploaded files success")
        } catch (e: Exception) {
            Log.e(editTag, "cleanup newly uploaded files failed: ${e.message}")
        }
    }

    private suspend fun deleteSingleStorageFileBestEffort(oldPath: String, slot: String, tag: String) {
        try {
            Log.d(tag, "deleting old file for slot $slot: $oldPath")
            storage.from("siapel-documents").delete(listOf(oldPath))
            Log.d(tag, "delete old file success for slot $slot")
        } catch (e: Exception) {
            Log.e(tag, "delete old file failure for slot $slot: ${e.message}")
        }
    }

    // ==========================================================
    // SHARED / HELPER SECTION
    // ==========================================================

    suspend fun createSignedUrlForStoragePath(
        storagePath: String
    ): Result<String> {
        val storageTag = "SIAPEL_STORAGE"
        Log.d(storageTag, "signed url request start")

        if (storagePath.isBlank() || storagePath.startsWith("/") || !storagePath.contains("/")) {
            Log.e(storageTag, "signed url failed: invalid path $storagePath")
            return Result.failure(Exception("Path dokumen tidak valid."))
        }

        return try {
            val bucket = storage.from("siapel-documents")
            val signedUrl = bucket.createSignedUrl(
                path = storagePath,
                expiresIn = 300.seconds
            )
            Log.d(storageTag, "signed url success")
            Result.success(signedUrl)
        } catch (e: Exception) {
            Log.e(storageTag, "signed url failed for path $storagePath: ${e.message}", e)
            Result.failure(Exception("Gagal membuat akses dokumen sementara: ${e.message}"))
        }
    }

    suspend fun uploadKiaDocumentRemote(
        context: Context,
        selectedDocument: SelectedDocument,
        currentUuid: String,
        submissionCode: String,
        documentSlot: String
    ): Result<String> {
        val storageTag = "SIAPEL_STORAGE"
        Log.d(storageTag, "upload start for slot $documentSlot, submissionCode $submissionCode")

        val validSlots = listOf("doc1", "doc2", "doc3", "docSelfie")
        if (!validSlots.contains(documentSlot)) {
            Log.e(storageTag, "upload failed: invalid slot $documentSlot")
            return Result.failure(Exception("Slot dokumen tidak valid."))
        }

        val uri = selectedDocument.uri
        if (uri == null) {
            Log.e(storageTag, "upload failed: URI is null")
            return Result.failure(Exception("File dokumen tidak ditemukan (URI null)."))
        }

        val displayName = selectedDocument.displayName
        val rawMime = selectedDocument.mimeType

        val extension = when {
            rawMime?.contains("jpeg", true) == true || rawMime?.contains("jpg", true) == true -> "jpg"
            rawMime?.contains("png", true) == true -> "png"
            rawMime?.contains("pdf", true) == true -> "pdf"
            rawMime?.contains("msword", true) == true -> "doc"
            rawMime?.contains("wordprocessingml", true) == true -> "docx"
            else -> displayName.substringAfterLast('.', "").lowercase()
        }

        val imageExtensions = listOf("jpg", "jpeg", "png")
        val documentExtensions = listOf("pdf", "doc", "docx")

        val maxSizeBytes = when {
            imageExtensions.contains(extension) -> 5 * 1024 * 1024L
            documentExtensions.contains(extension) -> 10 * 1024 * 1024L
            else -> {
                Log.e(storageTag, "upload failed: invalid extension or mime ($extension / $rawMime)")
                return Result.failure(Exception("Format file tidak didukung. Gunakan JPG, PNG, PDF, DOC, atau DOCX."))
            }
        }

        val sizeErrorMessage = if (imageExtensions.contains(extension)) {
            "Ukuran gambar melebihi batas maksimal 5 MB."
        } else {
            "Ukuran dokumen melebihi batas maksimal 10 MB."
        }

        val rawSize = selectedDocument.fileSize
        if (rawSize > maxSizeBytes) {
            Log.e(storageTag, "upload failed: file size exceeds limit ($rawSize > $maxSizeBytes bytes)")
            return Result.failure(Exception(sizeErrorMessage))
        }

        Log.d(storageTag, "slot: $documentSlot, file size: $rawSize bytes, mime/extension: $rawMime / $extension")

        val fileBytes = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            }
        } catch (e: Exception) {
            Log.e(storageTag, "upload failed: unable to read Uri stream (${e.message})")
            null
        }

        if (fileBytes == null || fileBytes.isEmpty()) {
            Log.e(storageTag, "upload failed: file content is empty or unreadable")
            return Result.failure(Exception("Gagal membaca berkas file."))
        }

        if (fileBytes.size > maxSizeBytes) {
            Log.e(storageTag, "upload failed: read file size exceeds limit (${fileBytes.size} > $maxSizeBytes bytes)")
            return Result.failure(Exception(sizeErrorMessage))
        }

        val targetPath = "$currentUuid/$submissionCode/${documentSlot}_${System.currentTimeMillis()}.$extension"
        Log.d(storageTag, "target path: $targetPath")

        return try {
            val bucket = storage.from("siapel-documents")
            bucket.upload(targetPath, fileBytes) {
                upsert = false
            }
            Log.d(storageTag, "upload success for target path: $targetPath")
            Result.success(targetPath)
        } catch (e: Exception) {
            Log.e(storageTag, "upload failed for target path $targetPath: ${e.message}", e)
            Result.failure(Exception("Gagal mengunggah dokumen ke penyimpanan cloud: ${e.message}"))
        }
    }

    private suspend fun uploadKkDocumentRemote(
        context: Context,
        selectedDocument: SelectedDocument,
        currentUuid: String,
        submissionCode: String,
        documentSlot: String
    ): Result<String> {
        val storageTag = "SIAPEL_STORAGE"
        Log.d(storageTag, "KK upload start for slot $documentSlot, submissionCode $submissionCode")

        val validSlots = listOf(
            "doc1", "doc2", "doc3", "doc4", "doc5",
            "doc6", "doc7", "doc8", "doc9", "doc10", "docSelfie"
        )
        if (!validSlots.contains(documentSlot)) {
            Log.e(storageTag, "upload failed: invalid KK slot $documentSlot")
            return Result.failure(Exception("Slot dokumen KK tidak valid."))
        }

        val uri = selectedDocument.uri
        if (uri == null) {
            Log.e(storageTag, "upload failed: URI is null")
            return Result.failure(Exception("File dokumen tidak ditemukan (URI null)."))
        }

        val displayName = selectedDocument.displayName
        val rawMime = selectedDocument.mimeType

        val extension = when {
            rawMime?.contains("jpeg", true) == true || rawMime?.contains("jpg", true) == true -> "jpg"
            rawMime?.contains("png", true) == true -> "png"
            rawMime?.contains("pdf", true) == true -> "pdf"
            rawMime?.contains("msword", true) == true -> "doc"
            rawMime?.contains("wordprocessingml", true) == true -> "docx"
            else -> displayName.substringAfterLast('.', "").lowercase()
        }

        val imageExtensions = listOf("jpg", "jpeg", "png")
        val documentExtensions = listOf("pdf", "doc", "docx")

        val maxSizeBytes = when {
            imageExtensions.contains(extension) -> 5 * 1024 * 1024L
            documentExtensions.contains(extension) -> 10 * 1024 * 1024L
            else -> {
                Log.e(storageTag, "upload failed: invalid extension or mime ($extension / $rawMime)")
                return Result.failure(Exception("Format file tidak didukung. Gunakan JPG, PNG, PDF, DOC, atau DOCX."))
            }
        }

        val sizeErrorMessage = if (imageExtensions.contains(extension)) {
            "Ukuran gambar melebihi batas maksimal 5 MB."
        } else {
            "Ukuran dokumen melebihi batas maksimal 10 MB."
        }

        val rawSize = selectedDocument.fileSize
        if (rawSize > maxSizeBytes) {
            Log.e(storageTag, "upload failed: file size exceeds limit ($rawSize > $maxSizeBytes bytes)")
            return Result.failure(Exception(sizeErrorMessage))
        }

        val fileBytes = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            }
        } catch (e: Exception) {
            Log.e(storageTag, "upload failed: unable to read Uri stream (${e.message})")
            null
        }

        if (fileBytes == null || fileBytes.isEmpty()) {
            Log.e(storageTag, "upload failed: file content is empty or unreadable")
            return Result.failure(Exception("Gagal membaca berkas file."))
        }

        if (fileBytes.size > maxSizeBytes) {
            Log.e(storageTag, "upload failed: read file size exceeds limit (${fileBytes.size} > $maxSizeBytes bytes)")
            return Result.failure(Exception(sizeErrorMessage))
        }

        val targetPath = "$currentUuid/$submissionCode/${documentSlot}_${System.currentTimeMillis()}.$extension"
        Log.d(storageTag, "target path: $targetPath")

        return try {
            val bucket = storage.from("siapel-documents")
            bucket.upload(targetPath, fileBytes) {
                upsert = false
            }
            Log.d(storageTag, "upload success for target path: $targetPath")
            Result.success(targetPath)
        } catch (e: Exception) {
            Log.e(storageTag, "upload failed for target path $targetPath: ${e.message}", e)
            Result.failure(Exception("Gagal mengunggah dokumen ke penyimpanan cloud: ${e.message}"))
        }
    }

    // ==========================================================
    // REMOTE KIA SECTION
    // ==========================================================

    suspend fun fetchKiaDetailRemote(
        submissionCode: String,
        currentUuid: String
    ): Result<KiaDetailRemoteDto> {
        Log.d(TAG, "remote kia_detail fetch start for submissionCode $submissionCode")
        return try {
            val dto = postgrest.from("applications").select(Columns.raw("*, kia_detail(*)")) {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentUuid)
                    eq("service_type", "KIA")
                }
            }.decodeSingle<ApplicationWithKiaDetailRemoteDto>()

            val kiaDetail = dto.kiaDetail
            if (kiaDetail != null) {
                Log.d(TAG, "remote kia_detail fetch success for submissionCode $submissionCode")
                Result.success(kiaDetail)
            } else {
                Log.e(TAG, "remote kia_detail fetch failed: kia_detail is null")
                Result.failure(Exception("Detail dokumen KIA belum tersedia."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "remote kia_detail fetch failed for code $submissionCode: ${e.message}", e)
            Result.failure(Exception("Gagal mengambil detail dokumen dari server: ${e.message}"))
        }
    }

    fun getKiaDocumentPathBySlot(
        kiaDetail: KiaDetailRemoteDto,
        documentSlot: String
    ): Result<String> {
        val validSlots = listOf("doc1", "doc2", "doc3", "docSelfie")
        if (!validSlots.contains(documentSlot)) {
            return Result.failure(Exception("Slot dokumen tidak valid."))
        }

        val path = when (documentSlot) {
            "doc1" -> kiaDetail.doc1Path
            "doc2" -> kiaDetail.doc2Path
            "doc3" -> kiaDetail.doc3Path
            "docSelfie" -> kiaDetail.docSelfiePath
            else -> null
        }

        return if (!path.isNullOrBlank()) {
            Result.success(path)
        } else {
            Result.failure(Exception("Dokumen belum tersedia."))
        }
    }

    suspend fun createKiaApplicationRemote(
        context: Context,
        application: ApplicationEntity,
        currentUuid: String,
        doc1: SelectedDocument? = null,
        doc2: SelectedDocument? = null,
        doc3: SelectedDocument? = null,
        docSelfie: SelectedDocument? = null
    ): Boolean {
        Log.d(TAG, "KIA remote create start for code ${application.submissionCode}")
        var insertedAppId: String? = null

        return try {
            val appDto = ApplicationRemoteDto(
                userId = currentUuid,
                serviceType = application.serviceType,
                category = application.category,
                submissionCode = application.submissionCode,
                submissionDate = application.submissionDate,
                status = application.status.name,
                note = application.note,
                nikPelapor = application.nikPelapor,
                namaPelapor = application.namaPelapor,
                email = application.email,
                whatsapp = application.whatsapp,
                kecamatan = application.kecamatan,
                kelurahan = application.kelurahan,
                keterangan = application.keterangan
            )

            val insertedApp = postgrest.from("applications").insert(appDto) {
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = insertedApp.id
            if (remoteAppId.isNullOrEmpty()) {
                Log.e(TAG, "Remote create failed: inserted application ID is null")
                return false
            }
            insertedAppId = remoteAppId
            Log.d(TAG, "Application remote insert success with ID: $remoteAppId")

            var doc1Path: String? = null
            if (doc1 != null) {
                val res1 = uploadKiaDocumentRemote(context, doc1, currentUuid, application.submissionCode, "doc1")
                if (res1.isFailure) {
                    cleanupRemoteApplication(application.submissionCode, currentUuid)
                    return false
                }
                doc1Path = res1.getOrNull()
            }

            var doc2Path: String? = null
            if (doc2 != null) {
                val res2 = uploadKiaDocumentRemote(context, doc2, currentUuid, application.submissionCode, "doc2")
                if (res2.isFailure) {
                    cleanupRemoteApplication(application.submissionCode, currentUuid)
                    return false
                }
                doc2Path = res2.getOrNull()
            }

            var doc3Path: String? = null
            if (doc3 != null) {
                val res3 = uploadKiaDocumentRemote(context, doc3, currentUuid, application.submissionCode, "doc3")
                if (res3.isFailure) {
                    cleanupRemoteApplication(application.submissionCode, currentUuid)
                    return false
                }
                doc3Path = res3.getOrNull()
            }

            var docSelfiePath: String? = null
            if (docSelfie != null) {
                val resSelfie = uploadKiaDocumentRemote(context, docSelfie, currentUuid, application.submissionCode, "docSelfie")
                if (resSelfie.isFailure) {
                    cleanupRemoteApplication(application.submissionCode, currentUuid)
                    return false
                }
                docSelfiePath = resSelfie.getOrNull()
            }

            val kiaDetailDto = KiaDetailRemoteDto(
                applicationId = remoteAppId,
                nikAnak = application.nikAnak?.ifEmpty { null },
                namaAnak = application.namaAnak?.ifEmpty { null },
                doc1Path = doc1Path,
                doc2Path = doc2Path,
                doc3Path = doc3Path,
                docSelfiePath = docSelfiePath
            )
            try {
                postgrest.from("kia_detail").insert(kiaDetailDto)
                Log.d(TAG, "Kia detail remote insert success for application ID $remoteAppId with storage paths")
            } catch (_: Exception) {
                cleanupRemoteApplication(application.submissionCode, currentUuid)
                return false
            }

            applicationDao.insertApplication(application)
            Log.d(TAG, "Room insert success for code ${application.submissionCode}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Remote create failed for code ${application.submissionCode}: ${e.message}", e)
            if (insertedAppId != null) {
                cleanupRemoteApplication(application.submissionCode, currentUuid)
            }
            false
        }
    }

    suspend fun syncKiaApplicationsRemote(currentUuid: String, localUserId: Int): Boolean {
        Log.d(TAG, "Remote sync start for user $currentUuid")
        return try {
            val remoteApps = postgrest.from("applications").select(Columns.raw("*, kia_detail(*)")) {
                filter {
                    eq("user_id", currentUuid)
                    eq("service_type", "KIA")
                }
            }.decodeList<ApplicationWithKiaDetailRemoteDto>()

            Log.d(TAG, "Remote sync success. Jumlah data diterima: ${remoteApps.size}")

            for (dto in remoteApps) {
                val existingLocal = applicationDao.getApplicationByCode(dto.submissionCode)
                val statusEnum = try {
                    ApplicationStatus.valueOf(dto.status)
                } catch (_: Exception) {
                    ApplicationStatus.SUBMITTED
                }
                val kia = dto.kiaDetail

                val entity = ApplicationEntity(
                    id = existingLocal?.id ?: 0,
                    userId = localUserId,
                    serviceType = dto.serviceType,
                    category = dto.category,
                    submissionCode = dto.submissionCode,
                    submissionDate = dto.submissionDate,
                    status = statusEnum,
                    note = dto.note ?: existingLocal?.note,
                    kecamatan = dto.kecamatan,
                    kelurahan = dto.kelurahan,
                    email = dto.email,
                    whatsapp = dto.whatsapp,
                    keterangan = dto.keterangan,
                    nikPelapor = dto.nikPelapor,
                    namaPelapor = dto.namaPelapor,
                    nikAnak = kia?.nikAnak ?: existingLocal?.nikAnak,
                    namaAnak = kia?.namaAnak ?: existingLocal?.namaAnak,
                    doc1 = existingLocal?.doc1,
                    doc2 = existingLocal?.doc2,
                    doc3 = existingLocal?.doc3,
                    docSelfie = existingLocal?.docSelfie,
                    createdAt = existingLocal?.createdAt ?: System.currentTimeMillis()
                )

                if (existingLocal != null) {
                    applicationDao.updateApplication(entity)
                } else {
                    applicationDao.insertApplication(entity)
                }
            }
            Log.d(TAG, "Room sync success")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Remote sync failed for user $currentUuid: ${e.message}", e)
            false
        }
    }

    suspend fun updateKiaApplicationRemoteWithDocuments(
        context: Context,
        application: ApplicationEntity,
        currentUuid: String,
        newDoc1: SelectedDocument? = null,
        newDoc2: SelectedDocument? = null,
        newDoc3: SelectedDocument? = null,
        newDocSelfie: SelectedDocument? = null
    ): Boolean {
        val editTag = "SIAPEL_EDIT_KIA"
        Log.d(editTag, "edit remote start for code ${application.submissionCode}")

        return try {
            val oldDetailResult = fetchKiaDetailRemote(application.submissionCode, currentUuid)
            if (oldDetailResult.isFailure) {
                return false
            }
            val oldDetail = oldDetailResult.getOrThrow()

            val oldDoc1Path = oldDetail.doc1Path
            val oldDoc2Path = oldDetail.doc2Path
            val oldDoc3Path = oldDetail.doc3Path
            val oldDocSelfiePath = oldDetail.docSelfiePath

            val isKiaPlusKkCategory = isKiaPlusKk(application.category)

            val isDoc1Replaced = !isKiaPlusKkCategory && isNewSelectedDocument(newDoc1)
            val isDoc2Replaced = !isKiaPlusKkCategory && isNewSelectedDocument(newDoc2)
            val isDoc3Replaced = !isKiaPlusKkCategory && isNewSelectedDocument(newDoc3)
            val isDocSelfieReplaced = !isKiaPlusKkCategory && isNewSelectedDocument(newDocSelfie)

            val newlyUploadedPaths = mutableListOf<String>()

            val finalDoc1Path = when {
                isKiaPlusKkCategory -> null
                isDoc1Replaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDoc1!!, currentUuid, application.submissionCode, "doc1")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc1Path
            }

            val finalDoc2Path = when {
                isKiaPlusKkCategory -> null
                isDoc2Replaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDoc2!!, currentUuid, application.submissionCode, "doc2")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc2Path
            }

            val finalDoc3Path = when {
                isKiaPlusKkCategory || application.category.contains("RUSAK", ignoreCase = true) || application.category.contains("HILANG", ignoreCase = true) -> null
                isDoc3Replaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDoc3!!, currentUuid, application.submissionCode, "doc3")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc3Path
            }

            val finalDocSelfiePath = when {
                isKiaPlusKkCategory -> null
                isDocSelfieReplaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDocSelfie!!, currentUuid, application.submissionCode, "docSelfie")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDocSelfiePath
            }

            val appDto = ApplicationRemoteDto(
                userId = currentUuid,
                serviceType = application.serviceType,
                category = application.category,
                submissionCode = application.submissionCode,
                submissionDate = application.submissionDate,
                status = application.status.name,
                note = application.note,
                nikPelapor = application.nikPelapor,
                namaPelapor = application.namaPelapor,
                email = application.email,
                whatsapp = application.whatsapp,
                kecamatan = application.kecamatan,
                kelurahan = application.kelurahan,
                keterangan = application.keterangan
            )

            val updatedApp = postgrest.from("applications").update(appDto) {
                filter {
                    eq("submission_code", application.submissionCode)
                    eq("user_id", currentUuid)
                }
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = updatedApp.id
            if (remoteAppId.isNullOrEmpty()) {
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }

            val kiaDetailDto = KiaDetailRemoteDto(
                applicationId = remoteAppId,
                nikAnak = application.nikAnak?.ifEmpty { null },
                namaAnak = application.namaAnak?.ifEmpty { null },
                doc1Path = finalDoc1Path,
                doc2Path = finalDoc2Path,
                doc3Path = finalDoc3Path,
                docSelfiePath = finalDocSelfiePath
            )
            try {
                postgrest.from("kia_detail").update(kiaDetailDto) {
                    filter {
                        eq("application_id", remoteAppId)
                    }
                }
            } catch (_: Exception) {
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }

            if (isDoc1Replaced && !oldDoc1Path.isNullOrBlank() && oldDoc1Path != finalDoc1Path) {
                deleteSingleStorageFileBestEffort(oldDoc1Path, "doc1", editTag)
            }
            if (isDoc2Replaced && !oldDoc2Path.isNullOrBlank() && oldDoc2Path != finalDoc2Path) {
                deleteSingleStorageFileBestEffort(oldDoc2Path, "doc2", editTag)
            }
            if (isDoc3Replaced && !oldDoc3Path.isNullOrBlank() && oldDoc3Path != finalDoc3Path) {
                deleteSingleStorageFileBestEffort(oldDoc3Path, "doc3", editTag)
            }
            if (isDocSelfieReplaced && !oldDocSelfiePath.isNullOrBlank() && oldDocSelfiePath != finalDocSelfiePath) {
                deleteSingleStorageFileBestEffort(oldDocSelfiePath, "docSelfie", editTag)
            }

            applicationDao.updateApplication(application)
            true
        } catch (e: Exception) {
            Log.e(editTag, "edit remote failed for code ${application.submissionCode}: ${e.message}", e)
            false
        }
    }

    suspend fun updateKiaApplicationRemote(application: ApplicationEntity, currentUuid: String): Boolean {
        return try {
            val appDto = ApplicationRemoteDto(
                userId = currentUuid,
                serviceType = application.serviceType,
                category = application.category,
                submissionCode = application.submissionCode,
                submissionDate = application.submissionDate,
                status = application.status.name,
                note = application.note,
                nikPelapor = application.nikPelapor,
                namaPelapor = application.namaPelapor,
                email = application.email,
                whatsapp = application.whatsapp,
                kecamatan = application.kecamatan,
                kelurahan = application.kelurahan,
                keterangan = application.keterangan
            )

            val updatedApp = postgrest.from("applications").update(appDto) {
                filter {
                    eq("submission_code", application.submissionCode)
                    eq("user_id", currentUuid)
                }
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = updatedApp.id
            if (remoteAppId.isNullOrEmpty()) return false

            val kiaDetailDto = KiaDetailRemoteDto(
                applicationId = remoteAppId,
                nikAnak = application.nikAnak?.ifEmpty { null },
                namaAnak = application.namaAnak?.ifEmpty { null }
            )
            try {
                postgrest.from("kia_detail").update(kiaDetailDto) {
                    filter {
                        eq("application_id", remoteAppId)
                    }
                }
            } catch (_: Exception) {
                return false
            }

            applicationDao.updateApplication(application)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteKiaApplicationRemote(submissionCode: String, currentSupabaseUuid: String): Boolean {
        val isDbDeleteSuccess = try {
            postgrest.from("applications").delete {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentSupabaseUuid)
                }
            }
            true
        } catch (_: Exception) {
            false
        }

        if (!isDbDeleteSuccess) return false

        val storageTag = "SIAPEL_STORAGE"
        val folderPath = "$currentSupabaseUuid/$submissionCode"

        try {
            val bucket = storage.from("siapel-documents")
            val files = bucket.list(folderPath)
            if (files.isNotEmpty()) {
                val fullPaths = files.map { file -> "$folderPath/${file.name}" }
                bucket.delete(fullPaths)
            }
        } catch (storageEx: Exception) {
            Log.e(storageTag, "storage cleanup failed for folder $folderPath: ${storageEx.message}", storageEx)
        }

        return true
    }

    // ==========================================================
    // REMOTE KTP SECTION (TAHAP 5 - 8)
    // ==========================================================

    suspend fun createKtpApplicationRemote(
        context: Context,
        application: ApplicationEntity,
        currentSupabaseUuid: String,
        doc1: SelectedDocument? = null,
        doc2: SelectedDocument? = null,
        docSelfie: SelectedDocument? = null
    ): Boolean {
        Log.d(TAG, "KTP remote create start for code ${application.submissionCode}")
        var insertedAppId: String? = null

        return try {
            val appDto = ApplicationRemoteDto(
                userId = currentSupabaseUuid,
                serviceType = application.serviceType,
                category = application.category,
                submissionCode = application.submissionCode,
                submissionDate = application.submissionDate,
                status = application.status.name,
                note = application.note,
                nikPelapor = application.nikPelapor,
                namaPelapor = application.namaPelapor,
                email = application.email,
                whatsapp = application.whatsapp,
                kecamatan = application.kecamatan,
                kelurahan = application.kelurahan,
                keterangan = application.keterangan
            )

            val insertedApp = postgrest.from("applications").insert(appDto) {
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = insertedApp.id
            if (remoteAppId.isNullOrEmpty()) {
                Log.e(TAG, "Remote KTP create failed: inserted application ID is null")
                return false
            }
            insertedAppId = remoteAppId
            Log.d(TAG, "KTP Application remote insert success with ID: $remoteAppId")

            var doc1Path: String? = null
            if (doc1 != null) {
                Log.d("SIAPEL_STORAGE", "Uploading doc1 for KTP code ${application.submissionCode}")
                val res1 = uploadKiaDocumentRemote(context, doc1, currentSupabaseUuid, application.submissionCode, "doc1")
                if (res1.isFailure) {
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc1Path = res1.getOrNull()
            }

            var doc2Path: String? = null
            if (doc2 != null) {
                Log.d("SIAPEL_STORAGE", "Uploading doc2 for KTP code ${application.submissionCode}")
                val res2 = uploadKiaDocumentRemote(context, doc2, currentSupabaseUuid, application.submissionCode, "doc2")
                if (res2.isFailure) {
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc2Path = res2.getOrNull()
            }

            var docSelfiePath: String? = null
            if (docSelfie != null) {
                Log.d("SIAPEL_STORAGE", "Uploading docSelfie for KTP code ${application.submissionCode}")
                val resSelfie = uploadKiaDocumentRemote(context, docSelfie, currentSupabaseUuid, application.submissionCode, "docSelfie")
                if (resSelfie.isFailure) {
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                docSelfiePath = resSelfie.getOrNull()
            }

            val ktpDetailDto = KtpDetailRemoteDto(
                applicationId = remoteAppId,
                doc1Path = doc1Path,
                doc2Path = doc2Path,
                docSelfiePath = docSelfiePath
            )
            try {
                postgrest.from("ktp_detail").insert(ktpDetailDto)
                Log.d(TAG, "Ktp detail remote insert success for application ID $remoteAppId with storage paths")
            } catch (_: Exception) {
                cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                return false
            }

            applicationDao.insertApplication(application)
            Log.d(TAG, "Room insert success for KTP code ${application.submissionCode}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Remote KTP create failed for code ${application.submissionCode}: ${e.message}", e)
            if (insertedAppId != null) {
                cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
            }
            false
        }
    }

    suspend fun syncKtpApplicationsRemote(currentUuid: String, localUserId: Int): Boolean {
        Log.d(TAG, "KTP remote sync start")
        return try {
            val remoteApps = postgrest.from("applications").select(Columns.raw("*, ktp_detail(*)")) {
                filter {
                    eq("user_id", currentUuid)
                    eq("service_type", "KTP")
                }
            }.decodeList<ApplicationWithKtpDetailRemoteDto>()

            Log.d(TAG, "KTP remote sync success count: ${remoteApps.size}")

            for (dto in remoteApps) {
                val existingLocal = applicationDao.getApplicationByCode(dto.submissionCode)
                val statusEnum = try {
                    ApplicationStatus.valueOf(dto.status)
                } catch (_: Exception) {
                    ApplicationStatus.SUBMITTED
                }
                val ktp = dto.ktpDetail

                val entity = ApplicationEntity(
                    id = existingLocal?.id ?: 0,
                    userId = localUserId,
                    serviceType = dto.serviceType,
                    category = dto.category,
                    submissionCode = dto.submissionCode,
                    submissionDate = dto.submissionDate,
                    status = statusEnum,
                    note = dto.note ?: existingLocal?.note,
                    kecamatan = dto.kecamatan,
                    kelurahan = dto.kelurahan,
                    email = dto.email,
                    whatsapp = dto.whatsapp,
                    keterangan = dto.keterangan,
                    nikPelapor = dto.nikPelapor,
                    namaPelapor = dto.namaPelapor,
                    doc1 = ktp?.doc1Path ?: existingLocal?.doc1,
                    doc2 = ktp?.doc2Path ?: existingLocal?.doc2,
                    docSelfie = ktp?.docSelfiePath ?: existingLocal?.docSelfie,
                    createdAt = existingLocal?.createdAt ?: System.currentTimeMillis()
                )

                if (existingLocal != null) {
                    applicationDao.updateApplication(entity)
                } else {
                    applicationDao.insertApplication(entity)
                }
            }
            Log.d(TAG, "Room sync success for KTP")
            true
        } catch (e: Exception) {
            Log.e(TAG, "KTP remote sync failed for user $currentUuid: ${e.message}", e)
            false
        }
    }

    suspend fun fetchKtpDetailRemote(
        submissionCode: String,
        currentUuid: String
    ): Result<KtpDetailRemoteDto> {
        Log.d(TAG, "KTP fetch detail start for code $submissionCode")
        return try {
            val dto = postgrest.from("applications").select(Columns.raw("*, ktp_detail(*)")) {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentUuid)
                    eq("service_type", "KTP")
                }
            }.decodeSingle<ApplicationWithKtpDetailRemoteDto>()

            val ktpDetail = dto.ktpDetail
            if (ktpDetail != null) {
                Log.d(TAG, "KTP fetch detail success for code $submissionCode")
                Result.success(ktpDetail)
            } else {
                Log.e(TAG, "KTP fetch detail failed: ktp_detail is null")
                Result.failure(Exception("Detail dokumen KTP belum tersedia."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "KTP fetch detail failed for code $submissionCode: ${e.message}", e)
            Result.failure(Exception("Gagal mengambil detail dokumen KTP dari server: ${e.message}"))
        }
    }

    fun getKtpDocumentPathBySlot(
        ktpDetail: KtpDetailRemoteDto,
        documentSlot: String
    ): Result<String> {
        val validSlots = listOf("doc1", "doc2", "docSelfie")
        if (!validSlots.contains(documentSlot)) {
            return Result.failure(Exception("Slot dokumen KTP tidak valid."))
        }

        val path = when (documentSlot) {
            "doc1" -> ktpDetail.doc1Path
            "doc2" -> ktpDetail.doc2Path
            "docSelfie" -> ktpDetail.docSelfiePath
            else -> null
        }

        return if (!path.isNullOrBlank()) {
            Result.success(path)
        } else {
            Result.failure(Exception("Dokumen KTP belum tersedia."))
        }
    }

    suspend fun updateKtpApplicationRemoteWithDocuments(
        context: Context,
        application: ApplicationEntity,
        currentUuid: String,
        newDoc1: SelectedDocument? = null,
        newDoc2: SelectedDocument? = null,
        newDocSelfie: SelectedDocument? = null
    ): Boolean {
        val editTag = "SIAPEL_EDIT_KTP"
        Log.d(editTag, "KTP remote update start for code ${application.submissionCode}")

        return try {
            val oldDetailResult = fetchKtpDetailRemote(application.submissionCode, currentUuid)
            if (oldDetailResult.isFailure) {
                Log.e(editTag, "KTP fetch old detail failed for code ${application.submissionCode}")
                return false
            }
            val oldDetail = oldDetailResult.getOrThrow()

            val oldDoc1Path = oldDetail.doc1Path
            val oldDoc2Path = oldDetail.doc2Path
            val oldDocSelfiePath = oldDetail.docSelfiePath

            val isPerekaman = application.category.contains("PEREKAMAN", ignoreCase = true)

            val isDoc1Replaced = isNewSelectedDocument(newDoc1)
            val isDoc2Replaced = !isPerekaman && isNewSelectedDocument(newDoc2)
            val isDocSelfieReplaced = isNewSelectedDocument(newDocSelfie)

            val newlyUploadedPaths = mutableListOf<String>()

            val finalDoc1Path = when {
                isDoc1Replaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDoc1!!, currentUuid, application.submissionCode, "doc1")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc1Path
            }

            val finalDoc2Path = when {
                isPerekaman -> null
                isDoc2Replaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDoc2!!, currentUuid, application.submissionCode, "doc2")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc2Path
            }

            val finalDocSelfiePath = when {
                isDocSelfieReplaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDocSelfie!!, currentUuid, application.submissionCode, "docSelfie")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDocSelfiePath
            }

            val appDto = ApplicationRemoteDto(
                userId = currentUuid,
                serviceType = application.serviceType,
                category = application.category,
                submissionCode = application.submissionCode,
                submissionDate = application.submissionDate,
                status = application.status.name,
                note = application.note,
                nikPelapor = application.nikPelapor,
                namaPelapor = application.namaPelapor,
                email = application.email,
                whatsapp = application.whatsapp,
                kecamatan = application.kecamatan,
                kelurahan = application.kelurahan,
                keterangan = application.keterangan
            )

            val updatedApp = postgrest.from("applications").update(appDto) {
                filter {
                    eq("submission_code", application.submissionCode)
                    eq("user_id", currentUuid)
                }
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = updatedApp.id
            if (remoteAppId.isNullOrEmpty()) {
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }

            val ktpDetailDto = KtpDetailRemoteDto(
                applicationId = remoteAppId,
                doc1Path = finalDoc1Path,
                doc2Path = finalDoc2Path,
                docSelfiePath = finalDocSelfiePath
            )
            try {
                postgrest.from("ktp_detail").update(ktpDetailDto) {
                    filter {
                        eq("application_id", remoteAppId)
                    }
                }
                Log.d(editTag, "KTP remote update success for code ${application.submissionCode}")
            } catch (_: Exception) {
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }

            if (isDoc1Replaced && !oldDoc1Path.isNullOrBlank() && oldDoc1Path != finalDoc1Path) {
                deleteSingleStorageFileBestEffort(oldDoc1Path, "doc1", editTag)
            }
            if (isDoc2Replaced && !oldDoc2Path.isNullOrBlank() && oldDoc2Path != finalDoc2Path) {
                deleteSingleStorageFileBestEffort(oldDoc2Path, "doc2", editTag)
            }
            if (isDocSelfieReplaced && !oldDocSelfiePath.isNullOrBlank() && oldDocSelfiePath != finalDocSelfiePath) {
                deleteSingleStorageFileBestEffort(oldDocSelfiePath, "docSelfie", editTag)
            }

            applicationDao.updateApplication(application)
            true
        } catch (e: Exception) {
            Log.e(editTag, "KTP remote update failed for code ${application.submissionCode}: ${e.message}", e)
            false
        }
    }

    suspend fun deleteKtpApplicationRemote(submissionCode: String, currentSupabaseUuid: String): Boolean {
        Log.d(TAG, "KTP remote delete start for code $submissionCode")
        val isDbDeleteSuccess = try {
            postgrest.from("applications").delete {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentSupabaseUuid)
                    eq("service_type", "KTP")
                }
            }
            Log.d(TAG, "KTP remote delete success for code $submissionCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "KTP remote delete failed for code $submissionCode: ${e.message}")
            false
        }

        if (!isDbDeleteSuccess) return false

        val storageTag = "SIAPEL_STORAGE"
        val folderPath = "$currentSupabaseUuid/$submissionCode"
        Log.d(storageTag, "KTP storage cleanup start for folder $folderPath")

        try {
            val bucket = storage.from("siapel-documents")
            val files = bucket.list(folderPath)
            if (files.isNotEmpty()) {
                val fullPaths = files.map { file -> "$folderPath/${file.name}" }
                bucket.delete(fullPaths)
                Log.d(storageTag, "KTP storage cleanup success")
            }
        } catch (storageEx: Exception) {
            Log.e(storageTag, "KTP storage cleanup failed for folder $folderPath: ${storageEx.message}", storageEx)
        }

        return true
    }

    // ==========================================================
    // REMOTE KK SECTION (TAHAP 3B - 6)
    // ==========================================================

    suspend fun createKkApplicationRemote(
        context: Context,
        application: ApplicationEntity,
        currentSupabaseUuid: String,
        doc1: SelectedDocument? = null,
        doc2: SelectedDocument? = null,
        doc3: SelectedDocument? = null,
        doc4: SelectedDocument? = null,
        doc5: SelectedDocument? = null,
        doc6: SelectedDocument? = null,
        doc7: SelectedDocument? = null,
        doc8: SelectedDocument? = null,
        doc9: SelectedDocument? = null,
        doc10: SelectedDocument? = null,
        docSelfie: SelectedDocument? = null
    ): Boolean {
        Log.d(TAG, "KK remote create start for code ${application.submissionCode}")
        var insertedAppId: String? = null
        val uploadedPaths = mutableListOf<String>()

        return try {
            val appDto = ApplicationRemoteDto(
                userId = currentSupabaseUuid,
                serviceType = application.serviceType,
                category = application.category,
                submissionCode = application.submissionCode,
                submissionDate = application.submissionDate,
                status = application.status.name,
                note = application.note,
                nikPelapor = application.nikPelapor,
                namaPelapor = application.namaPelapor,
                email = application.email,
                whatsapp = application.whatsapp,
                kecamatan = application.kecamatan,
                kelurahan = application.kelurahan,
                keterangan = application.keterangan
            )

            val insertedApp = postgrest.from("applications").insert(appDto) {
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = insertedApp.id
            if (remoteAppId.isNullOrEmpty()) {
                Log.e(TAG, "KK remote create failed: inserted application ID is null")
                return false
            }
            insertedAppId = remoteAppId
            Log.d(TAG, "KK applications remote insert success id $remoteAppId")

            var doc1Path: String? = null
            if (doc1 != null) {
                val res = uploadKkDocumentRemote(context, doc1, currentSupabaseUuid, application.submissionCode, "doc1")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc1Path = res.getOrNull()
                doc1Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc1 success path $doc1Path")
            }

            var doc2Path: String? = null
            if (doc2 != null) {
                val res = uploadKkDocumentRemote(context, doc2, currentSupabaseUuid, application.submissionCode, "doc2")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc2Path = res.getOrNull()
                doc2Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc2 success path $doc2Path")
            }

            var doc3Path: String? = null
            if (doc3 != null) {
                val res = uploadKkDocumentRemote(context, doc3, currentSupabaseUuid, application.submissionCode, "doc3")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc3Path = res.getOrNull()
                doc3Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc3 success path $doc3Path")
            }

            var doc4Path: String? = null
            if (doc4 != null) {
                val res = uploadKkDocumentRemote(context, doc4, currentSupabaseUuid, application.submissionCode, "doc4")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc4Path = res.getOrNull()
                doc4Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc4 success path $doc4Path")
            }

            var doc5Path: String? = null
            if (doc5 != null) {
                val res = uploadKkDocumentRemote(context, doc5, currentSupabaseUuid, application.submissionCode, "doc5")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc5Path = res.getOrNull()
                doc5Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc5 success path $doc5Path")
            }

            var doc6Path: String? = null
            if (doc6 != null) {
                val res = uploadKkDocumentRemote(context, doc6, currentSupabaseUuid, application.submissionCode, "doc6")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc6Path = res.getOrNull()
                doc6Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc6 success path $doc6Path")
            }

            var doc7Path: String? = null
            if (doc7 != null) {
                val res = uploadKkDocumentRemote(context, doc7, currentSupabaseUuid, application.submissionCode, "doc7")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc7Path = res.getOrNull()
                doc7Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc7 success path $doc7Path")
            }

            var doc8Path: String? = null
            if (doc8 != null) {
                val res = uploadKkDocumentRemote(context, doc8, currentSupabaseUuid, application.submissionCode, "doc8")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc8Path = res.getOrNull()
                doc8Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc8 success path $doc8Path")
            }

            var doc9Path: String? = null
            if (doc9 != null) {
                val res = uploadKkDocumentRemote(context, doc9, currentSupabaseUuid, application.submissionCode, "doc9")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc9Path = res.getOrNull()
                doc9Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc9 success path $doc9Path")
            }

            var doc10Path: String? = null
            if (doc10 != null) {
                val res = uploadKkDocumentRemote(context, doc10, currentSupabaseUuid, application.submissionCode, "doc10")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc10Path = res.getOrNull()
                doc10Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload doc10 success path $doc10Path")
            }

            var docSelfiePath: String? = null
            if (docSelfie != null) {
                val res = uploadKkDocumentRemote(context, docSelfie, currentSupabaseUuid, application.submissionCode, "docSelfie")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                docSelfiePath = res.getOrNull()
                docSelfiePath?.let { uploadedPaths.add(it) }
                Log.d(TAG, "KK upload docSelfie success path $docSelfiePath")
            }

            val kkDetailDto = KkDetailRemoteDto(
                applicationId = remoteAppId,
                nikAnak = application.nikAnak?.ifEmpty { null },
                namaAnak = application.namaAnak?.ifEmpty { null },
                nomorKk = application.nomorKk?.ifEmpty { null },
                doc1Path = doc1Path,
                doc2Path = doc2Path,
                doc3Path = doc3Path,
                doc4Path = doc4Path,
                doc5Path = doc5Path,
                doc6Path = doc6Path,
                doc7Path = doc7Path,
                doc8Path = doc8Path,
                doc9Path = doc9Path,
                doc10Path = doc10Path,
                docSelfiePath = docSelfiePath
            )

            try {
                postgrest.from("kk_detail").insert(kkDetailDto)
                Log.d(TAG, "KK detail remote insert success")
            } catch (e: Exception) {
                Log.e(TAG, "KK detail remote insert failed: ${e.message}", e)
                cleanupNewlyUploadedFiles(uploadedPaths)
                cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
                return false
            }

            applicationDao.insertApplication(application)
            Log.d(TAG, "KK Room insert success for code ${application.submissionCode}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "KK remote create failed for code ${application.submissionCode}: ${e.message}", e)
            cleanupNewlyUploadedFiles(uploadedPaths)
            if (insertedAppId != null) {
                cleanupRemoteApplication(application.submissionCode, currentSupabaseUuid)
            }
            false
        }
    }

    suspend fun syncKkApplicationsRemote(currentUuid: String, localUserId: Int): Boolean {
        Log.d(TAG, "KK remote sync start")
        return try {
            val remoteApps = postgrest.from("applications").select(Columns.raw("*, kk_detail(*)")) {
                filter {
                    eq("user_id", currentUuid)
                    eq("service_type", "KK")
                }
            }.decodeList<ApplicationWithKkDetailRemoteDto>()

            Log.d(TAG, "KK remote sync success count: ${remoteApps.size}")

            for (dto in remoteApps) {
                val existingLocal = applicationDao.getApplicationByCode(dto.submissionCode)
                val statusEnum = try {
                    ApplicationStatus.valueOf(dto.status)
                } catch (_: Exception) {
                    ApplicationStatus.SUBMITTED
                }
                val kk = dto.kkDetail

                val entity = ApplicationEntity(
                    id = existingLocal?.id ?: 0,
                    userId = localUserId,
                    serviceType = dto.serviceType,
                    category = dto.category,
                    submissionCode = dto.submissionCode,
                    submissionDate = dto.submissionDate,
                    status = statusEnum,
                    note = dto.note ?: existingLocal?.note,
                    kecamatan = dto.kecamatan,
                    kelurahan = dto.kelurahan,
                    email = dto.email,
                    whatsapp = dto.whatsapp,
                    keterangan = dto.keterangan,
                    nikPelapor = dto.nikPelapor,
                    namaPelapor = dto.namaPelapor,
                    nikAnak = kk?.nikAnak ?: existingLocal?.nikAnak,
                    namaAnak = kk?.namaAnak ?: existingLocal?.namaAnak,
                    nomorKk = kk?.nomorKk ?: existingLocal?.nomorKk,
                    doc1 = existingLocal?.doc1,
                    doc2 = existingLocal?.doc2,
                    doc3 = existingLocal?.doc3,
                    doc4 = existingLocal?.doc4,
                    doc5 = existingLocal?.doc5,
                    doc6 = existingLocal?.doc6,
                    doc7 = existingLocal?.doc7,
                    doc8 = existingLocal?.doc8,
                    doc9 = existingLocal?.doc9,
                    doc10 = existingLocal?.doc10,
                    docSelfie = existingLocal?.docSelfie,
                    createdAt = existingLocal?.createdAt ?: System.currentTimeMillis()
                )

                if (existingLocal != null) {
                    applicationDao.updateApplication(entity)
                } else {
                    applicationDao.insertApplication(entity)
                }
            }
            Log.d(TAG, "Room sync success for KK")
            true
        } catch (e: Exception) {
            Log.e(TAG, "KK remote sync failed for user $currentUuid: ${e.message}", e)
            false
        }
    }

    suspend fun fetchKkDetailRemote(
        submissionCode: String,
        currentUuid: String
    ): Result<KkDetailRemoteDto> {
        Log.d(TAG, "KK fetch detail start for code $submissionCode")
        return try {
            val dto = postgrest.from("applications").select(Columns.raw("*, kk_detail(*)")) {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentUuid)
                    eq("service_type", "KK")
                }
            }.decodeSingle<ApplicationWithKkDetailRemoteDto>()

            val kkDetail = dto.kkDetail
            if (kkDetail != null) {
                Log.d(TAG, "KK fetch detail success for code $submissionCode")
                Result.success(kkDetail)
            } else {
                Log.e(TAG, "KK fetch detail failed: kk_detail is null")
                Result.failure(Exception("Detail dokumen KK belum tersedia."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "KK fetch detail failed for code $submissionCode: ${e.message}", e)
            Result.failure(Exception("Gagal mengambil detail dokumen KK dari server: ${e.message}"))
        }
    }

    fun getKkDocumentPathBySlot(
        kkDetail: KkDetailRemoteDto,
        documentSlot: String
    ): Result<String> {
        val validSlots = listOf(
            "doc1", "doc2", "doc3", "doc4", "doc5",
            "doc6", "doc7", "doc8", "doc9", "doc10", "docSelfie"
        )
        if (!validSlots.contains(documentSlot)) {
            return Result.failure(Exception("Slot dokumen KK tidak valid."))
        }

        val path = when (documentSlot) {
            "doc1" -> kkDetail.doc1Path
            "doc2" -> kkDetail.doc2Path
            "doc3" -> kkDetail.doc3Path
            "doc4" -> kkDetail.doc4Path
            "doc5" -> kkDetail.doc5Path
            "doc6" -> kkDetail.doc6Path
            "doc7" -> kkDetail.doc7Path
            "doc8" -> kkDetail.doc8Path
            "doc9" -> kkDetail.doc9Path
            "doc10" -> kkDetail.doc10Path
            "docSelfie" -> kkDetail.docSelfiePath
            else -> null
        }

        return if (!path.isNullOrBlank()) {
            Result.success(path)
        } else {
            Result.failure(Exception("Dokumen KK belum tersedia."))
        }
    }

    // TAHAP 5: Remote EDIT / UPDATE KK WITH DOCUMENTS
    suspend fun updateKkApplicationRemoteWithDocuments(
        context: Context,
        application: ApplicationEntity,
        currentUuid: String,
        newDoc1: SelectedDocument? = null,
        newDoc2: SelectedDocument? = null,
        newDoc3: SelectedDocument? = null,
        newDoc4: SelectedDocument? = null,
        newDoc5: SelectedDocument? = null,
        newDoc6: SelectedDocument? = null,
        newDoc7: SelectedDocument? = null,
        newDoc8: SelectedDocument? = null,
        newDoc9: SelectedDocument? = null,
        newDoc10: SelectedDocument? = null,
        newDocSelfie: SelectedDocument? = null
    ): Boolean {
        val editTag = "SIAPEL_EDIT_KK"
        Log.d(editTag, "KK remote update start for code ${application.submissionCode}")

        val newlyUploadedPaths = mutableListOf<String>()

        return try {
            val oldDetailResult = fetchKkDetailRemote(application.submissionCode, currentUuid)
            if (oldDetailResult.isFailure) {
                Log.e(editTag, "KK fetch old detail failed for code ${application.submissionCode}")
                return false
            }
            val oldDetail = oldDetailResult.getOrThrow()

            val oldDoc1Path = oldDetail.doc1Path
            val oldDoc2Path = oldDetail.doc2Path
            val oldDoc3Path = oldDetail.doc3Path
            val oldDoc4Path = oldDetail.doc4Path
            val oldDoc5Path = oldDetail.doc5Path
            val oldDoc6Path = oldDetail.doc6Path
            val oldDoc7Path = oldDetail.doc7Path
            val oldDoc8Path = oldDetail.doc8Path
            val oldDoc9Path = oldDetail.doc9Path
            val oldDoc10Path = oldDetail.doc10Path
            val oldDocSelfiePath = oldDetail.docSelfiePath

            val isDoc1Replaced = isNewSelectedDocument(newDoc1)
            val isDoc2Replaced = isNewSelectedDocument(newDoc2)
            val isDoc3Replaced = isNewSelectedDocument(newDoc3)
            val isDoc4Replaced = isNewSelectedDocument(newDoc4)
            val isDoc5Replaced = isNewSelectedDocument(newDoc5)
            val isDoc6Replaced = isNewSelectedDocument(newDoc6)
            val isDoc7Replaced = isNewSelectedDocument(newDoc7)
            val isDoc8Replaced = isNewSelectedDocument(newDoc8)
            val isDoc9Replaced = isNewSelectedDocument(newDoc9)
            val isDoc10Replaced = isNewSelectedDocument(newDoc10)
            val isDocSelfieReplaced = isNewSelectedDocument(newDocSelfie)

            val finalDoc1Path = if (isDoc1Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc1!!, currentUuid, application.submissionCode, "doc1")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc1Path

            val finalDoc2Path = if (isDoc2Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc2!!, currentUuid, application.submissionCode, "doc2")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc2Path

            val finalDoc3Path = if (isDoc3Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc3!!, currentUuid, application.submissionCode, "doc3")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc3Path

            val finalDoc4Path = if (isDoc4Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc4!!, currentUuid, application.submissionCode, "doc4")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc4Path

            val finalDoc5Path = if (isDoc5Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc5!!, currentUuid, application.submissionCode, "doc5")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc5Path

            val finalDoc6Path = if (isDoc6Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc6!!, currentUuid, application.submissionCode, "doc6")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc6Path

            val finalDoc7Path = if (isDoc7Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc7!!, currentUuid, application.submissionCode, "doc7")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc7Path

            val finalDoc8Path = if (isDoc8Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc8!!, currentUuid, application.submissionCode, "doc8")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc8Path

            val finalDoc9Path = if (isDoc9Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc9!!, currentUuid, application.submissionCode, "doc9")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc9Path

            val finalDoc10Path = if (isDoc10Replaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDoc10!!, currentUuid, application.submissionCode, "doc10")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDoc10Path

            val finalDocSelfiePath = if (isDocSelfieReplaced) {
                val uploadRes = uploadKkDocumentRemote(context, newDocSelfie!!, currentUuid, application.submissionCode, "docSelfie")
                if (uploadRes.isFailure) {
                    cleanupNewlyUploadedFiles(newlyUploadedPaths)
                    return false
                }
                val path = uploadRes.getOrThrow()
                newlyUploadedPaths.add(path)
                path
            } else oldDocSelfiePath

            val appDto = ApplicationRemoteDto(
                userId = currentUuid,
                serviceType = application.serviceType,
                category = application.category,
                submissionCode = application.submissionCode,
                submissionDate = application.submissionDate,
                status = application.status.name,
                note = application.note,
                nikPelapor = application.nikPelapor,
                namaPelapor = application.namaPelapor,
                email = application.email,
                whatsapp = application.whatsapp,
                kecamatan = application.kecamatan,
                kelurahan = application.kelurahan,
                keterangan = application.keterangan
            )

            val updatedApp = postgrest.from("applications").update(appDto) {
                filter {
                    eq("submission_code", application.submissionCode)
                    eq("user_id", currentUuid)
                    eq("service_type", "KK")
                }
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = updatedApp.id
            if (remoteAppId.isNullOrEmpty()) {
                Log.e(editTag, "KK applications update failed: remoteAppId null")
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }

            val kkDetailDto = KkDetailRemoteDto(
                applicationId = remoteAppId,
                nikAnak = application.nikAnak?.ifEmpty { null },
                namaAnak = application.namaAnak?.ifEmpty { null },
                nomorKk = application.nomorKk?.ifEmpty { null },
                doc1Path = finalDoc1Path,
                doc2Path = finalDoc2Path,
                doc3Path = finalDoc3Path,
                doc4Path = finalDoc4Path,
                doc5Path = finalDoc5Path,
                doc6Path = finalDoc6Path,
                doc7Path = finalDoc7Path,
                doc8Path = finalDoc8Path,
                doc9Path = finalDoc9Path,
                doc10Path = finalDoc10Path,
                docSelfiePath = finalDocSelfiePath
            )

            try {
                postgrest.from("kk_detail").update(kkDetailDto) {
                    filter {
                        eq("application_id", remoteAppId)
                    }
                }
                Log.d(editTag, "KK detail remote update success for code ${application.submissionCode}")
            } catch (e: Exception) {
                Log.e(editTag, "KK detail remote update failed: ${e.message}", e)
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }

            if (isDoc1Replaced && !oldDoc1Path.isNullOrBlank() && oldDoc1Path != finalDoc1Path) {
                deleteSingleStorageFileBestEffort(oldDoc1Path, "doc1", editTag)
            }
            if (isDoc2Replaced && !oldDoc2Path.isNullOrBlank() && oldDoc2Path != finalDoc2Path) {
                deleteSingleStorageFileBestEffort(oldDoc2Path, "doc2", editTag)
            }
            if (isDoc3Replaced && !oldDoc3Path.isNullOrBlank() && oldDoc3Path != finalDoc3Path) {
                deleteSingleStorageFileBestEffort(oldDoc3Path, "doc3", editTag)
            }
            if (isDoc4Replaced && !oldDoc4Path.isNullOrBlank() && oldDoc4Path != finalDoc4Path) {
                deleteSingleStorageFileBestEffort(oldDoc4Path, "doc4", editTag)
            }
            if (isDoc5Replaced && !oldDoc5Path.isNullOrBlank() && oldDoc5Path != finalDoc5Path) {
                deleteSingleStorageFileBestEffort(oldDoc5Path, "doc5", editTag)
            }
            if (isDoc6Replaced && !oldDoc6Path.isNullOrBlank() && oldDoc6Path != finalDoc6Path) {
                deleteSingleStorageFileBestEffort(oldDoc6Path, "doc6", editTag)
            }
            if (isDoc7Replaced && !oldDoc7Path.isNullOrBlank() && oldDoc7Path != finalDoc7Path) {
                deleteSingleStorageFileBestEffort(oldDoc7Path, "doc7", editTag)
            }
            if (isDoc8Replaced && !oldDoc8Path.isNullOrBlank() && oldDoc8Path != finalDoc8Path) {
                deleteSingleStorageFileBestEffort(oldDoc8Path, "doc8", editTag)
            }
            if (isDoc9Replaced && !oldDoc9Path.isNullOrBlank() && oldDoc9Path != finalDoc9Path) {
                deleteSingleStorageFileBestEffort(oldDoc9Path, "doc9", editTag)
            }
            if (isDoc10Replaced && !oldDoc10Path.isNullOrBlank() && oldDoc10Path != finalDoc10Path) {
                deleteSingleStorageFileBestEffort(oldDoc10Path, "doc10", editTag)
            }
            if (isDocSelfieReplaced && !oldDocSelfiePath.isNullOrBlank() && oldDocSelfiePath != finalDocSelfiePath) {
                deleteSingleStorageFileBestEffort(oldDocSelfiePath, "docSelfie", editTag)
            }

            applicationDao.updateApplication(application)
            Log.d(editTag, "KK remote update success for code ${application.submissionCode}")
            true
        } catch (e: Exception) {
            Log.e(editTag, "KK remote update failed for code ${application.submissionCode}: ${e.message}", e)
            cleanupNewlyUploadedFiles(newlyUploadedPaths)
            false
        }
    }

    // TAHAP 6: Remote DELETE KK (applications + cascade kk_detail + Storage cleanup)
    suspend fun deleteKkApplicationRemote(
        submissionCode: String,
        currentSupabaseUuid: String
    ): Boolean {
        Log.d(TAG, "KK remote delete start for code $submissionCode")
        val isDbDeleteSuccess = try {
            postgrest.from("applications").delete {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentSupabaseUuid)
                    eq("service_type", "KK")
                }
            }
            Log.d(TAG, "KK remote delete success for code $submissionCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "KK remote delete failed for code $submissionCode: ${e.message}", e)
            false
        }

        if (!isDbDeleteSuccess) return false

        val storageTag = "SIAPEL_STORAGE"
        val folderPath = "$currentSupabaseUuid/$submissionCode"
        Log.d(storageTag, "KK storage cleanup start for folder $folderPath")

        try {
            val bucket = storage.from("siapel-documents")
            val files = bucket.list(folderPath)
            if (files.isNotEmpty()) {
                val fullPaths = files.map { file -> "$folderPath/${file.name}" }
                bucket.delete(fullPaths)
                Log.d(storageTag, "KK storage cleanup success for folder $folderPath")
            } else {
                Log.d(storageTag, "KK storage cleanup skipped: folder empty for $folderPath")
            }
        } catch (storageEx: Exception) {
            Log.e(
                storageTag,
                "KK storage cleanup failed for folder $folderPath: ${storageEx.message}",
                storageEx
            )
        }

        return true
    }

    // ==========================================================
    // REMOTE AKTA KELAHIRAN SECTION (TAHAP AKTA E & F & G & H & I)
    // ==========================================================

    private suspend fun uploadAktaDocumentRemote(
        context: Context,
        selectedDocument: SelectedDocument,
        currentUuid: String,
        submissionCode: String,
        documentSlot: String
    ): Result<String> {
        val storageTag = "SIAPEL_STORAGE"
        Log.d(storageTag, "AKTA upload start for slot $documentSlot, submissionCode $submissionCode")

        val validSlots = listOf(
            "doc1", "doc2", "doc3", "doc4", "doc5",
            "doc6", "doc7", "doc8", "doc9", "docSelfie"
        )
        if (!validSlots.contains(documentSlot)) {
            Log.e(storageTag, "upload failed: invalid AKTA slot $documentSlot")
            return Result.failure(Exception("Slot dokumen AKTA tidak valid."))
        }

        val uri = selectedDocument.uri
        if (uri == null) {
            Log.e(storageTag, "upload failed: URI is null")
            return Result.failure(Exception("File dokumen tidak ditemukan (URI null)."))
        }

        val displayName = selectedDocument.displayName
        val rawMime = selectedDocument.mimeType

        val extension = when {
            rawMime?.contains("jpeg", true) == true || rawMime?.contains("jpg", true) == true -> "jpg"
            rawMime?.contains("png", true) == true -> "png"
            rawMime?.contains("pdf", true) == true -> "pdf"
            rawMime?.contains("msword", true) == true -> "doc"
            rawMime?.contains("wordprocessingml", true) == true -> "docx"
            else -> displayName.substringAfterLast('.', "").lowercase()
        }

        val imageExtensions = listOf("jpg", "jpeg", "png")
        val documentExtensions = listOf("pdf", "doc", "docx")

        val maxSizeBytes = when {
            imageExtensions.contains(extension) -> 5 * 1024 * 1024L
            documentExtensions.contains(extension) -> 10 * 1024 * 1024L
            else -> {
                Log.e(storageTag, "upload failed: invalid extension or mime ($extension / $rawMime)")
                return Result.failure(Exception("Format file tidak didukung. Gunakan JPG, PNG, PDF, DOC, atau DOCX."))
            }
        }

        val sizeErrorMessage = if (imageExtensions.contains(extension)) {
            "Ukuran gambar melebihi batas maksimal 5 MB."
        } else {
            "Ukuran dokumen melebihi batas maksimal 10 MB."
        }

        val rawSize = selectedDocument.fileSize
        if (rawSize > maxSizeBytes) {
            Log.e(storageTag, "upload failed: file size exceeds limit ($rawSize > $maxSizeBytes bytes)")
            return Result.failure(Exception(sizeErrorMessage))
        }

        val fileBytes = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            }
        } catch (e: Exception) {
            Log.e(storageTag, "upload failed: unable to read Uri stream (${e.message})")
            null
        }

        if (fileBytes == null || fileBytes.isEmpty()) {
            Log.e(storageTag, "upload failed: file content is empty or unreadable")
            return Result.failure(Exception("Gagal membaca berkas file."))
        }

        if (fileBytes.size > maxSizeBytes) {
            Log.e(storageTag, "upload failed: read file size exceeds limit (${fileBytes.size} > $maxSizeBytes bytes)")
            return Result.failure(Exception(sizeErrorMessage))
        }

        val targetPath = "$currentUuid/$submissionCode/${documentSlot}_${System.currentTimeMillis()}.$extension"
        Log.d(storageTag, "target path: $targetPath")

        return try {
            val bucket = storage.from("siapel-documents")
            bucket.upload(targetPath, fileBytes) {
                upsert = false
            }
            Log.d(storageTag, "upload success for target path: $targetPath")
            Result.success(targetPath)
        } catch (e: Exception) {
            Log.e(storageTag, "upload failed for target path $targetPath: ${e.message}", e)
            Result.failure(Exception("Gagal mengunggah dokumen ke penyimpanan cloud: ${e.message}"))
        }
    }

    suspend fun createAktaApplicationRemote(
        context: Context,
        application: ApplicationEntity,
        currentSupabaseUuid: String,
        doc1: SelectedDocument? = null,
        doc2: SelectedDocument? = null,
        doc3: SelectedDocument? = null,
        doc4: SelectedDocument? = null,
        doc5: SelectedDocument? = null,
        doc6: SelectedDocument? = null,
        doc7: SelectedDocument? = null,
        doc8: SelectedDocument? = null,
        doc9: SelectedDocument? = null,
        docSelfie: SelectedDocument? = null
    ): Boolean {
        Log.d(TAG, "AKTA remote create start for code ${application.submissionCode}")

        val validAktaCategories = setOf(
            "Paket Kelahiran Umum + KIA + KK",
            "Paket Kelahiran Terlambat + KIA + KK",
            "Kelahiran Terlambat Tunggal",
            "Paket Kelahiran Umum + KIA + KK + KTP",
            "Paket Kelahiran Terlambat + KIA + KK + KTP",
            "Paket Kelahiran Terlambat + KK + KTP",
            "Paket Kelahiran Terlambat + KTP"
        )

        if (currentSupabaseUuid.isBlank()) {
            Log.e(TAG, "AKTA remote create failed: currentSupabaseUuid is blank")
            return false
        }

        if (application.serviceType != "AKTA") {
            Log.e(TAG, "AKTA remote create failed: invalid serviceType ${application.serviceType}")
            return false
        }

        if (application.category !in validAktaCategories) {
            Log.e(TAG, "AKTA remote create failed: invalid category ${application.category}")
            return false
        }

        var insertedAppId: String? = null
        val uploadedPaths = mutableListOf<String>()

        return try {
            val appDto = ApplicationRemoteDto(
                userId = currentSupabaseUuid,
                serviceType = application.serviceType,
                category = application.category,
                submissionCode = application.submissionCode,
                submissionDate = application.submissionDate,
                status = application.status.name,
                note = application.note,
                nikPelapor = application.nikPelapor,
                namaPelapor = application.namaPelapor,
                email = application.email,
                whatsapp = application.whatsapp,
                kecamatan = application.kecamatan,
                kelurahan = application.kelurahan,
                keterangan = application.keterangan
            )

            val insertedApp = postgrest.from("applications").insert(appDto) {
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = insertedApp.id
            if (remoteAppId.isNullOrEmpty()) {
                Log.e(TAG, "AKTA remote create failed: inserted application ID is null")
                return false
            }
            insertedAppId = remoteAppId
            Log.d(TAG, "AKTA applications remote insert success id $remoteAppId")

            var doc1Path: String? = null
            if (doc1 != null) {
                val res = uploadAktaDocumentRemote(context, doc1, currentSupabaseUuid, application.submissionCode, "doc1")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc1Path = res.getOrNull()
                doc1Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload doc1 success path $doc1Path")
            }

            var doc2Path: String? = null
            if (doc2 != null) {
                val res = uploadAktaDocumentRemote(context, doc2, currentSupabaseUuid, application.submissionCode, "doc2")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc2Path = res.getOrNull()
                doc2Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload doc2 success path $doc2Path")
            }

            var doc3Path: String? = null
            if (doc3 != null) {
                val res = uploadAktaDocumentRemote(context, doc3, currentSupabaseUuid, application.submissionCode, "doc3")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc3Path = res.getOrNull()
                doc3Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload doc3 success path $doc3Path")
            }

            var doc4Path: String? = null
            if (doc4 != null) {
                val res = uploadAktaDocumentRemote(context, doc4, currentSupabaseUuid, application.submissionCode, "doc4")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc4Path = res.getOrNull()
                doc4Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload doc4 success path $doc4Path")
            }

            var doc5Path: String? = null
            if (doc5 != null) {
                val res = uploadAktaDocumentRemote(context, doc5, currentSupabaseUuid, application.submissionCode, "doc5")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc5Path = res.getOrNull()
                doc5Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload doc5 success path $doc5Path")
            }

            var doc6Path: String? = null
            if (doc6 != null) {
                val res = uploadAktaDocumentRemote(context, doc6, currentSupabaseUuid, application.submissionCode, "doc6")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc6Path = res.getOrNull()
                doc6Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload doc6 success path $doc6Path")
            }

            var doc7Path: String? = null
            if (doc7 != null) {
                val res = uploadAktaDocumentRemote(context, doc7, currentSupabaseUuid, application.submissionCode, "doc7")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc7Path = res.getOrNull()
                doc7Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload doc7 success path $doc7Path")
            }

            var doc8Path: String? = null
            if (doc8 != null) {
                val res = uploadAktaDocumentRemote(context, doc8, currentSupabaseUuid, application.submissionCode, "doc8")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc8Path = res.getOrNull()
                doc8Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload doc8 success path $doc8Path")
            }

            var doc9Path: String? = null
            if (doc9 != null) {
                val res = uploadAktaDocumentRemote(context, doc9, currentSupabaseUuid, application.submissionCode, "doc9")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                doc9Path = res.getOrNull()
                doc9Path?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload doc9 success path $doc9Path")
            }

            var docSelfiePath: String? = null
            if (docSelfie != null) {
                val res = uploadAktaDocumentRemote(context, docSelfie, currentSupabaseUuid, application.submissionCode, "docSelfie")
                if (res.isFailure) {
                    cleanupNewlyUploadedFiles(uploadedPaths)
                    cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                    return false
                }
                docSelfiePath = res.getOrNull()
                docSelfiePath?.let { uploadedPaths.add(it) }
                Log.d(TAG, "AKTA upload docSelfie success path $docSelfiePath")
            }

            val aktaDetailDto = AktaDetailRemoteDto(
                applicationId = remoteAppId,
                nikBayi = application.nikBayi?.ifEmpty { null },
                namaBayi = application.namaBayi?.ifEmpty { null },
                nikIbu = application.nikIbu?.ifEmpty { null },
                namaIbu = application.namaIbu?.ifEmpty { null },
                nomorKk = application.nomorKk?.ifEmpty { null },
                doc1Path = doc1Path,
                doc2Path = doc2Path,
                doc3Path = doc3Path,
                doc4Path = doc4Path,
                doc5Path = doc5Path,
                doc6Path = doc6Path,
                doc7Path = doc7Path,
                doc8Path = doc8Path,
                doc9Path = doc9Path,
                docSelfiePath = docSelfiePath
            )

            try {
                postgrest.from("akta_detail").insert(aktaDetailDto)
                Log.d(TAG, "AKTA detail remote insert success")
            } catch (e: Exception) {
                Log.e(TAG, "AKTA detail remote insert failed: ${e.message}", e)
                cleanupNewlyUploadedFiles(uploadedPaths)
                cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
                return false
            }

            applicationDao.insertApplication(application)
            Log.d(TAG, "AKTA Room insert success for code ${application.submissionCode}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "AKTA remote create failed for code ${application.submissionCode}: ${e.message}", e)
            cleanupNewlyUploadedFiles(uploadedPaths)
            if (insertedAppId != null) {
                cleanupRemoteAktaApplication(application.submissionCode, currentSupabaseUuid)
            }
            false
        }
    }

    suspend fun syncAktaApplicationsRemote(currentUuid: String, localUserId: Int): Boolean {
        if (currentUuid.isBlank()) {
            Log.e(TAG, "AKTA remote sync failed: currentUuid blank")
            return false
        }
        Log.d(TAG, "AKTA remote sync start")
        return try {
            val remoteApps = postgrest.from("applications").select(Columns.raw("*, akta_detail(*)")) {
                filter {
                    eq("user_id", currentUuid)
                    eq("service_type", "AKTA")
                }
            }.decodeList<ApplicationWithAktaDetailRemoteDto>()

            Log.d(TAG, "AKTA remote sync success count: ${remoteApps.size}")

            for (dto in remoteApps) {
                val existingLocal = applicationDao.getApplicationByCode(dto.submissionCode)
                val statusEnum = try {
                    ApplicationStatus.valueOf(dto.status)
                } catch (_: Exception) {
                    ApplicationStatus.SUBMITTED
                }
                val akta = dto.aktaDetail

                val entity = ApplicationEntity(
                    id = existingLocal?.id ?: 0,
                    userId = localUserId,
                    serviceType = dto.serviceType,
                    category = dto.category,
                    submissionCode = dto.submissionCode,
                    submissionDate = dto.submissionDate,
                    status = statusEnum,
                    note = dto.note ?: existingLocal?.note,
                    kecamatan = dto.kecamatan,
                    kelurahan = dto.kelurahan,
                    email = dto.email,
                    whatsapp = dto.whatsapp,
                    keterangan = dto.keterangan,
                    nikPelapor = dto.nikPelapor,
                    namaPelapor = dto.namaPelapor,
                    nikBayi = akta?.nikBayi ?: existingLocal?.nikBayi,
                    namaBayi = akta?.namaBayi ?: existingLocal?.namaBayi,
                    nikIbu = akta?.nikIbu ?: existingLocal?.nikIbu,
                    namaIbu = akta?.namaIbu ?: existingLocal?.namaIbu,
                    nomorKk = akta?.nomorKk ?: existingLocal?.nomorKk,
                    doc1 = existingLocal?.doc1 ?: akta?.doc1Path,
                    doc2 = existingLocal?.doc2 ?: akta?.doc2Path,
                    doc3 = existingLocal?.doc3 ?: akta?.doc3Path,
                    doc4 = existingLocal?.doc4 ?: akta?.doc4Path,
                    doc5 = existingLocal?.doc5 ?: akta?.doc5Path,
                    doc6 = existingLocal?.doc6 ?: akta?.doc6Path,
                    doc7 = existingLocal?.doc7 ?: akta?.doc7Path,
                    doc8 = existingLocal?.doc8 ?: akta?.doc8Path,
                    doc9 = existingLocal?.doc9 ?: akta?.doc9Path,
                    docSelfie = existingLocal?.docSelfie ?: akta?.docSelfiePath,
                    createdAt = existingLocal?.createdAt ?: System.currentTimeMillis()
                )

                if (existingLocal != null) {
                    applicationDao.updateApplication(entity)
                } else {
                    applicationDao.insertApplication(entity)
                }
            }
            Log.d(TAG, "Room sync success for AKTA")
            true
        } catch (e: Exception) {
            Log.e(TAG, "AKTA remote sync failed for user $currentUuid: ${e.message}", e)
            false
        }
    }

    suspend fun fetchAktaDetailRemote(
        submissionCode: String,
        currentUuid: String
    ): Result<AktaDetailRemoteDto> {
        if (submissionCode.isBlank() || currentUuid.isBlank()) {
            return Result.failure(Exception("Parameter fetch detail AKTA tidak valid."))
        }
        Log.d(TAG, "AKTA fetch detail start for code $submissionCode")
        return try {
            val dto = postgrest.from("applications").select(Columns.raw("*, akta_detail(*)")) {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentUuid)
                    eq("service_type", "AKTA")
                }
            }.decodeSingle<ApplicationWithAktaDetailRemoteDto>()

            val aktaDetail = dto.aktaDetail
            if (aktaDetail != null) {
                Log.d(TAG, "AKTA fetch detail success for code $submissionCode")
                Result.success(aktaDetail)
            } else {
                Log.e(TAG, "AKTA fetch detail failed: akta_detail is null")
                Result.failure(Exception("Detail dokumen AKTA belum tersedia."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "AKTA fetch detail failed for code $submissionCode: ${e.message}", e)
            Result.failure(Exception("Gagal mengambil detail dokumen AKTA dari server: ${e.message}"))
        }
    }

    fun getAktaDocumentPathBySlot(
        aktaDetail: AktaDetailRemoteDto,
        documentSlot: String
    ): Result<String> {
        val validSlots = listOf(
            "doc1", "doc2", "doc3", "doc4", "doc5",
            "doc6", "doc7", "doc8", "doc9", "docSelfie"
        )
        if (!validSlots.contains(documentSlot)) {
            return Result.failure(Exception("Slot dokumen AKTA tidak valid."))
        }

        val path = when (documentSlot) {
            "doc1" -> aktaDetail.doc1Path
            "doc2" -> aktaDetail.doc2Path
            "doc3" -> aktaDetail.doc3Path
            "doc4" -> aktaDetail.doc4Path
            "doc5" -> aktaDetail.doc5Path
            "doc6" -> aktaDetail.doc6Path
            "doc7" -> aktaDetail.doc7Path
            "doc8" -> aktaDetail.doc8Path
            "doc9" -> aktaDetail.doc9Path
            "docSelfie" -> aktaDetail.docSelfiePath
            else -> null
        }

        return if (!path.isNullOrBlank()) {
            Result.success(path)
        } else {
            Result.failure(Exception("Dokumen AKTA belum tersedia."))
        }
    }

    // TAHAP AKTA H: Remote EDIT / UPDATE AKTA WITH DOCUMENTS
    suspend fun updateAktaApplicationRemoteWithDocuments(
        context: Context,
        application: ApplicationEntity,
        currentUuid: String,

        newDoc1: SelectedDocument? = null,
        newDoc2: SelectedDocument? = null,
        newDoc3: SelectedDocument? = null,
        newDoc4: SelectedDocument? = null,
        newDoc5: SelectedDocument? = null,
        newDoc6: SelectedDocument? = null,
        newDoc7: SelectedDocument? = null,
        newDoc8: SelectedDocument? = null,
        newDoc9: SelectedDocument? = null,
        newDocSelfie: SelectedDocument? = null,

        removeDoc1: Boolean = false,
        removeDoc2: Boolean = false,
        removeDoc3: Boolean = false,
        removeDoc4: Boolean = false,
        removeDoc5: Boolean = false,
        removeDoc6: Boolean = false,
        removeDoc7: Boolean = false,
        removeDoc8: Boolean = false,
        removeDoc9: Boolean = false,
        removeDocSelfie: Boolean = false
    ): Boolean {
        val editTag = "SIAPEL_EDIT_AKTA"
        Log.d(editTag, "AKTA remote update start for code ${application.submissionCode}")

        val validAktaCategories = setOf(
            "Paket Kelahiran Umum + KIA + KK",
            "Paket Kelahiran Terlambat + KIA + KK",
            "Kelahiran Terlambat Tunggal",
            "Paket Kelahiran Umum + KIA + KK + KTP",
            "Paket Kelahiran Terlambat + KIA + KK + KTP",
            "Paket Kelahiran Terlambat + KK + KTP",
            "Paket Kelahiran Terlambat + KTP"
        )

        if (currentUuid.isBlank()) {
            Log.e(editTag, "AKTA remote update failed: currentUuid is blank")
            return false
        }

        if (application.serviceType != "AKTA") {
            Log.e(editTag, "AKTA remote update failed: invalid serviceType ${application.serviceType}")
            return false
        }

        if (application.category !in validAktaCategories) {
            Log.e(editTag, "AKTA remote update failed: invalid category ${application.category}")
            return false
        }

        val newlyUploadedPaths = mutableListOf<String>()

        return try {
            val oldDetailResult = fetchAktaDetailRemote(application.submissionCode, currentUuid)
            if (oldDetailResult.isFailure) {
                Log.e(editTag, "AKTA fetch old detail failed for code ${application.submissionCode}")
                return false
            }
            val oldDetail = oldDetailResult.getOrThrow()

            val oldDoc1Path = oldDetail.doc1Path
            val oldDoc2Path = oldDetail.doc2Path
            val oldDoc3Path = oldDetail.doc3Path
            val oldDoc4Path = oldDetail.doc4Path
            val oldDoc5Path = oldDetail.doc5Path
            val oldDoc6Path = oldDetail.doc6Path
            val oldDoc7Path = oldDetail.doc7Path
            val oldDoc8Path = oldDetail.doc8Path
            val oldDoc9Path = oldDetail.doc9Path
            val oldDocSelfiePath = oldDetail.docSelfiePath

            val isDoc1Replaced = isNewSelectedDocument(newDoc1)
            val isDoc2Replaced = isNewSelectedDocument(newDoc2)
            val isDoc3Replaced = isNewSelectedDocument(newDoc3)
            val isDoc4Replaced = isNewSelectedDocument(newDoc4)
            val isDoc5Replaced = isNewSelectedDocument(newDoc5)
            val isDoc6Replaced = isNewSelectedDocument(newDoc6)
            val isDoc7Replaced = isNewSelectedDocument(newDoc7)
            val isDoc8Replaced = isNewSelectedDocument(newDoc8)
            val isDoc9Replaced = isNewSelectedDocument(newDoc9)
            val isDocSelfieReplaced = isNewSelectedDocument(newDocSelfie)

            val finalDoc1Path = when {
                removeDoc1 -> null
                isDoc1Replaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDoc1!!, currentUuid, application.submissionCode, "doc1")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc1Path
            }

            val finalDoc2Path = when {
                removeDoc2 -> null
                isDoc2Replaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDoc2!!, currentUuid, application.submissionCode, "doc2")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc2Path
            }

            val finalDoc3Path = when {
                removeDoc3 -> null
                isDoc3Replaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDoc3!!, currentUuid, application.submissionCode, "doc3")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc3Path
            }

            val finalDoc4Path = when {
                removeDoc4 -> null
                isDoc4Replaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDoc4!!, currentUuid, application.submissionCode, "doc4")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc4Path
            }

            val finalDoc5Path = when {
                removeDoc5 -> null
                isDoc5Replaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDoc5!!, currentUuid, application.submissionCode, "doc5")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc5Path
            }

            val finalDoc6Path = when {
                removeDoc6 -> null
                isDoc6Replaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDoc6!!, currentUuid, application.submissionCode, "doc6")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc6Path
            }

            val finalDoc7Path = when {
                removeDoc7 -> null
                isDoc7Replaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDoc7!!, currentUuid, application.submissionCode, "doc7")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc7Path
            }

            val finalDoc8Path = when {
                removeDoc8 -> null
                isDoc8Replaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDoc8!!, currentUuid, application.submissionCode, "doc8")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc8Path
            }

            val finalDoc9Path = when {
                removeDoc9 -> null
                isDoc9Replaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDoc9!!, currentUuid, application.submissionCode, "doc9")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDoc9Path
            }

            val finalDocSelfiePath = when {
                removeDocSelfie -> null
                isDocSelfieReplaced -> {
                    val uploadRes = uploadAktaDocumentRemote(context, newDocSelfie!!, currentUuid, application.submissionCode, "docSelfie")
                    if (uploadRes.isFailure) {
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    path
                }
                else -> oldDocSelfiePath
            }

            val appDto = ApplicationRemoteDto(
                userId = currentUuid,
                serviceType = application.serviceType,
                category = application.category,
                submissionCode = application.submissionCode,
                submissionDate = application.submissionDate,
                status = application.status.name,
                note = application.note,
                nikPelapor = application.nikPelapor,
                namaPelapor = application.namaPelapor,
                email = application.email,
                whatsapp = application.whatsapp,
                kecamatan = application.kecamatan,
                kelurahan = application.kelurahan,
                keterangan = application.keterangan
            )

            val updatedApp = postgrest.from("applications").update(appDto) {
                filter {
                    eq("submission_code", application.submissionCode)
                    eq("user_id", currentUuid)
                    eq("service_type", "AKTA")
                }
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = updatedApp.id
            if (remoteAppId.isNullOrEmpty()) {
                Log.e(editTag, "AKTA applications update failed: remoteAppId null")
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }

            val aktaDetailDto = AktaDetailRemoteDto(
                applicationId = remoteAppId,
                nikBayi = application.nikBayi?.ifEmpty { null },
                namaBayi = application.namaBayi?.ifEmpty { null },
                nikIbu = application.nikIbu?.ifEmpty { null },
                namaIbu = application.namaIbu?.ifEmpty { null },
                nomorKk = application.nomorKk?.ifEmpty { null },
                doc1Path = finalDoc1Path,
                doc2Path = finalDoc2Path,
                doc3Path = finalDoc3Path,
                doc4Path = finalDoc4Path,
                doc5Path = finalDoc5Path,
                doc6Path = finalDoc6Path,
                doc7Path = finalDoc7Path,
                doc8Path = finalDoc8Path,
                doc9Path = finalDoc9Path,
                docSelfiePath = finalDocSelfiePath
            )

            try {
                postgrest.from("akta_detail").update(aktaDetailDto) {
                    filter {
                        eq("application_id", remoteAppId)
                    }
                }
                Log.d(editTag, "AKTA detail remote update success for code ${application.submissionCode}")
            } catch (e: Exception) {
                Log.e(editTag, "AKTA detail remote update failed: ${e.message}", e)
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }

            if ((isDoc1Replaced || removeDoc1) && !oldDoc1Path.isNullOrBlank() && oldDoc1Path != finalDoc1Path) {
                deleteSingleStorageFileBestEffort(oldDoc1Path, "doc1", editTag)
            }
            if ((isDoc2Replaced || removeDoc2) && !oldDoc2Path.isNullOrBlank() && oldDoc2Path != finalDoc2Path) {
                deleteSingleStorageFileBestEffort(oldDoc2Path, "doc2", editTag)
            }
            if ((isDoc3Replaced || removeDoc3) && !oldDoc3Path.isNullOrBlank() && oldDoc3Path != finalDoc3Path) {
                deleteSingleStorageFileBestEffort(oldDoc3Path, "doc3", editTag)
            }
            if ((isDoc4Replaced || removeDoc4) && !oldDoc4Path.isNullOrBlank() && oldDoc4Path != finalDoc4Path) {
                deleteSingleStorageFileBestEffort(oldDoc4Path, "doc4", editTag)
            }
            if ((isDoc5Replaced || removeDoc5) && !oldDoc5Path.isNullOrBlank() && oldDoc5Path != finalDoc5Path) {
                deleteSingleStorageFileBestEffort(oldDoc5Path, "doc5", editTag)
            }
            if ((isDoc6Replaced || removeDoc6) && !oldDoc6Path.isNullOrBlank() && oldDoc6Path != finalDoc6Path) {
                deleteSingleStorageFileBestEffort(oldDoc6Path, "doc6", editTag)
            }
            if ((isDoc7Replaced || removeDoc7) && !oldDoc7Path.isNullOrBlank() && oldDoc7Path != finalDoc7Path) {
                deleteSingleStorageFileBestEffort(oldDoc7Path, "doc7", editTag)
            }
            if ((isDoc8Replaced || removeDoc8) && !oldDoc8Path.isNullOrBlank() && oldDoc8Path != finalDoc8Path) {
                deleteSingleStorageFileBestEffort(oldDoc8Path, "doc8", editTag)
            }
            if ((isDoc9Replaced || removeDoc9) && !oldDoc9Path.isNullOrBlank() && oldDoc9Path != finalDoc9Path) {
                deleteSingleStorageFileBestEffort(oldDoc9Path, "doc9", editTag)
            }
            if ((isDocSelfieReplaced || removeDocSelfie) && !oldDocSelfiePath.isNullOrBlank() && oldDocSelfiePath != finalDocSelfiePath) {
                deleteSingleStorageFileBestEffort(oldDocSelfiePath, "docSelfie", editTag)
            }

            applicationDao.updateApplication(application)
            Log.d(editTag, "AKTA remote update success for code ${application.submissionCode}")
            true
        } catch (e: Exception) {
            Log.e(editTag, "AKTA remote update failed for code ${application.submissionCode}: ${e.message}", e)
            cleanupNewlyUploadedFiles(newlyUploadedPaths)
            false
        }
    }

    // TAHAP AKTA I: Remote DELETE AKTA (applications + cascade akta_detail + Storage cleanup)
    suspend fun deleteAktaApplicationRemote(
        submissionCode: String,
        currentSupabaseUuid: String
    ): Boolean {
        if (submissionCode.isBlank() || currentSupabaseUuid.isBlank()) {
            Log.e(TAG, "AKTA remote delete failed: submissionCode or currentSupabaseUuid is blank")
            return false
        }
        Log.d(TAG, "AKTA remote delete start for code $submissionCode")
        val isDbDeleteSuccess = try {
            postgrest.from("applications").delete {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentSupabaseUuid)
                    eq("service_type", "AKTA")
                }
            }
            Log.d(TAG, "AKTA remote delete success for code $submissionCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "AKTA remote delete failed for code $submissionCode: ${e.message}", e)
            false
        }

        if (!isDbDeleteSuccess) return false

        val storageTag = "SIAPEL_STORAGE"
        val folderPath = "$currentSupabaseUuid/$submissionCode"
        Log.d(storageTag, "AKTA storage cleanup start for folder $folderPath")

        try {
            val bucket = storage.from("siapel-documents")
            val files = bucket.list(folderPath)
            if (files.isNotEmpty()) {
                val fullPaths = files.map { file -> "$folderPath/${file.name}" }
                bucket.delete(fullPaths)
                Log.d(storageTag, "AKTA storage cleanup success for folder $folderPath")
            } else {
                Log.d(storageTag, "AKTA storage cleanup skipped: folder empty for $folderPath")
            }
        } catch (storageEx: Exception) {
            Log.e(
                storageTag,
                "AKTA storage cleanup failed for folder $folderPath: ${storageEx.message}",
                storageEx
            )
        }

        return true
    }
}
