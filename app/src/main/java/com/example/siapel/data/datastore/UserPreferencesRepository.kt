package com.example.siapel.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

data class UserSession(
    val isLoggedIn: Boolean,
    val userId: Int,
    val email: String,
    val remoteUserUuid: String? = null,
    val token: String? = null,
    val generation: String = ""
) {
    override fun toString(): String {
        return "UserSession(isLoggedIn=$isLoggedIn, credentials=REDACTED)"
    }
}

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {

    companion object {

        internal val sessionMutex = Mutex()

        private val CANONICAL_UUID_REGEX = Regex(
            "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
        )
    }

    private object PreferencesKeys {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val USER_ID = intPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")

        val LARAVEL_ACCESS_TOKEN =
            stringPreferencesKey("laravel_access_token")

        val LARAVEL_REMOTE_USER_UUID =
            stringPreferencesKey("laravel_remote_user_uuid")

        val SESSION_GENERATION =
            stringPreferencesKey("session_generation")
    }

    private fun isCanonicalUuid(value: String?): Boolean {
        return !value.isNullOrBlank() &&
                CANONICAL_UUID_REGEX.matches(value)
    }

    private fun newGeneration(): String {
        return UUID.randomUUID().toString()
    }

    private fun Preferences.toUserSession(): UserSession {
        return UserSession(
            isLoggedIn = this[PreferencesKeys.IS_LOGGED_IN] ?: false,
            userId = this[PreferencesKeys.USER_ID] ?: -1,
            email = this[PreferencesKeys.USER_EMAIL] ?: "",
            remoteUserUuid =
                this[PreferencesKeys.LARAVEL_REMOTE_USER_UUID]
                    ?.ifBlank { null },
            token =
                this[PreferencesKeys.LARAVEL_ACCESS_TOKEN]
                    ?.ifBlank { null },
            generation =
                this[PreferencesKeys.SESSION_GENERATION] ?: ""
        )
    }

    private fun MutablePreferences.invalidateGeneration() {
        this[PreferencesKeys.SESSION_GENERATION] = newGeneration()
    }

    private fun MutablePreferences.removeLaravelCredentials() {
        remove(PreferencesKeys.LARAVEL_ACCESS_TOKEN)
        remove(PreferencesKeys.LARAVEL_REMOTE_USER_UUID)
    }

    private fun MutablePreferences.clearSessionValues() {
        this[PreferencesKeys.IS_LOGGED_IN] = false
        this[PreferencesKeys.USER_ID] = -1
        this[PreferencesKeys.USER_EMAIL] = ""

        removeLaravelCredentials()
        invalidateGeneration()
    }

    private fun MutablePreferences.writeLaravelSession(
        userId: Int,
        email: String,
        token: String,
        remoteUserUuid: String?
    ) {
        this[PreferencesKeys.IS_LOGGED_IN] = true
        this[PreferencesKeys.USER_ID] = userId
        this[PreferencesKeys.USER_EMAIL] = email
        this[PreferencesKeys.LARAVEL_ACCESS_TOKEN] = token

        if (remoteUserUuid != null) {
            this[PreferencesKeys.LARAVEL_REMOTE_USER_UUID] =
                remoteUserUuid
        } else {
            remove(PreferencesKeys.LARAVEL_REMOTE_USER_UUID)
        }

        invalidateGeneration()
    }

    private fun matchesSession(
        current: UserSession,
        expected: UserSession
    ): Boolean {
        return current.generation == expected.generation &&
                current.isLoggedIn == expected.isLoggedIn &&
                current.userId == expected.userId &&
                current.email == expected.email &&
                current.token == expected.token &&
                current.remoteUserUuid == expected.remoteUserUuid
    }

    // ==========================================
    // SESSION OBSERVATION
    // ==========================================

    val userSessionFlow: Flow<UserSession> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences.toUserSession()
        }

    val laravelTokenFlow: Flow<String?> =
        userSessionFlow.map { session ->
            session.token
        }

    val laravelRemoteUserUuidFlow: Flow<String?> =
        userSessionFlow.map { session ->
            session.remoteUserUuid
        }

    /**
     * Membaca seluruh sesi dari satu snapshot DataStore.
     *
     * Sesi lama yang belum memiliki generation akan memperoleh
     * generation tanpa menghapus data yang sudah tersimpan.
     *
     * Kegagalan pembacaan DataStore tidak dianggap sebagai
     * sesi kosong yang sah.
     */
    suspend fun getSessionSnapshot(): UserSession {
        val preferences = dataStore.edit { current ->
            if (
                current[PreferencesKeys.SESSION_GENERATION]
                    .isNullOrBlank()
            ) {
                current.invalidateGeneration()
            }
        }

        return preferences.toUserSession()
    }

    /**
     * Memeriksa apakah snapshot masih merupakan sesi terkini.
     *
     * Pemeriksaan ini bukan pengganti shared session
     * synchronization untuk operasi lintas repository.
     */
    suspend fun isSessionCurrent(
        expectedSession: UserSession
    ): Boolean {
        if (expectedSession.generation.isBlank()) {
            return false
        }

        val currentSession = getSessionSnapshot()

        return matchesSession(
            current = currentSession,
            expected = expectedSession
        )
    }

    // ==========================================
    // LARAVEL SESSION PERSISTENCE
    // ==========================================

    suspend fun saveLaravelSession(
        userId: Int,
        email: String,
        token: String,
        remoteUserUuid: String? = null
    ) {
        require(userId > 0) {
            "User ID harus bernilai positif"
        }

        require(email.isNotBlank()) {
            "Email tidak boleh kosong"
        }

        require(token.isNotBlank()) {
            "Token tidak boleh kosong"
        }

        require(
            remoteUserUuid == null ||
                    isCanonicalUuid(remoteUserUuid)
        ) {
            "Remote user UUID tidak valid"
        }

        dataStore.edit { preferences ->
            preferences.writeLaravelSession(
                userId = userId,
                email = email,
                token = token,
                remoteUserUuid = remoteUserUuid
            )
        }
    }

    /**
     * Menyimpan sesi login baru hanya apabila sesi yang
     * mendasari operasi login belum berubah.
     *
     * Digunakan untuk mencegah respons login lama
     * mengaktifkan kembali sesi setelah logout atau
     * pergantian akun.
     */
    suspend fun saveLaravelSessionIfUnchanged(
        expectedSession: UserSession,
        userId: Int,
        email: String,
        token: String,
        remoteUserUuid: String
    ): Boolean {
        if (
            expectedSession.generation.isBlank() ||
            userId <= 0 ||
            email.isBlank() ||
            token.isBlank() ||
            !isCanonicalUuid(remoteUserUuid)
        ) {
            return false
        }

        var isSaved = false

        dataStore.edit { preferences ->
            val current = preferences.toUserSession()

            if (matchesSession(current, expectedSession)) {
                preferences.writeLaravelSession(
                    userId = userId,
                    email = email,
                    token = token,
                    remoteUserUuid = remoteUserUuid
                )

                isSaved = true
            }
        }

        return isSaved
    }

    // ==========================================
    // CONDITIONAL SESSION RESTORATION
    // ==========================================

    /**
     * Kontrak aman untuk pemulihan UUID sesi Laravel.
     *
     * Token, local user ID, email, remote UUID dan
     * generation diperiksa dalam satu transaksi DataStore.
     *
     * Tidak mengizinkan reassignment identitas.
     */
    suspend fun updateLaravelSessionIfActive(
        expectedSession: UserSession,
        userId: Int,
        email: String,
        remoteUserUuid: String
    ): Boolean {
        if (
            !expectedSession.isLoggedIn ||
            expectedSession.generation.isBlank() ||
            expectedSession.token.isNullOrBlank() ||
            userId <= 0 ||
            expectedSession.userId != userId ||
            expectedSession.email != email ||
            !isCanonicalUuid(remoteUserUuid)
        ) {
            return false
        }

        var isUpdated = false

        dataStore.edit { preferences ->
            val current = preferences.toUserSession()

            val existingBindingValid =
                current.remoteUserUuid == null ||
                        current.remoteUserUuid == remoteUserUuid

            if (
                matchesSession(current, expectedSession) &&
                current.isLoggedIn &&
                existingBindingValid
            ) {
                preferences[PreferencesKeys.LARAVEL_REMOTE_USER_UUID] =
                    remoteUserUuid

                if (current.remoteUserUuid != remoteUserUuid) {
                    preferences.invalidateGeneration()
                }

                isUpdated = true
            }
        }

        return isUpdated
    }

    /**
     * Overload kompatibilitas sementara.
     *
     * Dipertahankan agar LaravelAuthRepository versi
     * sebelumnya dan unit test existing tetap dapat
     * dikompilasi.
     *
     * Tidak digunakan untuk kontrak sesi final karena
     * caller belum memberikan generation sebelum HTTP.
     */
    @Deprecated(
        message = "Gunakan overload expectedSession untuk proteksi sesi penuh"
    )
    suspend fun updateLaravelSessionIfActive(
        expectedToken: String,
        userId: Int,
        email: String,
        remoteUserUuid: String
    ): Boolean {
        if (
            expectedToken.isBlank() ||
            userId <= 0 ||
            email.isBlank() ||
            !isCanonicalUuid(remoteUserUuid)
        ) {
            return false
        }

        val snapshot = getSessionSnapshot()

        if (snapshot.token != expectedToken) {
            return false
        }

        return updateLaravelSessionIfActive(
            expectedSession = snapshot,
            userId = userId,
            email = email,
            remoteUserUuid = remoteUserUuid
        )
    }

    // ==========================================
    // DATASTORE SESSION CONTRACT
    // ==========================================

    /**
     * Memvalidasi struktur sesi DataStore.
     *
     * Verifikasi akhir terhadap UserEntity di Room
     * dilakukan oleh LaravelAuthRepository.
     */
    suspend fun getVerifiedSessionContract(): UserSession? {
        val session = getSessionSnapshot()

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

        return session
    }

    // ==========================================
    // SESSION CLEARING
    // ==========================================

    /**
     * Menghapus seluruh kredensial Laravel secara atomik.
     */
    suspend fun clearLaravelSession() {
        dataStore.edit { preferences ->
            preferences.clearSessionValues()
        }
    }

    /**
     * Menghapus sesi hanya jika snapshot yang mendasari
     * logout atau kegagalan autentikasi masih aktif.
     *
     * Respons lama tidak dapat menghapus sesi baru.
     */
    suspend fun clearLaravelSessionIfActive(
        expectedSession: UserSession
    ): Boolean {
        if (expectedSession.generation.isBlank()) {
            return false
        }

        var isCleared = false

        dataStore.edit { preferences ->
            val current = preferences.toUserSession()

            if (matchesSession(current, expectedSession)) {
                preferences.clearSessionValues()
                isCleared = true
            }
        }

        return isCleared
    }

}

