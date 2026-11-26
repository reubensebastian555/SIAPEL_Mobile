package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.repository.LaravelApplicationRepository
import com.example.siapel.data.repository.LaravelAuthRepository
import com.example.siapel.model.ApplicationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ServiceItem(
    val title: String,
    val route: String,
    val description: String
)

data class ActiveApplication(
    val code: String,
    val serviceName: String,
    val status: String,
    val lastUpdated: String
)

class HomeViewModel(
    private val authRepository: LaravelAuthRepository,
    private val applicationRepository: LaravelApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    companion object {
        private const val UNKNOWN_USER =
            "Pengguna Tidak Diketahui"
    }

    // ==========================================
    // EXISTING UI STATE
    // ==========================================

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> =
        _userName.asStateFlow()

    private val _activeApplication =
        MutableStateFlow<ActiveApplication?>(null)

    val activeApplication: StateFlow<ActiveApplication?> =
        _activeApplication.asStateFlow()

    private val _services = MutableStateFlow(
        listOf(
            ServiceItem(
                "Kartu Identitas Anak",
                "kia",
                "Pengajuan KIA baru atau pengganti"
            ),
            ServiceItem(
                "KTP Elektronik",
                "ktp",
                "Perekaman dan cetak KTP-el"
            ),
            ServiceItem(
                "Kartu Keluarga",
                "kk",
                "Penerbitan dan perubahan susunan KK"
            ),
            ServiceItem(
                "Paket Akta Kelahiran",
                "akta",
                "Pencatatan kelahiran baru lengkap"
            ),
            ServiceItem(
                "Layanan Disabilitas",
                "disabilitas",
                "Pelayanan khusus ramah disabilitas"
            )
        )
    )

    val services: StateFlow<List<ServiceItem>> =
        _services.asStateFlow()

    // ==========================================
    // SYNC STATE
    // ==========================================

    private val _isSyncing = MutableStateFlow(true)

    val isSyncing: StateFlow<Boolean> =
        _isSyncing.asStateFlow()

    private val _syncError =
        MutableStateFlow<String?>(null)

    val syncError: StateFlow<String?> =
        _syncError.asStateFlow()

    private val refreshRequests = MutableStateFlow(0L)

    private var loadJob: Job? = null

    init {
        observeSession()
    }

    // ==========================================
    // SESSION-AWARE HOME DATA
    // ==========================================

    private fun observeSession() {

        loadJob?.cancel()

        loadJob = viewModelScope.launch {

            combine(
                userPreferencesRepository.userSessionFlow
                    .distinctUntilChanged(),
                refreshRequests
            ) { session, _ ->
                session
            }.collectLatest sessionLoad@ {

                _userName.value = UNKNOWN_USER
                _activeApplication.value = null
                _syncError.value = null
                _isSyncing.value = true

                try {

                    val session =
                        authRepository.getVerifiedLaravelSession()
                            ?: return@sessionLoad

                    val user =
                        authRepository.getVerifiedSessionContract()
                            ?: return@sessionLoad

                    if (
                        user.id != session.localUserId ||
                        user.remoteUserUuid != session.remoteUserUuid ||
                        !authRepository.isVerifiedSessionCurrent(session)
                    ) {
                        return@sessionLoad
                    }

                    val profile = try {

                        authRepository.getUserProfile()

                    } catch (e: CancellationException) {
                        throw e

                    } catch (_: Exception) {
                        null
                    }

                    if (
                        !authRepository.isVerifiedSessionCurrent(
                            session
                        )
                    ) {
                        return@sessionLoad
                    }

                    _userName.value =
                        profile?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: user.namaLengkap
                                .takeIf { it.isNotBlank() }
                                    ?: UNKNOWN_USER

                    // ==================================
                    // REMOTE SYNC â€” LARAVEL TO ROOM
                    // ==================================

                    try {

                        applicationRepository
                            .syncApplicationsRemoteAndCache(
                                localUserId =
                                    session.localUserId,
                                remoteUserUuid =
                                    session.remoteUserUuid,
                                token = session.token
                            )
                            .getOrThrow()

                    } catch (e: CancellationException) {
                        throw e

                    } catch (_: Exception) {

                        if (
                            !authRepository
                                .isVerifiedSessionCurrent(session)
                        ) {
                            _userName.value = UNKNOWN_USER
                            _activeApplication.value = null
                            return@sessionLoad
                        }

                        _syncError.value =
                            "Sinkronisasi pengajuan belum berhasil"

                    }

                    // ==================================
                    // REACTIVE ROOM CACHE
                    // ==================================

                    applicationRepository
                        .observeCachedApplications(session)
                        .collectLatest cacheLoad@ { applications ->

                            if (
                                !authRepository
                                    .isVerifiedSessionCurrent(session)
                            ) {

                                _userName.value = UNKNOWN_USER
                                _activeApplication.value = null
                                _isSyncing.value = false

                                return@cacheLoad
                            }

                            val latest =
                                applications.firstOrNull()

                            _activeApplication.value =
                                latest?.let { application ->

                                    ActiveApplication(
                                        code =
                                            application.submissionCode,

                                        serviceName =
                                            application.serviceType,

                                        status =
                                            when (application.status) {

                                                ApplicationStatus.SUBMITTED ->
                                                    "Diajukan"

                                                ApplicationStatus.PROCESSING ->
                                                    "Sedang Diproses"

                                                ApplicationStatus.COMPLETED ->
                                                    "Selesai"

                                                ApplicationStatus.NEED_REVISION ->
                                                    "Perlu Perbaikan"
                                            },

                                        lastUpdated =
                                            "Terakhir diperbarui"
                                    )
                                }
                            _isSyncing.value = false
                        }

                } catch (e: CancellationException) {
                    throw e

                } catch (_: Exception) {

                    _userName.value = UNKNOWN_USER
                    _activeApplication.value = null

                    _syncError.value =
                        "Data beranda belum dapat dimuat"

                } finally {
                    _isSyncing.value = false
                }
            }
        }
    }

    // ==========================================
    // EXISTING PUBLIC REFRESH CONTRACT
    // ==========================================

    fun loadData() {
        refreshRequests.update { it + 1L }
    }
}
