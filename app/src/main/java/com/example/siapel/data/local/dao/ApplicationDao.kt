package com.example.siapel.data.local.dao

import androidx.room.*
import com.example.siapel.data.local.entity.ApplicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(application: ApplicationEntity): Long

    @Query("SELECT * FROM applications WHERE userId = :userId ORDER BY createdAt DESC")
    fun getApplicationsByUser(userId: Int): Flow<List<ApplicationEntity>>

    @Query("SELECT * FROM applications WHERE id = :id LIMIT 1")
    suspend fun getApplicationById(id: Int): ApplicationEntity?

    @Query("SELECT * FROM applications WHERE submissionCode = :code LIMIT 1")
    suspend fun getApplicationByCode(code: String): ApplicationEntity?

    @Query("SELECT * FROM applications WHERE submissionCode = :code LIMIT 1")
    fun getApplicationByCodeFlow(code: String): Flow<ApplicationEntity?>

    @Update
    suspend fun updateApplication(application: ApplicationEntity)

    @Delete
    suspend fun deleteApplication(application: ApplicationEntity)
}
