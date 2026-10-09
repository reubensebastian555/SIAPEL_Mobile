package com.example.siapel.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.remote.dto.KiaDetailRemoteDto
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
                        val syncSuccess = applicationRepository.syncKiaApplicationsRemote(
                            currentUuid = currentUuid,
                            localUserId = localUserId
                        )
                        if (syncSuccess) {
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
            } else {
                // Non-KIA services (not migrated yet): retain existing local Room delete
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
            // Check in-memory signed URL cache first
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
                // Resolve current Supabase Auth UUID
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

                // Resolve KiaDetailRemoteDto (from memory cache or remote fetch)
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

                // Extract storage path by slot
                val pathResult = applicationRepository.getKiaDocumentPathBySlot(kiaDetail, documentSlot)
                if (pathResult.isFailure) {
                    val errorMsg = pathResult.exceptionOrNull()?.message ?: "Dokumen belum tersedia."
                    Log.e(storageTag, "path failed: $errorMsg")
                    _documentViewError.emit(errorMsg)
                    return@launch
                }
                val storagePath = pathResult.getOrThrow()
                Log.d(storageTag, "path found")

                // Generate signed URL for storage path
                val urlResult = applicationRepository.createSignedUrlForStoragePath(storagePath)
                if (urlResult.isSuccess) {
                    val signedUrl = urlResult.getOrThrow()
                    Log.d(storageTag, "signed url generated success")
                    
                    // Save to memory cache for 240 seconds
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
}
