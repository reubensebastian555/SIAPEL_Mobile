package com.example.siapel.data.repository

import android.content.Context
import android.util.Log
import com.example.siapel.data.local.dao.ApplicationDao
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.remote.SupabaseClientProvider
import com.example.siapel.data.remote.dto.ApplicationRemoteDto
import com.example.siapel.data.remote.dto.ApplicationWithKiaDetailRemoteDto
import com.example.siapel.data.remote.dto.KiaDetailRemoteDto
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

    // Fetch remote kia_detail DTO by submissionCode (On-Demand request for viewing remote documents)
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

    // Create temporary Signed URL for private storage path (On-Demand 300 seconds / 5 minutes)
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

    // Helper Upload Storage KIA (Upload SelectedDocument to private bucket 'siapel-documents')
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

        // Determine extension with fallback from displayName
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

        // Size check 1: from SelectedDocument.fileSize
        val rawSize = selectedDocument.fileSize
        if (rawSize > maxSizeBytes) {
            Log.e(storageTag, "upload failed: file size exceeds limit ($rawSize > $maxSizeBytes bytes)")
            return Result.failure(Exception(sizeErrorMessage))
        }

        Log.d(storageTag, "slot: $documentSlot, file size: $rawSize bytes, mime/extension: $rawMime / $extension")

        // Read file bytes via ContentResolver
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

        // Size check 2: from actual fileBytes.size
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

    // Remote CREATE KIA (applications + storage upload + kia_detail with document paths)
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

            // Step 1: Insert applications table and decode inserted row to get generated UUID
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

            // Step 2: Upload documents to Supabase Storage if present
            var doc1Path: String? = null
            if (doc1 != null) {
                Log.d("SIAPEL_STORAGE", "Uploading doc1 for code ${application.submissionCode}")
                val res1 = uploadKiaDocumentRemote(context, doc1, currentUuid, application.submissionCode, "doc1")
                if (res1.isFailure) {
                    Log.e("SIAPEL_STORAGE", "Upload doc1 failed: ${res1.exceptionOrNull()?.message}")
                    cleanupRemoteApplication(application.submissionCode, currentUuid)
                    return false
                }
                doc1Path = res1.getOrNull()
                Log.d("SIAPEL_STORAGE", "Upload doc1 success: $doc1Path")
            }

            var doc2Path: String? = null
            if (doc2 != null) {
                Log.d("SIAPEL_STORAGE", "Uploading doc2 for code ${application.submissionCode}")
                val res2 = uploadKiaDocumentRemote(context, doc2, currentUuid, application.submissionCode, "doc2")
                if (res2.isFailure) {
                    Log.e("SIAPEL_STORAGE", "Upload doc2 failed: ${res2.exceptionOrNull()?.message}")
                    cleanupRemoteApplication(application.submissionCode, currentUuid)
                    return false
                }
                doc2Path = res2.getOrNull()
                Log.d("SIAPEL_STORAGE", "Upload doc2 success: $doc2Path")
            }

            var doc3Path: String? = null
            if (doc3 != null) {
                Log.d("SIAPEL_STORAGE", "Uploading doc3 for code ${application.submissionCode}")
                val res3 = uploadKiaDocumentRemote(context, doc3, currentUuid, application.submissionCode, "doc3")
                if (res3.isFailure) {
                    Log.e("SIAPEL_STORAGE", "Upload doc3 failed: ${res3.exceptionOrNull()?.message}")
                    cleanupRemoteApplication(application.submissionCode, currentUuid)
                    return false
                }
                doc3Path = res3.getOrNull()
                Log.d("SIAPEL_STORAGE", "Upload doc3 success: $doc3Path")
            }

            var docSelfiePath: String? = null
            if (docSelfie != null) {
                Log.d("SIAPEL_STORAGE", "Uploading docSelfie for code ${application.submissionCode}")
                val resSelfie = uploadKiaDocumentRemote(context, docSelfie, currentUuid, application.submissionCode, "docSelfie")
                if (resSelfie.isFailure) {
                    Log.e("SIAPEL_STORAGE", "Upload docSelfie failed: ${resSelfie.exceptionOrNull()?.message}")
                    cleanupRemoteApplication(application.submissionCode, currentUuid)
                    return false
                }
                docSelfiePath = resSelfie.getOrNull()
                Log.d("SIAPEL_STORAGE", "Upload docSelfie success: $docSelfiePath")
            }

            // Step 3: Insert kia_detail table with storage paths
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
            } catch (kiaEx: Exception) {
                Log.e(TAG, "Kia detail remote insert failed for code ${application.submissionCode}: ${kiaEx.message}", kiaEx)
                cleanupRemoteApplication(application.submissionCode, currentUuid)
                return false
            }

            // Step 4: Save to local Room DB
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

    // Remote READ KIA (1 Embedded PostgREST query strategy for applications + kia_detail)
    suspend fun syncKiaApplicationsRemote(currentUuid: String, localUserId: Int): Boolean {
        Log.d(TAG, "Remote sync start for user $currentUuid")
        return try {
            val remoteApps = postgrest.from("applications").select(Columns.raw("*, kia_detail(*)")) {
                filter {
                    eq("user_id", currentUuid)
                    eq("service_type", "KIA")
                }
            }.decodeList<ApplicationWithKiaDetailRemoteDto>()

            Log.d(TAG, "Remote sync success")
            Log.d(TAG, "Jumlah data diterima: ${remoteApps.size}")

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

    // Remote EDIT / UPDATE KIA WITH DOCUMENTS
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
            // 1. Fetch old remote kia_detail
            val oldDetailResult = fetchKiaDetailRemote(application.submissionCode, currentUuid)
            if (oldDetailResult.isFailure) {
                Log.e(editTag, "failed to fetch old remote detail for code ${application.submissionCode}")
                return false
            }
            val oldDetail = oldDetailResult.getOrThrow()
            Log.d(editTag, "old remote detail fetched for code ${application.submissionCode}")

            val oldDoc1Path = oldDetail.doc1Path
            val oldDoc2Path = oldDetail.doc2Path
            val oldDoc3Path = oldDetail.doc3Path
            val oldDocSelfiePath = oldDetail.docSelfiePath

            val isKiaPlusKkCategory = isKiaPlusKk(application.category)

            // Determine if documents are UNTOUCHED vs REPLACED
            val isDoc1Replaced = !isKiaPlusKkCategory && isNewSelectedDocument(newDoc1)
            val isDoc2Replaced = !isKiaPlusKkCategory && isNewSelectedDocument(newDoc2)
            val isDoc3Replaced = !isKiaPlusKkCategory && isNewSelectedDocument(newDoc3)
            val isDocSelfieReplaced = !isKiaPlusKkCategory && isNewSelectedDocument(newDocSelfie)

            Log.d(editTag, "oldDoc1Path=$oldDoc1Path, isDoc1Replaced=$isDoc1Replaced")
            Log.d(editTag, "oldDoc2Path=$oldDoc2Path, isDoc2Replaced=$isDoc2Replaced")
            Log.d(editTag, "oldDoc3Path=$oldDoc3Path, isDoc3Replaced=$isDoc3Replaced")
            Log.d(editTag, "oldDocSelfiePath=$oldDocSelfiePath, isDocSelfieReplaced=$isDocSelfieReplaced")

            val newlyUploadedPaths = mutableListOf<String>()

            // Handle doc1
            val finalDoc1Path = when {
                isKiaPlusKkCategory -> null
                isDoc1Replaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDoc1!!, currentUuid, application.submissionCode, "doc1")
                    if (uploadRes.isFailure) {
                        Log.e(editTag, "upload new doc failed for slot doc1: ${uploadRes.exceptionOrNull()?.message}")
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    Log.d(editTag, "upload new doc success for slot doc1: $path")
                    path
                }
                else -> oldDoc1Path // Untouched
            }

            // Handle doc2
            val finalDoc2Path = when {
                isKiaPlusKkCategory -> null
                isDoc2Replaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDoc2!!, currentUuid, application.submissionCode, "doc2")
                    if (uploadRes.isFailure) {
                        Log.e(editTag, "upload new doc failed for slot doc2: ${uploadRes.exceptionOrNull()?.message}")
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    Log.d(editTag, "upload new doc success for slot doc2: $path")
                    path
                }
                else -> oldDoc2Path // Untouched
            }

            // Handle doc3
            val finalDoc3Path = when {
                isKiaPlusKkCategory || application.category.contains("RUSAK", ignoreCase = true) || application.category.contains("HILANG", ignoreCase = true) -> null
                isDoc3Replaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDoc3!!, currentUuid, application.submissionCode, "doc3")
                    if (uploadRes.isFailure) {
                        Log.e(editTag, "upload new doc failed for slot doc3: ${uploadRes.exceptionOrNull()?.message}")
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    Log.d(editTag, "upload new doc success for slot doc3: $path")
                    path
                }
                else -> oldDoc3Path // Untouched
            }

            // Handle docSelfie
            val finalDocSelfiePath = when {
                isKiaPlusKkCategory -> null
                isDocSelfieReplaced -> {
                    val uploadRes = uploadKiaDocumentRemote(context, newDocSelfie!!, currentUuid, application.submissionCode, "docSelfie")
                    if (uploadRes.isFailure) {
                        Log.e(editTag, "upload new doc failed for slot docSelfie: ${uploadRes.exceptionOrNull()?.message}")
                        cleanupNewlyUploadedFiles(newlyUploadedPaths)
                        return false
                    }
                    val path = uploadRes.getOrThrow()
                    newlyUploadedPaths.add(path)
                    Log.d(editTag, "upload new doc success for slot docSelfie: $path")
                    path
                }
                else -> oldDocSelfiePath // Untouched
            }

            Log.d(editTag, "finalDoc1Path=$finalDoc1Path")
            Log.d(editTag, "finalDoc2Path=$finalDoc2Path")
            Log.d(editTag, "finalDoc3Path=$finalDoc3Path")
            Log.d(editTag, "finalDocSelfiePath=$finalDocSelfiePath")

            // 2. Update applications table
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
                Log.e(editTag, "edit remote failed: updated application ID is null")
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }
            Log.d(editTag, "update applications success for code ${application.submissionCode}")

            // 3. Update kia_detail table with final storage paths
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
                Log.d(editTag, "update kia_detail success for code ${application.submissionCode}")
            } catch (kiaEx: Exception) {
                Log.e(editTag, "update kia_detail failed for code ${application.submissionCode}: ${kiaEx.message}", kiaEx)
                cleanupNewlyUploadedFiles(newlyUploadedPaths)
                return false
            }

            // 4. Delete old replaced files (best-effort)
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

            // 5. Save to local Room DB
            applicationDao.updateApplication(application)
            Log.d(editTag, "edit remote completed for code ${application.submissionCode}")
            true
        } catch (e: Exception) {
            Log.e(editTag, "edit remote failed for code ${application.submissionCode}: ${e.message}", e)
            false
        }
    }

    private suspend fun cleanupNewlyUploadedFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        val editTag = "SIAPEL_EDIT_KIA"
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

    // Remote UPDATE KIA (Existing legacy method retained for compatibility)
    suspend fun updateKiaApplicationRemote(application: ApplicationEntity, currentUuid: String): Boolean {
        Log.d(TAG, "KIA remote update start for code ${application.submissionCode}")
        Log.d(TAG, "UUID resolved = true")
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

            // Request 1: Update applications and decode updated row to get remote application UUID directly from response without extra SELECT!
            Log.d(TAG, "Applications update start for code ${application.submissionCode}")
            val updatedApp = postgrest.from("applications").update(appDto) {
                filter {
                    eq("submission_code", application.submissionCode)
                    eq("user_id", currentUuid)
                }
                select()
            }.decodeSingle<ApplicationRemoteDto>()

            val remoteAppId = updatedApp.id
            if (remoteAppId.isNullOrEmpty()) {
                Log.e(TAG, "KIA remote update failed: updated application ID is null for code ${application.submissionCode}")
                return false
            }
            Log.d(TAG, "Applications update success + remote UUID: $remoteAppId")

            // Request 2: Update kia_detail for remoteAppId
            Log.d(TAG, "Kia detail update start for application ID: $remoteAppId")
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
                Log.d(TAG, "Kia detail update success for application ID $remoteAppId")
            } catch (kiaEx: Exception) {
                Log.e(TAG, "Partial remote update failure (kia_detail update failed for code ${application.submissionCode}): ${kiaEx.message}", kiaEx)
                return false
            }

            Log.d(TAG, "Room update start for code ${application.submissionCode}")
            applicationDao.updateApplication(application)
            Log.d(TAG, "Room update success for code ${application.submissionCode}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "KIA remote update failed for code ${application.submissionCode}: ${e.message}", e)
            false
        }
    }

    // Remote DELETE KIA (1 PostgREST request strategy with submission_code + user_id filters + Storage cleanup)
    suspend fun deleteKiaApplicationRemote(submissionCode: String, currentSupabaseUuid: String): Boolean {
        Log.d(TAG, "KIA remote delete start for code $submissionCode and user $currentSupabaseUuid")
        Log.d(TAG, "UUID resolved = true, submissionCode = $submissionCode")
        val isDbDeleteSuccess = try {
            postgrest.from("applications").delete {
                filter {
                    eq("submission_code", submissionCode)
                    eq("user_id", currentSupabaseUuid)
                }
            }
            Log.d(TAG, "KIA remote delete success for code $submissionCode")
            Log.d(TAG, "delete database success")
            true
        } catch (e: Exception) {
            val errorClass = e::class.java.simpleName
            val errorMessage = e.message ?: "Unknown message"
            val postgrestDetail = (e as? RestException)?.message ?: ""
            Log.e(TAG, "Remote delete failed")
            Log.e(TAG, "Exception = $errorClass")
            Log.e(TAG, "Message = $errorMessage")
            if (postgrestDetail.isNotEmpty()) {
                Log.e(TAG, "Postgrest error detail = $postgrestDetail")
            }
            Log.e(TAG, "submissionCode = $submissionCode")
            Log.e(TAG, "UUID resolved = true")
            false
        }

        if (!isDbDeleteSuccess) {
            return false
        }

        // Best-effort Storage cleanup
        val storageTag = "SIAPEL_STORAGE"
        val folderPath = "$currentSupabaseUuid/$submissionCode"
        Log.d(storageTag, "storage cleanup start")
        Log.d(storageTag, "folder path: $folderPath")

        try {
            val bucket = storage.from("siapel-documents")
            val files = bucket.list(folderPath)
            if (files.isEmpty()) {
                Log.d(storageTag, "No storage files to cleanup")
            } else {
                Log.d(storageTag, "jumlah file ditemukan: ${files.size}")
                val fullPaths = files.map { file -> "$folderPath/${file.name}" }
                for (path in fullPaths) {
                    Log.d(storageTag, "file path yang akan dihapus: $path")
                }
                bucket.delete(fullPaths)
                Log.d(storageTag, "storage cleanup success")
            }
        } catch (storageEx: Exception) {
            Log.e(storageTag, "storage cleanup failed for folder $folderPath: ${storageEx.message}", storageEx)
        }

        return true
    }
}
