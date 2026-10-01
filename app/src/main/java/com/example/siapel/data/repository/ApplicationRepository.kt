package com.example.siapel.data.repository

import com.example.siapel.data.local.dao.ApplicationDao
import com.example.siapel.data.local.entity.ApplicationEntity
import kotlinx.coroutines.flow.Flow

class ApplicationRepository(private val applicationDao: ApplicationDao) {

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
}
