package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.repository.ApplicationRepository
import com.example.siapel.data.repository.AuthRepository
import com.example.siapel.model.ApplicationStatus
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
    private val authRepository: AuthRepository,
    private val applicationRepository: ApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _activeApplication = MutableStateFlow<ActiveApplication?>(null)
    val activeApplication: StateFlow<ActiveApplication?> = _activeApplication.asStateFlow()

    private val _services = MutableStateFlow(
        listOf(
            ServiceItem("Kartu Identitas Anak", "kia", "Pengajuan KIA baru atau pengganti"),
            ServiceItem("KTP Elektronik", "ktp", "Perekaman dan cetak KTP-el"),
            ServiceItem("Kartu Keluarga", "kk", "Penerbitan dan perubahan susunan KK"),
            ServiceItem("Paket Akta Kelahiran", "akta", "Pencatatan kelahiran baru lengkap"),
            ServiceItem("Layanan Disabilitas", "disabilitas", "Pelayanan khusus ramah disabilitas")
        )
    )
    val services: StateFlow<List<ServiceItem>> = _services.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            if (authRepository.isUserLoggedIn()) {
                val profile = authRepository.getUserProfile()
                _userName.value = profile?.name ?: "Pengguna Tidak Diketahui"

                val user = authRepository.getAuthenticatedUser()
                val userId = user?.id ?: -1
                if (userId != -1) {
                    applicationRepository.getApplicationsByUser(userId).collectLatest { apps ->
                        if (apps.isNotEmpty()) {
                            val latest = apps.first()
                            _activeApplication.value = ActiveApplication(
                                code = latest.submissionCode,
                                serviceName = latest.serviceType,
                                status = when(latest.status) {
                                    ApplicationStatus.SUBMITTED -> "Diajukan"
                                    ApplicationStatus.PROCESSING -> "Sedang Diproses"
                                    ApplicationStatus.COMPLETED -> "Selesai"
                                    ApplicationStatus.NEED_REVISION -> "Perlu Perbaikan"
                                },
                                lastUpdated = "Terakhir diperbarui"
                            )
                        } else {
                            _activeApplication.value = null
                        }
                    }
                }
            } else {
                _userName.value = "Pengguna Tidak Diketahui"
                _activeApplication.value = null
            }
        }
    }
}
