package com.example.siapel

import android.net.TestUri
import android.net.Uri
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.remote.laravel.LaravelApplicationApiService
import com.example.siapel.data.remote.laravel.LaravelClientProvider
import com.example.siapel.data.remote.laravel.LaravelDocumentApiService
import com.example.siapel.data.remote.laravel.LaravelEndpoints
import com.example.siapel.data.remote.laravel.dto.*
import com.example.siapel.data.repository.LaravelApplicationRepository
import com.example.siapel.data.repository.LaravelCacheSyncStatus
import com.example.siapel.data.repository.LaravelRemoteOutcome
import com.example.siapel.data.repository.LaravelMutationUncertainException
import com.example.siapel.data.repository.LaravelDocumentRepository
import com.example.siapel.data.repository.toRoomEntity
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.SelectedDocument
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Unit Test terisolasi untuk [LaravelApplicationRepository] menggunakan Ktor MockEngine 3.0.3.
 */
class LaravelApplicationRepositoryTest {

    @After
    fun tearDown() {
        LaravelClientProvider.tokenProvider = null
    }

    private fun createMockClient(
        responder: (request: HttpRequestData) -> Pair<HttpStatusCode, String>
    ): HttpClient {
        val engine = MockEngine { requestData ->
            val (status, content) = responder(requestData)
            respond(
                content = content,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(LaravelClientProvider.json)
            }
            defaultRequest {
                url(LaravelEndpoints.BASE_URL)
            }
        }
    }

    private val validAppUuid = "9b1deb4d-3b7d-419b-a683-123456789abc"
    private val validUserUuid = "11111111-1111-1111-1111-111111111111"
    private val sampleBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    private val testUri: Uri = TestUri()

    private fun buildMockAppJson(id: String, serviceType: String): String {
        return """
            {
                "id": "$id",
                "user_id": "$validUserUuid",
                "service_type": "$serviceType",
                "category": "BARU",
                "submission_code": "$serviceType-777888",
                "submission_date": "18-11-2026",
                "status": "SUBMITTED",
                "nik_pelapor": "1111111111111111",
                "nama_pelapor": "Pelapor Test",
                "email": "test@example.com",
                "whatsapp": "081234567890",
                "kecamatan": "Klojen",
                "kelurahan": "Klojen"
            }
        """.trimIndent()
    }

    @Test
    fun testToRoomEntityMapping_kiaAndKtp(): Unit = runBlocking {
        val dtoKia = LaravelApplicationDto(
            id = validAppUuid, userId = validUserUuid, serviceType = "KIA", category = "KIA BARU",
            submissionCode = "KIA-111", submissionDate = "18-11-2026", status = "SUBMITTED",
            nikPelapor = "111", namaPelapor = "Pelapor", email = "e@m.com", whatsapp = "081",
            kecamatan = "Klojen", kelurahan = "Klojen",
            kiaDetail = LaravelKiaDetailDto(applicationId = validAppUuid, nikAnak = "357301", namaAnak = "Anak", doc1Path = "path/doc1.jpg")
        )

        val entityKia = dtoKia.toRoomEntity(localUserId = 10, verifiedRemoteUserUuid = validUserUuid)

        assertEquals(10, entityKia.userId)
        assertEquals(validAppUuid, entityKia.remoteUuid)
        assertEquals(validUserUuid, entityKia.remoteUserUuid)
        assertEquals("KIA", entityKia.serviceType)
        assertEquals("KIA-111", entityKia.submissionCode)
        assertEquals("357301", entityKia.nikAnak)
        assertEquals("Anak", entityKia.namaAnak)
        assertEquals("path/doc1.jpg", entityKia.doc1)

        val dtoKtp = LaravelApplicationDto(
            id = validAppUuid, userId = validUserUuid, serviceType = "KTP", category = "KTP PEREKAMAN",
            submissionCode = "KTP-222", submissionDate = "18-11-2026", status = "PROCESSING",
            nikPelapor = "111", namaPelapor = "Pelapor", email = "e@m.com", whatsapp = "081",
            kecamatan = "Klojen", kelurahan = "Klojen",
            ktpDetail = LaravelKtpDetailDto(applicationId = validAppUuid, doc1Path = "ktp/doc1.jpg", docSelfiePath = "ktp/selfie.jpg")
        )

        val entityKtp = dtoKtp.toRoomEntity(localUserId = 10, verifiedRemoteUserUuid = validUserUuid)

        assertEquals("KTP", entityKtp.serviceType)
        assertEquals("KTP-222", entityKtp.submissionCode)
        assertEquals(ApplicationStatus.PROCESSING, entityKtp.status)
        assertEquals("ktp/doc1.jpg", entityKtp.doc1)
        assertEquals("ktp/selfie.jpg", entityKtp.docSelfie)
    }

    @Test
    fun testToRoomEntity_unknownStatus_throwsIllegalArgumentException(): Unit = runBlocking {
        val dtoInvalidStatus = LaravelApplicationDto(
            id = validAppUuid, userId = validUserUuid, serviceType = "KIA", category = "KIA BARU",
            submissionCode = "KIA-999", submissionDate = "18-11-2026", status = "UNKNOWN_SERVER_STATUS",
            nikPelapor = "111", namaPelapor = "Pelapor", email = "e@m.com", whatsapp = "081",
            kecamatan = "Klojen", kelurahan = "Klojen"
        )

        try {
            dtoInvalidStatus.toRoomEntity(localUserId = 10, verifiedRemoteUserUuid = validUserUuid)
            fail("Unknown server status harus melempar IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("tidak dikenal") == true)
        }
    }

    @Test
    fun testToRoomEntity_explicitNullDocumentPathInRelation_clearsStaleMetadata(): Unit = runBlocking {
        val existingLocal = ApplicationEntity(
            id = 5, userId = 10, remoteUuid = validAppUuid, remoteUserUuid = validUserUuid, serviceType = "KIA", category = "KIA BARU",
            submissionCode = "KIA-111", submissionDate = "18-11-2026", status = ApplicationStatus.SUBMITTED,
            kecamatan = "Klojen", kelurahan = "Klojen", email = "e@m.com", whatsapp = "081", keterangan = "",
            nikPelapor = "111", namaPelapor = "Pelapor",
            doc1 = "old_stale_path.jpg"
        )

        val dtoWithNullDoc1 = LaravelApplicationDto(
            id = validAppUuid, userId = validUserUuid, serviceType = "KIA", category = "KIA BARU",
            submissionCode = "KIA-111", submissionDate = "18-11-2026", status = "SUBMITTED",
            nikPelapor = "111", namaPelapor = "Pelapor", email = "e@m.com", whatsapp = "081",
            kecamatan = "Klojen", kelurahan = "Klojen",
            kiaDetail = LaravelKiaDetailDto(applicationId = validAppUuid, doc1Path = null) // Explicit null from server!
        )

        val entityUpdated = dtoWithNullDoc1.toRoomEntity(localUserId = 10, verifiedRemoteUserUuid = validUserUuid, existingEntity = existingLocal)

        assertNull("Explicit null dari relasi server harus menghapus stale metadata lokal", entityUpdated.doc1)
    }

    @Test
    fun testCreateKiaApplicationWithDocuments_fullSuccess(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val mockClient = HttpClient(MockEngine { requestData ->
            requests.add(requestData)
            val path = requestData.url.encodedPath
            when {
                path == "/api/applications" && requestData.method == HttpMethod.Post -> {
                    respond(
                        content = """{"message":"OK","data":${buildMockAppJson(validAppUuid, "KIA")}}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                path == "/api/applications/$validAppUuid" && requestData.method == HttpMethod.Get -> {
                    respond(
                        content = """{"message":"OK","data":${buildMockAppJson(validAppUuid, "KIA")}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                path.startsWith("/api/applications/$validAppUuid/documents/") && requestData.method == HttpMethod.Post -> {
                    val slot = path.substringAfterLast('/')
                    respond(
                        content = """{"message":"Uploaded","data":{"slot":"$slot","column":"col","path":"p/$slot","size":4,"mime_type":"image/jpeg","replaced":false}}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }) {
            install(ContentNegotiation) { json(LaravelClientProvider.json) }
            defaultRequest { url(LaravelEndpoints.BASE_URL) }
        }

        val repository = LaravelApplicationRepository(
            LaravelApplicationApiService(mockClient),
            LaravelDocumentRepository(LaravelDocumentApiService(mockClient), Dispatchers.Unconfined),
            remoteOnly = true
        )

        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak Test")
        )

        val docMap = mapOf(
            "doc1" to SelectedDocument(uri = testUri, displayName = "doc1.jpg", mimeType = "image/jpeg"),
            "docSelfie" to SelectedDocument(uri = testUri, displayName = "selfie.jpg", mimeType = "image/jpeg")
        )

        val result = repository.createKiaApplicationWithDocuments(
            context = null,
            request = kiaReq,
            documents = docMap,
            token = "synthetic-token",
            inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )

        assertTrue(result.isSuccess)
        val createResult = result.getOrNull()
        assertNotNull(createResult)
        assertTrue(createResult!!.isFullySuccessful)
        assertEquals(validAppUuid, createResult.applicationUuid)
        assertEquals("KIA-777888", createResult.submissionCode)
        assertEquals(2, createResult.uploadedSlots.size)
        assertTrue(createResult.failedSlots.isEmpty())

        // 1 POST create + 2 POST uploads + 1 GET authoritative detail refresh = 4 requests
        assertEquals(4, requests.size)
        assertEquals("/api/applications", requests[0].url.encodedPath)

        mockClient.close()
    }

    @Test
    fun testCreateKkAndUpdateAktaWithDocuments_explicitCoverage(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val aktaUuid = "9b1deb4d-3b7d-419b-a683-123456789abd"
        val mockClient = HttpClient(MockEngine { requestData ->
            requests.add(requestData)
            val path = requestData.url.encodedPath
            when {
                path == "/api/applications" && requestData.method == HttpMethod.Post -> {
                    respond(
                        content = """{"message":"OK","data":${buildMockAppJson(validAppUuid, "KK")}}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                path == "/api/applications/$aktaUuid" && requestData.method == HttpMethod.Put -> {
                    respond(
                        content = """{"message":"OK","data":${buildMockAppJson(aktaUuid, "AKTA")}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                path == "/api/applications/$aktaUuid" && requestData.method == HttpMethod.Get -> {
                    respond("""{"message":"OK","data":${buildMockAppJson(aktaUuid, "AKTA")}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
                path == "/api/applications/$validAppUuid" && requestData.method == HttpMethod.Get -> {
                    respond(
                        content = """{"message":"OK","data":${buildMockAppJson(validAppUuid, "KK")}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                path.startsWith("/api/applications/$validAppUuid/documents/") && requestData.method == HttpMethod.Post -> {
                    val slot = path.substringAfterLast('/')
                    respond(
                        content = """{"message":"Uploaded","data":{"slot":"$slot","column":"col","path":"p/$slot","size":4,"mime_type":"image/jpeg","replaced":false}}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }) {
            install(ContentNegotiation) { json(LaravelClientProvider.json) }
            defaultRequest { url(LaravelEndpoints.BASE_URL) }
        }

        val repository = LaravelApplicationRepository(
            LaravelApplicationApiService(mockClient),
            LaravelDocumentRepository(LaravelDocumentApiService(mockClient), Dispatchers.Unconfined),
            remoteOnly = true
        )

        // 1. KK Create
        val kkReq = LaravelKkApplicationRequest(
            category = "KK Tunggal", nikPelapor = "111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen"
        )
        val resCreateKk = repository.createKkApplicationWithDocuments(
            context = null, request = kkReq,
            documents = mapOf("doc1" to SelectedDocument(uri = testUri, displayName = "kk.jpg", mimeType = "image/jpeg")),
            token = "synthetic-token", inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )
        assertTrue(resCreateKk.getOrThrow().isFullySuccessful)
        assertEquals("KK", resCreateKk.getOrThrow().applicationDto?.serviceType)

        // 2. AKTA Update
        val aktaReq = LaravelAktaApplicationRequest(
            category = "Kelahiran Terlambat Tunggal", nikPelapor = "111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelAktaDetailRequest("1", "Bayi", "2", "Ibu", "3")
        )
        val resUpdateAkta = repository.updateAktaApplicationWithDocuments(
            context = null, applicationUuid = aktaUuid, request = aktaReq,
            replacementDocuments = emptyMap(), deletedSlots = emptyList(), token = "synthetic-token"
        )
        assertTrue(resUpdateAkta.getOrThrow().isFullySuccessful)
        assertEquals("AKTA", resUpdateAkta.getOrThrow().applicationDto?.serviceType)
        assertEquals(5, requests.size)

        mockClient.close()
    }

    @Test
    fun testPreValidateCreateDocuments_nullUri_rejectedBeforeHttpRequest(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val mockClient = createMockClient { request ->
            requests.add(request)
            Pair(HttpStatusCode.Created, """{"message":"OK"}""")
        }
        val repository = LaravelApplicationRepository(
            LaravelApplicationApiService(mockClient),
            LaravelDocumentRepository(LaravelDocumentApiService(mockClient), Dispatchers.Unconfined),
            remoteOnly = true
        )

        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak Test")
        )

        // Document selected with uri = null
        val docMap = mapOf("doc1" to SelectedDocument(uri = null, displayName = "doc1.jpg", mimeType = "image/jpeg"))

        val result = repository.createKiaApplicationWithDocuments(
            context = null, request = kiaReq, documents = docMap, token = "synthetic-token"
        )

        assertTrue(result.isFailure)
        assertEquals("URI dokumen tidak valid (null)", result.exceptionOrNull()?.message)
        assertEquals("MockEngine harus menerima NOL request HTTP ketika pre-validasi dokumen gagal", 0, requests.size)

        mockClient.close()
    }

    @Test
    fun testCreateApplicationFailure_preventsDocumentUpload(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val mockClient = HttpClient(MockEngine { requestData ->
            requests.add(requestData)
            respond(
                content = """{"message":"Validasi data gagal."}""",
                status = HttpStatusCode.UnprocessableEntity,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }) {
            install(ContentNegotiation) { json(LaravelClientProvider.json) }
            defaultRequest { url(LaravelEndpoints.BASE_URL) }
        }

        val repository = LaravelApplicationRepository(
            LaravelApplicationApiService(mockClient),
            LaravelDocumentRepository(LaravelDocumentApiService(mockClient), Dispatchers.Unconfined),
            remoteOnly = true
        )

        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("123", "Anak")
        )

        val docMap = mapOf("doc1" to SelectedDocument(uri = testUri, displayName = "doc1.jpg", mimeType = "image/jpeg"))

        val result = repository.createKiaApplicationWithDocuments(
            context = null, request = kiaReq, documents = docMap, token = "synthetic-token",
            inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )

        assertTrue(result.isFailure)
        assertEquals(1, requests.size) // Only 1 POST /api/applications was made, NO document uploads triggered!

        mockClient.close()
    }

    @Test
    fun testPartialUploadFailure_retainsApplicationUuidAndReportsFailedSlots(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val mockClient = HttpClient(MockEngine { requestData ->
            requests.add(requestData)
            val path = requestData.url.encodedPath
            when {
                path == "/api/applications" -> respond("""{"message":"OK","data":${buildMockAppJson(validAppUuid, "KIA")}}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                path == "/api/applications/$validAppUuid" -> respond("""{"message":"OK","data":${buildMockAppJson(validAppUuid, "KIA")}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                path.endsWith("/doc1") -> respond("""{"message":"OK","data":{"slot":"doc1","column":"col","path":"p1","size":4,"mime_type":"image/jpeg","replaced":false}}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                path.endsWith("/doc2") -> respond("""{"message":"Upload Error"}""", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }) {
            install(ContentNegotiation) { json(LaravelClientProvider.json) }
            defaultRequest { url(LaravelEndpoints.BASE_URL) }
        }

        val repository = LaravelApplicationRepository(
            LaravelApplicationApiService(mockClient),
            LaravelDocumentRepository(LaravelDocumentApiService(mockClient), Dispatchers.Unconfined),
            remoteOnly = true
        )

        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak Test")
        )

        val docMap = mapOf(
            "doc1" to SelectedDocument(uri = testUri, displayName = "doc1.jpg", mimeType = "image/jpeg"),
            "doc2" to SelectedDocument(uri = testUri, displayName = "doc2.jpg", mimeType = "image/jpeg")
        )

        val result = repository.createKiaApplicationWithDocuments(
            context = null, request = kiaReq, documents = docMap, token = "synthetic-token",
            inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )

        assertTrue(result.isSuccess)
        val createResult = result.getOrNull()
        assertNotNull(createResult)
        assertFalse(createResult!!.isFullySuccessful)
        assertEquals(validAppUuid, createResult.applicationUuid)
        assertEquals("KIA-777888", createResult.submissionCode)
        assertEquals(1, createResult.uploadedSlots.size)
        assertTrue(createResult.uploadedSlots.containsKey("doc1"))
        assertEquals(1, createResult.failedSlots.size)
        assertTrue(createResult.failedSlots.containsKey("doc2"))
        assertTrue(createResult.uncertainSlots.contains("doc2"))
        assertEquals(LaravelRemoteOutcome.CONFIRMED, createResult.remoteOutcome)

        mockClient.close()
    }

    @Test
    fun testUpdateApplication_slotConflict_rejectedBeforeHttpRequest(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val mockClient = createMockClient { request ->
            requests.add(request)
            Pair(HttpStatusCode.OK, """{"message":"OK"}""")
        }
        val repository = LaravelApplicationRepository(
            LaravelApplicationApiService(mockClient),
            LaravelDocumentRepository(LaravelDocumentApiService(mockClient), Dispatchers.Unconfined),
            remoteOnly = true
        )

        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak Test")
        )

        // Slot "doc1" requested for BOTH replacement AND deletion!
        val replacements = mapOf("doc1" to SelectedDocument(uri = testUri, displayName = "doc1.jpg", mimeType = "image/jpeg"))
        val deletions = listOf("doc1")

        val result = repository.updateKiaApplicationWithDocuments(
            context = null, applicationUuid = validAppUuid, request = kiaReq,
            replacementDocuments = replacements, deletedSlots = deletions, token = "synthetic-token"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("tidak boleh diminta untuk diganti (replace) dan dihapus sekaligus") == true)
        assertEquals("MockEngine harus menerima NOL request HTTP saat terdapat konflik slot", 0, requests.size)

        mockClient.close()
    }

    @Test
    fun testUpdateApplication_skipsUploadWhenNoReplacementDocumentsProvided(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val mockClient = HttpClient(MockEngine { requestData ->
            requests.add(requestData)
            val path = requestData.url.encodedPath
            when {
                path == "/api/applications/$validAppUuid" && requestData.method == HttpMethod.Put -> respond("""{"message":"Pengajuan berhasil diperbarui.","data":${buildMockAppJson(validAppUuid, "KIA")}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                path == "/api/applications/$validAppUuid" && requestData.method == HttpMethod.Get -> respond("""{"message":"OK","data":${buildMockAppJson(validAppUuid, "KIA")}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }) {
            install(ContentNegotiation) { json(LaravelClientProvider.json) }
            defaultRequest { url(LaravelEndpoints.BASE_URL) }
        }

        val repository = LaravelApplicationRepository(
            LaravelApplicationApiService(mockClient),
            LaravelDocumentRepository(LaravelDocumentApiService(mockClient), Dispatchers.Unconfined),
            remoteOnly = true
        )

        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak Test")
        )

        val result = repository.updateKiaApplicationWithDocuments(
            context = null, applicationUuid = validAppUuid, request = kiaReq,
            replacementDocuments = emptyMap(), deletedSlots = emptyList(), token = "synthetic-token"
        )

        assertTrue(result.getOrThrow().isFullySuccessful)
        // 1 PUT form update + 1 GET authoritative detail refresh = 2 requests
        assertEquals(2, requests.size)
        assertEquals(HttpMethod.Put, requests[0].method)

        mockClient.close()
    }

    @Test
    fun testUpdateApplication_explicitDeleteAndReplacementUploads(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val mockClient = HttpClient(MockEngine { requestData ->
            requests.add(requestData)
            val path = requestData.url.encodedPath
            when {
                path == "/api/applications/$validAppUuid/documents/doc1" && requestData.method == HttpMethod.Delete -> respond("""{"message":"Deleted"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                path == "/api/applications/$validAppUuid/documents/doc2" && requestData.method == HttpMethod.Post -> respond("""{"message":"Replaced","data":{"slot":"doc2","column":"c2","path":"p2","size":4,"mime_type":"image/jpeg","replaced":true}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                path == "/api/applications/$validAppUuid" && requestData.method == HttpMethod.Put -> respond("""{"message":"Updated","data":${buildMockAppJson(validAppUuid, "KIA")}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                path == "/api/applications/$validAppUuid" && requestData.method == HttpMethod.Get -> respond("""{"message":"OK","data":${buildMockAppJson(validAppUuid, "KIA")}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }) {
            install(ContentNegotiation) { json(LaravelClientProvider.json) }
            defaultRequest { url(LaravelEndpoints.BASE_URL) }
        }

        val repository = LaravelApplicationRepository(
            LaravelApplicationApiService(mockClient),
            LaravelDocumentRepository(LaravelDocumentApiService(mockClient), Dispatchers.Unconfined),
            remoteOnly = true
        )

        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak Test")
        )

        val replacements = mapOf("doc2" to SelectedDocument(uri = testUri, displayName = "doc2.jpg", mimeType = "image/jpeg"))
        val deletions = listOf("doc1")

        val result = repository.updateKiaApplicationWithDocuments(
            context = null, applicationUuid = validAppUuid, request = kiaReq,
            replacementDocuments = replacements, deletedSlots = deletions, token = "synthetic-token",
            inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )

        assertTrue(result.isSuccess)
        val updateResult = result.getOrNull()
        assertNotNull(updateResult)
        assertTrue(updateResult!!.isFullySuccessful)
        assertTrue(updateResult.isFormUpdated)

        // 1 PUT form update + 1 DELETE doc1 + 1 POST doc2 replacement + 1 GET detail refresh = 4 requests
        assertEquals(4, requests.size)
        assertEquals(HttpMethod.Put, requests[0].method)    // 1. Form PUT update first
        assertEquals(HttpMethod.Delete, requests[1].method) // 2. Explicit DELETE doc1
        assertEquals(HttpMethod.Post, requests[2].method)   // 3. Replacement POST doc2

        mockClient.close()
    }

    private fun sampleKiaRequest() = LaravelKiaApplicationRequest(
        category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Test",
        email = "t@t.com", whatsapp = "081", kecamatan = "Klojen", kelurahan = "Klojen",
        detail = LaravelKiaDetailRequest("3573010000000099", "Anak Test")
    )

    @Test
    fun testProductionWithoutVerifiedDependencies_rejectsBeforeHttp(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = createMockClient { request ->
            requests.add(request)
            Pair(HttpStatusCode.OK, """{"message":"OK"}""")
        }
        try {
            val repository = LaravelApplicationRepository(
                LaravelApplicationApiService(client),
                LaravelDocumentRepository(LaravelDocumentApiService(client), Dispatchers.Unconfined)
            )
            val result = repository.createKiaApplicationWithDocuments(
                context = null, request = sampleKiaRequest(), localUserId = 10,
                remoteUserUuid = validUserUuid, token = "synthetic-token"
            )
            assertTrue(result.isFailure)
            assertEquals(0, requests.size)
        } finally { client.close() }
    }

    @Test
    fun testConfirmedCreate_refreshFailure_doesNotReturnStaleDtoOrRetry(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = createMockClient { request ->
            requests.add(request)
            if (request.method == HttpMethod.Post) {
                Pair(HttpStatusCode.Created, """{"message":"OK","data":${buildMockAppJson(validAppUuid, "KIA")}}""")
            } else Pair(HttpStatusCode.InternalServerError, """{"message":"Refresh failed"}""")
        }
        try {
            val repository = LaravelApplicationRepository(
                LaravelApplicationApiService(client),
                LaravelDocumentRepository(LaravelDocumentApiService(client), Dispatchers.Unconfined),
                remoteOnly = true
            )
            val result = repository.createKiaApplicationWithDocuments(
                context = null, request = sampleKiaRequest(), token = "synthetic-token"
            ).getOrThrow()
            assertEquals(LaravelRemoteOutcome.CONFIRMED, result.remoteOutcome)
            assertEquals(validAppUuid, result.applicationUuid)
            assertEquals("KIA-777888", result.submissionCode)
            assertNull(result.applicationDto)
            assertFalse(result.isFullySuccessful)
            assertEquals(LaravelCacheSyncStatus.REFRESH_REQUIRED, result.cacheSyncStatus)
            assertEquals(1, requests.count { it.method == HttpMethod.Post })
            assertEquals(2, requests.size)
        } finally { client.close() }
    }

    @Test
    fun testDetailMismatchedUuid_rejected(): Unit = runBlocking {
        val client = createMockClient {
            Pair(HttpStatusCode.OK, """{"message":"OK","data":${buildMockAppJson("9b1deb4d-3b7d-419b-a683-123456789abd", "KIA")}}""")
        }
        try {
            val repository = LaravelApplicationRepository(
                LaravelApplicationApiService(client),
                LaravelDocumentRepository(LaravelDocumentApiService(client), Dispatchers.Unconfined),
                remoteOnly = true
            )
            assertTrue(repository.getApplicationDetail(validAppUuid, token = "synthetic-token").isFailure)
        } finally { client.close() }
    }

    @Test
    fun testCreateUncertainServerFailure_noRetry(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = createMockClient { request ->
            requests.add(request)
            Pair(HttpStatusCode.InternalServerError, """{"message":"Server error"}""")
        }
        try {
            val repository = LaravelApplicationRepository(
                LaravelApplicationApiService(client),
                LaravelDocumentRepository(LaravelDocumentApiService(client), Dispatchers.Unconfined),
                remoteOnly = true
            )
            val result = repository.createKiaApplicationWithDocuments(
                context = null, request = sampleKiaRequest(), token = "synthetic-token"
            )
            assertTrue(result.exceptionOrNull() is LaravelMutationUncertainException)
            assertEquals(1, requests.size)
        } finally { client.close() }
    }
}
