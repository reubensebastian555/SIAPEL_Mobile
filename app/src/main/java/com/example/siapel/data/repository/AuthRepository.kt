package com.example.siapel.data.repository

import com.example.siapel.data.local.dao.UserDao
import com.example.siapel.data.local.entity.UserEntity
import java.security.MessageDigest

class AuthRepository(private val userDao: UserDao) {

    suspend fun register(user: UserEntity): Result<Long> {
        return try {
            val existingEmail = userDao.getUserByEmail(user.email)
            if (existingEmail != null) return Result.failure(Exception("Email sudah terdaftar"))

            val existingNik = userDao.getUserByNik(user.nik)
            if (existingNik != null) return Result.failure(Exception("NIK sudah terdaftar"))

            val id = userDao.insertUser(user)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, passwordRaw: String): Result<UserEntity> {
        return try {
            val user = userDao.getUserByEmail(email) ?: return Result.failure(Exception("Email belum terdaftar"))
            
            val hashedInput = hashPassword(passwordRaw)
            if (user.passwordHash == hashedInput) {
                Result.success(user)
            } else {
                Result.failure(Exception("Password salah"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserById(id: Int): UserEntity? {
        return userDao.getUserById(id)
    }

    fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
