package com.example.siapel.data.local.dao

import androidx.room.*
import com.example.siapel.data.local.entity.ApplicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationDao {

    // ==========================================
    // LEGACY COMPATIBILITY
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(
        application: ApplicationEntity
    ): Long

    // ==========================================
    // STRICT LARAVEL INSERT
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApplicationStrict(
        application: ApplicationEntity
    ): Long

    // ==========================================
    // APPLICATION LIST
    // ==========================================

    @Query(
        """
        SELECT * FROM applications
        WHERE userId = :userId
        ORDER BY createdAt DESC
        """
    )
    fun getApplicationsByUser(
        userId: Int
    ): Flow<List<ApplicationEntity>>

    @Query(
        """
        SELECT * FROM applications
        WHERE userId = :localUserId
        AND remoteUserUuid = :remoteUserUuid
        AND remoteUuid IS NOT NULL
        ORDER BY createdAt DESC
        """
    )
    fun getApplicationsByUserAndRemoteUuid(
        localUserId: Int,
        remoteUserUuid: String
    ): Flow<List<ApplicationEntity>>

    // ==========================================
    // APPLICATION LOOKUP
    // ==========================================

    @Query(
        """
        SELECT * FROM applications
        WHERE id = :id
        LIMIT 1
        """
    )
    suspend fun getApplicationById(
        id: Int
    ): ApplicationEntity?

    @Query(
        """
        SELECT * FROM applications
        WHERE remoteUuid = :remoteUuid
        AND userId = :localUserId
        AND remoteUserUuid = :remoteUserUuid
        LIMIT 1
        """
    )
    suspend fun getApplicationByRemoteUuid(
        remoteUuid: String,
        localUserId: Int,
        remoteUserUuid: String
    ): ApplicationEntity?

    // Legacy lookup.
    // Tidak digunakan sebagai fallback identitas Laravel.

    @Query(
        """
        SELECT * FROM applications
        WHERE submissionCode = :code
        LIMIT 1
        """
    )
    suspend fun getApplicationByCode(
        code: String
    ): ApplicationEntity?

    @Query(
        """
        SELECT * FROM applications
        WHERE submissionCode = :code
        LIMIT 1
        """
    )
    fun getApplicationByCodeFlow(
        code: String
    ): Flow<ApplicationEntity?>

    // ==========================================
    // UPDATE AND DELETE
    // ==========================================

    @Update
    suspend fun updateApplication(
        application: ApplicationEntity
    )

    @Delete
    suspend fun deleteApplication(
        application: ApplicationEntity
    )

    @Query(
        """
        DELETE FROM applications
        WHERE remoteUuid = :remoteUuid
        AND userId = :localUserId
        AND remoteUserUuid = :remoteUserUuid
        """
    )
    suspend fun deleteApplicationByRemoteUuid(
        remoteUuid: String,
        localUserId: Int,
        remoteUserUuid: String
    ): Int

    // ==========================================
    // STRICT TRANSACTIONAL LARAVEL UPSERT
    // ==========================================

    /**
     * Menyimpan cache Laravel secara atomik dengan
     * validasi ownership dan canonical UUID.
     *
     * Tidak menggunakan REPLACE.
     * Tidak menggunakan submissionCode sebagai fallback.
     * Tidak mengambil alih record milik pengguna lain.
     */
    @Transaction
    suspend fun upsertLaravelApplicationStrict(
        entity: ApplicationEntity,
        localUserId: Int,
        remoteUserUuid: String
    ): Long {

        val canonicalUuidRegex = Regex(
            "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
        )

        require(localUserId > 0) {
            "Local user ID tidak valid"
        }

        require(
            canonicalUuidRegex.matches(remoteUserUuid)
        ) {
            "Remote user UUID tidak valid"
        }

        val applicationUuid = requireNotNull(
            entity.remoteUuid
        ) {
            "Remote application UUID tidak tersedia"
        }

        require(
            canonicalUuidRegex.matches(applicationUuid)
        ) {
            "Remote application UUID tidak valid"
        }

        require(
            entity.userId == localUserId
        ) {
            "Ownership mismatch: local user ID tidak sesuai"
        }

        require(
            entity.remoteUserUuid == remoteUserUuid
        ) {
            "Ownership mismatch: remote user UUID tidak sesuai"
        }

        val existingLocal = getApplicationByRemoteUuid(
            remoteUuid = applicationUuid,
            localUserId = localUserId,
            remoteUserUuid = remoteUserUuid
        )

        return if (existingLocal != null) {

            // Pertahankan primary key lokal; timestamp berasal dari mapper.
            val updatedEntity = entity.copy(
                id = existingLocal.id
            )

            updateApplication(updatedEntity)

            existingLocal.id.toLong()

        } else {

            // Record baru wajib memperoleh primary key
            // otomatis dari Room.
            val newEntity = entity.copy(
                id = 0
            )

            val insertedId = insertApplicationStrict(
                newEntity
            )

            check(
                insertedId in 1L..Int.MAX_VALUE.toLong()
            ) {
                "Room gagal menghasilkan local application ID"
            }

            insertedId
        }
    }
}
