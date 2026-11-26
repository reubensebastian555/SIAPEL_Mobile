package com.example.siapel

import com.example.siapel.data.remote.laravel.LaravelApiException
import com.example.siapel.data.remote.laravel.LaravelClientProvider
import com.example.siapel.data.remote.laravel.LaravelDocumentApiService
import com.example.siapel.data.remote.laravel.LaravelEndpoints
import com.example.siapel.data.remote.laravel.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/**
 * Targeted Unit Test terisolasi untuk [LaravelDocumentApiService] menggunakan Ktor MockEngine 3.0.3.
 */
class LaravelDocumentApiServiceTest {

    @After
    fun tearDown() {
        LaravelClientProvider.tokenProvider = null
    }

    private fun createMockClient(
        responder: (request: HttpRequestData) -> Pair<HttpStatusCode, String>
    ): Pair<HttpClient, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { requestData ->
            requests.add(requestData)
            val (status, content) = responder(requestData)
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
            defaultRequest {
                url(LaravelEndpoints.BASE_URL)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }

        return Pair(client, requests)
    }

    private fun createBinaryMockClient(
        responder: (request: HttpRequestData) -> Triple<HttpStatusCode, ByteArray, Map<String, String>>
    ): Pair<HttpClient, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { requestData ->
            requests.add(requestData)
            val (status, bytes, headersMap) = responder(requestData)
            val headersBuilder = headersOf(
                *headersMap.map { (key, value) -> key to listOf(value) }.toTypedArray()
            )
            respond(
                content = bytes,
                status = status,
                headers = headersBuilder
            )
        }

        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(LaravelClientProvider.json)
            }
            defaultRequest {
                url(LaravelEndpoints.BASE_URL)
            }
        }

        return Pair(client, requests)
    }

    private fun extractRequestBodyText(requestData: HttpRequestData): String = runBlocking {
        when (val body = requestData.body) {
            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
            is OutgoingContent.WriteChannelContent -> {
                val channel = ByteChannel(true)
                body.writeTo(channel)
                val bytes = channel.readRemaining().readByteArray()
                bytes.decodeToString()
            }
            else -> body.toString()
        }
    }

    private val validUuid = "9b1deb4d-3b7d-419b-a683-123456789abc"
    private val sampleBytes = byteArrayOf(0x4A, 0x50, 0x45, 0x47) // Dummy 4 bytes

    @Test
    fun testUploadDocument_successHttp201_newUpload(): Unit = runBlocking {
        val jsonResponseBody = """
            {
                "message": "Dokumen berhasil diunggah.",
                "data": {
                    "slot": "doc1",
                    "column": "doc_1_path",
                    "path": "user-uuid/app-uuid/doc1_uuid.jpg",
                    "size": 1024,
                    "mime_type": "image/jpeg",
                    "replaced": false
                }
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, jsonResponseBody)
        }

        val service = LaravelDocumentApiService(client)

        val response = service.uploadOrReplaceDocument(
            applicationUuid = validUuid,
            slot = "doc1",
            fileBytes = sampleBytes,
            filename = "ktp_pelapor.jpg",
            declaredMimeType = "image/jpeg",
            token = "synthetic-upload-token"
        )

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertEquals(HttpMethod.Post, requestData.method)
        assertEquals("/api/applications/$validUuid/documents/doc1", requestData.url.encodedPath)
        assertEquals("Bearer synthetic-upload-token", requestData.headers[HttpHeaders.Authorization])
        assertTrue(requestData.body.contentType?.toString()?.startsWith("multipart/form-data") == true)

        assertEquals("Dokumen berhasil diunggah.", response.message)
        assertNotNull(response.data)
        assertEquals("doc1", response.data?.slot)
        assertEquals("doc_1_path", response.data?.column)
        assertEquals(false, response.data?.replaced)

        client.close()
    }

    @Test
    fun testReplaceDocument_successHttp200_fileReplaced(): Unit = runBlocking {
        val jsonResponseBody = """
            {
                "message": "Dokumen berhasil diperbarui.",
                "data": {
                    "slot": "docSelfie",
                    "column": "doc_selfie_path",
                    "path": "user-uuid/app-uuid/docSelfie_newuuid.png",
                    "size": 2048,
                    "mime_type": "image/png",
                    "replaced": true
                }
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, jsonResponseBody)
        }

        val service = LaravelDocumentApiService(client)

        val response = service.uploadOrReplaceDocument(
            applicationUuid = validUuid,
            slot = "docSelfie",
            fileBytes = sampleBytes,
            filename = "selfie.png",
            declaredMimeType = "image/png",
            token = "synthetic-replace-token"
        )

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertEquals(HttpMethod.Post, requestData.method)
        assertEquals("/api/applications/$validUuid/documents/docSelfie", requestData.url.encodedPath)
        assertEquals("Dokumen berhasil diperbarui.", response.message)
        assertEquals(true, response.data?.replaced)

        client.close()
    }

    @Test
    fun testUploadDocument_strictSuccessResponseValidation_dataNull_throwsLaravelApiException(): Unit = runBlocking {
        // Scenario 1: HTTP 201 dengan "data": null
        val (client1, _) = createMockClient {
            Pair(HttpStatusCode.Created, """{"message":"Dokumen diunggah","data":null}""")
        }
        val service1 = LaravelDocumentApiService(client1)
        try {
            service1.uploadOrReplaceDocument(validUuid, "doc1", sampleBytes, "a.jpg", "image/jpeg")
            fail("Upload dengan respons 'data: null' seharusnya melempar LaravelApiException")
        } catch (e: LaravelApiException) {
            assertTrue(e.message.contains("data"))
        }
        client1.close()

        // Scenario 2: HTTP 201 tanpa field "data"
        val (client2, _) = createMockClient {
            Pair(HttpStatusCode.Created, """{"message":"Dokumen diunggah"}""")
        }
        val service2 = LaravelDocumentApiService(client2)
        try {
            service2.uploadOrReplaceDocument(validUuid, "doc1", sampleBytes, "a.jpg", "image/jpeg")
            fail("Upload tanpa field 'data' seharusnya melempar LaravelApiException")
        } catch (e: LaravelApiException) {
            assertTrue(e.message.contains("data"))
        }
        client2.close()

        // Scenario 3: HTTP 201 dengan malformed JSON
        val (client3, _) = createMockClient {
            Pair(HttpStatusCode.Created, """{invalid-json-body""")
        }
        val service3 = LaravelDocumentApiService(client3)
        try {
            service3.uploadOrReplaceDocument(validUuid, "doc1", sampleBytes, "a.jpg", "image/jpeg")
            fail("Upload dengan malformed JSON seharusnya melempar Exception")
        } catch (e: Exception) {
            assertFalse("Harus melempar exception parsing, bukan dianggap sukses", e is NullPointerException && e.message == null)
        }
        client3.close()
    }

    @Test
    fun testUploadDocument_multipartContractVerification(): Unit = runBlocking {
        val jsonResponseBody = """
            {
                "message": "Dokumen berhasil diunggah.",
                "data": {
                    "slot": "doc1",
                    "column": "doc_1_path",
                    "path": "u/a/doc1_uuid.jpg",
                    "size": 4,
                    "mime_type": "image/jpeg",
                    "replaced": false
                }
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, jsonResponseBody)
        }

        val service = LaravelDocumentApiService(client)
        val testBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        service.uploadOrReplaceDocument(
            applicationUuid = validUuid,
            slot = "doc1",
            fileBytes = testBytes,
            filename = "ktp_quotes\"test\".jpg",
            declaredMimeType = "image/jpeg"
        )

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertTrue("Request body harus berupa MultiPartFormDataContent", requestData.body is MultiPartFormDataContent)
        val bodyText = extractRequestBodyText(requestData)
        assertTrue("Multipart body harus memuat nama field 'file'", bodyText.contains("name=\"file\"") || bodyText.contains("name=file"))
        assertTrue("Multipart body harus memuat Content-Type 'image/jpeg'", bodyText.contains("image/jpeg"))

        client.close()
    }

    @Test
    fun testDownloadDocument_successHttp200_binaryStream(): Unit = runBlocking {
        val mockBinaryBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val headers = mapOf(
            HttpHeaders.ContentType to "image/jpeg",
            HttpHeaders.ContentDisposition to "attachment; filename=\"doc1.jpg\""
        )

        val (client, requests) = createBinaryMockClient {
            Triple(HttpStatusCode.OK, mockBinaryBytes, headers)
        }

        val service = LaravelDocumentApiService(client)

        val result = service.downloadDocument(
            applicationUuid = validUuid,
            slot = "doc1",
            token = "synthetic-download-token"
        )

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertEquals(HttpMethod.Get, requestData.method)
        assertEquals("/api/applications/$validUuid/documents/doc1", requestData.url.encodedPath)
        assertEquals("Bearer synthetic-download-token", requestData.headers[HttpHeaders.Authorization])

        assertArrayEquals(mockBinaryBytes, result.bytes)
        assertEquals("image/jpeg", result.contentType)
        assertEquals("attachment; filename=\"doc1.jpg\"", result.contentDisposition)

        client.close()
    }

    @Test
    fun testDownloadDocument_errorResponse_throwsLaravelApiExceptionNotReturnedAsBinarySuccess(): Unit = runBlocking {
        val errorJsonResponseBody = """{"message":"Dokumen tidak ditemukan."}"""
        val (client, _) = createMockClient {
            Pair(HttpStatusCode.NotFound, errorJsonResponseBody)
        }

        val service = LaravelDocumentApiService(client)

        try {
            service.downloadDocument(validUuid, "doc1")
            fail("Download dokumen yang mengembalikan HTTP 404 seharusnya melempar LaravelApiException")
        } catch (e: LaravelApiException) {
            assertEquals(404, e.statusCode)
            assertTrue(e.message.contains("Dokumen tidak ditemukan") || e.message.contains("404"))
        }

        client.close()
    }

    @Test
    fun testDeleteDocument_successHttp200(): Unit = runBlocking {
        val jsonResponseBody = """{"message":"Dokumen berhasil dihapus."}"""

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, jsonResponseBody)
        }

        val service = LaravelDocumentApiService(client)

        val response = service.deleteDocument(
            applicationUuid = validUuid,
            slot = "doc2",
            token = "synthetic-delete-token"
        )

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertEquals(HttpMethod.Delete, requestData.method)
        assertEquals("/api/applications/$validUuid/documents/doc2", requestData.url.encodedPath)
        assertEquals("Bearer synthetic-delete-token", requestData.headers[HttpHeaders.Authorization])
        assertEquals("Dokumen berhasil dihapus.", response.message)

        client.close()
    }

    @Test
    fun testClientSideValidation_invalidInputs_rejectedBeforeHttpRequest(): Unit = runBlocking {
        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, """{"data": null}""")
        }
        val service = LaravelDocumentApiService(client)

        // 1. File kosong (0 bytes)
        try {
            service.uploadOrReplaceDocument(validUuid, "doc1", byteArrayOf(), "test.jpg", "image/jpeg")
            fail("File 0 byte seharusnya melempar IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Dokumen tidak boleh kosong") == true)
        }

        // 2. Oversized file (> 2 MiB)
        val oversizedBytes = ByteArray(2_097_153) // 2 MiB + 1 byte
        try {
            service.uploadOrReplaceDocument(validUuid, "doc1", oversizedBytes, "test.jpg", "image/jpeg")
            fail("File > 2 MiB seharusnya melempar IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("melebihi batas maksimum 2 MiB") == true)
        }

        // 3. Filename tanpa ekstensi
        try {
            service.uploadOrReplaceDocument(validUuid, "doc1", sampleBytes, "testfile", "image/jpeg")
            fail("Filename tanpa ekstensi seharusnya melempar IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("harus memiliki ekstensi berkas yang valid") == true)
        }

        // 4. Ekstensi tidak didukung (.xyz)
        try {
            service.uploadOrReplaceDocument(validUuid, "doc1", sampleBytes, "test.xyz", "application/octet-stream")
            fail("Ekstensi .xyz seharusnya melempar IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("tidak didukung") == true)
        }

        // 5. MIME type & ekstensi mismatch (.pdf dengan image/png)
        try {
            service.uploadOrReplaceDocument(validUuid, "doc1", sampleBytes, "test.pdf", "image/png")
            fail("MIME & extension mismatch seharusnya melempar IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("tidak sesuai dengan ekstensi berkas") == true)
        }

        // 6. UUID Invalid
        try {
            service.uploadOrReplaceDocument("invalid-uuid", "doc1", sampleBytes, "test.jpg", "image/jpeg")
            fail("UUID invalid seharusnya melempar IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Format UUID pengajuan tidak valid") == true)
        }

        // 7. Slot Invalid
        try {
            service.uploadOrReplaceDocument(validUuid, "doc99", sampleBytes, "test.jpg", "image/jpeg")
            fail("Slot invalid seharusnya melempar IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Slot dokumen 'doc99' tidak valid") == true)
        }

        assertEquals("MockEngine harus menerima NOL request HTTP untuk validasi client-side yang gagal", 0, requests.size)
        client.close()
    }

    @Test
    fun testClientSideValidation_exactMaxLimit_2MiB_accepted(): Unit = runBlocking {
        val jsonResponseBody = """
            {
                "message": "Dokumen berhasil diunggah.",
                "data": {
                    "slot": "doc1",
                    "column": "doc_1_path",
                    "path": "user-uuid/app-uuid/doc1_uuid.pdf",
                    "size": 2097152,
                    "mime_type": "application/pdf",
                    "replaced": false
                }
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, jsonResponseBody)
        }
        val service = LaravelDocumentApiService(client)

        val exactMaxBytes = ByteArray(2_097_152) // Tepat 2 MiB

        val response = service.uploadOrReplaceDocument(
            applicationUuid = validUuid,
            slot = "doc1",
            fileBytes = exactMaxBytes,
            filename = "document_exact_2mb.pdf",
            declaredMimeType = "application/pdf"
        )

        assertEquals("Request dengan file tepat 2 MiB harus diterima dan dikirim ke server", 1, requests.size)
        assertEquals("Dokumen berhasil diunggah.", response.message)
        assertNotNull(response.data)

        client.close()
    }

    @Test
    fun testNetworkAndTimeoutFailures_throwsExpectedException(): Unit = runBlocking {
        // 1. Network Failure (IOException)
        val engineNetworkError = MockEngine { throw IOException("Koneksi terputus") }
        val clientNetwork = HttpClient(engineNetworkError)
        val serviceNetwork = LaravelDocumentApiService(clientNetwork)

        try {
            serviceNetwork.downloadDocument(validUuid, "doc1")
            fail("Network failure seharusnya melempar IOException")
        } catch (e: IOException) {
            assertEquals("Koneksi terputus", e.message)
        }
        clientNetwork.close()

        // 2. Cancellation Exception Propagation
        val engineCancelled = MockEngine { throw CancellationException("Job dibatalkan") }
        val clientCancelled = HttpClient(engineCancelled)
        val serviceCancelled = LaravelDocumentApiService(clientCancelled)

        try {
            serviceCancelled.downloadDocument(validUuid, "doc1")
            fail("CancellationException seharusnya dilempar kembali")
        } catch (e: CancellationException) {
            assertEquals("Job dibatalkan", e.message)
        }
        clientCancelled.close()
    }

    @Test
    fun testHttp413PayloadTooLargeNonJson_producesLaravelApiException(): Unit = runBlocking {
        val nonJsonHtml413 = "<html><body>413 Request Entity Too Large</body></html>"
        val (client, _) = createMockClient {
            Pair(HttpStatusCode.PayloadTooLarge, nonJsonHtml413)
        }

        val service = LaravelDocumentApiService(client)

        try {
            service.uploadOrReplaceDocument(validUuid, "doc1", sampleBytes, "test.jpg", "image/jpeg")
            fail("HTTP 413 non-JSON response seharusnya melempar LaravelApiException")
        } catch (e: LaravelApiException) {
            assertEquals(413, e.statusCode)
            assertNotNull(e.message)
        }

        client.close()
    }

    @Test
    fun testAuthorizationHeader_explicitTokenVsFallback_singleHeader(): Unit = runBlocking {
        // Set global tokenProvider
        LaravelClientProvider.tokenProvider = object : TokenProvider {
            override fun getToken(): String = "global-fallback-token"
        }

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, """{"message":"Dokumen berhasil dihapus."}""")
        }

        val service = LaravelDocumentApiService(client)

        // Request with explicit token
        service.deleteDocument(validUuid, "doc1", token = "explicit-param-token")

        assertEquals(1, requests.size)
        val authHeaders = requests[0].headers.getAll(HttpHeaders.Authorization)
        assertNotNull(authHeaders)
        assertEquals("Authorization header count harus tepat 1", 1, authHeaders?.size)
        assertEquals("Bearer explicit-param-token", authHeaders?.get(0))

        client.close()
    }

    @Test
    fun testErrorHandlingStatusCodes_throwsLaravelApiException(): Unit = runBlocking {
        val errorScenarios = listOf(
            Pair(HttpStatusCode.Unauthorized, """{"message":"Unauthenticated."}"""),
            Pair(HttpStatusCode.NotFound, """{"message":"Dokumen tidak ditemukan."}"""),
            Pair(HttpStatusCode.Conflict, """{"message":"Dokumen tidak dapat diubah pada status pengajuan ini."}"""),
            Pair(HttpStatusCode.PayloadTooLarge, """{"message":"Request Entity Too Large"}"""),
            Pair(HttpStatusCode.UnprocessableEntity, """{"message":"Validasi file gagal.","errors":{"file":["Isi file tidak sesuai dengan ekstensi dokumen."]}}"""),
            Pair(HttpStatusCode.InternalServerError, """{"message":"Server Error"}""")
        )

        for ((status, body) in errorScenarios) {
            val (client, _) = createMockClient {
                Pair(status, body)
            }
            val service = LaravelDocumentApiService(client)

            try {
                service.uploadOrReplaceDocument(
                    applicationUuid = validUuid,
                    slot = "doc1",
                    fileBytes = sampleBytes,
                    filename = "test.jpg",
                    declaredMimeType = "image/jpeg"
                )
                fail("Request ke status ${status.value} seharusnya melempar LaravelApiException")
            } catch (e: LaravelApiException) {
                assertEquals(status.value, e.statusCode)
                assertNotNull(e.message)
                if (status == HttpStatusCode.UnprocessableEntity) {
                    assertNotNull(e.errors)
                    assertTrue(e.errors?.containsKey("file") == true)
                }
            }
            client.close()
        }
    }
}
