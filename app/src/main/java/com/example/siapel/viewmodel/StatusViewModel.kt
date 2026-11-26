package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.remote.laravel.dto.LaravelDocumentDownloadResult
import com.example.siapel.data.repository.LaravelApplicationRepository
import com.example.siapel.data.repository.LaravelAuthRepository
import com.example.siapel.data.repository.VerifiedLaravelSession
import com.example.siapel.data.repository.LaravelMutationUncertainException
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.ApplicationStatusItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LaravelDocumentOpenEvent(
    val document: LaravelDocumentDownloadResult,
    val sessionGeneration: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatusViewModel(
    private val applicationRepository: LaravelApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: LaravelAuthRepository
) : ViewModel() {

    // ==========================================
    // EXISTING STATUS UI STATE
    // ==========================================

    private val _selectedFilter =
        MutableStateFlow("Semua")

    val selectedFilter: StateFlow<String> =
        _selectedFilter.asStateFlow()

    private val _isRefreshing =
        MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> =
        _isRefreshing.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    private val _cacheError = MutableStateFlow<String?>(null)
    val cacheError: StateFlow<String?> = _cacheError.asStateFlow()

    private val _cacheReady = MutableStateFlow(false)
    val cacheReady: StateFlow<Boolean> = _cacheReady.asStateFlow()

    private val cacheReloadRequests = MutableStateFlow(0L)

    private val _isDeleting =
        MutableStateFlow(false)

    val isDeleting: StateFlow<Boolean> =
        _isDeleting.asStateFlow()

    private val _deleteResult =
        MutableSharedFlow<Boolean>()

    val deleteResult: SharedFlow<Boolean> =
        _deleteResult.asSharedFlow()

    private val _operationError =
        MutableSharedFlow<String>()

    val operationError: SharedFlow<String> =
        _operationError.asSharedFlow()

    // ==========================================
    // PRIVATE DOCUMENT STATE
    // ==========================================

    private val _documentViewLoading =
        MutableStateFlow(false)

    val documentViewLoading: StateFlow<Boolean> =
        _documentViewLoading.asStateFlow()

    private val _documentViewError =
        MutableSharedFlow<String>()

    val documentViewError: SharedFlow<String> =
        _documentViewError.asSharedFlow()

    private val _openDocument =
        MutableSharedFlow<LaravelDocumentOpenEvent>()

    val openDocument: SharedFlow<LaravelDocumentOpenEvent> =
        _openDocument.asSharedFlow()

    // ==========================================
    // VERIFIED REACTIVE CACHE
    // ==========================================

    private val cachedApplications: Flow<List<ApplicationEntity>> =
        combine(
            userPreferencesRepository.userSessionFlow.distinctUntilChanged(),
            cacheReloadRequests
        ) { storedSession, _ -> storedSession }
            .flatMapLatest { storedSession ->

                flow {
                    _cacheReady.value = false
                    _cacheError.value = null
                    emit(emptyList<ApplicationEntity>())

                    if (!storedSession.isLoggedIn) {
                        _cacheError.value = "Sesi pengguna tidak aktif. Silakan login ulang."
                        return@flow
                    }

                    val session =
                        authRepository.getVerifiedLaravelSession()
                            ?: run {
                                _cacheError.value = "Sesi pengguna belum dapat diverifikasi."
                                return@flow
                            }

                    if (
                        session.localUserId != storedSession.userId ||
                        session.remoteUserUuid != storedSession.remoteUserUuid ||
                        session.token != storedSession.token ||
                        session.generation != storedSession.generation
                    ) {
                        _cacheError.value = "Sesi pengguna telah berubah."
                        return@flow
                    }

                    emitAll(
                        applicationRepository
                            .observeCachedApplications(session)
                            .onEach { _cacheReady.value = true }
                    )

                }.catch { error ->

                    if (error is CancellationException) {
                        throw error
                    }

                    _cacheReady.value = false
                    _cacheError.value = "Cache pengajuan belum dapat dimuat."
                    emit(emptyList<ApplicationEntity>())
                }
            }

    // ==========================================
    // STATUS LIST AND FILTER
    // ==========================================

    val filteredApplications:
            StateFlow<List<ApplicationStatusItem>> =
        cachedApplications
            .map { entities ->

                entities.map { entity ->

                    ApplicationStatusItem(
                        code = entity.submissionCode,
                        serviceName = entity.serviceType,
                        category = entity.category,
                        submittedDate = entity.submissionDate,
                        status = entity.status,
                        note = entity.note
                    )
                }
            }
            .combine(_selectedFilter) { apps, filter ->

                when (filter) {

                    "Diajukan" ->
                        apps.filter {
                            it.status ==
                                    ApplicationStatus.SUBMITTED
                        }

                    "Sedang Diproses" ->
                        apps.filter {
                            it.status ==
                                    ApplicationStatus.PROCESSING
                        }

                    "Selesai" ->
                        apps.filter {
                            it.status ==
                                    ApplicationStatus.COMPLETED
                        }

                    "Perlu Perbaikan" ->
                        apps.filter {
                            it.status ==
                                    ApplicationStatus.NEED_REVISION
                        }

                    else -> apps
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                emptyList()
            )

    fun updateFilter(filter: String) {
        _selectedFilter.value = filter
    }

    // ==========================================
    // SESSION VALIDATION
    // ==========================================

    private suspend fun requireSession():
            VerifiedLaravelSession {

        return requireNotNull(
            authRepository.getVerifiedLaravelSession()
        ) {
            "Sesi pengguna tidak valid. Silakan login ulang."
        }
    }

    private suspend fun requireCurrentSession(
        session: VerifiedLaravelSession
    ) {
        check(
            authRepository.isVerifiedSessionCurrent(
                session
            )
        ) {
            "Sesi pengguna telah berubah"
        }
    }

    private fun requireOwnedApplication(
        application: ApplicationEntity,
        session: VerifiedLaravelSession
    ): String {

        require(
            application.userId == session.localUserId &&
                    application.remoteUserUuid ==
                    session.remoteUserUuid
        ) {
            "Pengajuan tidak sesuai dengan pengguna aktif"
        }

        return requireNotNull(
            application.remoteUuid
        ).also { uuid ->
            require(uuid.isNotBlank()) {
                "UUID pengajuan tidak tersedia"
            }
        }
    }

    // ==========================================
    // MANUAL REFRESH â€” ALL FIVE SERVICES
    // ==========================================

    fun refreshApplications() {

        if (_isRefreshing.value) return

        _isRefreshing.value = true
        _refreshError.value = null
        if (_cacheError.value != null) {
            cacheReloadRequests.update { it + 1L }
        }

        viewModelScope.launch {

            try {

                val session = requireSession()

                applicationRepository
                    .syncApplicationsRemoteAndCache(
                        localUserId = session.localUserId,
                        remoteUserUuid =
                            session.remoteUserUuid,
                        token = session.token
                    )
                    .getOrThrow()

                requireCurrentSession(session)

            } catch (e: CancellationException) {
                throw e

            } catch (_: Exception) {

                _refreshError.value = "Sinkronisasi pengajuan belum berhasil"
                _operationError.emit(
                    "Sinkronisasi pengajuan belum berhasil"
                )

            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ==========================================
    // APPLICATION DETAIL â€” REACTIVE ROOM
    // ==========================================

    suspend fun refreshApplicationDetail(code: String) {
        val session = requireSession()
        val application = requireNotNull(
            applicationRepository.observeCachedApplications(session).first()
                .firstOrNull { it.submissionCode == code }
        ) { "Pengajuan belum tersedia di cache" }
        val uuid = requireOwnedApplication(application, session)
        requireCurrentSession(session)
        applicationRepository.getApplicationDetail(
            applicationUuid = uuid,
            localUserId = session.localUserId,
            remoteUserUuid = session.remoteUserUuid,
            token = session.token
        ).getOrThrow()
        requireCurrentSession(session)
    }

    fun getApplicationByCodeFlow(
        code: String
    ): Flow<ApplicationEntity?> {

        return cachedApplications
            .map { applications ->

                applications.firstOrNull {
                    it.submissionCode == code
                }
            }
            .distinctUntilChanged()
    }

    // ==========================================
    // DELETE APPLICATION â€” ALL FIVE SERVICES
    // ==========================================

    fun deleteApplication(
        application: ApplicationEntity
    ) {

        if (_isDeleting.value) return

        _isDeleting.value = true

        viewModelScope.launch {

            var session: VerifiedLaravelSession? = null

            try {

                val activeSession = requireSession()
                session = activeSession

                val uuid = requireOwnedApplication(
                    application,
                    activeSession
                )

                requireCurrentSession(activeSession)

                val result =
                    applicationRepository.deleteApplication(
                        applicationUuid = uuid,
                        callerLocalUserId =
                            activeSession.localUserId,
                        callerRemoteUserUuid =
                            activeSession.remoteUserUuid,
                        token = activeSession.token
                    ).getOrThrow()

                requireCurrentSession(activeSession)

                if (result.refreshRequired) {

                    _operationError.emit(
                        "Pengajuan telah dihapus di server. " +
                                "Cache lokal perlu disinkronkan ulang."
                    )
                }

                // Server deletion confirmed.
                // Do not repeat DELETE merely because
                // local cache synchronization failed.

                _deleteResult.emit(true)

            } catch (e: CancellationException) {
                throw e

            } catch (e: LaravelMutationUncertainException) {

                val activeSession = session

                if (
                    activeSession != null &&
                    authRepository.isVerifiedSessionCurrent(
                        activeSession
                    )
                ) {

                    _operationError.emit(
                        "Hasil penghapusan belum dapat dipastikan. " +
                                "Periksa status pengajuan sebelum mengulang."
                    )

                    _deleteResult.emit(false)
                }

            } catch (_: Exception) {

                val activeSession = session

                if (
                    activeSession == null ||
                    authRepository.isVerifiedSessionCurrent(
                        activeSession
                    )
                ) {

                    _operationError.emit(
                        "Pengajuan belum berhasil dihapus"
                    )

                    _deleteResult.emit(false)
                }

            } finally {
                _isDeleting.value = false
            }
        }
    }

    // ==========================================
    // DOCUMENT SLOT VALIDATION
    // ==========================================

    private val serviceSlots = mapOf(
        "KIA" to setOf(
            "doc1", "doc2", "doc3", "docSelfie"
        ),

        "KTP" to setOf(
            "doc1", "doc2", "docSelfie"
        ),

        "KK" to (
                (1..10).map { "doc$it" } +
                        "docSelfie"
                ).toSet(),

        "AKTA" to (
                (1..9).map { "doc$it" } +
                        "docSelfie"
                ).toSet(),

        "DISABILITAS" to setOf(
            "doc1", "doc2", "docSelfie"
        )
    )

    // ==========================================
    // PRIVATE LARAVEL DOCUMENT DOWNLOAD
    // ==========================================

    private fun viewDocument(
        submissionCode: String,
        documentSlot: String,
        serviceType: String
    ) {

        if (_documentViewLoading.value) return

        _documentViewLoading.value = true

        viewModelScope.launch {

            var session: VerifiedLaravelSession? = null

            try {

                val activeSession = requireSession()
                session = activeSession

                require(
                    serviceSlots[serviceType]
                        ?.contains(documentSlot) == true
                ) {
                    "Slot dokumen tidak valid"
                }

                val applications =
                    applicationRepository
                        .observeCachedApplications(
                            activeSession
                        )
                        .first()

                val application = requireNotNull(
                    applications.firstOrNull {
                        it.submissionCode == submissionCode &&
                                it.serviceType.equals(
                                    serviceType,
                                    ignoreCase = true
                                )
                    }
                ) {
                    "Pengajuan tidak ditemukan"
                }

                val uuid = requireOwnedApplication(
                    application,
                    activeSession
                )

                requireCurrentSession(activeSession)

                val document =
                    applicationRepository.downloadDocument(
                        applicationUuid = uuid,
                        slot = documentSlot,
                        token = activeSession.token
                    ).getOrThrow()

                requireCurrentSession(activeSession)

                require(
                    document.bytes.isNotEmpty()
                ) {
                    "Dokumen kosong"
                }

                require(
                    document.bytes.size <= 2 * 1024 * 1024
                ) {
                    "Ukuran dokumen melebihi batas"
                }

                _openDocument.emit(
                    LaravelDocumentOpenEvent(
                        document = document,
                        sessionGeneration =
                            activeSession.generation
                    )
                )

            } catch (e: CancellationException) {
                throw e

            } catch (_: Exception) {

                val activeSession = session

                if (
                    activeSession == null ||
                    authRepository.isVerifiedSessionCurrent(
                        activeSession
                    )
                ) {
                    _documentViewError.emit(
                        "Gagal membuka dokumen $serviceType"
                    )
                }

            } finally {
                _documentViewLoading.value = false
            }
        }
    }

    // ==========================================
    // DOCUMENT EVENT SESSION GUARD
    // ==========================================

    suspend fun isDocumentSessionCurrent(
        event: LaravelDocumentOpenEvent
    ): Boolean {

        val session =
            authRepository.getVerifiedLaravelSession()
                ?: return false

        return session.generation ==
                event.sessionGeneration &&
                authRepository.isVerifiedSessionCurrent(
                    session
                )
    }

    // ==========================================
    // EXISTING PUBLIC DOCUMENT METHODS
    // ==========================================

    fun viewKiaDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        viewDocument(
            submissionCode,
            documentSlot,
            "KIA"
        )
    }

    fun viewKtpDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        viewDocument(
            submissionCode,
            documentSlot,
            "KTP"
        )
    }

    fun viewKkDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        viewDocument(
            submissionCode,
            documentSlot,
            "KK"
        )
    }

    fun viewAktaDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        viewDocument(
            submissionCode,
            documentSlot,
            "AKTA"
        )
    }

    fun viewDisabilitasDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        viewDocument(
            submissionCode,
            documentSlot,
            "DISABILITAS"
        )
    }
}
