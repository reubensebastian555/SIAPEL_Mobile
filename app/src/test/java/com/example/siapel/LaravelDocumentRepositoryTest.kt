package com.example.siapel

import android.net.TestUri
import android.net.Uri
import com.example.siapel.data.remote.laravel.LaravelApiException
import com.example.siapel.data.remote.laravel.LaravelClientProvider
import com.example.siapel.data.remote.laravel.LaravelDocumentApiService
import com.example.siapel.data.remote.laravel.LaravelEndpoints
import com.example.siapel.data.repository.LaravelDocumentRepository
import com.example.siapel.model.SelectedDocument
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Unit Test terisolasi untuk [LaravelDocumentRepository] menggunakan Ktor MockEngine 3.0.3.
 */
class LaravelDocumentRepositoryTest {

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

    private val validUuid = "9b1deb4d-3b7d-419b-a683-123456789abc"
    private val sampleBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    private val testUri: Uri = TestUri()

    @Test
    fun testReadDocumentBytes_success() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)

        val result = repository.readDocumentBytes(
            contentResolver = null,
            uri = testUri,
            inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )

        assertTrue(result.isSuccess)
        assertArrayEquals(sampleBytes, result.getOrNull())
    }

    @Test
    fun testReadDocumentBytes_emptyFile_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)

        val result = repository.readDocumentBytes(
            contentResolver = null,
            uri = testUri,
            inputStreamSupplier = { ByteArrayInputStream(byteArrayOf()) }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("kosong") == true)
    }

    @Test
    fun testReadDocumentBytes_oversizedFile_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        val oversizedBytes = ByteArray(LaravelDocumentRepository.MAX_FILE_SIZE_BYTES.toInt() + 10)

        val result = repository.readDocumentBytes(
            contentResolver = null,
            uri = testUri,
            inputStreamSupplier = { ByteArrayInputStream(oversizedBytes) }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("melebihi batas maksimum 2 MiB") == true)
    }

    @Test
    fun testReadDocumentBytes_unreadableInputStream_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)

        val result = repository.readDocumentBytes(
            contentResolver = null,
            uri = testUri,
            inputStreamSupplier = { throw IOException("Stream unreadable") }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Gagal membaca isi berkas dokumen") == true)
    }

    @Test
    fun testResolveExtensionAndMime_mimeExtensionMismatch_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        val selectedDocument = SelectedDocument(uri = testUri, displayName = "document.pdf", mimeType = "image/png")

        val result = repository.uploadOrReplaceDocument(
            context = null,
            applicationUuid = validUuid,
            slot = "doc1",
            selectedDocument = selectedDocument,
            token = "synthetic-token",
            inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("MIME type 'image/png' tidak sesuai dengan ekstensi berkas 'pdf'") == true)
    }

    @Test
    fun testResolveExtensionAndMime_unsupportedExtensionWithValidDeclaredMime_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        val selectedDocument = SelectedDocument(uri = testUri, displayName = "document.exe", mimeType = "image/jpeg")

        val result = repository.uploadOrReplaceDocument(
            context = null,
            applicationUuid = validUuid,
            slot = "doc1",
            selectedDocument = selectedDocument,
            token = "synthetic-token",
            inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Format berkas 'exe' tidak didukung") == true)
    }

    @Test
    fun testUploadOrReplaceDocument_success() = runBlocking {
        val jsonResponseBody = """
            {
                "message": "Dokumen berhasil diunggah.",
                "data": {
                    "slot": "doc1",
                    "column": "doc_1_path",
                    "path": "user-uuid/app-uuid/doc1_uuid.jpg",
                    "size": 4,
                    "mime_type": "image/jpeg",
                    "replaced": false
                }
            }
        """.trimIndent()

        val mockClient = createMockClient { Pair(HttpStatusCode.Created, jsonResponseBody) }
        val apiService = LaravelDocumentApiService(mockClient)
        val repository = LaravelDocumentRepository(apiService, Dispatchers.Unconfined)

        val selectedDocument = SelectedDocument(
            uri = testUri,
            displayName = "ktp.jpg",
            fileSize = 4L,
            mimeType = "image/jpeg"
        )

        val result = repository.uploadOrReplaceDocument(
            context = null,
            applicationUuid = validUuid,
            slot = "doc1",
            selectedDocument = selectedDocument,
            token = "synthetic-token-123",
            inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals("Dokumen berhasil diunggah.", response?.message)
        assertEquals("doc1", response?.data?.slot)

        mockClient.close()
    }

    @Test
    fun testUploadOrReplaceDocument_unauthenticated_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        val selectedDocument = SelectedDocument(uri = testUri, displayName = "ktp.jpg", mimeType = "image/jpeg")

        LaravelClientProvider.tokenProvider = null

        val result = repository.uploadOrReplaceDocument(
            context = null,
            applicationUuid = validUuid,
            slot = "doc1",
            selectedDocument = selectedDocument,
            token = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Sesi autentikasi Laravel tidak valid") == true)
    }

    @Test
    fun testUploadOrReplaceDocument_invalidApplicationUuid_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        val selectedDocument = SelectedDocument(uri = testUri, displayName = "ktp.jpg", mimeType = "image/jpeg")

        val result = repository.uploadOrReplaceDocument(
            context = null,
            applicationUuid = "invalid-uuid-format",
            slot = "doc1",
            selectedDocument = selectedDocument,
            token = "synthetic-token"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Format UUID pengajuan tidak valid") == true)
    }

    @Test
    fun testUploadOrReplaceDocument_invalidSlot_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        val selectedDocument = SelectedDocument(uri = testUri, displayName = "ktp.jpg", mimeType = "image/jpeg")

        val result = repository.uploadOrReplaceDocument(
            context = null,
            applicationUuid = validUuid,
            slot = "doc99",
            selectedDocument = selectedDocument,
            token = "synthetic-token"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Slot dokumen 'doc99' tidak valid") == true)
    }

    @Test
    fun testUploadOrReplaceDocument_selectedDocumentUriNull_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        val selectedDocument = SelectedDocument(uri = null, displayName = "ktp.jpg", mimeType = "image/jpeg")

        val result = repository.uploadOrReplaceDocument(
            context = null,
            applicationUuid = validUuid,
            slot = "doc1",
            selectedDocument = selectedDocument,
            token = "synthetic-token"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("URI null") == true)
    }

    @Test
    fun testUploadOrReplaceDocument_disallowedExtension_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        val selectedDocument = SelectedDocument(uri = testUri, displayName = "ktp.xyz", mimeType = "application/octet-stream")

        val result = repository.uploadOrReplaceDocument(
            context = null,
            applicationUuid = validUuid,
            slot = "doc1",
            selectedDocument = selectedDocument,
            token = "synthetic-token",
            inputStreamSupplier = { ByteArrayInputStream(sampleBytes) }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("tidak didukung") == true)
    }

    @Test
    fun testDownloadDocument_success() = runBlocking {
        val mockClient = HttpClient(MockEngine {
            respond(
                content = sampleBytes,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("image/jpeg"),
                    HttpHeaders.ContentDisposition to listOf("attachment; filename=\"doc1.jpg\"")
                )
            )
        }) {
            install(ContentNegotiation) { json(LaravelClientProvider.json) }
            defaultRequest { url(LaravelEndpoints.BASE_URL) }
        }

        val apiService = LaravelDocumentApiService(mockClient)
        val repository = LaravelDocumentRepository(apiService, Dispatchers.Unconfined)

        val result = repository.downloadDocument(
            applicationUuid = validUuid,
            slot = "doc1",
            token = "synthetic-token"
        )

        assertTrue(result.isSuccess)
        val downloadResult = result.getOrNull()
        assertNotNull(downloadResult)
        assertArrayEquals(sampleBytes, downloadResult?.bytes)
        assertEquals("image/jpeg", downloadResult?.contentType)

        mockClient.close()
    }

    @Test
    fun testDownloadDocument_unauthenticated_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        LaravelClientProvider.tokenProvider = null

        val result = repository.downloadDocument(
            applicationUuid = validUuid,
            slot = "doc1",
            token = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Sesi autentikasi Laravel tidak valid") == true)
    }

    @Test
    fun testDeleteDocument_success() = runBlocking {
        val jsonResponseBody = """{"message":"Dokumen berhasil dihapus."}"""
        val mockClient = createMockClient { Pair(HttpStatusCode.OK, jsonResponseBody) }
        val apiService = LaravelDocumentApiService(mockClient)
        val repository = LaravelDocumentRepository(apiService, Dispatchers.Unconfined)

        val result = repository.deleteDocument(
            applicationUuid = validUuid,
            slot = "doc2",
            token = "synthetic-token"
        )

        assertTrue(result.isSuccess)
        assertEquals("Dokumen berhasil dihapus.", result.getOrNull()?.message)

        mockClient.close()
    }

    @Test
    fun testDeleteDocument_unauthenticated_returnsFailure() = runBlocking {
        val repository = LaravelDocumentRepository(ioDispatcher = Dispatchers.Unconfined)
        LaravelClientProvider.tokenProvider = null

        val result = repository.deleteDocument(
            applicationUuid = validUuid,
            slot = "doc2",
            token = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Sesi autentikasi Laravel tidak valid") == true)
    }

    @Test
    fun testApiException_propagatedAsFailure() = runBlocking {
        val jsonResponseBody = """{"message":"Pengajuan tidak ditemukan."}"""
        val mockClient = createMockClient { Pair(HttpStatusCode.NotFound, jsonResponseBody) }
        val apiService = LaravelDocumentApiService(mockClient)
        val repository = LaravelDocumentRepository(apiService, Dispatchers.Unconfined)

        val result = repository.deleteDocument(
            applicationUuid = validUuid,
            slot = "doc1",
            token = "synthetic-token"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is LaravelApiException)
        assertEquals(404, (exception as LaravelApiException).statusCode)

        mockClient.close()
    }
}
