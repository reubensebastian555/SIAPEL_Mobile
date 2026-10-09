package com.example.siapel.data.repository

import android.util.Log
import com.example.siapel.data.local.dao.UserDao
import com.example.siapel.data.local.entity.UserEntity
import com.example.siapel.data.remote.SupabaseClientProvider
import com.example.siapel.data.remote.dto.ProfileRemoteDto
import com.example.siapel.model.UserProfile
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest

private const val TAG = "SIAPEL_SUPABASE"

class AuthRepository(private val userDao: UserDao) {

    private val authPlugin = SupabaseClientProvider.auth
    private val postgrest = SupabaseClientProvider.postgrest

    private val _cachedProfile = MutableStateFlow<UserProfile?>(null)
    val cachedProfile: StateFlow<UserProfile?> = _cachedProfile.asStateFlow()

    private val _cachedUserEntity = MutableStateFlow<UserEntity?>(null)
    val cachedUserEntity: StateFlow<UserEntity?> = _cachedUserEntity.asStateFlow()

    suspend fun register(user: UserEntity, rawPassword: String): Result<Long> {
        return try {
            Log.d(TAG, "Starting register for email: ${user.email}")

            // 1. Register via Supabase Auth with full metadata for database trigger
            authPlugin.signUpWith(Email) {
                email = user.email
                password = rawPassword
                data = buildJsonObject {
                    put("full_name", user.namaLengkap)
                    put("nik", user.nik)
                    put("whatsapp", user.whatsapp)
                    put("kecamatan", user.kecamatan)
                    put("kelurahan", user.kelurahan)
                }
            }

            Log.d(TAG, "Register Auth success for email: ${user.email}")
            Log.d(TAG, "Register metadata sent (full_name, nik, whatsapp, kecamatan, kelurahan)")

            val supabaseUser = getCurrentSupabaseUser()
            val supabaseUuid = supabaseUser?.id
            Log.d(TAG, "Current user UUID: $supabaseUuid")

            // Check if NIK already exists in local DB
            val existingNik = userDao.getUserByNik(user.nik)
            if (existingNik != null) {
                return Result.failure(Exception("NIK sudah terdaftar di perangkat ini"))
            }

            // 2. Save user profile locally in Room for local compatibility (without storing plaintext password)
            val userToSave = user.copy(passwordHash = "SUPABASE_AUTH")
            val existingUser = userDao.getUserByEmail(user.email)
            val id = if (existingUser != null) {
                userDao.updateUser(userToSave.copy(id = existingUser.id))
                existingUser.id.toLong()
            } else {
                userDao.insertUser(userToSave)
            }

            refreshLocalCache()
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "register failed + error message: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchOnlineProfile(uuid: String): ProfileRemoteDto? {
        return try {
            postgrest.from("profiles").select {
                filter {
                    eq("user_id", uuid)
                }
            }.decodeSingleOrNull<ProfileRemoteDto>()
        } catch (e: Exception) {
            Log.w(TAG, "fetchOnlineProfile exception for UUID $uuid: ${e.message}")
            null
        }
    }

    suspend fun login(email: String, passwordRaw: String): Result<UserEntity> {
        return try {
            Log.d(TAG, "Starting login for email: $email")

            // 1. Login via Supabase Auth
            authPlugin.signInWith(Email) {
                this.email = email
                this.password = passwordRaw
            }

            val currentUuid = getCurrentSupabaseUser()?.id
            Log.d(TAG, "Login Auth success. UUID: $currentUuid")

            val metadataFullName = getSupabaseFullName()

            // 2. Fetch profile online MAXIMAL 1x
            val onlineProfile = if (!currentUuid.isNullOrEmpty()) {
                val profile = fetchOnlineProfile(currentUuid)
                if (profile != null) {
                    Log.d(TAG, "Login profile fetch success for UUID: $currentUuid")
                } else {
                    Log.w(TAG, "Login profile fetch failure or not found for UUID: $currentUuid (falling back to metadata/Room)")
                }
                profile
            } else null

            val effectiveFullName = when {
                !onlineProfile?.fullName.isNullOrBlank() -> onlineProfile.fullName
                !metadataFullName.isNullOrBlank() -> metadataFullName
                else -> email.substringBefore("@")
            }

            // 3. Sync to local Room UserEntity
            var user = userDao.getUserByEmail(email)
            val effectiveNik = onlineProfile?.nik ?: user?.nik ?: ""
            val effectiveWa = onlineProfile?.whatsapp ?: user?.whatsapp ?: ""
            val effectiveKec = onlineProfile?.kecamatan ?: user?.kecamatan ?: ""
            val effectiveKel = onlineProfile?.kelurahan ?: user?.kelurahan ?: ""

            if (user == null) {
                val newLocalUser = UserEntity(
                    namaLengkap = effectiveFullName,
                    nik = effectiveNik,
                    email = email,
                    whatsapp = effectiveWa,
                    kecamatan = effectiveKec,
                    kelurahan = effectiveKel,
                    passwordHash = "SUPABASE_AUTH"
                )
                val newId = userDao.insertUser(newLocalUser)
                user = newLocalUser.copy(id = newId.toInt())
            } else {
                val updatedUser = user.copy(
                    namaLengkap = effectiveFullName,
                    nik = if (effectiveNik.isNotEmpty()) effectiveNik else user.nik,
                    whatsapp = if (effectiveWa.isNotEmpty()) effectiveWa else user.whatsapp,
                    kecamatan = if (effectiveKec.isNotEmpty()) effectiveKec else user.kecamatan,
                    kelurahan = if (effectiveKel.isNotEmpty()) effectiveKel else user.kelurahan
                )
                userDao.updateUser(updatedUser)
                user = updatedUser
            }

            refreshLocalCache(onlineProfile)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "login failed + error message: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            authPlugin.signOut()
            _cachedProfile.value = null
            _cachedUserEntity.value = null
            Log.d(TAG, "logout success")
            Result.success(Unit)
        } catch (e: Exception) {
            _cachedProfile.value = null
            _cachedUserEntity.value = null
            Log.e(TAG, "logout failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun isUserLoggedIn(): Boolean {
        return authPlugin.currentSessionOrNull() != null
    }

    suspend fun validateSessionWithServer(): Boolean {
        val session = authPlugin.currentSessionOrNull() ?: return false
        return try {
            val user = authPlugin.retrieveUserForCurrentSession(updateSession = true)
            if (user != null) {
                Log.d(TAG, "validateSessionWithServer success for UUID: ${user.id}")
                refreshLocalCache()
                true
            } else {
                _cachedProfile.value = null
                _cachedUserEntity.value = null
                false
            }
        } catch (e: Exception) {
            val errorMsg = e.message?.lowercase() ?: ""
            Log.w(TAG, "validateSessionWithServer exception: ${e.message}")
            if (errorMsg.contains("401") || errorMsg.contains("403") || errorMsg.contains("invalid") || errorMsg.contains("unauthorized") || errorMsg.contains("not found")) {
                try {
                    authPlugin.signOut()
                } catch (_: Exception) {}
                _cachedProfile.value = null
                _cachedUserEntity.value = null
                false
            } else {
                // If offline or network error, rely on active cached session
                val isLoggedIn = authPlugin.currentSessionOrNull() != null
                if (isLoggedIn) {
                    refreshLocalCache()
                }
                isLoggedIn
            }
        }
    }

    fun getCurrentUserEmail(): String? {
        return authPlugin.currentUserOrNull()?.email
    }

    fun getCurrentSupabaseUser(): UserInfo? {
        return authPlugin.currentUserOrNull()
    }

    fun getCurrentSupabaseSessionUserId(): String? {
        return authPlugin.currentSessionOrNull()?.user?.id
    }

    fun getSupabaseFullName(): String? {
        val userInfo = authPlugin.currentUserOrNull() ?: return null
        val metadata = userInfo.userMetadata ?: return null
        return try {
            val element = metadata["full_name"] ?: metadata["name"]
            val rawName = element?.jsonPrimitive?.contentOrNull ?: element?.jsonPrimitive?.content
            if (!rawName.isNullOrBlank() && rawName != "null") {
                rawName
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun refreshLocalCache(remoteProfile: ProfileRemoteDto? = null): UserProfile? {
        if (!isUserLoggedIn()) {
            _cachedProfile.value = null
            _cachedUserEntity.value = null
            return null
        }

        val supabaseUser = getCurrentSupabaseUser()
        val email = supabaseUser?.email ?: ""
        val currentUuid = supabaseUser?.id ?: ""

        val profileDto = remoteProfile ?: if (currentUuid.isNotEmpty()) fetchOnlineProfile(currentUuid) else null

        val localUser = if (email.isNotEmpty()) userDao.getUserByEmail(email) else null

        val metadataName = getSupabaseFullName()

        val displayName = when {
            !profileDto?.fullName.isNullOrBlank() -> profileDto.fullName
            !metadataName.isNullOrBlank() -> metadataName
            localUser != null && localUser.namaLengkap.isNotBlank() && localUser.namaLengkap != "Pengguna" && localUser.namaLengkap != "Pengguna Tidak Diketahui" -> localUser.namaLengkap
            else -> "Pengguna Tidak Diketahui"
        }

        val profile = UserProfile(
            name = displayName,
            nik = profileDto?.nik ?: localUser?.nik ?: "",
            email = if (email.isNotEmpty()) email else localUser?.email ?: "",
            whatsapp = profileDto?.whatsapp ?: localUser?.whatsapp ?: "",
            kecamatan = profileDto?.kecamatan ?: localUser?.kecamatan ?: "",
            kelurahan = profileDto?.kelurahan ?: localUser?.kelurahan ?: ""
        )
        _cachedProfile.value = profile

        // Sync local Room UserEntity
        if (localUser != null) {
            val syncedUser = localUser.copy(
                namaLengkap = displayName,
                nik = if (!profileDto?.nik.isNullOrBlank()) profileDto.nik else localUser.nik,
                whatsapp = if (!profileDto?.whatsapp.isNullOrBlank()) profileDto.whatsapp else localUser.whatsapp,
                kecamatan = if (!profileDto?.kecamatan.isNullOrBlank()) profileDto.kecamatan else localUser.kecamatan,
                kelurahan = if (!profileDto?.kelurahan.isNullOrBlank()) profileDto.kelurahan else localUser.kelurahan
            )
            userDao.updateUser(syncedUser)
            _cachedUserEntity.value = syncedUser
        } else if (email.isNotEmpty()) {
            val newUser = UserEntity(
                namaLengkap = displayName,
                nik = profileDto?.nik ?: "",
                email = email,
                whatsapp = profileDto?.whatsapp ?: "",
                kecamatan = profileDto?.kecamatan ?: "",
                kelurahan = profileDto?.kelurahan ?: "",
                passwordHash = "SUPABASE_AUTH"
            )
            val newId = userDao.insertUser(newUser)
            _cachedUserEntity.value = newUser.copy(id = newId.toInt())
        }

        return profile
    }

    suspend fun getUserProfile(): UserProfile? {
        return _cachedProfile.value ?: refreshLocalCache()
    }

    private suspend fun fetchOrSyncAuthenticatedUser(): UserEntity? {
        if (!isUserLoggedIn()) return null
        val email = getCurrentUserEmail() ?: return null
        val user = userDao.getUserByEmail(email)
        val metadataFullName = getSupabaseFullName()

        if (user != null) {
            if (!metadataFullName.isNullOrBlank() && (user.namaLengkap.isBlank() || user.namaLengkap == email.substringBefore("@") || user.namaLengkap == "Pengguna" || user.namaLengkap == "Pengguna Tidak Diketahui")) {
                val updated = user.copy(namaLengkap = metadataFullName)
                userDao.updateUser(updated)
                return updated
            }
            return user
        } else {
            val displayName = when {
                !metadataFullName.isNullOrBlank() -> metadataFullName
                else -> email.substringBefore("@")
            }
            val newLocalUser = UserEntity(
                namaLengkap = displayName,
                nik = "",
                email = email,
                whatsapp = "",
                kecamatan = "",
                kelurahan = "",
                passwordHash = "SUPABASE_AUTH"
            )
            val newId = userDao.insertUser(newLocalUser)
            return newLocalUser.copy(id = newId.toInt())
        }
    }

    suspend fun getAuthenticatedUser(): UserEntity? {
        return _cachedUserEntity.value ?: fetchOrSyncAuthenticatedUser()
    }

    suspend fun getUserById(id: Int): UserEntity? {
        return userDao.getUserById(id)
    }

    suspend fun getUserByEmail(email: String): UserEntity? {
        return userDao.getUserByEmail(email)
    }

    fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
