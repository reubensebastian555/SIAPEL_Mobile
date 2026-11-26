package com.example.siapel.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.siapel.data.local.database.SiapelDatabase
import com.example.siapel.data.remote.laravel.LaravelApiException
import com.example.siapel.data.local.dao.ApplicationDao
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.remote.laravel.LaravelApplicationApiService
import com.example.siapel.data.remote.laravel.dto.*
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.SelectedDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Model hasil operasi pembuatan permohonan beserta pengunggahan dokumen privat.
 * Mampu menyampaikan informasi parsial apabila pengajuan berhasil dibuat tetapi sebagian dokumen gagal diunggah.
 */
data class LaravelApplicationCreateResult(
    val isFullySuccessful: Boolean,
    val applicationUuid: String?,
    val submissionCode: String?,
    val applicationDto: LaravelApplicationDto?,
    val uploadedSlots: Map<String, String> = emptyMap(),
    val failedSlots: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val cacheSyncStatus: LaravelCacheSyncStatus = LaravelCacheSyncStatus.NOT_REQUESTED,
    val cacheError: String? = null,
    val uncertainSlots: Set<String> = emptySet(),
    val remoteOutcome: LaravelRemoteOutcome = LaravelRemoteOutcome.CONFIRMED
) {
    val isCacheSynced: Boolean get() = cacheSyncStatus == LaravelCacheSyncStatus.SYNCED
    val refreshRequired: Boolean get() = cacheSyncStatus == LaravelCacheSyncStatus.REFRESH_REQUIRED || cacheSyncStatus == LaravelCacheSyncStatus.SESSION_CHANGED
}

/**
 * Model hasil operasi pembaharuan (update) permohonan beserta dokumen privat.
 * Mampu menyampaikan informasi parsial apabila data formulir berhasil diperbarui tetapi sebagian dokumen gagal diproses.
 */
data class LaravelApplicationUpdateResult(
    val isFullySuccessful: Boolean,
    val applicationUuid: String,
    val isFormUpdated: Boolean,
    val applicationDto: LaravelApplicationDto?,
    val uploadedSlots: Map<String, String> = emptyMap(),
    val deletedSlots: List<String> = emptyList(),
    val failedSlots: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val cacheSyncStatus: LaravelCacheSyncStatus = LaravelCacheSyncStatus.NOT_REQUESTED,
    val cacheError: String? = null,
    val uncertainSlots: Set<String> = emptySet(),
    val remoteOutcome: LaravelRemoteOutcome = LaravelRemoteOutcome.CONFIRMED,
    val submissionCode: String? = null
) {
    val isCacheSynced: Boolean get() = cacheSyncStatus == LaravelCacheSyncStatus.SYNCED
    val refreshRequired: Boolean get() = cacheSyncStatus == LaravelCacheSyncStatus.REFRESH_REQUIRED || cacheSyncStatus == LaravelCacheSyncStatus.SESSION_CHANGED
}

/**
 * Fungsi ekstensi untuk memetakan DTO respons Laravel ke [ApplicationEntity] milik Room cache.
 * Menyimpan identitas remoteUuid dan remoteUserUuid secara presisi, memverifikasi ownership, serta memperbarui path dokumen secara eksplisit.
 */
fun LaravelApplicationDto.toRoomEntity(
    localUserId: Int,
    verifiedRemoteUserUuid: String? = null,
    existingEntity: ApplicationEntity? = null
): ApplicationEntity {
    require(localUserId > 0) { "Local user ID tidak valid" }
    require(isApplicationUuid(id)) { "UUID pengajuan tidak valid" }
    require(isApplicationUuid(verifiedRemoteUserUuid) && userId == verifiedRemoteUserUuid) {
        "Ownership mismatch: identitas pengajuan tidak sesuai sesi"
    }
    require(existingEntity == null || (
            existingEntity.userId == localUserId &&
                    existingEntity.remoteUserUuid == verifiedRemoteUserUuid &&
                    existingEntity.remoteUuid == id
            )) { "Ownership mismatch pada cache existing" }

    // 2. Strict status parsing (tanpa fallback SUBMITTED)
    val parsedStatus = try {
        ApplicationStatus.valueOf(this.status)
    } catch (_: Exception) {
        throw IllegalArgumentException("Status permohonan '$status' dari server Laravel tidak dikenal oleh aplikasi.")
    }

    // 3. Resolusi path dokumen: Jika DTO relation ada, gunakan nilai dari DTO secara eksplisit (termasuk null saat dihapus di server)
    val doc1Val = when {
        kiaDetail != null -> kiaDetail.doc1Path
        ktpDetail != null -> ktpDetail.doc1Path
        kkDetail != null -> kkDetail.doc1Path
        aktaDetail != null -> aktaDetail.doc1Path
        disabilitasDetail != null -> disabilitasDetail.doc1Path
        else -> existingEntity?.doc1
    }

    val doc2Val = when {
        kiaDetail != null -> kiaDetail.doc2Path
        ktpDetail != null -> ktpDetail.doc2Path
        kkDetail != null -> kkDetail.doc2Path
        aktaDetail != null -> aktaDetail.doc2Path
        disabilitasDetail != null -> disabilitasDetail.doc2Path
        else -> existingEntity?.doc2
    }

    val doc3Val = when {
        kiaDetail != null -> kiaDetail.doc3Path
        kkDetail != null -> kkDetail.doc3Path
        aktaDetail != null -> aktaDetail.doc3Path
        else -> existingEntity?.doc3
    }

    val doc4Val = when {
        kkDetail != null -> kkDetail.doc4Path
        aktaDetail != null -> aktaDetail.doc4Path
        else -> existingEntity?.doc4
    }

    val doc5Val = when {
        kkDetail != null -> kkDetail.doc5Path
        aktaDetail != null -> aktaDetail.doc5Path
        else -> existingEntity?.doc5
    }

    val doc6Val = when {
        kkDetail != null -> kkDetail.doc6Path
        aktaDetail != null -> aktaDetail.doc6Path
        else -> existingEntity?.doc6
    }

    val doc7Val = when {
        kkDetail != null -> kkDetail.doc7Path
        aktaDetail != null -> aktaDetail.doc7Path
        else -> existingEntity?.doc7
    }

    val doc8Val = when {
        kkDetail != null -> kkDetail.doc8Path
        aktaDetail != null -> aktaDetail.doc8Path
        else -> existingEntity?.doc8
    }

    val doc9Val = when {
        kkDetail != null -> kkDetail.doc9Path
        aktaDetail != null -> aktaDetail.doc9Path
        else -> existingEntity?.doc9
    }

    val doc10Val = when {
        kkDetail != null -> kkDetail.doc10Path
        else -> existingEntity?.doc10
    }

    val docSelfieVal = when {
        kiaDetail != null -> kiaDetail.docSelfiePath
        ktpDetail != null -> ktpDetail.docSelfiePath
        kkDetail != null -> kkDetail.docSelfiePath
        aktaDetail != null -> aktaDetail.docSelfiePath
        disabilitasDetail != null -> disabilitasDetail.docSelfiePath
        else -> existingEntity?.docSelfie
    }

    return ApplicationEntity(
        id = existingEntity?.id ?: 0,
        userId = localUserId,
        remoteUuid = this.id,
        remoteUserUuid = this.userId,
        serviceType = this.serviceType,
        category = this.category,
        submissionCode = this.submissionCode,
        submissionDate = this.submissionDate,
        status = parsedStatus,
        note = this.note,
        kecamatan = this.kecamatan,
        kelurahan = this.kelurahan,
        email = this.email,
        whatsapp = this.whatsapp,
        keterangan = this.keterangan ?: existingEntity?.keterangan ?: "",
        nikPelapor = this.nikPelapor,
        namaPelapor = this.namaPelapor,

        nikAnak = when { kiaDetail != null -> kiaDetail.nikAnak; kkDetail != null -> kkDetail.nikAnak; else -> existingEntity?.nikAnak },
        namaAnak = when { kiaDetail != null -> kiaDetail.namaAnak; kkDetail != null -> kkDetail.namaAnak; else -> existingEntity?.namaAnak },
        nikBayi = if (aktaDetail != null) aktaDetail.nikBayi else existingEntity?.nikBayi,
        namaBayi = if (aktaDetail != null) aktaDetail.namaBayi else existingEntity?.namaBayi,
        nikIbu = if (aktaDetail != null) aktaDetail.nikIbu else existingEntity?.nikIbu,
        namaIbu = if (aktaDetail != null) aktaDetail.namaIbu else existingEntity?.namaIbu,
        nomorKk = when { kkDetail != null -> kkDetail.nomorKk; aktaDetail != null -> aktaDetail.nomorKk; else -> existingEntity?.nomorKk },

        doc1 = doc1Val,
        doc2 = doc2Val,
        doc3 = doc3Val,
        doc4 = doc4Val,
        doc5 = doc5Val,
        doc6 = doc6Val,
        doc7 = doc7Val,
        doc8 = doc8Val,
        doc9 = doc9Val,
        doc10 = doc10Val,
        docSelfie = docSelfieVal,

        createdAt = parseServerCreatedAt(this.createdAt) ?: existingEntity?.createdAt ?: System.currentTimeMillis()
    )
}


// Laravel ISO-8601 timestamps include an explicit UTC offset (including Z).
// Preserve the existing local timestamp if the server omits it or sends an unsupported format.
private fun parseServerCreatedAt(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

/** Status cache terpisah dari hasil mutasi server. */
enum class LaravelCacheSyncStatus { NOT_REQUESTED, SYNCED, REFRESH_REQUIRED, SESSION_CHANGED }
enum class LaravelRemoteOutcome { CONFIRMED, UNCERTAIN }

/** Mutasi sudah dikirim, tetapi hasil akhirnya tidak dapat dipastikan. Jangan retry otomatis. */
class LaravelMutationUncertainException(cause: Throwable) :
    Exception("Hasil mutasi server belum dapat dipastikan. Periksa data sebelum mengulangi.", cause)

data class LaravelDeleteResult(
    val response: LaravelMessageResponse,
    val applicationUuid: String,
    val cacheSyncStatus: LaravelCacheSyncStatus,
    val cacheError: String? = null
) {
    val message get() = response.message
    val isCacheSynced get() = cacheSyncStatus == LaravelCacheSyncStatus.SYNCED
    val refreshRequired get() = cacheSyncStatus == LaravelCacheSyncStatus.REFRESH_REQUIRED || cacheSyncStatus == LaravelCacheSyncStatus.SESSION_CHANGED
    val isFullySuccessful get() = cacheSyncStatus == LaravelCacheSyncStatus.SYNCED ||
            cacheSyncStatus == LaravelCacheSyncStatus.NOT_REQUESTED
}

private val APPLICATION_UUID_REGEX = Regex(
    "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
)

private fun isApplicationUuid(value: String?): Boolean =
    value != null && APPLICATION_UUID_REGEX.matches(value)

/**
 * Laravel merupakan sumber data utama. Mode remote-only harus dipilih eksplisit
 * untuk pengujian terisolasi; mode cache produksi memerlukan database dan auth.
 */
class LaravelApplicationRepository(
    private val applicationApiService: LaravelApplicationApiService = LaravelApplicationApiService(),
    private val documentRepository: LaravelDocumentRepository = LaravelDocumentRepository(),
    private val applicationDao: ApplicationDao? = null,
    private val authRepository: LaravelAuthRepository? = null,
    private val database: SiapelDatabase? = null,
    private val remoteOnly: Boolean = false
) {
    companion object {
        private val SERVICE_SLOTS = mapOf(
            "KIA" to setOf("doc1", "doc2", "doc3", "docSelfie"),
            "KTP" to setOf("doc1", "doc2", "docSelfie"),
            "KK" to ((1..10).map { "doc$it" } + "docSelfie").toSet(),
            "AKTA" to ((1..9).map { "doc$it" } + "docSelfie").toSet(),
            "DISABILITAS" to setOf("doc1", "doc2", "docSelfie")
        )
    }

    private data class OperationSession(
        val token: String,
        val verified: VerifiedLaravelSession?
    )

    private data class RefreshResult(
        val dto: LaravelApplicationDto?,
        val status: LaravelCacheSyncStatus,
        val error: String? = null
    )

    private class SessionChangedException : IllegalStateException("Sesi Laravel telah berubah")

    private val cacheDao: ApplicationDao
        get() = requireNotNull(database).applicationDao()

    private suspend fun resolveSession(
        token: String?, localUserId: Int? = null, remoteUserUuid: String? = null
    ): OperationSession {
        if (remoteOnly) {
            check(database == null && applicationDao == null && authRepository == null) {
                "Mode remote-only tidak boleh mengakses cache atau auth produksi"
            }
            val activeToken = token
            require(!activeToken.isNullOrBlank()) { "Token Laravel tidak tersedia" }
            return OperationSession(activeToken, null)
        }
        check(database != null && authRepository != null) { "Dependency cache dan auth Laravel belum lengkap" }
        check(applicationDao == null || applicationDao === cacheDao) { "DAO tidak berasal dari database cache yang sama" }
        val verified = authRepository.getVerifiedLaravelSession()
            ?: throw IllegalStateException("Sesi Laravel tidak terverifikasi")
        require(localUserId == null || localUserId == verified.localUserId) { "Local user ID tidak sesuai sesi" }
        require(remoteUserUuid == null || remoteUserUuid == verified.remoteUserUuid) { "Remote user UUID tidak sesuai sesi" }
        require(token == null || token == verified.token) { "Token tidak sesuai sesi" }
        return OperationSession(verified.token, verified)
    }

    private suspend fun ensureCurrent(session: OperationSession) {
        val verified = session.verified ?: return
        if (!requireNotNull(authRepository).isVerifiedSessionCurrent(verified)) {
            throw SessionChangedException()
        }
    }

    private suspend fun handleUnauthorized(session: OperationSession, error: Throwable) {
        if (error is LaravelApiException && error.statusCode == 401) {
            session.verified?.let { requireNotNull(authRepository).invalidateUnauthorizedSession(it) }
        }
    }

    private suspend fun <T> authorizedRequest(session: OperationSession, block: suspend () -> T): T {
        ensureCurrent(session)
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleUnauthorized(session, e)
            throw e
        }
    }

    private fun validateDto(
        dto: LaravelApplicationDto?, session: OperationSession,
        expectedUuid: String? = null, expectedService: String? = null,
        requireDetail: Boolean = false
    ): LaravelApplicationDto {
        val value = requireNotNull(dto) { "Data pengajuan dari server kosong" }
        require(isApplicationUuid(value.id) && isApplicationUuid(value.userId)) { "UUID respons tidak valid" }
        require(expectedUuid == null || value.id == expectedUuid) { "UUID respons tidak sesuai request" }
        require(session.verified == null || value.userId == session.verified.remoteUserUuid) { "Ownership respons tidak sesuai sesi" }
        require(SERVICE_SLOTS.containsKey(value.serviceType)) { "Jenis layanan tidak dikenal" }
        require(expectedService == null || value.serviceType == expectedService) { "Jenis layanan respons tidak sesuai" }
        ApplicationStatus.valueOf(value.status)
        if (requireDetail) {
            val present = when (value.serviceType) {
                "KIA" -> value.kiaDetail != null
                "KTP" -> value.ktpDetail != null
                "KK" -> value.kkDetail != null
                "AKTA" -> value.aktaDetail != null
                "DISABILITAS" -> value.disabilitasDetail != null
                else -> false
            }
            require(present) { "Detail layanan dari server tidak lengkap" }
        }
        return value
    }

    private suspend fun <T> protectedCache(session: OperationSession, block: suspend () -> T): T {
        val verified = requireNotNull(session.verified) { "Cache memerlukan sesi terverifikasi" }
        val result = requireNotNull(authRepository).withVerifiedSession(verified) {
            requireNotNull(database).withTransaction { block() }
        }
        ensureCurrent(session)
        return result
    }

    // Hanya dipanggil di dalam protectedCache: lookup, mapping, dan upsert satu transaksi.
    private suspend fun persistDto(dto: LaravelApplicationDto, session: OperationSession): ApplicationEntity {
        val verified = requireNotNull(session.verified)
        val existing = cacheDao.getApplicationByRemoteUuid(dto.id, verified.localUserId, verified.remoteUserUuid)
        val entity = dto.toRoomEntity(verified.localUserId, verified.remoteUserUuid, existing)
        val id = cacheDao.upsertLaravelApplicationStrict(entity, verified.localUserId, verified.remoteUserUuid)
        check(id in 1L..Int.MAX_VALUE.toLong()) { "ID cache tidak valid" }
        return entity.copy(id = id.toInt())
    }

    private suspend fun refreshAfterMutation(
        uuid: String, session: OperationSession, expectedService: String? = null
    ): RefreshResult {
        return try {
            ensureCurrent(session)
            val response = authorizedRequest(session) { applicationApiService.getApplicationDetail(uuid, session.token) }
            ensureCurrent(session)
            val dto = validateDto(response.data, session, uuid, expectedService, requireDetail = !remoteOnly)
            if (remoteOnly) {
                RefreshResult(dto, LaravelCacheSyncStatus.NOT_REQUESTED)
            } else {
                protectedCache(session) { persistDto(dto, session) }
                RefreshResult(dto, LaravelCacheSyncStatus.SYNCED)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val changed = e is SessionChangedException || !sessionStillCurrent(session)
            RefreshResult(null,
                if (changed) LaravelCacheSyncStatus.SESSION_CHANGED else LaravelCacheSyncStatus.REFRESH_REQUIRED,
                if (changed) "Sesi berubah; data tidak disimpan ke cache" else "Mutasi server telah diproses; detail/cache perlu disinkronkan ulang")
        }
    }

    private suspend fun sessionStillCurrent(session: OperationSession): Boolean = try {
        ensureCurrent(session)
        true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    private fun validateServiceSlot(serviceType: String, slot: String) {
        require(SERVICE_SLOTS[serviceType]?.contains(slot) == true) { "Slot dokumen tidak valid untuk layanan tersebut" }
    }

    private suspend fun preValidateDocuments(
        context: Context?, serviceType: String, documents: Map<String, SelectedDocument?>,
        deletedSlots: List<String>, inputStreamSupplier: ((Uri) -> InputStream?)?
    ) {
        require(SERVICE_SLOTS.containsKey(serviceType)) { "Layanan tidak dikenal" }
        require(documents.filterValues { it != null }.keys.intersect(deletedSlots.toSet()).isEmpty()) {
            "Slot dokumen tidak boleh diminta untuk diganti (replace) dan dihapus sekaligus"
        }
        deletedSlots.forEach { validateServiceSlot(serviceType, it) }
        for ((slot, doc) in documents) {
            if (doc == null) continue
            validateServiceSlot(serviceType, slot)
            val uri = requireNotNull(doc.uri) { "URI dokumen tidak valid (null)" }
            documentRepository.resolveExtensionAndMime(doc)
            documentRepository.readDocumentBytes(context?.contentResolver, uri, inputStreamSupplier).getOrThrow()
        }
    }

    private fun uncertain(error: Throwable): Boolean {
        return error !is LaravelApiException || error.statusCode !in 400..499 || error.statusCode == 408
    }

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    private suspend fun <T> sendMutation(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        if (uncertain(e)) throw LaravelMutationUncertainException(e)
        throw e
    }

    // ==========================================
    // REACTIVE LARAVEL CACHE
    // ==========================================

    fun observeCachedApplications(
        expectedSession: VerifiedLaravelSession
    ): Flow<List<ApplicationEntity>> = flow {

        check(!remoteOnly) {
            "Reactive cache membutuhkan mode produksi"
        }

        val session = resolveSession(
            token = expectedSession.token,
            localUserId = expectedSession.localUserId,
            remoteUserUuid = expectedSession.remoteUserUuid
        )

        check(session.verified == expectedSession) {
            "Sesi Laravel telah berubah"
        }

        ensureCurrent(session)

        cacheDao.getApplicationsByUserAndRemoteUuid(
            localUserId = expectedSession.localUserId,
            remoteUserUuid = expectedSession.remoteUserUuid
        ).collect { applications ->

            ensureCurrent(session)

            check(
                applications.all { application ->
                    application.userId ==
                            expectedSession.localUserId &&
                            application.remoteUserUuid ==
                            expectedSession.remoteUserUuid &&
                            isApplicationUuid(application.remoteUuid)
                }
            ) {
                "Ownership cache pengajuan tidak sesuai sesi"
            }

            emit(applications)
        }
    }

    suspend fun getApplications(
        serviceType: String? = null, status: String? = null,
        perPage: Int? = null, page: Int? = null, token: String? = null
    ): Result<LaravelApplicationPaginatedResponse> = resultOf {
        val session = resolveSession(token)
        val response = authorizedRequest(session) { applicationApiService.getApplications(serviceType, status, perPage, page, session.token) }
        ensureCurrent(session)
        response.data.forEach { validateDto(it, session, expectedService = serviceType) }
        response
    }

    suspend fun syncApplicationsRemoteAndCache(
        localUserId: Int? = null, remoteUserUuid: String? = null, serviceType: String? = null,
        status: String? = null, page: Int? = null, token: String? = null
    ): Result<List<ApplicationEntity>> = resultOf {
        check(!remoteOnly) { "Sinkronisasi membutuhkan cache produksi" }
        val session = resolveSession(token, localUserId, remoteUserUuid)
        require(page == null || page > 0) { "Nomor halaman tidak valid" }
        val fullUnfilteredSync = page == null && serviceType.isNullOrBlank() && status.isNullOrBlank()
        val verified = requireNotNull(session.verified)
        val previousCache = if (fullUnfilteredSync) {
            protectedCache(session) {
                cacheDao.getApplicationsByUserAndRemoteUuid(verified.localUserId, verified.remoteUserUuid).first()
            }
        } else emptyList()
        // Explicit page requests retain their existing single-page semantics.
        val collected = linkedMapOf<String, LaravelApplicationDto>()
        var requestedPage = page ?: 1
        var expectedLastPage: Int? = null
        var expectedTotal: Int? = null
        do {
            ensureCurrent(session)
            val response = authorizedRequest(session) { applicationApiService.getApplications(serviceType, status, null, requestedPage, session.token) }
            ensureCurrent(session)
            require(response.currentPage == requestedPage && response.lastPage >= 1) {
                "Metadata pagination tidak sesuai request"
            }
            if (page == null) {
                require(requestedPage <= response.lastPage && response.lastPage <= 1000) {
                    "Batas pagination tidak valid"
                }
                require(expectedLastPage == null || expectedLastPage == response.lastPage) {
                    "Jumlah halaman berubah selama sinkronisasi; ulangi refresh"
                }
                require(expectedTotal == null || expectedTotal == response.total) {
                    "Jumlah pengajuan berubah selama sinkronisasi; ulangi refresh"
                }
                expectedLastPage = response.lastPage
                expectedTotal = response.total
            }
            for (item in response.data) {
                val dto = validateDto(item, session, expectedService = serviceType)
                require(status.isNullOrBlank() || dto.status == status) { "Status respons tidak sesuai filter" }
                require(collected.put(dto.id, dto) == null) { "Duplikasi pengajuan selama pagination; ulangi refresh" }
            }
            if (page != null || requestedPage >= response.lastPage) break
            require(response.data.isNotEmpty()) { "Halaman pengajuan kosong sebelum halaman terakhir" }
            requestedPage++
        } while (true)
        if (page == null) {
            require(collected.size == expectedTotal) { "Jumlah pengajuan tidak sesuai metadata pagination" }
        }
        // Absence from an offset-paginated list is not proof of deletion.
        // Confirm each missing cached record with the authenticated detail endpoint.
        val confirmedMissing = mutableListOf<ApplicationEntity>()
        for (cached in previousCache) {
            val uuid = cached.remoteUuid ?: continue
            if (!isApplicationUuid(uuid) || uuid in collected) continue
            ensureCurrent(session)
            try {
                val response = authorizedRequest(session) {
                    applicationApiService.getApplicationDetail(uuid, session.token)
                }
                ensureCurrent(session)
                val dto = validateDto(response.data, session, uuid, requireDetail = true)
                collected[uuid] = dto
            } catch (e: LaravelApiException) {
                if (e.statusCode != 404) throw e
                ensureCurrent(session)
                confirmedMissing.add(cached)
            }
        }
        // No write or deletion until all pages and missing-record checks succeed.
        protectedCache(session) {
            val persisted = collected.values.map { persistDto(it, session) }
            for (cached in confirmedMissing) {
                val uuid = requireNotNull(cached.remoteUuid)
                val current = cacheDao.getApplicationByRemoteUuid(uuid, verified.localUserId, verified.remoteUserUuid)
                // Do not delete a cache row changed by another local operation meanwhile.
                if (current == cached) {
                    cacheDao.deleteApplicationByRemoteUuid(uuid, verified.localUserId, verified.remoteUserUuid)
                }
            }
            persisted
        }
    }

    suspend fun getApplicationDetail(
        applicationUuid: String, localUserId: Int? = null,
        remoteUserUuid: String? = null, token: String? = null
    ): Result<LaravelApplicationSingleResponse> = resultOf {
        require(isApplicationUuid(applicationUuid)) { "UUID pengajuan tidak valid" }
        val session = resolveSession(token, localUserId, remoteUserUuid)
        val response = authorizedRequest(session) { applicationApiService.getApplicationDetail(applicationUuid, session.token) }
        ensureCurrent(session)
        val dto = validateDto(response.data, session, applicationUuid, requireDetail = !remoteOnly)
        if (!remoteOnly) protectedCache(session) { persistDto(dto, session) }
        response
    }

    suspend fun createKiaApplicationWithDocuments(
        context: Context?, request: LaravelKiaApplicationRequest,
        documents: Map<String, SelectedDocument?> = emptyMap(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationCreateResult> = createApplicationGeneric(
        context, "KIA", documents, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.createKiaApplication(request, activeToken)
    }

    suspend fun createKtpApplicationWithDocuments(
        context: Context?, request: LaravelKtpApplicationRequest,
        documents: Map<String, SelectedDocument?> = emptyMap(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationCreateResult> = createApplicationGeneric(
        context, "KTP", documents, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.createKtpApplication(request, activeToken)
    }

    suspend fun createKkApplicationWithDocuments(
        context: Context?, request: LaravelKkApplicationRequest,
        documents: Map<String, SelectedDocument?> = emptyMap(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationCreateResult> = createApplicationGeneric(
        context, "KK", documents, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.createKkApplication(request, activeToken)
    }

    suspend fun createAktaApplicationWithDocuments(
        context: Context?, request: LaravelAktaApplicationRequest,
        documents: Map<String, SelectedDocument?> = emptyMap(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationCreateResult> = createApplicationGeneric(
        context, "AKTA", documents, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.createAktaApplication(request, activeToken)
    }

    suspend fun createDisabilitasApplicationWithDocuments(
        context: Context?, request: LaravelDisabilitasApplicationRequest,
        documents: Map<String, SelectedDocument?> = emptyMap(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationCreateResult> = createApplicationGeneric(
        context, "DISABILITAS", documents, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.createDisabilitasApplication(request, activeToken)
    }

    private suspend fun createApplicationGeneric(
        context: Context?, serviceType: String, documents: Map<String, SelectedDocument?>,
        localUserId: Int?, remoteUserUuid: String?, token: String?,
        inputStreamSupplier: ((Uri) -> InputStream?)?,
        callApiService: suspend (String) -> LaravelApplicationSingleResponse
    ): Result<LaravelApplicationCreateResult> = resultOf {
        val session = resolveSession(token, localUserId, remoteUserUuid)
        preValidateDocuments(context, serviceType, documents, emptyList(), inputStreamSupplier)
        ensureCurrent(session)
        val response = sendMutation { authorizedRequest(session) { callApiService(session.token) } }
        // A 2xx with missing/conflicting metadata is not a safe reason to repeat POST.
        val dto = try {
            validateDto(response.data, session, expectedService = serviceType)
        } catch (e: Exception) {
            return@resultOf LaravelApplicationCreateResult(
                isFullySuccessful = false,
                applicationUuid = null,
                submissionCode = null,
                applicationDto = null,
                errorMessage = "Server merespons mutasi, tetapi identitas pengajuan tidak dapat diverifikasi. Jangan ulangi POST sebelum rekonsiliasi.",
                cacheSyncStatus = LaravelCacheSyncStatus.REFRESH_REQUIRED,
                cacheError = "Metadata respons tidak valid",
                remoteOutcome = LaravelRemoteOutcome.UNCERTAIN
            )
        }
        val uploaded = linkedMapOf<String, String>()
        val failed = linkedMapOf<String, String>()
        val uncertainSlots = linkedSetOf<String>()
        uploadDocuments(context, dto.id, documents, session, inputStreamSupplier, uploaded, failed, uncertainSlots)
        val refresh = refreshAfterMutation(dto.id, session, serviceType)
        val complete = failed.isEmpty() && refreshSuccessful(refresh.status)
        LaravelApplicationCreateResult(
            isFullySuccessful = complete,
            applicationUuid = dto.id,
            submissionCode = dto.submissionCode,
            applicationDto = refresh.dto,
            uploadedSlots = uploaded.toMap(),
            failedSlots = failed.toMap(),
            errorMessage = mutationMessage(failed, refresh),
            cacheSyncStatus = refresh.status,
            cacheError = refresh.error,
            uncertainSlots = uncertainSlots.toSet()
        )
    }

    private fun refreshSuccessful(status: LaravelCacheSyncStatus): Boolean =
        status == LaravelCacheSyncStatus.SYNCED || status == LaravelCacheSyncStatus.NOT_REQUESTED

    private fun mutationMessage(failed: Map<String, String>, refresh: RefreshResult): String? {
        val messages = mutableListOf<String>()
        if (failed.isNotEmpty()) messages.add("Mutasi formulir berhasil, tetapi ${failed.size} dokumen belum terkonfirmasi berhasil.")
        refresh.error?.let { messages.add(it) }
        return messages.joinToString(" ").ifBlank { null }
    }

    private suspend fun uploadDocuments(
        context: Context?, uuid: String, documents: Map<String, SelectedDocument?>,
        session: OperationSession, inputStreamSupplier: ((Uri) -> InputStream?)?,
        uploaded: MutableMap<String, String>, failed: MutableMap<String, String>,
        uncertainSlots: MutableSet<String>
    ) {
        for ((slot, doc) in documents) {
            if (doc == null) continue
            if (!sessionStillCurrent(session)) {
                failed[slot] = "Sesi berubah; dokumen belum dikirim"
                continue
            }
            val result = documentRepository.uploadOrReplaceDocument(context, uuid, slot, doc, session.token, inputStreamSupplier)
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            if (error != null) handleUnauthorized(session, error)
            if (error != null) {
                failed[slot] = "Dokumen belum terkonfirmasi berhasil"
                if (uncertain(error)) uncertainSlots.add(slot)
            } else {
                val metadata = result.getOrNull()?.data
                val path = metadata?.path
                if (path.isNullOrBlank() || metadata?.slot != slot) {
                    failed[slot] = "Metadata dokumen dari server tidak valid"
                    uncertainSlots.add(slot)
                } else {
                    uploaded[slot] = path
                }
            }
        }
    }

    // --- UPDATE WITH DOCUMENTS (5 SERVICES) ---

    suspend fun updateKiaApplicationWithDocuments(
        context: Context?, applicationUuid: String, request: LaravelKiaApplicationRequest,
        replacementDocuments: Map<String, SelectedDocument?> = emptyMap(), deletedSlots: List<String> = emptyList(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationUpdateResult> = updateApplicationGeneric(
        context, "KIA", applicationUuid, replacementDocuments, deletedSlots, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.updateKiaApplication(applicationUuid, request, activeToken)
    }

    suspend fun updateKtpApplicationWithDocuments(
        context: Context?, applicationUuid: String, request: LaravelKtpApplicationRequest,
        replacementDocuments: Map<String, SelectedDocument?> = emptyMap(), deletedSlots: List<String> = emptyList(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationUpdateResult> = updateApplicationGeneric(
        context, "KTP", applicationUuid, replacementDocuments, deletedSlots, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.updateKtpApplication(applicationUuid, request, activeToken)
    }

    suspend fun updateKkApplicationWithDocuments(
        context: Context?, applicationUuid: String, request: LaravelKkApplicationRequest,
        replacementDocuments: Map<String, SelectedDocument?> = emptyMap(), deletedSlots: List<String> = emptyList(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationUpdateResult> = updateApplicationGeneric(
        context, "KK", applicationUuid, replacementDocuments, deletedSlots, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.updateKkApplication(applicationUuid, request, activeToken)
    }

    suspend fun updateAktaApplicationWithDocuments(
        context: Context?, applicationUuid: String, request: LaravelAktaApplicationRequest,
        replacementDocuments: Map<String, SelectedDocument?> = emptyMap(), deletedSlots: List<String> = emptyList(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationUpdateResult> = updateApplicationGeneric(
        context, "AKTA", applicationUuid, replacementDocuments, deletedSlots, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.updateAktaApplication(applicationUuid, request, activeToken)
    }

    suspend fun updateDisabilitasApplicationWithDocuments(
        context: Context?, applicationUuid: String, request: LaravelDisabilitasApplicationRequest,
        replacementDocuments: Map<String, SelectedDocument?> = emptyMap(), deletedSlots: List<String> = emptyList(),
        localUserId: Int? = null, remoteUserUuid: String? = null,
        token: String? = null, inputStreamSupplier: ((Uri) -> InputStream?)? = null
    ): Result<LaravelApplicationUpdateResult> = updateApplicationGeneric(
        context, "DISABILITAS", applicationUuid, replacementDocuments, deletedSlots, localUserId, remoteUserUuid, token, inputStreamSupplier
    ) { activeToken ->
        applicationApiService.updateDisabilitasApplication(applicationUuid, request, activeToken)
    }

    private suspend fun updateApplicationGeneric(
        context: Context?, serviceType: String, applicationUuid: String,
        replacementDocuments: Map<String, SelectedDocument?>, deletedSlots: List<String>,
        localUserId: Int?, remoteUserUuid: String?, token: String?,
        inputStreamSupplier: ((Uri) -> InputStream?)?,
        callUpdateApiService: suspend (String) -> LaravelApplicationSingleResponse
    ): Result<LaravelApplicationUpdateResult> = resultOf {
        require(isApplicationUuid(applicationUuid)) { "UUID pengajuan tidak valid" }
        val session = resolveSession(token, localUserId, remoteUserUuid)
        preValidateDocuments(context, serviceType, replacementDocuments, deletedSlots, inputStreamSupplier)
        ensureCurrent(session)
        val response = sendMutation { authorizedRequest(session) { callUpdateApiService(session.token) } }
        val updatedDto = try {
            validateDto(response.data, session, applicationUuid, serviceType)
        } catch (e: Exception) {
            return@resultOf LaravelApplicationUpdateResult(
                isFullySuccessful = false,
                applicationUuid = applicationUuid,
                isFormUpdated = false,
                applicationDto = null,
                errorMessage = "Server merespons mutasi, tetapi identitas respons tidak dapat diverifikasi. Periksa data sebelum mengulang.",
                cacheSyncStatus = LaravelCacheSyncStatus.REFRESH_REQUIRED,
                cacheError = "Metadata respons tidak valid",
                remoteOutcome = LaravelRemoteOutcome.UNCERTAIN
            )
        }
        val deleted = mutableListOf<String>()
        val uploaded = linkedMapOf<String, String>()
        val failed = linkedMapOf<String, String>()
        val uncertainSlots = linkedSetOf<String>()
        for (slot in deletedSlots.distinct()) {
            if (!sessionStillCurrent(session)) {
                failed[slot] = "Sesi berubah; penghapusan belum dikirim"
                continue
            }
            val result = documentRepository.deleteDocument(applicationUuid, slot, session.token)
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            if (error != null) handleUnauthorized(session, error)
            if (error == null) {
                deleted.add(slot)
            } else {
                failed[slot] = "Penghapusan dokumen belum terkonfirmasi berhasil"
                if (uncertain(error)) uncertainSlots.add(slot)
            }
        }
        uploadDocuments(context, applicationUuid, replacementDocuments, session, inputStreamSupplier, uploaded, failed, uncertainSlots)
        val refresh = refreshAfterMutation(applicationUuid, session, serviceType)
        LaravelApplicationUpdateResult(
            isFullySuccessful = failed.isEmpty() && refreshSuccessful(refresh.status),
            applicationUuid = applicationUuid,
            isFormUpdated = true,
            applicationDto = refresh.dto,
            uploadedSlots = uploaded.toMap(),
            deletedSlots = deleted.toList(),
            submissionCode = updatedDto.submissionCode,
            failedSlots = failed.toMap(),
            errorMessage = mutationMessage(failed, refresh),
            cacheSyncStatus = refresh.status,
            cacheError = refresh.error,
            uncertainSlots = uncertainSlots.toSet()
        )
    }

    // --- DELETE & DOWNLOAD ---

    suspend fun deleteApplication(
        applicationUuid: String, callerLocalUserId: Int? = null, callerRemoteUserUuid: String? = null, token: String? = null
    ): Result<LaravelDeleteResult> = resultOf {
        require(isApplicationUuid(applicationUuid)) { "UUID pengajuan tidak valid" }
        val session = resolveSession(token, callerLocalUserId, callerRemoteUserUuid)
        ensureCurrent(session)
        val response = sendMutation { authorizedRequest(session) { applicationApiService.deleteApplication(applicationUuid, session.token) } }
        if (remoteOnly) {
            LaravelDeleteResult(response, applicationUuid, LaravelCacheSyncStatus.NOT_REQUESTED)
        } else {
            try {
                protectedCache(session) {
                    val verified = requireNotNull(session.verified)
                    cacheDao.deleteApplicationByRemoteUuid(applicationUuid, verified.localUserId, verified.remoteUserUuid)
                }
                LaravelDeleteResult(response, applicationUuid, LaravelCacheSyncStatus.SYNCED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val changed = !sessionStillCurrent(session)
                LaravelDeleteResult(response, applicationUuid,
                    if (changed) LaravelCacheSyncStatus.SESSION_CHANGED else LaravelCacheSyncStatus.REFRESH_REQUIRED,
                    "Pengajuan telah dihapus di server; cache lokal belum tersinkronisasi")
            }
        }
    }

    suspend fun downloadDocument(
        applicationUuid: String, slot: String, token: String? = null
    ): Result<LaravelDocumentDownloadResult> = resultOf {
        require(isApplicationUuid(applicationUuid)) { "UUID pengajuan tidak valid" }
        val session = resolveSession(token)
        ensureCurrent(session)
        val response = authorizedRequest(session) { documentRepository.downloadDocument(applicationUuid, slot, session.token).getOrThrow() }
        ensureCurrent(session)
        response
    }

    suspend fun deleteDocument(
        applicationUuid: String, slot: String,
        callerLocalUserId: Int? = null, callerRemoteUserUuid: String? = null, token: String? = null
    ): Result<LaravelDeleteResult> = resultOf {
        require(isApplicationUuid(applicationUuid)) { "UUID pengajuan tidak valid" }
        require(SERVICE_SLOTS.values.any { slot in it }) { "Slot dokumen tidak valid" }
        val session = resolveSession(token, callerLocalUserId, callerRemoteUserUuid)
        ensureCurrent(session)
        val response = sendMutation { authorizedRequest(session) { documentRepository.deleteDocument(applicationUuid, slot, session.token).getOrThrow() } }
        if (remoteOnly) {
            LaravelDeleteResult(response, applicationUuid, LaravelCacheSyncStatus.NOT_REQUESTED)
        } else {
            val refresh = refreshAfterMutation(applicationUuid, session)
            LaravelDeleteResult(response, applicationUuid, refresh.status, refresh.error)
        }
    }
}
