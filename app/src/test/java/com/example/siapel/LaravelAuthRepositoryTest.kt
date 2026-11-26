package com.example.siapel

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.siapel.data.datastore.UserPreferencesRepository
import com.example.siapel.data.local.dao.UserDao
import com.example.siapel.data.local.entity.UserEntity
import com.example.siapel.data.remote.laravel.LaravelAuthApiService
import com.example.siapel.data.remote.laravel.LaravelClientProvider
import com.example.siapel.data.repository.LaravelAuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

private class FakeUserDao : UserDao {
    var userByEmail: UserEntity? = null
    var userByNik: UserEntity? = null
    var userById: UserEntity? = null
    var insertedUser: UserEntity? = null
    var updatedUser: UserEntity? = null

    override suspend fun insertUser(user: UserEntity): Long {
        insertedUser = user
        return if (user.id > 0) user.id.toLong() else 10L
    }

    override suspend fun getUserByEmail(email: String): UserEntity? = userByEmail
    override suspend fun getUserByNik(nik: String): UserEntity? = userByNik
    override suspend fun getUserById(id: Int): UserEntity? = userById ?: userByEmail
    override suspend fun getUserByRemoteUuid(remoteUserUuid: String): UserEntity? = null
    override suspend fun updateUser(user: UserEntity) {
        updatedUser = user
    }
}

/**
 * Unit Test terisolasi untuk [LaravelAuthRepository] dan sesi identity (Batch A).
 */
class LaravelAuthRepositoryTest {

    private lateinit var fakeUserDao: FakeUserDao
    private lateinit var dataStoreScope: CoroutineScope
    private val clients = mutableListOf<HttpClient>()

    @After
    fun tearDown() {
        clients.forEach { it.close() }
        dataStoreScope.cancel()
    }
    private val validRemoteUuid = "88888888-4444-4444-4444-123456789abc"
    private val differentRemoteUuid = "22222222-2222-2222-2222-222222222222"

    private fun createMockAuthApiService(
        responder: suspend () -> Pair<HttpStatusCode, String>
    ): LaravelAuthApiService {
        val engine = MockEngine {
            val (status, content) = responder()
            respond(
                content = content,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(LaravelClientProvider.json)
            }
        }
        clients.add(client)
        return LaravelAuthApiService(client)
    }

    @Before
    fun setUp() {
        fakeUserDao = FakeUserDao()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @Test
    fun testLogin_validCanonicalRemoteUuid_persistsToRoomAndDataStore(): Unit = runBlocking {
        val responseJson = """
            {
                "access_token": "valid-token-123",
                "token_type": "Bearer",
                "user": {
                    "id": "$validRemoteUuid",
                    "name": "Test User",
                    "email": "test@example.com"
                }
            }
        """.trimIndent()

        val apiService = createMockAuthApiService { Pair(HttpStatusCode.OK, responseJson) }

        val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        val preferencesRepo = UserPreferencesRepository(testDataStore)

        val authRepository = LaravelAuthRepository(fakeUserDao, preferencesRepo, apiService)

        val result = authRepository.login("test@example.com", "password123")

        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals(validRemoteUuid, user?.remoteUserUuid)
        assertEquals(validRemoteUuid, authRepository.getCurrentUserRemoteUuid())

        // Verify session saved
        val session = preferencesRepo.getVerifiedSessionContract()
        assertNotNull(session)
        assertEquals(validRemoteUuid, session?.remoteUserUuid)
        assertEquals("valid-token-123", session?.token)

    }

    @Test
    fun testLogin_invalidRemoteUuid_returnsFailure(): Unit = runBlocking {
        val responseJson = """
            {
                "access_token": "valid-token-123",
                "token_type": "Bearer",
                "user": {
                    "id": "invalid-non-canonical-id",
                    "name": "Test User",
                    "email": "test@example.com"
                }
            }
        """.trimIndent()

        val apiService = createMockAuthApiService { Pair(HttpStatusCode.OK, responseJson) }
        val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        val preferencesRepo = UserPreferencesRepository(testDataStore)

        val authRepository = LaravelAuthRepository(fakeUserDao, preferencesRepo, apiService)

        val result = authRepository.login("test@example.com", "password123")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("UUID pengguna Laravel tidak valid") == true)
        assertNull(fakeUserDao.insertedUser)
        assertNull(fakeUserDao.updatedUser)
        assertNull(preferencesRepo.getVerifiedSessionContract())

    }

    @Test
    fun testLogin_conflictingLocalAccountUuid_returnsFailure(): Unit = runBlocking {
        val responseJson = """
            {
                "access_token": "valid-token-123",
                "token_type": "Bearer",
                "user": {
                    "id": "$validRemoteUuid",
                    "name": "Test User",
                    "email": "test@example.com"
                }
            }
        """.trimIndent()

        fakeUserDao.userByEmail = UserEntity(
            id = 10,
            remoteUserUuid = differentRemoteUuid,
            namaLengkap = "Old User",
            nik = "111", email = "test@example.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen", passwordHash = "HASH"
        )

        val apiService = createMockAuthApiService { Pair(HttpStatusCode.OK, responseJson) }
        val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        val preferencesRepo = UserPreferencesRepository(testDataStore)

        val authRepository = LaravelAuthRepository(fakeUserDao, preferencesRepo, apiService)

        val result = authRepository.login("test@example.com", "password123")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Konflik identitas akun") == true)

    }

    @Test
    fun testStaleSessionProtection_updateLaravelSessionIfActive(): Unit = runBlocking {
        val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        val preferencesRepo = UserPreferencesRepository(testDataStore)

        preferencesRepo.saveLaravelSession(
            userId = 10,
            email = "user1@example.com",
            token = "token-session-A",
            remoteUserUuid = validRemoteUuid
        )

        val isUpdated = preferencesRepo.updateLaravelSessionIfActive(
            expectedToken = "old-token-X",
            userId = 10,
            email = "user1@example.com",
            remoteUserUuid = validRemoteUuid
        )

        assertFalse("Stale response with different token must NOT update DataStore session", isUpdated)

        preferencesRepo.clearLaravelSession()
        val sessionAfterClear = preferencesRepo.getVerifiedSessionContract()
        assertNull("Verified session contract must be null after session clear", sessionAfterClear)

    }

    @Test
    fun testGetMe_arrivingAfterLogout_rejectedAndNoStateMutation(): Unit = runBlocking {
        val responseJson = """
            {
                "user": {
                    "id": "$validRemoteUuid",
                    "name": "Test User",
                    "email": "test@example.com"
                }
            }
        """.trimIndent()

        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val apiService = createMockAuthApiService {
            requestStarted.complete(Unit)
            releaseResponse.await()
            Pair(HttpStatusCode.OK, responseJson)
        }
        val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        val preferencesRepo = UserPreferencesRepository(testDataStore)

        // Save active session with token "token-A"
        preferencesRepo.saveLaravelSession(10, "test@example.com", "token-A", validRemoteUuid)
        fakeUserDao.userByEmail = UserEntity(id = 10, remoteUserUuid = validRemoteUuid, namaLengkap = "Test", nik = "111", email = "test@example.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen", passwordHash = "HASH")

        val authRepository = LaravelAuthRepository(fakeUserDao, preferencesRepo, apiService)

        val pending = async { authRepository.getMe() }
        val result = withTimeout(5_000) {
            try {
                requestStarted.await()
                preferencesRepo.clearLaravelSession()
            } finally {
                releaseResponse.complete(Unit)
            }
            pending.await()
        }
        assertNull(fakeUserDao.updatedUser)
        assertNull(fakeUserDao.insertedUser)
        assertNull(preferencesRepo.getVerifiedSessionContract())

        assertTrue("getMe() setelah logout harus ditolak sebagai stale response", result.isFailure)
        assertNull("State remote UUID in-memory harus null setelah logout", authRepository.getCurrentUserRemoteUuid())

    }

    @Test
    fun testGetMe_conflictingRemoteUuid_rejectedAndNoRoomMutation(): Unit = runBlocking {
        val responseJson = """
            {
                "user": {
                    "id": "$validRemoteUuid",
                    "name": "Test User",
                    "email": "test@example.com"
                }
            }
        """.trimIndent()

        val apiService = createMockAuthApiService { Pair(HttpStatusCode.OK, responseJson) }
        val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        val preferencesRepo = UserPreferencesRepository(testDataStore)

        preferencesRepo.saveLaravelSession(10, "test@example.com", "token-A", differentRemoteUuid)
        // Local user is bound to differentRemoteUuid
        fakeUserDao.userByEmail = UserEntity(id = 10, remoteUserUuid = differentRemoteUuid, namaLengkap = "Test", nik = "111", email = "test@example.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen", passwordHash = "HASH")

        val authRepository = LaravelAuthRepository(fakeUserDao, preferencesRepo, apiService)

        val result = authRepository.getMe()

        assertTrue("getMe() dengan UUID server berbeda harus ditolak akibat konflik identitas", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("UUID respons profil tidak sesuai sesi") == true)
        assertNull(fakeUserDao.updatedUser)
        assertNull(fakeUserDao.insertedUser)
        assertEquals(differentRemoteUuid, preferencesRepo.getVerifiedSessionContract()?.remoteUserUuid)
        assertEquals(differentRemoteUuid, fakeUserDao.userByEmail?.remoteUserUuid)

    }

    @Test
    fun testVerifiedSessionContract_rejectsRoomIdentityMismatch(): Unit = runBlocking {
        val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        val preferencesRepo = UserPreferencesRepository(testDataStore)

        // DataStore has remoteUserUuid = validRemoteUuid
        preferencesRepo.saveLaravelSession(10, "test@example.com", "token-A", validRemoteUuid)

        // Room user has remoteUserUuid = differentRemoteUuid (mismatch!)
        fakeUserDao.userById = UserEntity(id = 10, remoteUserUuid = differentRemoteUuid, namaLengkap = "Test", nik = "111", email = "test@example.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen", passwordHash = "HASH")

        val authRepository = LaravelAuthRepository(fakeUserDao, preferencesRepo)

        val verifiedContract = authRepository.getVerifiedSessionContract()

        assertNull("getVerifiedSessionContract harus null jika identitas Room dan DataStore mismatch", verifiedContract)

    }

    @Test
    fun testMissingRemoteUuidRestoration_successfulWhenValid(): Unit = runBlocking {
        val responseJson = """
            {
                "user": {
                    "id": "$validRemoteUuid",
                    "name": "Test User",
                    "email": "test@example.com"
                }
            }
        """.trimIndent()

        val apiService = createMockAuthApiService { Pair(HttpStatusCode.OK, responseJson) }
        val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        val preferencesRepo = UserPreferencesRepository(testDataStore)

        // Local user has NULL remoteUserUuid
        fakeUserDao.userByEmail = UserEntity(id = 10, remoteUserUuid = null, namaLengkap = "Test", nik = "111", email = "test@example.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen", passwordHash = "HASH")
        preferencesRepo.saveLaravelSession(10, "test@example.com", "token-A", null)

        val authRepository = LaravelAuthRepository(fakeUserDao, preferencesRepo, apiService)

        val result = authRepository.getMe()

        assertTrue(result.isSuccess)
        assertEquals(validRemoteUuid, fakeUserDao.updatedUser?.remoteUserUuid) // Restored in Room!
        assertEquals(validRemoteUuid, authRepository.getCurrentUserRemoteUuid())

    }

    @Test
    fun testCancellationException_propagatedInAuth(): Unit = runBlocking {
        val mockEngineCancelled = MockEngine {
            throw CancellationException("Job cancelled explicitly")
        }
        val client = HttpClient(mockEngineCancelled) {
            install(ContentNegotiation) { json(LaravelClientProvider.json) }
        }
        clients.add(client)
        val apiService = LaravelAuthApiService(client)

        val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        val preferencesRepo = UserPreferencesRepository(testDataStore)

        val authRepository = LaravelAuthRepository(fakeUserDao, preferencesRepo, apiService)

        try {
            authRepository.login("test@example.com", "password")
            fail("CancellationException harus dipropagasi")
        } catch (e: Exception) {
            assertTrue("Exception harus berupa CancellationException atau memiliki cause CancellationException", e is CancellationException || e.cause is CancellationException)
        }

    }

    @Test
    fun testSameCredentialsNewGeneration_invalidatesOldSnapshot(): Unit = runBlocking {
        val tempFile = File.createTempFile("datastore_generation", ".preferences_pb")
        tempFile.deleteOnExit()
        val preferencesRepo = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        )
        preferencesRepo.saveLaravelSession(10, "test@example.com", "token-A", validRemoteUuid)
        val old = preferencesRepo.getSessionSnapshot()
        preferencesRepo.clearLaravelSession()
        preferencesRepo.saveLaravelSession(10, "test@example.com", "token-A", validRemoteUuid)
        val current = preferencesRepo.getSessionSnapshot()
        assertNotEquals(old.generation, current.generation)
        assertFalse(preferencesRepo.isSessionCurrent(old))
        assertFalse(preferencesRepo.clearLaravelSessionIfActive(old))
        assertEquals(current, preferencesRepo.getSessionSnapshot())
    }

}
