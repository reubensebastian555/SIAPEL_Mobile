package com.example.siapel.data.repository

import android.util.Log
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.datastore.UserSession
import com.example.siapel.data.local.dao.UserDao
import com.example.siapel.data.local.entity.UserEntity
import com.example.siapel.data.remote.laravel.LaravelApiException
import com.example.siapel.data.remote.laravel.LaravelAuthApiService
import com.example.siapel.data.remote.laravel.dto.LaravelLoginRequest
import com.example.siapel.data.remote.laravel.dto.LaravelRegisterRequest
import com.example.siapel.model.UserProfile
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import java.io.IOException

private const val TAG = "SIAPEL_LARAVEL_AUTH"

private fun logError(tag: String, message: String) {
    try {
        Log.e(tag, message)
    } catch (_: RuntimeException) {
        // Android Log may be unavailable in JVM unit tests.
    }
}

/**
 * Snapshot identitas Laravel yang telah diverifikasi
 * terhadap DataStore dan Room.
 *
 * Token tidak boleh ditampilkan melalui log.
 */
data class VerifiedLaravelSession(
    val localUserId: Int,
    val remoteUserUuid: String,
    val token: String,
    val generation: String,
    val email: String
) {
    override fun toString(): String {
        return "VerifiedLaravelSession(credentials=REDACTED)"
    }
}

/**
 * Repository autentikasi Laravel 12 Sanctum.
 *
 * Laravel merupakan sumber identitas utama.
 * Room menyimpan identitas lokal.
 * DataStore menyimpan sesi aktif secara persisten.
 */
class LaravelAuthRepository(
    private val userDao: UserDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val apiService: LaravelAuthApiService =
        LaravelAuthApiService()
) {

    companion object {

        private val CANONICAL_UUID_REGEX = Regex(
            "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
        )

        /**
         * Diakses hanya dalam shared session Mutex.
         * Mencegah respons login lama mengalahkan
         * operasi login/logout yang lebih baru.
         */
        private var authenticationOperation: Long = 0L
    }

    private val sessionMutex =
        UserPreferencesRepository.sessionMutex

    private val _cachedProfile =
        MutableStateFlow<UserProfile?>(null)

    val cachedProfile: StateFlow<UserProfile?> =
        _cachedProfile.asStateFlow()

    private val _cachedUserEntity =
        MutableStateFlow<UserEntity?>(null)

    val cachedUserEntity: StateFlow<UserEntity?> =
        _cachedUserEntity.asStateFlow()

    private val _currentRemoteUuid =
        MutableStateFlow<String?>(null)

    val currentRemoteUuid: StateFlow<String?> =
        _currentRemoteUuid.asStateFlow()

    private var publishedGeneration: String? = null

    private data class LoginOperation(
        val generation: Long,
        val session: UserSession
    )

    // ==========================================
    // INTERNAL VALIDATION
    // ==========================================

    private fun isCanonicalUuid(value: String?): Boolean {
        return !value.isNullOrBlank() &&
                CANONICAL_UUID_REGEX.matches(value)
    }

    private fun ensureCancellationPropagated(
        exception: Exception
    ) {
        if (exception is CancellationException) {
            throw exception
        }

        val cause = exception.cause

        if (cause is CancellationException) {
            throw cause
        }
    }

    private fun reportFailure(
        operation: String,
        exception: Exception
    ) {
        if (exception is LaravelApiException) {
            logError(
                TAG,
                "$operation failed [HTTP ${exception.statusCode}]"
            )
        } else {
            logError(
                TAG,
                "$operation failed [${exception.javaClass.simpleName}]"
            )
        }
    }

    private fun isTransientConnectivityFailure(
        exception: Throwable?
    ): Boolean {
        return exception is IOException ||
                exception is HttpRequestTimeoutException
    }

    private fun profileFromUser(
        user: UserEntity
    ): UserProfile {
        return UserProfile(
            name = user.namaLengkap,
            nik = user.nik,
            email = user.email,
            whatsapp = user.whatsapp,
            kecamatan = user.kecamatan,
            kelurahan = user.kelurahan
        )
    }

    /**
     * Hanya dipanggil ketika shared Mutex telah diperoleh.
     */
    private fun clearPublishedSessionLocked() {
        _cachedProfile.value = null
        _cachedUserEntity.value = null
        _currentRemoteUuid.value = null
        publishedGeneration = null
    }

    /**
     * Publikasi state dilakukan setelah identitas
     * Room dan DataStore berhasil diverifikasi.
     */
    private fun publishSessionLocked(
        user: UserEntity,
        session: UserSession
    ) {
        _cachedUserEntity.value = user
        _cachedProfile.value = profileFromUser(user)
        _currentRemoteUuid.value = session.remoteUserUuid
        publishedGeneration = session.generation
    }

    private suspend fun validateNikOwnershipLocked(
        nik: String,
        email: String
    ) {
        if (nik.isBlank()) {
            return
        }

        val owner = userDao.getUserByNik(nik)

        require(owner == null || owner.email == email) {
            "Konflik data lokal: NIK sudah terdaftar pada akun lain di perangkat ini"
        }
    }

    private suspend fun validateRemoteBindingLocked(
        localUser: UserEntity?,
        remoteUserUuid: String
    ) {
        require(isCanonicalUuid(remoteUserUuid)) {
            "UUID pengguna Laravel tidak valid"
        }

        if (localUser != null) {
            require(
                localUser.remoteUserUuid.isNullOrBlank() ||
                        localUser.remoteUserUuid == remoteUserUuid
            ) {
                "Konflik identitas akun lokal dengan akun Laravel"
            }
        }

        val uuidOwner =
            userDao.getUserByRemoteUuid(remoteUserUuid)

        require(
            uuidOwner == null ||
                    uuidOwner.id == localUser?.id
        ) {
            "UUID Laravel telah terikat pada akun lokal lain"
        }
    }

    // ==========================================
    // VERIFIED SESSION
    // ==========================================

    /**
     * Verifikasi sesi terhadap local user ID dan
     * remote UUID yang tersimpan di Room.
     *
     * Fungsi ini tidak mengambil Mutex sendiri.
     */
    private suspend fun getVerifiedSessionLocked():
            Pair<UserSession, UserEntity>? {

        val session =
            userPreferencesRepository
                .getVerifiedSessionContract()
                ?: return null

        if (
            !session.isLoggedIn ||
            session.userId <= 0 ||
            session.email.isBlank() ||
            session.token.isNullOrBlank() ||
            session.generation.isBlank() ||
            !isCanonicalUuid(session.remoteUserUuid)
        ) {
            return null
        }

        val localUser =
            userDao.getUserById(session.userId)
                ?: return null

        if (
            localUser.id != session.userId ||
            localUser.email != session.email ||
            !isCanonicalUuid(localUser.remoteUserUuid) ||
            localUser.remoteUserUuid != session.remoteUserUuid
        ) {
            return null
        }

        val uuidOwner = userDao.getUserByRemoteUuid(
            session.remoteUserUuid!!
        )

        if (
            uuidOwner != null &&
            uuidOwner.id != localUser.id
        ) {
            return null
        }

        if (
            !userPreferencesRepository
                .isSessionCurrent(session)
        ) {
            return null
        }

        return Pair(session, localUser)
    }

    /**
     * Kontrak lama dipertahankan agar caller existing
     * dan unit test tetap kompatibel.
     */
    suspend fun getVerifiedSessionContract(): UserEntity? {
        return sessionMutex.withLock {
            getVerifiedSessionLocked()?.second
        }
    }

    /**
     * Kontrak baru untuk LaravelApplicationRepository.
     *
     * Token, local ID, remote UUID dan generation
     * berasal dari satu sesi yang sama.
     */
    suspend fun getVerifiedLaravelSession():
            VerifiedLaravelSession? {

        return sessionMutex.withLock {
            val verified =
                getVerifiedSessionLocked()
                    ?: return@withLock null

            val session = verified.first

            VerifiedLaravelSession(
                localUserId = session.userId,
                remoteUserUuid = session.remoteUserUuid!!,
                token = session.token!!,
                generation = session.generation,
                email = session.email
            )
        }
    }

    private fun matchesVerifiedSession(
        current: UserSession,
        expected: VerifiedLaravelSession
    ): Boolean {
        return current.isLoggedIn &&
                current.userId == expected.localUserId &&
                current.remoteUserUuid == expected.remoteUserUuid &&
                current.token == expected.token &&
                current.generation == expected.generation &&
                current.email == expected.email
    }

    // A rejected request may invalidate only the exact session that sent it.
    suspend fun invalidateUnauthorizedSession(expected: VerifiedLaravelSession): Boolean {
        return sessionMutex.withLock {
            val current = userPreferencesRepository.getSessionSnapshot()
            if (!matchesVerifiedSession(current, expected)) return@withLock false
            val cleared = userPreferencesRepository.clearLaravelSessionIfActive(current)
            if (cleared) {
                authenticationOperation += 1L
                clearPublishedSessionLocked()
            }
            cleared
        }
    }

    suspend fun isVerifiedSessionCurrent(
        expected: VerifiedLaravelSession
    ): Boolean {
        return sessionMutex.withLock {
            val verified =
                getVerifiedSessionLocked()
                    ?: return@withLock false

            matchesVerifiedSession(
                current = verified.first,
                expected = expected
            )
        }
    }

    /**
     * Digunakan repository aplikasi untuk operasi
     * cache lokal yang harus terikat sesi aktif.
     *
     * Block hanya boleh menjalankan operasi lokal.
     * Jangan menjalankan HTTP atau mengambil Mutex
     * sesi kembali dari dalam block.
     */
    suspend fun <T> withVerifiedSession(
        expected: VerifiedLaravelSession,
        block: suspend () -> T
    ): T {
        return sessionMutex.withLock {
            val verified =
                getVerifiedSessionLocked()
                    ?: throw IllegalStateException(
                        "Sesi Laravel tidak terverifikasi"
                    )

            check(
                matchesVerifiedSession(
                    current = verified.first,
                    expected = expected
                )
            ) {
                "Sesi Laravel telah berubah"
            }

            block()
        }
    }

    // ==========================================
    // REGISTER
    // ==========================================

    suspend fun register(
        user: UserEntity,
        rawPassword: String
    ): Result<Long> {
        return try {

            sessionMutex.withLock {
                validateNikOwnershipLocked(
                    nik = user.nik,
                    email = user.email
                )
            }

            val request = LaravelRegisterRequest(
                name = user.namaLengkap,
                nik = user.nik,
                email = user.email,
                whatsapp = user.whatsapp,
                kecamatan = user.kecamatan,
                kelurahan = user.kelurahan,
                password = rawPassword,
                passwordConfirmation = rawPassword
            )

            apiService.register(request)

            sessionMutex.withLock {

                validateNikOwnershipLocked(
                    nik = user.nik,
                    email = user.email
                )

                val existingUser =
                    userDao.getUserByEmail(user.email)

                val id = if (existingUser != null) {

                    if (
                        !existingUser.remoteUserUuid
                            .isNullOrBlank()
                    ) {
                        // Jangan menimpa akun Laravel yang
                        // sebelumnya sudah terverifikasi.
                        existingUser.id.toLong()
                    } else {
                        val updatedUser = user.copy(
                            id = existingUser.id,
                            remoteUserUuid = null,
                            passwordHash = "LARAVEL_AUTH"
                        )

                        userDao.updateUser(updatedUser)

                        existingUser.id.toLong()
                    }

                } else {

                    userDao.insertUser(
                        user.copy(
                            id = 0,
                            remoteUserUuid = null,
                            passwordHash = "LARAVEL_AUTH"
                        )
                    )
                }

                Result.success(id)
            }

        } catch (e: Exception) {

            ensureCancellationPropagated(e)
            reportFailure("Register", e)

            Result.failure(e)
        }
    }

    // ==========================================
    // LOGIN
    // ==========================================

    suspend fun login(
        email: String,
        passwordRaw: String
    ): Result<UserEntity> {

        return try {

            require(email.isNotBlank()) {
                "Email tidak boleh kosong"
            }

            val operation = sessionMutex.withLock {

                authenticationOperation += 1L

                LoginOperation(
                    generation = authenticationOperation,
                    session = userPreferencesRepository
                        .getSessionSnapshot()
                )
            }

            // HTTP di luar Mutex.
            val response = apiService.login(
                LaravelLoginRequest(
                    email = email,
                    password = passwordRaw
                )
            )

            val token = response.accessToken

            val userDto = response.user
                ?: throw IllegalStateException(
                    "Respons login tidak memiliki data pengguna"
                )

            require(!token.isNullOrBlank()) {
                "Token login tidak valid"
            }

            val remoteUserUuid = userDto.id
                ?: throw IllegalStateException(
                    "UUID pengguna tidak ditemukan"
                )

            require(isCanonicalUuid(remoteUserUuid)) {
                "UUID pengguna Laravel tidak valid"
            }

            val serverEmail = userDto.email
                ?.takeIf { it.isNotBlank() }
                ?: email

            require(
                serverEmail.equals(email, ignoreCase = true)
            ) {
                "Identitas email pada respons login tidak sesuai"
            }

            val profileDto = userDto.profile

            val effectiveFullName = when {
                !profileDto?.fullName.isNullOrBlank() ->
                    profileDto.fullName

                !userDto.name.isNullOrBlank() ->
                    userDto.name

                else ->
                    email.substringBefore("@")
            }

            val serverNik = profileDto?.nik ?: ""
            val serverWa = profileDto?.whatsapp ?: ""
            val serverKec = profileDto?.kecamatan ?: ""
            val serverKel = profileDto?.kelurahan ?: ""

            sessionMutex.withLock {

                check(
                    authenticationOperation ==
                            operation.generation
                ) {
                    "Respons login sudah tidak berlaku"
                }

                check(
                    userPreferencesRepository.isSessionCurrent(
                        operation.session
                    )
                ) {
                    "Sesi telah berubah selama proses login"
                }

                validateNikOwnershipLocked(
                    nik = serverNik,
                    email = email
                )

                val existingUser =
                    userDao.getUserByEmail(email)

                validateRemoteBindingLocked(
                    localUser = existingUser,
                    remoteUserUuid = remoteUserUuid
                )

                val effectiveNik =
                    serverNik.ifEmpty {
                        existingUser?.nik ?: ""
                    }

                val effectiveWa =
                    serverWa.ifEmpty {
                        existingUser?.whatsapp ?: ""
                    }

                val effectiveKec =
                    serverKec.ifEmpty {
                        existingUser?.kecamatan ?: ""
                    }

                val effectiveKel =
                    serverKel.ifEmpty {
                        existingUser?.kelurahan ?: ""
                    }

                val localUser = if (existingUser == null) {

                    val newLocalUser = UserEntity(
                        remoteUserUuid = remoteUserUuid,
                        namaLengkap = effectiveFullName,
                        nik = effectiveNik,
                        email = email,
                        whatsapp = effectiveWa,
                        kecamatan = effectiveKec,
                        kelurahan = effectiveKel,
                        passwordHash = "LARAVEL_AUTH"
                    )

                    val newId =
                        userDao.insertUser(newLocalUser)

                    check(
                        newId in 1L..Int.MAX_VALUE.toLong()
                    ) {
                        "Local user ID tidak valid"
                    }

                    newLocalUser.copy(
                        id = newId.toInt()
                    )

                } else {

                    val updatedUser = existingUser.copy(
                        remoteUserUuid = remoteUserUuid,
                        namaLengkap = effectiveFullName,
                        nik = effectiveNik,
                        whatsapp = effectiveWa,
                        kecamatan = effectiveKec,
                        kelurahan = effectiveKel,
                        passwordHash = "LARAVEL_AUTH"
                    )

                    userDao.updateUser(updatedUser)

                    updatedUser
                }

                val saved =
                    userPreferencesRepository
                        .saveLaravelSessionIfUnchanged(
                            expectedSession = operation.session,
                            userId = localUser.id,
                            email = localUser.email,
                            token = token,
                            remoteUserUuid = remoteUserUuid
                        )

                check(saved) {
                    "Sesi berubah sebelum login disimpan"
                }

                val verifiedSession =
                    userPreferencesRepository
                        .getVerifiedSessionContract()
                        ?: throw IllegalStateException(
                            "Sesi login gagal diverifikasi"
                        )

                check(
                    verifiedSession.userId == localUser.id &&
                            verifiedSession.remoteUserUuid ==
                            remoteUserUuid &&
                            verifiedSession.token == token
                ) {
                    "Identitas sesi login tidak konsisten"
                }

                publishSessionLocked(
                    user = localUser,
                    session = verifiedSession
                )

                Result.success(localUser)
            }

        } catch (e: Exception) {

            ensureCancellationPropagated(e)
            reportFailure("Login", e)

            Result.failure(e)
        }
    }

    // ==========================================
    // GET CURRENT SERVER USER
    // ==========================================

    suspend fun getMe(): Result<UserProfile> {
        return try {

            val expectedSession = sessionMutex.withLock {
                userPreferencesRepository.getSessionSnapshot()
            }

            val result = getMeForSession(expectedSession)
            val error = result.exceptionOrNull()
            if (error is LaravelApiException &&
                (error.statusCode == 401 || error.statusCode == 403)
            ) {
                sessionMutex.withLock {
                    if (userPreferencesRepository.clearLaravelSessionIfActive(expectedSession)) {
                        authenticationOperation += 1L
                        clearPublishedSessionLocked()
                    }
                }
            }
            result

        } catch (e: Exception) {

            ensureCancellationPropagated(e)
            reportFailure("GetMe", e)

            Result.failure(e)
        }
    }

    private suspend fun getMeForSession(
        expectedSession: UserSession
    ): Result<UserProfile> {

        return try {

            val token = expectedSession.token

            require(
                expectedSession.isLoggedIn &&
                        expectedSession.userId > 0 &&
                        !token.isNullOrBlank()
            ) {
                "Sesi autentikasi Laravel tidak valid"
            }

            // HTTP di luar Mutex.
            val response = apiService.getMe(token)

            val userDto = response.user
                ?: throw IllegalStateException(
                    "Data pengguna Laravel tidak ditemukan"
                )

            val profileDto = userDto.profile

            val userEmail = userDto.email
                ?: throw IllegalStateException(
                    "Email pengguna Laravel tidak tersedia"
                )

            val remoteUserUuid = userDto.id
                ?: throw IllegalStateException(
                    "UUID pengguna Laravel tidak tersedia"
                )

            require(isCanonicalUuid(remoteUserUuid)) {
                "UUID pengguna Laravel tidak valid"
            }

            require(userEmail.isNotBlank()) {
                "Email pengguna Laravel kosong"
            }

            sessionMutex.withLock {

                check(
                    userPreferencesRepository
                        .isSessionCurrent(expectedSession)
                ) {
                    "Respons profil berasal dari sesi lama"
                }

                require(
                    expectedSession.remoteUserUuid == null ||
                            expectedSession.remoteUserUuid ==
                            remoteUserUuid
                ) {
                    "UUID respons profil tidak sesuai sesi"
                }

                val localUser = userDao.getUserById(
                    expectedSession.userId
                ) ?: throw IllegalStateException(
                    "Identitas pengguna lokal tidak ditemukan"
                )

                require(
                    localUser.id == expectedSession.userId &&
                            localUser.email == expectedSession.email &&
                            localUser.email.equals(
                                userEmail,
                                ignoreCase = true
                            )
                ) {
                    "Identitas pengguna lokal tidak sesuai sesi"
                }

                validateRemoteBindingLocked(
                    localUser = localUser,
                    remoteUserUuid = remoteUserUuid
                )

                val serverNik = profileDto?.nik ?: ""

                validateNikOwnershipLocked(
                    nik = serverNik,
                    email = localUser.email
                )

                val displayName = when {
                    !profileDto?.fullName.isNullOrBlank() ->
                        profileDto.fullName

                    !userDto.name.isNullOrBlank() ->
                        userDto.name

                    localUser.namaLengkap.isNotBlank() ->
                        localUser.namaLengkap

                    else ->
                        userEmail.substringBefore("@")
                            .ifEmpty { "Pengguna" }
                }

                val syncedUser = localUser.copy(
                    remoteUserUuid = remoteUserUuid,
                    namaLengkap = displayName,
                    nik = serverNik.ifEmpty {
                        localUser.nik
                    },
                    whatsapp = profileDto?.whatsapp
                        ?: localUser.whatsapp,
                    kecamatan = profileDto?.kecamatan
                        ?: localUser.kecamatan,
                    kelurahan = profileDto?.kelurahan
                        ?: localUser.kelurahan,
                    passwordHash = "LARAVEL_AUTH"
                )

                // Selesaikan penyimpanan identitas Room
                // sebelum mempublikasikan sesi terverifikasi.
                userDao.updateUser(syncedUser)

                val updated =
                    userPreferencesRepository
                        .updateLaravelSessionIfActive(
                            expectedSession = expectedSession,
                            userId = syncedUser.id,
                            email = syncedUser.email,
                            remoteUserUuid = remoteUserUuid
                        )

                check(updated) {
                    "Sesi berubah selama sinkronisasi profil"
                }

                val verifiedSession =
                    userPreferencesRepository
                        .getVerifiedSessionContract()
                        ?: throw IllegalStateException(
                            "Sesi profil gagal diverifikasi"
                        )

                check(
                    verifiedSession.userId == syncedUser.id &&
                            verifiedSession.remoteUserUuid ==
                            remoteUserUuid &&
                            verifiedSession.token == token
                ) {
                    "Identitas sesi profil tidak konsisten"
                }

                publishSessionLocked(
                    user = syncedUser,
                    session = verifiedSession
                )

                Result.success(
                    profileFromUser(syncedUser)
                )
            }

        } catch (e: Exception) {

            ensureCancellationPropagated(e)
            reportFailure("GetMe", e)

            Result.failure(e)
        }
    }

    // ==========================================
    // LOGOUT
    // ==========================================

    suspend fun logout(): Result<Unit> {

        return try {

            val expectedSession = sessionMutex.withLock {

                // Batalkan hasil login yang masih in-flight.
                authenticationOperation += 1L

                userPreferencesRepository.getSessionSnapshot()
            }

            val token = expectedSession.token

            if (!token.isNullOrBlank()) {

                try {

                    // HTTP di luar Mutex.
                    apiService.logout(token)

                } catch (e: Exception) {

                    ensureCancellationPropagated(e)

                    val alreadyUnauthorized =
                        e is LaravelApiException &&
                                e.statusCode == 401

                    if (!alreadyUnauthorized) {
                        throw e
                    }
                }
            }

            sessionMutex.withLock {

                val cleared =
                    userPreferencesRepository
                        .clearLaravelSessionIfActive(
                            expectedSession
                        )

                if (cleared) {
                    authenticationOperation += 1L
                    clearPublishedSessionLocked()
                }

                // Respons logout lama tidak boleh menghapus sesi baru
                // atau melaporkan sukses ketika sesi lain masih aktif.
                if (!cleared) {
                    val current = userPreferencesRepository.getSessionSnapshot()
                    check(!current.isLoggedIn && current.token.isNullOrBlank()) {
                        "Sesi berubah selama logout. Periksa sesi aktif."
                    }
                    clearPublishedSessionLocked()
                }
            }

            Result.success(Unit)

        } catch (e: Exception) {

            ensureCancellationPropagated(e)
            reportFailure("Logout", e)

            Result.failure(e)
        }
    }

    // ==========================================
    // SERVER SESSION VALIDATION
    // ==========================================

    suspend fun validateSessionWithServer(): Boolean {

        return try {

            val expectedSession = sessionMutex.withLock {

                val session =
                    userPreferencesRepository
                        .getSessionSnapshot()

                if (
                    !session.isLoggedIn ||
                    session.token.isNullOrBlank()
                ) {
                    clearPublishedSessionLocked()
                }

                session
            }

            if (
                !expectedSession.isLoggedIn ||
                expectedSession.token.isNullOrBlank()
            ) {
                return false
            }

            val result =
                getMeForSession(expectedSession)

            if (result.isSuccess) {

                return sessionMutex.withLock {
                    getVerifiedSessionLocked() != null
                }
            }

            val error = result.exceptionOrNull()

            if (
                error is LaravelApiException &&
                (
                        error.statusCode == 401 ||
                                error.statusCode == 403
                        )
            ) {

                sessionMutex.withLock {

                    val cleared =
                        userPreferencesRepository
                            .clearLaravelSessionIfActive(
                                expectedSession
                            )

                    if (cleared) {
                        authenticationOperation += 1L
                        clearPublishedSessionLocked()
                    }
                }

                return false
            }

            // Offline fallback hanya untuk identitas
            // lokal yang benar-benar terverifikasi.
            if (isTransientConnectivityFailure(error)) {

                return sessionMutex.withLock {

                    if (
                        !userPreferencesRepository
                            .isSessionCurrent(expectedSession)
                    ) {
                        return@withLock false
                    }

                    val verified =
                        getVerifiedSessionLocked()
                            ?: return@withLock false

                    publishSessionLocked(
                        user = verified.second,
                        session = verified.first
                    )

                    true
                }
            }

            sessionMutex.withLock {

                if (
                    userPreferencesRepository
                        .isSessionCurrent(expectedSession)
                ) {
                    clearPublishedSessionLocked()
                }
            }

            false

        } catch (e: Exception) {

            ensureCancellationPropagated(e)
            reportFailure("ValidateSession", e)

            false
        }
    }

    // ==========================================
    // EXISTING AUTHENTICATION ACCESSORS
    // ==========================================

    /**
     * Indikator state yang telah dipublikasikan.
     * Bukan pengganti verified session contract.
     */
    fun isUserLoggedIn(): Boolean {
        return publishedGeneration != null &&
                _cachedUserEntity.value != null &&
                _currentRemoteUuid.value != null
    }

    fun getCurrentUserEmail(): String? {
        return _cachedProfile.value?.email
            ?: _cachedUserEntity.value?.email
    }

    fun getCurrentUserRemoteUuid(): String? {
        return _currentRemoteUuid.value
    }

    suspend fun getUserProfile(): UserProfile? {

        val cached = sessionMutex.withLock {

            val verified =
                getVerifiedSessionLocked()
                    ?: return@withLock null

            val session = verified.first

            if (
                publishedGeneration == session.generation
            ) {
                _cachedProfile.value
            } else {
                null
            }
        }

        return cached ?: getMe().getOrNull()
    }

    suspend fun getAuthenticatedUser(): UserEntity? {
        return getVerifiedSessionContract()
    }

    suspend fun getUserById(
        id: Int
    ): UserEntity? {
        return userDao.getUserById(id)
    }

    suspend fun getUserByEmail(
        email: String
    ): UserEntity? {
        return userDao.getUserByEmail(email)
    }
}
