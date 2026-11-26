package com.example.siapel.data.local.dao

import androidx.room.*
import com.example.siapel.data.local.entity.UserEntity

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE nik = :nik LIMIT 1")
    suspend fun getUserByNik(nik: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): UserEntity?

    @Query("SELECT * FROM users WHERE remoteUserUuid = :remoteUserUuid LIMIT 1")
    suspend fun getUserByRemoteUuid(remoteUserUuid: String): UserEntity?

    @Update
    suspend fun updateUser(user: UserEntity)
}
