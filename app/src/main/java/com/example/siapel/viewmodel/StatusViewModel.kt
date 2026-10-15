package com.example.siapel.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.remote.dto.KiaDetailRemoteDto
import com.example.siapel.data.remote.dto.KtpDetailRemoteDto
import com.example.siapel.data.remote.dto.KkDetailRemoteDto
import com.example.siapel.data.remote.dto.AktaDetailRemoteDto
import com.example.siapel.data.remote.dto.DisabilitasDetailRemoteDto
import com.example.siapel.data.repository.ApplicationRepository
import com.example.siapel.data.repository.AuthRepository
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.ApplicationStatusItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val TAG = "SIAPEL_SUPABASE"

private data class CachedSignedUrl(
    val url: String,
    val expiresAtMillis: Long
)

class StatusViewModel(
    private val applicationRepository: ApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("Semua")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteResult = MutableSharedFlow<Boolean>()
    val deleteResult: SharedFlow<Boolean> = _deleteResult.asSharedFlow()

    // Document View States & Events
    private val _documentViewLoading = MutableStateFlow(false)
    val documentViewLoading: StateFlow<Boolean> = _documentViewLoading.asStateFlow()

    private val _documentViewError = MutableSharedFlow<String>()
    val documentViewError: SharedFlow<String> = _documentViewError.asSharedFlow()

    private val _openDocumentUrl = MutableSharedFlow<String>()
    val openDocumentUrl: SharedFlow<String> = _openDocumentUrl.asSharedFlow()

    // In-memory cache for KiaDetailRemoteDto
    private var cachedKiaDetailCode: String? = null
    private var cachedKiaDetail: KiaDetailRemoteDto? = null

    // In-memory cache for KtpDetailRemoteDto
    private var cachedKtpDetailCode: String? = null
    private var cachedKtpDetail: KtpDetailRemoteDto? = null

    // In-memory cache for KkDetailRemoteDto
    private var cachedKkDetailCode: String? = null
    private var cachedKkDetail: KkDetailRemoteDto? = null

    // In-memory cache for AktaDetailRemoteDto
    private var cachedAktaDetailCode: String? = null
    private var cachedAktaDetail: AktaDetailRemoteDto? = null

    // In-memory cache for DisabilitasDetailRemoteDto
    private var cachedDisabilitasDetailCode: String? = null
    private var cachedDisabilitasDetail: DisabilitasDetailRemoteDto? = null

    // In-memory cache for Signed URLs (240 seconds validity)
    private val signedUrlCache = mutableMapOf<String, CachedSignedUrl>()

    val filteredApplications: StateFlow<List<ApplicationStatusItem>> = userPreferencesRepository.userSessionFlow
        .flatMapLatest { session ->
            if (session.isLoggedIn) {
                applicationRepository.getApplicationsByUser(session.userId)
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
            } else {
                flowOf(emptyList())
            }
        }
        .combine(_selectedFilter) { apps, filter ->
            when (filter) {
                "Diajukan" -> apps.filter { it.status == ApplicationStatus.SUBMITTED }
                "Sedang Diproses" -> apps.filter { it.status == ApplicationStatus.PROCESSING }
                "Selesai" -> apps.filter { it.status == ApplicationStatus.COMPLETED }
                "Perlu Perbaikan" -> apps.filter { it.status == ApplicationStatus.NEED_REVISION }
                else -> apps
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun refreshApplications() {
        viewModelScope.launch {
            _isRefreshing.value = true
            Log.d(TAG, "Manual refresh started")
            try {
                val session = userPreferencesRepository.userSessionFlow.first()
                if (session.isLoggedIn) {
                    val localUserId = session.userId
                    var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()

                    if (currentUuid.isNullOrEmpty()) {
                        authRepository.validateSessionWithServer()
                        currentUuid = authRepository.getCurrentSupabaseUser()?.id
                            ?: authRepository.getCurrentSupabaseSessionUserId()
                    }

                    if (!currentUuid.isNullOrEmpty()) {
                        Log.d(TAG, "UUID resolved")
                        val syncKiaSuccess = applicationRepository.syncKiaApplicationsRemote(
                            currentUuid = currentUuid,
                            localUserId = localUserId
                        )
                        
                        Log.d(TAG, "KTP remote sync start")
                        val syncKtpSuccess = applicationRepository.syncKtpApplicationsRemote(
                            currentUuid = currentUuid,
                            localUserId = localUserId
                        )

                        Log.d(TAG, "KK remote sync start")
                        val syncKkSuccess = applicationRepository.syncKkApplicationsRemote(
                            currentUuid = currentUuid,
                            localUserId = localUserId
                        )

                        Log.d(TAG, "AKTA remote sync start")
                        val syncAktaSuccess = applicationRepository.syncAktaApplicationsRemote(
                            currentUuid = currentUuid,
                            localUserId = localUserId
                        )

                        Log.d(TAG, "DISABILITAS remote sync start")
                        val syncDisabilitasSuccess = applicationRepository.syncDisabilitasApplicationsRemote(
                            currentUuid = currentUuid,
                            localUserId = localUserId
                        )

                        if (syncKiaSuccess || syncKtpSuccess || syncKkSuccess || syncAktaSuccess || syncDisabilitasSuccess) {
                            Log.d(TAG, "Manual refresh completed")
                        } else {
                            Log.e(TAG, "Manual refresh failed")
                        }
                    } else {
                        Log.e(TAG, "Manual refresh failed: UUID is NULL")
                    }
                } else {
                    Log.w(TAG, "Manual refresh skipped: User not logged in")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Manual refresh failed: ${e.message}", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun getApplicationByCodeFlow(code: String): Flow<ApplicationEntity?> {
        return applicationRepository.getApplicationByCodeFlow(code)
    }
    
    fun deleteApplication(application: ApplicationEntity) {
        viewModelScope.launch {
            _isDeleting.value = true
            Log.d("SIAPEL_DELETE", "Delete requested for code ${application.submissionCode}")
            Log.d("SIAPEL_DELETE", "AuthRepository injected = true")

            var isSuccess = false
            if (application.serviceType.equals("KIA", ignoreCase = true)) {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                if (!currentUuid.isNullOrEmpty()) {
                    Log.d("SIAPEL_DELETE", "UUID from currentUser: $currentUuid")
                } else {
                    currentUuid = authRepository.getCurrentSupabaseSessionUserId()
                    if (!currentUuid.isNullOrEmpty()) {
                        Log.d("SIAPEL_DELETE", "UUID from currentSession: $currentUuid")
                    }
                }

                if (currentUuid.isNullOrEmpty()) {
                    Log.d("SIAPEL_DELETE", "Session restore fallback started")
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (!currentUuid.isNullOrEmpty()) {
                    Log.d("SIAPEL_DELETE", "UUID resolved = true ($currentUuid)")
                    Log.d("SIAPEL_DELETE", "Remote delete start for code ${application.submissionCode}")

                    val remoteSuccess = applicationRepository.deleteKiaApplicationRemote(
                        submissionCode = application.submissionCode,
                        currentSupabaseUuid = currentUuid
                    )
                    if (remoteSuccess) {
                        Log.d("SIAPEL_DELETE", "Remote delete success for code ${application.submissionCode}")
                        Log.d("SIAPEL_DELETE", "Room delete start for code ${application.submissionCode}")
                        applicationRepository.deleteApplication(application)
                        Log.d("SIAPEL_DELETE", "Room delete success for code ${application.submissionCode}")
                        isSuccess = true
                        Log.d("SIAPEL_DELETE", "Delete completed for code ${application.submissionCode}")
                    } else {
                        Log.e("SIAPEL_DELETE", "Remote delete failed for code ${application.submissionCode}")
                        Log.e("SIAPEL_DELETE", "Delete failed for code ${application.submissionCode}")
                    }
                } else {
                    Log.e("SIAPEL_DELETE", "UUID resolved = false (NULL after fallback). Delete cancelled.")
                    Log.e("SIAPEL_DELETE", "Delete failed for code ${application.submissionCode}")
                }
            } else if (application.serviceType.equals("KTP", ignoreCase = true)) {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                    ?: authRepository.getCurrentSupabaseSessionUserId()

                if (currentUuid.isNullOrEmpty()) {
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (!currentUuid.isNullOrEmpty()) {
                    Log.d("SIAPEL_DELETE", "KTP remote delete start for code ${application.submissionCode}")
                    val remoteSuccess = applicationRepository.deleteKtpApplicationRemote(
                        submissionCode = application.submissionCode,
                        currentSupabaseUuid = currentUuid
                    )
                    if (remoteSuccess) {
                        Log.d("SIAPEL_DELETE", "KTP remote delete success for code ${application.submissionCode}")
                        applicationRepository.deleteApplication(application)
                        isSuccess = true
                    } else {
                        Log.e("SIAPEL_DELETE", "KTP remote delete failed for code ${application.submissionCode}")
                    }
                } else {
                    Log.e("SIAPEL_DELETE", "UUID resolved = false for KTP delete.")
                }
            } else if (application.serviceType.equals("AKTA", ignoreCase = true)) {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                    ?: authRepository.getCurrentSupabaseSessionUserId()

                if (currentUuid.isNullOrEmpty()) {
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (!currentUuid.isNullOrEmpty()) {
                    Log.d("SIAPEL_DELETE", "AKTA remote delete start for code ${application.submissionCode}")
                    val remoteSuccess = applicationRepository.deleteAktaApplicationRemote(
                        submissionCode = application.submissionCode,
                        currentSupabaseUuid = currentUuid
                    )
                    if (remoteSuccess) {
                        Log.d("SIAPEL_DELETE", "AKTA remote delete success for code ${application.submissionCode}")
                        applicationRepository.deleteApplication(application)
                        isSuccess = true
                    } else {
                        Log.e("SIAPEL_DELETE", "AKTA remote delete failed for code ${application.submissionCode}")
                    }
                } else {
                    Log.e("SIAPEL_DELETE", "UUID resolved = false for AKTA delete.")
                }
            } else if (application.serviceType.equals("KK", ignoreCase = true)) {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                    ?: authRepository.getCurrentSupabaseSessionUserId()

                if (currentUuid.isNullOrEmpty()) {
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (!currentUuid.isNullOrEmpty()) {
                    Log.d("SIAPEL_DELETE", "KK remote delete start for code ${application.submissionCode}")
                    val remoteSuccess = applicationRepository.deleteKkApplicationRemote(
                        submissionCode = application.submissionCode,
                        currentSupabaseUuid = currentUuid
                    )
                    if (remoteSuccess) {
                        Log.d("SIAPEL_DELETE", "KK remote delete success for code ${application.submissionCode}")
                        applicationRepository.deleteApplication(application)
                        isSuccess = true
                    } else {
                        Log.e("SIAPEL_DELETE", "KK remote delete failed for code ${application.submissionCode}")
                    }
                } else {
                    Log.e("SIAPEL_DELETE", "UUID resolved = false for KK delete.")
                }
            } else if (application.serviceType.equals("DISABILITAS", ignoreCase = true)) {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                    ?: authRepository.getCurrentSupabaseSessionUserId()

                if (currentUuid.isNullOrEmpty()) {
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (!currentUuid.isNullOrEmpty()) {
                    Log.d("SIAPEL_DELETE", "DISABILITAS remote delete start for code ${application.submissionCode}")
                    val remoteSuccess = applicationRepository.deleteDisabilitasApplicationRemote(
                        submissionCode = application.submissionCode,
                        currentSupabaseUuid = currentUuid
                    )
                    if (remoteSuccess) {
                        Log.d("SIAPEL_DELETE", "DISABILITAS remote delete success for code ${application.submissionCode}")
                        applicationRepository.deleteApplication(application)
                        isSuccess = true
                    } else {
                        Log.e("SIAPEL_DELETE", "DISABILITAS remote delete failed for code ${application.submissionCode}")
                    }
                } else {
                    Log.e("SIAPEL_DELETE", "UUID resolved = false for DISABILITAS delete.")
                }
            } else {
                // Non-migrated services: retain existing local Room delete
                Log.d("SIAPEL_DELETE", "Room delete start for code ${application.submissionCode}")
                applicationRepository.deleteApplication(application)
                Log.d("SIAPEL_DELETE", "Room delete success for code ${application.submissionCode}")
                isSuccess = true
                Log.d("SIAPEL_DELETE", "Delete completed for code ${application.submissionCode}")
            }

            _isDeleting.value = false
            _deleteResult.emit(isSuccess)
        }
    }

    fun viewKiaDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        val storageTag = "SIAPEL_STORAGE"
        Log.d(storageTag, "view document requested")
        Log.d(storageTag, "submissionCode = $submissionCode")
        Log.d(storageTag, "documentSlot = $documentSlot")

        val cacheKey = "$submissionCode-$documentSlot"
        val now = System.currentTimeMillis()
        val cacheDurationMillis = 240_000L // 240 seconds

        viewModelScope.launch {
            val cachedUrlObj = signedUrlCache[cacheKey]
            if (cachedUrlObj != null && now < cachedUrlObj.expiresAtMillis) {
                Log.d(storageTag, "signed url cache hit for $cacheKey")
                _openDocumentUrl.emit(cachedUrlObj.url)
                return@launch
            } else if (cachedUrlObj != null) {
                Log.d(storageTag, "signed url cache expired for $cacheKey")
            } else {
                Log.d(storageTag, "signed url cache miss for $cacheKey")
            }

            _documentViewLoading.value = true
            try {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                    ?: authRepository.getCurrentSupabaseSessionUserId()

                if (currentUuid.isNullOrEmpty()) {
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (currentUuid.isNullOrEmpty()) {
                    Log.e(storageTag, "UUID resolved = false")
                    _documentViewError.emit("Sesi pengguna tidak valid. Silakan login ulang.")
                    return@launch
                }
                Log.d(storageTag, "UUID resolved = true")

                val kiaDetail: KiaDetailRemoteDto = if (cachedKiaDetailCode == submissionCode && cachedKiaDetail != null) {
                    Log.d(storageTag, "cache hit")
                    cachedKiaDetail!!
                } else {
                    Log.d(storageTag, "cache miss")
                    val fetchResult = applicationRepository.fetchKiaDetailRemote(
                        submissionCode = submissionCode,
                        currentUuid = currentUuid
                    )
                    if (fetchResult.isSuccess) {
                        Log.d(storageTag, "fetch kia_detail success")
                        val detail = fetchResult.getOrThrow()
                        cachedKiaDetailCode = submissionCode
                        cachedKiaDetail = detail
                        detail
                    } else {
                        val errorMsg = fetchResult.exceptionOrNull()?.message ?: "Gagal mengambil detail dokumen."
                        Log.e(storageTag, "fetch kia_detail failed: $errorMsg")
                        _documentViewError.emit(errorMsg)
                        return@launch
                    }
                }

                val pathResult = applicationRepository.getKiaDocumentPathBySlot(kiaDetail, documentSlot)
                if (pathResult.isFailure) {
                    val errorMsg = pathResult.exceptionOrNull()?.message ?: "Dokumen belum tersedia."
                    Log.e(storageTag, "path failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                    return@launch
                }
                val storagePath = pathResult.getOrThrow()
                Log.d(storageTag, "path found")

                val urlResult = applicationRepository.createSignedUrlForStoragePath(storagePath)
                if (urlResult.isSuccess) {
                    val signedUrl = urlResult.getOrThrow()
                    Log.d(storageTag, "signed url generated success")
                    
                    signedUrlCache[cacheKey] = CachedSignedUrl(
                        url = signedUrl,
                        expiresAtMillis = System.currentTimeMillis() + cacheDurationMillis
                    )
                    Log.d(storageTag, "signed url cached for slot $documentSlot")

                    _openDocumentUrl.emit(signedUrl)
                } else {
                    val errorMsg = urlResult.exceptionOrNull()?.message ?: "Gagal membuat link dokumen."
                    Log.e(storageTag, "signed url generated failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(storageTag, "view document exception: ${e.message}", e)
                _documentViewError.emit("Terjadi kesalahan saat memuat dokumen: ${e.message}")
            } finally {
                _documentViewLoading.value = false
            }
        }
    }

    fun viewKtpDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        val storageTag = "SIAPEL_STORAGE"
        Log.d(storageTag, "KTP view document requested for code $submissionCode, slot $documentSlot")

        val cacheKey = "$submissionCode-$documentSlot"
        val now = System.currentTimeMillis()
        val cacheDurationMillis = 240_000L // 240 seconds

        viewModelScope.launch {
            val cachedUrlObj = signedUrlCache[cacheKey]
            if (cachedUrlObj != null && now < cachedUrlObj.expiresAtMillis) {
                Log.d(storageTag, "signed url cache hit for $cacheKey")
                _openDocumentUrl.emit(cachedUrlObj.url)
                return@launch
            } else if (cachedUrlObj != null) {
                Log.d(storageTag, "signed url cache expired for $cacheKey")
            } else {
                Log.d(storageTag, "signed url cache miss for $cacheKey")
            }

            _documentViewLoading.value = true
            try {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                    ?: authRepository.getCurrentSupabaseSessionUserId()

                if (currentUuid.isNullOrEmpty()) {
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (currentUuid.isNullOrEmpty()) {
                    Log.e(storageTag, "UUID resolved = false")
                    _documentViewError.emit("Sesi pengguna tidak valid. Silakan login ulang.")
                    return@launch
                }
                Log.d(storageTag, "UUID resolved = true")

                val ktpDetail: KtpDetailRemoteDto = if (cachedKtpDetailCode == submissionCode && cachedKtpDetail != null) {
                    Log.d(storageTag, "KTP cache hit")
                    cachedKtpDetail!!
                } else {
                    Log.d(storageTag, "KTP cache miss")
                    val fetchResult = applicationRepository.fetchKtpDetailRemote(
                        submissionCode = submissionCode,
                        currentUuid = currentUuid
                    )
                    if (fetchResult.isSuccess) {
                        Log.d(storageTag, "fetch ktp_detail success")
                        val detail = fetchResult.getOrThrow()
                        cachedKtpDetailCode = submissionCode
                        cachedKtpDetail = detail
                        detail
                    } else {
                        val errorMsg = fetchResult.exceptionOrNull()?.message ?: "Gagal mengambil detail dokumen KTP."
                        Log.e(storageTag, "fetch ktp_detail failed: $errorMsg")
                        _documentViewError.emit(errorMsg)
                        return@launch
                    }
                }

                val pathResult = applicationRepository.getKtpDocumentPathBySlot(ktpDetail, documentSlot)
                if (pathResult.isFailure) {
                    val errorMsg = pathResult.exceptionOrNull()?.message ?: "Dokumen KTP belum tersedia."
                    Log.e(storageTag, "path failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                    return@launch
                }
                val storagePath = pathResult.getOrThrow()
                Log.d(storageTag, "KTP path found: $storagePath")

                val urlResult = applicationRepository.createSignedUrlForStoragePath(storagePath)
                if (urlResult.isSuccess) {
                    val signedUrl = urlResult.getOrThrow()
                    Log.d(storageTag, "KTP signed url generated success")
                    
                    signedUrlCache[cacheKey] = CachedSignedUrl(
                        url = signedUrl,
                        expiresAtMillis = System.currentTimeMillis() + cacheDurationMillis
                    )
                    Log.d(storageTag, "signed url cached for slot $documentSlot")

                    _openDocumentUrl.emit(signedUrl)
                } else {
                    val errorMsg = urlResult.exceptionOrNull()?.message ?: "Gagal membuat link dokumen."
                    Log.e(storageTag, "signed url generated failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(storageTag, "view KTP document exception: ${e.message}", e)
                _documentViewError.emit("Terjadi kesalahan saat memuat dokumen KTP: ${e.message}")
            } finally {
                _documentViewLoading.value = false
            }
        }
    }

    fun viewKkDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        val storageTag = "SIAPEL_STORAGE"
        Log.d(storageTag, "KK view document start code $submissionCode slot $documentSlot")

        val cacheKey = "$submissionCode-$documentSlot"
        val now = System.currentTimeMillis()
        val cacheDurationMillis = 240_000L // 240 seconds

        viewModelScope.launch {
            val cachedUrlObj = signedUrlCache[cacheKey]
            if (cachedUrlObj != null && now < cachedUrlObj.expiresAtMillis) {
                Log.d(storageTag, "KK using cached signed url for $cacheKey")
                _openDocumentUrl.emit(cachedUrlObj.url)
                return@launch
            } else if (cachedUrlObj != null) {
                Log.d(storageTag, "signed url cache expired for $cacheKey")
            } else {
                Log.d(storageTag, "signed url cache miss for $cacheKey")
            }

            _documentViewLoading.value = true
            try {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                    ?: authRepository.getCurrentSupabaseSessionUserId()

                if (currentUuid.isNullOrEmpty()) {
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (currentUuid.isNullOrEmpty()) {
                    Log.e(storageTag, "UUID resolved = false")
                    _documentViewError.emit("Sesi pengguna tidak valid. Silakan login ulang.")
                    return@launch
                }
                Log.d(storageTag, "UUID resolved = true")

                val kkDetail: KkDetailRemoteDto = if (cachedKkDetailCode == submissionCode && cachedKkDetail != null) {
                    Log.d(storageTag, "KK fetch detail success for code $submissionCode")
                    cachedKkDetail!!
                } else {
                    Log.d(storageTag, "KK cache miss")
                    val fetchResult = applicationRepository.fetchKkDetailRemote(
                        submissionCode = submissionCode,
                        currentUuid = currentUuid
                    )
                    if (fetchResult.isSuccess) {
                        Log.d(storageTag, "KK fetch detail success for code $submissionCode")
                        val detail = fetchResult.getOrThrow()
                        cachedKkDetailCode = submissionCode
                        cachedKkDetail = detail
                        detail
                    } else {
                        val errorMsg = fetchResult.exceptionOrNull()?.message ?: "Gagal mengambil detail dokumen KK."
                        Log.e(storageTag, "fetch kk_detail failed: $errorMsg")
                        _documentViewError.emit(errorMsg)
                        return@launch
                    }
                }

                val pathResult = applicationRepository.getKkDocumentPathBySlot(kkDetail, documentSlot)
                if (pathResult.isFailure) {
                    val errorMsg = pathResult.exceptionOrNull()?.message ?: "Dokumen KK belum tersedia."
                    Log.e(storageTag, "path failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                    return@launch
                }
                val storagePath = pathResult.getOrThrow()
                Log.d(storageTag, "KK path found: $storagePath")

                val urlResult = applicationRepository.createSignedUrlForStoragePath(storagePath)
                if (urlResult.isSuccess) {
                    val signedUrl = urlResult.getOrThrow()
                    Log.d(storageTag, "KK signed url success for $cacheKey")
                    
                    signedUrlCache[cacheKey] = CachedSignedUrl(
                        url = signedUrl,
                        expiresAtMillis = System.currentTimeMillis() + cacheDurationMillis
                    )
                    Log.d(storageTag, "signed url cached for slot $documentSlot")

                    _openDocumentUrl.emit(signedUrl)
                } else {
                    val errorMsg = urlResult.exceptionOrNull()?.message ?: "Gagal membuka dokumen KK."
                    Log.e(storageTag, "signed url generated failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(storageTag, "KK view document failed code $submissionCode slot $documentSlot: ${e.message}", e)
                _documentViewError.emit("Gagal membuka dokumen KK: ${e.message}")
            } finally {
                _documentViewLoading.value = false
            }
        }
    }

    fun viewAktaDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        val storageTag = "SIAPEL_STORAGE"
        Log.d(storageTag, "AKTA view document start code $submissionCode slot $documentSlot")

        val cacheKey = "$submissionCode-$documentSlot"
        val now = System.currentTimeMillis()
        val cacheDurationMillis = 240_000L // 240 seconds

        viewModelScope.launch {
            val cachedUrlObj = signedUrlCache[cacheKey]
            if (cachedUrlObj != null && now < cachedUrlObj.expiresAtMillis) {
                Log.d(storageTag, "AKTA using cached signed url for $cacheKey")
                _openDocumentUrl.emit(cachedUrlObj.url)
                return@launch
            } else if (cachedUrlObj != null) {
                Log.d(storageTag, "signed url cache expired for $cacheKey")
            } else {
                Log.d(storageTag, "signed url cache miss for $cacheKey")
            }

            _documentViewLoading.value = true
            try {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                    ?: authRepository.getCurrentSupabaseSessionUserId()

                if (currentUuid.isNullOrEmpty()) {
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (currentUuid.isNullOrEmpty()) {
                    Log.e(storageTag, "UUID resolved = false")
                    _documentViewError.emit("Sesi pengguna tidak valid. Silakan login ulang.")
                    return@launch
                }
                Log.d(storageTag, "UUID resolved = true")

                val aktaDetail: AktaDetailRemoteDto = if (cachedAktaDetailCode == submissionCode && cachedAktaDetail != null) {
                    Log.d(storageTag, "AKTA fetch detail success for code $submissionCode")
                    cachedAktaDetail!!
                } else {
                    Log.d(storageTag, "AKTA cache miss")
                    val fetchResult = applicationRepository.fetchAktaDetailRemote(
                        submissionCode = submissionCode,
                        currentUuid = currentUuid
                    )
                    if (fetchResult.isSuccess) {
                        Log.d(storageTag, "AKTA fetch detail success for code $submissionCode")
                        val detail = fetchResult.getOrThrow()
                        cachedAktaDetailCode = submissionCode
                        cachedAktaDetail = detail
                        detail
                    } else {
                        val errorMsg = fetchResult.exceptionOrNull()?.message ?: "Gagal mengambil detail dokumen AKTA."
                        Log.e(storageTag, "fetch akta_detail failed: $errorMsg")
                        _documentViewError.emit(errorMsg)
                        return@launch
                    }
                }

                val pathResult = applicationRepository.getAktaDocumentPathBySlot(aktaDetail, documentSlot)
                if (pathResult.isFailure) {
                    val errorMsg = pathResult.exceptionOrNull()?.message ?: "Dokumen AKTA belum tersedia."
                    Log.e(storageTag, "path failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                    return@launch
                }
                val storagePath = pathResult.getOrThrow()
                Log.d(storageTag, "AKTA path found: $storagePath")

                val urlResult = applicationRepository.createSignedUrlForStoragePath(storagePath)
                if (urlResult.isSuccess) {
                    val signedUrl = urlResult.getOrThrow()
                    Log.d(storageTag, "AKTA signed url success for $cacheKey")
                    
                    signedUrlCache[cacheKey] = CachedSignedUrl(
                        url = signedUrl,
                        expiresAtMillis = System.currentTimeMillis() + cacheDurationMillis
                    )
                    Log.d(storageTag, "signed url cached for slot $documentSlot")

                    _openDocumentUrl.emit(signedUrl)
                } else {
                    val errorMsg = urlResult.exceptionOrNull()?.message ?: "Gagal membuka dokumen AKTA."
                    Log.e(storageTag, "signed url generated failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(storageTag, "AKTA view document failed code $submissionCode slot $documentSlot: ${e.message}", e)
                _documentViewError.emit("Gagal membuka dokumen AKTA: ${e.message}")
            } finally {
                _documentViewLoading.value = false
            }
        }
    }

    fun viewDisabilitasDocument(
        submissionCode: String,
        documentSlot: String
    ) {
        val storageTag = "SIAPEL_STORAGE"
        Log.d(storageTag, "DISABILITAS view document start code $submissionCode slot $documentSlot")

        val cacheKey = "$submissionCode-$documentSlot"
        val now = System.currentTimeMillis()
        val cacheDurationMillis = 240_000L // 240 seconds

        viewModelScope.launch {
            val cachedUrlObj = signedUrlCache[cacheKey]
            if (cachedUrlObj != null && now < cachedUrlObj.expiresAtMillis) {
                Log.d(storageTag, "DISABILITAS using cached signed url for $cacheKey")
                _openDocumentUrl.emit(cachedUrlObj.url)
                return@launch
            } else if (cachedUrlObj != null) {
                Log.d(storageTag, "signed url cache expired for $cacheKey")
            } else {
                Log.d(storageTag, "signed url cache miss for $cacheKey")
            }

            _documentViewLoading.value = true
            try {
                var currentUuid = authRepository.getCurrentSupabaseUser()?.id
                    ?: authRepository.getCurrentSupabaseSessionUserId()

                if (currentUuid.isNullOrEmpty()) {
                    authRepository.validateSessionWithServer()
                    currentUuid = authRepository.getCurrentSupabaseUser()?.id
                        ?: authRepository.getCurrentSupabaseSessionUserId()
                }

                if (currentUuid.isNullOrEmpty()) {
                    Log.e(storageTag, "UUID resolved = false")
                    _documentViewError.emit("Sesi pengguna tidak valid. Silakan login ulang.")
                    return@launch
                }
                Log.d(storageTag, "UUID resolved = true")

                val disabilitasDetail: DisabilitasDetailRemoteDto = if (cachedDisabilitasDetailCode == submissionCode && cachedDisabilitasDetail != null) {
                    Log.d(storageTag, "DISABILITAS fetch detail success for code $submissionCode")
                    cachedDisabilitasDetail!!
                } else {
                    Log.d(storageTag, "DISABILITAS cache miss")
                    val fetchResult = applicationRepository.fetchDisabilitasDetailRemote(
                        submissionCode = submissionCode,
                        currentUuid = currentUuid
                    )
                    if (fetchResult.isSuccess) {
                        Log.d(storageTag, "DISABILITAS fetch detail success for code $submissionCode")
                        val detail = fetchResult.getOrThrow()
                        cachedDisabilitasDetailCode = submissionCode
                        cachedDisabilitasDetail = detail
                        detail
                    } else {
                        val errorMsg = fetchResult.exceptionOrNull()?.message ?: "Gagal mengambil detail dokumen DISABILITAS."
                        Log.e(storageTag, "fetch disabilitas_detail failed: $errorMsg")
                        _documentViewError.emit(errorMsg)
                        return@launch
                    }
                }

                val pathResult = applicationRepository.getDisabilitasDocumentPathBySlot(disabilitasDetail, documentSlot)
                if (pathResult.isFailure) {
                    val errorMsg = pathResult.exceptionOrNull()?.message ?: "Dokumen DISABILITAS belum tersedia."
                    Log.e(storageTag, "path failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                    return@launch
                }
                val storagePath = pathResult.getOrThrow()
                Log.d(storageTag, "DISABILITAS path found: $storagePath")

                val urlResult = applicationRepository.createSignedUrlForStoragePath(storagePath)
                if (urlResult.isSuccess) {
                    val signedUrl = urlResult.getOrThrow()
                    Log.d(storageTag, "DISABILITAS signed url success for $cacheKey")
                    
                    signedUrlCache[cacheKey] = CachedSignedUrl(
                        url = signedUrl,
                        expiresAtMillis = System.currentTimeMillis() + cacheDurationMillis
                    )
                    Log.d(storageTag, "signed url cached for slot $documentSlot")

                    _openDocumentUrl.emit(signedUrl)
                } else {
                    val errorMsg = urlResult.exceptionOrNull()?.message ?: "Gagal membuka dokumen DISABILITAS."
                    Log.e(storageTag, "signed url generated failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(storageTag, "DISABILITAS view document failed code $submissionCode slot $documentSlot: ${e.message}", e)
                _documentViewError.emit("Gagal membuka dokumen DISABILITAS: ${e.message}")
            } finally {
                _documentViewLoading.value = false
            }
        }
    }
}
