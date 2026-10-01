package com.example.siapel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.repository.ApplicationRepository
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.ApplicationStatusItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StatusViewModel(
    private val applicationRepository: ApplicationRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("Semua")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
            // Backend-ready: Ketika nanti menggunakan API/Retrofit, panggil apiRepository.fetchApplications() di sini.
            // Saat ini menggunakan Room lokal yang mengandalkan Flow reaktif. Ditambahkan delay singkat untuk animasi refresh.
            delay(800)
            _isRefreshing.value = false
        }
    }

    fun getApplicationByCodeFlow(code: String): Flow<ApplicationEntity?> {
        return applicationRepository.getApplicationByCodeFlow(code)
    }
    
    fun deleteApplication(application: ApplicationEntity) {
        viewModelScope.launch {
            applicationRepository.deleteApplication(application)
        }
    }
}
