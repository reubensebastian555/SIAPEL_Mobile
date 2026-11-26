package com.example.siapel

import com.example.siapel.data.remote.laravel.LaravelApiException
import com.example.siapel.data.remote.laravel.LaravelApplicationApiService
import com.example.siapel.data.remote.laravel.LaravelClientProvider
import com.example.siapel.data.remote.laravel.LaravelEndpoints
import com.example.siapel.data.remote.laravel.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * Full Batch Unit Test terisolasi untuk [LaravelApplicationApiService] menggunakan Ktor MockEngine 3.0.3.
 * Menguji seluruh operasi CRUD 5 Layanan SIAPEL tanpa koneksi jaringan atau database nyata.
 */
class LaravelApplicationApiServiceTest {

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

    private fun extractRequestBodyText(requestData: HttpRequestData): String {
        return when (val body = requestData.body) {
            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
            else -> body.toString()
        }
    }

    private fun buildMockApplicationJson(
        id: String,
        serviceType: String,
        category: String,
        submissionCode: String,
        status: String = "SUBMITTED"
    ): String {
        return """
            {
                "id": "$id",
                "user_id": "9b1deb4d-3b7d-419b-a683-999999999999",
                "service_type": "$serviceType",
                "category": "$category",
                "submission_code": "$submissionCode",
                "submission_date": "18-11-2026",
                "status": "$status",
                "nik_pelapor": "1111111111111111",
                "nama_pelapor": "Pelapor Test",
                "email": "test@example.com",
                "whatsapp": "081234567890",
                "kecamatan": "Klojen",
                "kelurahan": "Klojen"
            }
        """.trimIndent()
    }

    // ==========================================
    // GROUP A: GET LIST & DETAIL TESTS
    // ==========================================

    @Test
    fun testGetApplications_noOptionalFilters_noQueryParamsSent() = runBlocking {
        val jsonResponseBody = """
            {
                "current_page": 1,
                "data": [],
                "total": 0
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)
        val response = service.getApplications()

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertEquals("/api/applications", requestData.url.encodedPath)
        assertNull(requestData.url.parameters["service_type"])
        assertNull(requestData.url.parameters["status"])
        assertNull(requestData.url.parameters["per_page"])
        assertNull(requestData.url.parameters["page"])

        assertEquals(1, response.currentPage)
        assertEquals(0, response.total)
        assertTrue(response.data.isEmpty())

        client.close()
    }

    @Test
    fun testUuidValidation_invalidInputs_rejectedLocallyWithoutHttpRequest() = runBlocking {
        val invalidUuids = listOf(
            "",
            "   ",
            " 9b1deb4d-3b7d-419b-a683-123456789abc ",
            "9b1deb4d-3b7d 419b-a683-123456789abc",
            "KTP-123456",
            "abc",
            "../applications/123",
            "9b1deb4d-3b7d-419b-a683-123456789abc/extra",
            "9b1deb4d3b7d419ba683123456789abc"
        )

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, """{"data": null}""")
        }
        val service = LaravelApplicationApiService(client)

        for (invalidUuid in invalidUuids) {
            try {
                service.getApplicationDetail(invalidUuid)
                fail("UUID tidak valid '$invalidUuid' seharusnya melempar IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("Format UUID pengajuan tidak valid") == true)
            }
        }

        assertEquals("MockEngine harus menerima NOL request HTTP untuk UUID invalid", 0, requests.size)
        client.close()
    }

    @Test
    fun testUuidValidation_validCanonicalUuids_sentToCorrectPath() = runBlocking {
        val validUuids = listOf(
            "9b1deb4d-3b7d-419b-a683-123456789abc",
            "9B1DEB4D-3B7D-419B-A683-123456789ABC",
            "9b1DEB4d-3b7D-419B-a683-123456789aBc"
        )

        val (client, requests) = createMockClient { requestData ->
            val uuid = requestData.url.segments.last()
            val jsonBody = """{"data": ${buildMockApplicationJson(uuid, "KIA", "KIA BARU", "KIA-123456")}}"""
            Pair(HttpStatusCode.OK, jsonBody)
        }

        val service = LaravelApplicationApiService(client)

        for ((index, validUuid) in validUuids.withIndex()) {
            val response = service.getApplicationDetail(validUuid)
            assertNotNull(response.data)
            assertEquals(validUuid, response.data?.id)

            val sentPath = requests[index].url.encodedPath
            assertEquals("/api/applications/$validUuid", sentPath)
        }

        assertEquals(validUuids.size, requests.size)
        client.close()
    }

    @Test
    fun testGetApplications_queryParametersAndPagination() = runBlocking {
        val jsonResponseBody = """
            {
                "current_page": 2,
                "data": [
                    ${buildMockApplicationJson("9b1deb4d-3b7d-419b-a683-123456789abc", "KIA", "KIA BARU", "KIA-999999")}
                ],
                "first_page_url": "http://10.0.2.2:8000/api/applications?page=1",
                "from": 11,
                "last_page": 3,
                "last_page_url": "http://10.0.2.2:8000/api/applications?page=3",
                "next_page_url": "http://10.0.2.2:8000/api/applications?page=3",
                "path": "http://10.0.2.2:8000/api/applications",
                "per_page": 10,
                "prev_page_url": "http://10.0.2.2:8000/api/applications?page=1",
                "to": 20,
                "total": 25
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)

        val response = service.getApplications(
            serviceType = "KIA",
            status = "SUBMITTED",
            perPage = 10,
            page = 2,
            token = "synthetic-bearer-token-123"
        )

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertEquals("/api/applications", requestData.url.encodedPath)
        assertEquals("KIA", requestData.url.parameters["service_type"])
        assertEquals("SUBMITTED", requestData.url.parameters["status"])
        assertEquals("10", requestData.url.parameters["per_page"])
        assertEquals("2", requestData.url.parameters["page"])
        assertEquals("Bearer synthetic-bearer-token-123", requestData.headers[HttpHeaders.Authorization])

        assertEquals(2, response.currentPage)
        assertEquals(10, response.perPage)
        assertEquals(25, response.total)
        assertEquals(1, response.data.size)
        assertEquals("9b1deb4d-3b7d-419b-a683-123456789abc", response.data[0].id)

        client.close()
    }

    @Test
    fun testGetApplicationDetail_successfulParsing() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-123456789abc"
        val jsonResponseBody = """
            {
                "message": null,
                "data": ${buildMockApplicationJson(validUuid, "KTP", "KTP PEREKAMAN", "KTP-888888", "PROCESSING")}
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)

        val response = service.getApplicationDetail(validUuid, token = "synthetic-detail-token")

        assertEquals(1, requests.size)
        assertEquals("/api/applications/$validUuid", requests[0].url.encodedPath)
        assertEquals("Bearer synthetic-detail-token", requests[0].headers[HttpHeaders.Authorization])

        assertNotNull(response.data)
        assertEquals(validUuid, response.data?.id)
        assertEquals("KTP-888888", response.data?.submissionCode)
        assertEquals("PROCESSING", response.data?.status)

        client.close()
    }

    // ==========================================
    // GROUP B: POST CREATE TESTS (5 SERVICES)
    // ==========================================

    @Test
    fun testCreateKiaApplication_successHttp201() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-101101101101"
        val jsonResponseBody = """
            {
                "message": "Pengajuan berhasil dibuat.",
                "data": ${buildMockApplicationJson(validUuid, "KIA", "KIA BARU", "KIA-777888")}
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)

        val request = LaravelKiaApplicationRequest(
            category = "KIA BARU",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor KIA Test",
            email = "kiatest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Klojen",
            kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest(
                nikAnak = "3573010000000099",
                namaAnak = "Anak KIA Test"
            )
        )

        val response = service.createKiaApplication(request, token = "synthetic-kia-token")

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertEquals(HttpMethod.Post, requestData.method)
        assertEquals("/api/applications", requestData.url.encodedPath)
        assertEquals("Bearer synthetic-kia-token", requestData.headers[HttpHeaders.Authorization])
        assertTrue(requestData.body.contentType?.toString()?.startsWith("application/json") == true)

        assertEquals("Pengajuan berhasil dibuat.", response.message)
        assertNotNull(response.data)

        val data = response.data!!
        assertEquals(validUuid, data.id)
        assertEquals("KIA-777888", data.submissionCode)
        assertNotEquals(data.id, data.submissionCode)
        assertEquals("SUBMITTED", data.status)

        client.close()
    }

    @Test
    fun testCreateKiaApplication_requestJsonBodyVerification() = runBlocking {
        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, """{"message":"Pengajuan berhasil dibuat.","data":null}""")
        }

        val service = LaravelApplicationApiService(client)

        val request = LaravelKiaApplicationRequest(
            category = "KIA BARU",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor KIA Test",
            email = "kiatest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Klojen",
            kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest(
                nikAnak = "3573010000000099",
                namaAnak = "Anak KIA Test"
            )
        )

        service.createKiaApplication(request)

        assertEquals(1, requests.size)
        val bodyText = extractRequestBodyText(requests[0])
        val jsonObject = Json.parseToJsonElement(bodyText).jsonObject

        assertEquals("KIA", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertEquals("KIA BARU", jsonObject["category"]?.jsonPrimitive?.content)
        assertEquals("1111111111111111", jsonObject["nik_pelapor"]?.jsonPrimitive?.content)
        assertEquals("Pelapor KIA Test", jsonObject["nama_pelapor"]?.jsonPrimitive?.content)

        assertTrue(jsonObject.containsKey("detail"))
        val detailObj = jsonObject["detail"]!!.jsonObject
        assertEquals("3573010000000099", detailObj["nik_anak"]?.jsonPrimitive?.content)
        assertEquals("Anak KIA Test", detailObj["nama_anak"]?.jsonPrimitive?.content)

        assertServerGeneratedKeysAbsentInObject(jsonObject)
        assertServerGeneratedKeysAbsentInObject(detailObj)

        client.close()
    }

    @Test
    fun testCreateKtpApplication_successHttp201() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-202202202202"
        val jsonResponseBody = """
            {
                "message": "Pengajuan berhasil dibuat.",
                "data": ${buildMockApplicationJson(validUuid, "KTP", "KTP PEREKAMAN", "KTP-555666")}
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)

        val request = LaravelKtpApplicationRequest(
            category = "KTP PEREKAMAN",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor KTP Test",
            email = "ktptest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Blimbing",
            kelurahan = "Blimbing"
        )

        val response = service.createKtpApplication(request, token = "synthetic-ktp-token")

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertEquals(HttpMethod.Post, requestData.method)
        assertEquals("/api/applications", requestData.url.encodedPath)

        val bodyText = extractRequestBodyText(requestData)
        val jsonObject = Json.parseToJsonElement(bodyText).jsonObject

        assertEquals("KTP", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertEquals("KTP PEREKAMAN", jsonObject["category"]?.jsonPrimitive?.content)
        assertFalse("KTP tidak boleh mengirim detail", jsonObject.containsKey("detail"))
        assertServerGeneratedKeysAbsentInObject(jsonObject)

        assertEquals(validUuid, response.data?.id)
        assertEquals("SUBMITTED", response.data?.status)

        client.close()
    }

    @Test
    fun testCreateKtpApplication_http422ValidationError() = runBlocking {
        val errorJsonResponseBody = """
            {
                "message": "Data pengajuan KTP tidak valid.",
                "errors": {
                    "nik_pelapor": ["NIK pelapor wajib 16 digit angka."]
                }
            }
        """.trimIndent()

        val (client, _) = createMockClient {
            Pair(HttpStatusCode.UnprocessableEntity, errorJsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)
        val request = LaravelKtpApplicationRequest(
            category = "KTP PEREKAMAN",
            nikPelapor = "123",
            namaPelapor = "Pelapor Invalid",
            email = "invalid@example.com",
            whatsapp = "081",
            kecamatan = "Blimbing",
            kelurahan = "Blimbing"
        )

        try {
            service.createKtpApplication(request)
            fail("Create KTP dengan HTTP 422 seharusnya melempar LaravelApiException")
        } catch (e: LaravelApiException) {
            assertEquals(422, e.statusCode)
            assertNotNull(e.errors)
            assertTrue(e.errors?.containsKey("nik_pelapor") == true)
            assertEquals("NIK pelapor wajib 16 digit angka.", e.errors?.get("nik_pelapor")?.get(0))
        }

        client.close()
    }

    @Test
    fun testCreateKkApplication_successHttp201_detailOptionalHandled() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-303303303303"
        val jsonResponseBody = """
            {
                "message": "Pengajuan berhasil dibuat.",
                "data": ${buildMockApplicationJson(validUuid, "KK", "KK Tunggal", "KK-333444")}
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)

        val request = LaravelKkApplicationRequest(
            category = "KK Tunggal",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor KK Test",
            email = "kktest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Lowokwaru",
            kelurahan = "Lowokwaru",
            detail = null
        )

        val response = service.createKkApplication(request, token = "synthetic-kk-token")

        assertEquals(1, requests.size)
        val bodyText = extractRequestBodyText(requests[0])
        val jsonObject = Json.parseToJsonElement(bodyText).jsonObject

        assertEquals("KK", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertFalse("KK dengan detail null harus ABSENT dari JSON", jsonObject.containsKey("detail"))
        assertServerGeneratedKeysAbsentInObject(jsonObject)

        assertEquals(validUuid, response.data?.id)
        client.close()
    }

    @Test
    fun testCreateKkApplication_withNonNullDetail_nestedJsonVerified() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-303303303303"
        val jsonResponseBody = """
            {
                "message": "Pengajuan berhasil dibuat.",
                "data": ${buildMockApplicationJson(validUuid, "KK", "KK + KTP", "KK-333445")}
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)

        val request = LaravelKkApplicationRequest(
            category = "KK + KTP",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor KK Test",
            email = "kktest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Lowokwaru",
            kelurahan = "Lowokwaru",
            detail = LaravelKkDetailRequest(
                nikAnak = "3573010000000012",
                namaAnak = "Anak KK Test",
                nomorKk = "3573010000000034"
            )
        )

        service.createKkApplication(request)

        assertEquals(1, requests.size)
        val bodyText = extractRequestBodyText(requests[0])
        val jsonObject = Json.parseToJsonElement(bodyText).jsonObject

        assertEquals("KK", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertTrue("KK dengan detail non-null harus PRESENT di JSON", jsonObject.containsKey("detail"))

        val detailObj = jsonObject["detail"]!!.jsonObject
        assertEquals("3573010000000012", detailObj["nik_anak"]?.jsonPrimitive?.content)
        assertEquals("Anak KK Test", detailObj["nama_anak"]?.jsonPrimitive?.content)
        assertEquals("3573010000000034", detailObj["nomor_kk"]?.jsonPrimitive?.content)

        client.close()
    }

    @Test
    fun testCreateAktaApplication_successHttp201() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-404404404404"
        val jsonResponseBody = """
            {
                "message": "Pengajuan berhasil dibuat.",
                "data": ${buildMockApplicationJson(validUuid, "AKTA", "Kelahiran Terlambat Tunggal", "AKTA-111222")}
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)

        val request = LaravelAktaApplicationRequest(
            category = "Kelahiran Terlambat Tunggal",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor AKTA Test",
            email = "aktatest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Sukun",
            kelurahan = "Sukun",
            detail = LaravelAktaDetailRequest(
                nikBayi = "3573010000000011",
                namaBayi = "Bayi AKTA Test",
                nikIbu = "3573010000000022",
                namaIbu = "Ibu AKTA Test",
                nomorKk = "3573010000000033"
            )
        )

        val response = service.createAktaApplication(request)

        assertEquals(1, requests.size)
        val bodyText = extractRequestBodyText(requests[0])
        val jsonObject = Json.parseToJsonElement(bodyText).jsonObject

        assertEquals("AKTA", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertTrue(jsonObject.containsKey("detail"))

        val detailObj = jsonObject["detail"]!!.jsonObject
        assertEquals("Bayi AKTA Test", detailObj["nama_bayi"]?.jsonPrimitive?.content)
        assertEquals("3573010000000022", detailObj["nik_ibu"]?.jsonPrimitive?.content)
        assertServerGeneratedKeysAbsentInObject(jsonObject)

        assertEquals(validUuid, response.data?.id)
        client.close()
    }

    @Test
    fun testCreateDisabilitasApplication_successHttp201() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-505505505505"
        val jsonResponseBody = """
            {
                "message": "Pengajuan berhasil dibuat.",
                "data": ${buildMockApplicationJson(validUuid, "DISABILITAS", "Biometric", "DIS-888999")}
            }
        """.trimIndent()

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Created, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)

        val request = LaravelDisabilitasApplicationRequest(
            category = "Biometric",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor DIS Test",
            email = "distest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Kedungkandang",
            kelurahan = "Kedungkandang"
        )

        val response = service.createDisabilitasApplication(request)

        assertEquals(1, requests.size)
        val bodyText = extractRequestBodyText(requests[0])
        val jsonObject = Json.parseToJsonElement(bodyText).jsonObject

        assertEquals("DISABILITAS", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertFalse("DISABILITAS tidak boleh mengirim detail", jsonObject.containsKey("detail"))
        assertServerGeneratedKeysAbsentInObject(jsonObject)

        assertEquals(validUuid, response.data?.id)
        client.close()
    }

    @Test
    fun testCreateKiaApplication_keteranganOptionalScenarios() = runBlocking {
        val (client1, requests1) = createMockClient {
            Pair(HttpStatusCode.Created, """{"message":"OK","data":null}""")
        }
        val service1 = LaravelApplicationApiService(client1)
        val req1 = LaravelKiaApplicationRequest(
            category = "KIA BARU",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor KIA Test",
            email = "kiatest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Klojen",
            kelurahan = "Klojen",
            keterangan = null,
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak KIA Test")
        )
        service1.createKiaApplication(req1)
        val json1 = Json.parseToJsonElement(extractRequestBodyText(requests1[0])).jsonObject
        assertFalse("Key 'keterangan' harus ABSENT saat keterangan null", json1.containsKey("keterangan"))
        client1.close()

        val (client2, requests2) = createMockClient {
            Pair(HttpStatusCode.Created, """{"message":"OK","data":null}""")
        }
        val service2 = LaravelApplicationApiService(client2)
        val req2 = LaravelKiaApplicationRequest(
            category = "KIA BARU",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor KIA Test",
            email = "kiatest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Klojen",
            kelurahan = "Klojen",
            keterangan = "Catatan Tambahan KIA DUMMY",
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak KIA Test")
        )
        service2.createKiaApplication(req2)
        val json2 = Json.parseToJsonElement(extractRequestBodyText(requests2[0])).jsonObject
        assertTrue("Key 'keterangan' harus PRESENT saat keterangan diisi", json2.containsKey("keterangan"))
        assertEquals("Catatan Tambahan KIA DUMMY", json2["keterangan"]?.jsonPrimitive?.content)
        client2.close()
    }

    @Test
    fun testCreateKiaApplication_http422ValidationError() = runBlocking {
        val errorJsonResponseBody = """
            {
                "message": "Data pengajuan tidak valid.",
                "errors": {
                    "detail.nik_anak": ["NIK anak wajib 16 digit angka."]
                }
            }
        """.trimIndent()

        val (client, _) = createMockClient {
            Pair(HttpStatusCode.UnprocessableEntity, errorJsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)
        val request = LaravelKiaApplicationRequest(
            category = "KIA BARU",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor KIA Test",
            email = "kiatest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Klojen",
            kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("123", "Anak Invalid")
        )

        try {
            service.createKiaApplication(request)
            fail("Create KIA dengan HTTP 422 seharusnya melempar LaravelApiException")
        } catch (e: LaravelApiException) {
            assertEquals(422, e.statusCode)
            assertNotNull(e.errors)
            assertTrue(e.errors?.containsKey("detail.nik_anak") == true)
            assertEquals("NIK anak wajib 16 digit angka.", e.errors?.get("detail.nik_anak")?.get(0))
        }

        client.close()
    }

    @Test
    fun testCreateKiaApplication_http401AuthenticationError() = runBlocking {
        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.Unauthorized, """{"message":"Unauthenticated."}""")
        }

        val service = LaravelApplicationApiService(client)
        val request = LaravelKiaApplicationRequest(
            category = "KIA BARU",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor KIA Test",
            email = "kiatest@example.com",
            whatsapp = "081234567890",
            kecamatan = "Klojen",
            kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak KIA Test")
        )

        try {
            service.createKiaApplication(request, token = "expired-token")
            fail("Create KIA dengan HTTP 401 seharusnya melempar LaravelApiException")
        } catch (e: LaravelApiException) {
            assertEquals(401, e.statusCode)
            assertEquals(1, requests.size)
        }

        client.close()
    }

    // ==========================================
    // GROUP C: PUT UPDATE TESTS (5 SERVICES + NEGATIVE + ERROR CASES)
    // ==========================================

    @Test
    fun testUpdateApplications_successHttp200_allFiveServices() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-123456789abc"

        // 1. KIA
        val (client1, reqs1) = createMockClient {
            Pair(HttpStatusCode.OK, """{"message":"Pengajuan berhasil diperbarui.","data":${buildMockApplicationJson(validUuid, "KIA", "KIA BARU", "KIA-123456")}}""")
        }
        val service1 = LaravelApplicationApiService(client1)
        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Pelapor Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("3573010000000099", "Anak Test")
        )
        val res1 = service1.updateKiaApplication(validUuid, kiaReq, token = "synthetic-put-token")
        assertEquals(HttpMethod.Put, reqs1[0].method)
        assertEquals("/api/applications/$validUuid", reqs1[0].url.encodedPath)
        assertEquals("Bearer synthetic-put-token", reqs1[0].headers[HttpHeaders.Authorization])
        assertTrue(reqs1[0].body.contentType?.toString()?.startsWith("application/json") == true)
        val body1 = Json.parseToJsonElement(extractRequestBodyText(reqs1[0])).jsonObject
        assertEquals("KIA", body1["service_type"]?.jsonPrimitive?.content)
        assertEquals("KIA BARU", body1["category"]?.jsonPrimitive?.content)
        assertEquals("Pelapor Test", body1["nama_pelapor"]?.jsonPrimitive?.content)
        assertTrue(body1.containsKey("detail"))
        assertServerGeneratedKeysAbsentInObject(body1)
        assertEquals("Pengajuan berhasil diperbarui.", res1.message)
        client1.close()

        // 2. KTP
        val (client2, reqs2) = createMockClient {
            Pair(HttpStatusCode.OK, """{"message":"Pengajuan berhasil diperbarui.","data":${buildMockApplicationJson(validUuid, "KTP", "KTP PEREKAMAN", "KTP-123456")}}""")
        }
        val service2 = LaravelApplicationApiService(client2)
        val ktpReq = LaravelKtpApplicationRequest(
            category = "KTP PEREKAMAN", nikPelapor = "1111111111111111", namaPelapor = "Pelapor Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen"
        )
        val res2 = service2.updateKtpApplication(validUuid, ktpReq, token = "synthetic-put-token")
        assertEquals(HttpMethod.Put, reqs2[0].method)
        assertEquals("/api/applications/$validUuid", reqs2[0].url.encodedPath)
        assertEquals("Bearer synthetic-put-token", reqs2[0].headers[HttpHeaders.Authorization])

        val body2 = Json.parseToJsonElement(extractRequestBodyText(reqs2[0])).jsonObject
        assertEquals("KTP", body2["service_type"]?.jsonPrimitive?.content)
        assertEquals("KTP PEREKAMAN", body2["category"]?.jsonPrimitive?.content)
        assertFalse(body2.containsKey("detail"))
        assertServerGeneratedKeysAbsentInObject(body2)

        assertEquals("Pengajuan berhasil diperbarui.", res2.message)
        client2.close()

        // 3. KK
        val (client3, reqs3) = createMockClient {
            Pair(HttpStatusCode.OK, """{"message":"Pengajuan berhasil diperbarui.","data":${buildMockApplicationJson(validUuid, "KK", "KK Tunggal", "KK-123456")}}""")
        }
        val service3 = LaravelApplicationApiService(client3)
        val kkReq = LaravelKkApplicationRequest(
            category = "KK Tunggal", nikPelapor = "1111111111111111", namaPelapor = "Pelapor Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKkDetailRequest("3573010000000012", "Anak KK Test", "3573010000000034")
        )
        val res3 = service3.updateKkApplication(validUuid, kkReq, token = "synthetic-put-token")
        assertEquals(HttpMethod.Put, reqs3[0].method)
        assertEquals("/api/applications/$validUuid", reqs3[0].url.encodedPath)

        val body3 = Json.parseToJsonElement(extractRequestBodyText(reqs3[0])).jsonObject
        assertEquals("KK", body3["service_type"]?.jsonPrimitive?.content)
        assertTrue(body3.containsKey("detail"))
        val kkDetailObj = body3["detail"]!!.jsonObject
        assertEquals("3573010000000012", kkDetailObj["nik_anak"]?.jsonPrimitive?.content)
        assertServerGeneratedKeysAbsentInObject(body3)

        assertEquals("Pengajuan berhasil diperbarui.", res3.message)
        client3.close()

        // 4. AKTA
        val (client4, reqs4) = createMockClient {
            Pair(HttpStatusCode.OK, """{"message":"Pengajuan berhasil diperbarui.","data":${buildMockApplicationJson(validUuid, "AKTA", "Kelahiran Terlambat Tunggal", "AKTA-123456")}}""")
        }
        val service4 = LaravelApplicationApiService(client4)
        val aktaReq = LaravelAktaApplicationRequest(
            category = "Kelahiran Terlambat Tunggal", nikPelapor = "1111111111111111", namaPelapor = "Pelapor Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelAktaDetailRequest("3573010000000011", "Bayi Test", "3573010000000022", "Ibu Test", "3573010000000033")
        )
        val res4 = service4.updateAktaApplication(validUuid, aktaReq, token = "synthetic-put-token")
        assertEquals(HttpMethod.Put, reqs4[0].method)
        assertEquals("/api/applications/$validUuid", reqs4[0].url.encodedPath)

        val body4 = Json.parseToJsonElement(extractRequestBodyText(reqs4[0])).jsonObject
        assertEquals("AKTA", body4["service_type"]?.jsonPrimitive?.content)
        assertTrue(body4.containsKey("detail"))
        assertServerGeneratedKeysAbsentInObject(body4)

        assertEquals("Pengajuan berhasil diperbarui.", res4.message)
        client4.close()

        // 5. DISABILITAS
        val (client5, reqs5) = createMockClient {
            Pair(HttpStatusCode.OK, """{"message":"Pengajuan berhasil diperbarui.","data":${buildMockApplicationJson(validUuid, "DISABILITAS", "Biometric", "DIS-123456")}}""")
        }
        val service5 = LaravelApplicationApiService(client5)
        val disReq = LaravelDisabilitasApplicationRequest(
            category = "Biometric", nikPelapor = "1111111111111111", namaPelapor = "Pelapor Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen"
        )
        val res5 = service5.updateDisabilitasApplication(validUuid, disReq, token = "synthetic-put-token")
        assertEquals(HttpMethod.Put, reqs5[0].method)
        assertEquals("/api/applications/$validUuid", reqs5[0].url.encodedPath)

        val body5 = Json.parseToJsonElement(extractRequestBodyText(reqs5[0])).jsonObject
        assertEquals("DISABILITAS", body5["service_type"]?.jsonPrimitive?.content)
        assertFalse(body5.containsKey("detail"))
        assertServerGeneratedKeysAbsentInObject(body5)

        assertEquals("Pengajuan berhasil diperbarui.", res5.message)
        client5.close()
    }

    @Test
    fun testUpdateApplication_invalidUuids_rejectedLocallyWithoutHttpRequest_allFiveFunctions() = runBlocking {
        val invalidUuids = listOf("", "   ", " 9b1deb4d-3b7d-419b-a683-123456789abc ", "KTP-123456", "abc", "../applications/123", "invalid-uuid")

        val (client, requests) = createMockClient { Pair(HttpStatusCode.OK, """{"data":null}""") }
        val service = LaravelApplicationApiService(client)

        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("123", "Test")
        )

        val ktpReq = LaravelKtpApplicationRequest(
            category = "KTP PEREKAMAN", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen"
        )

        val kkReq = LaravelKkApplicationRequest(
            category = "KK Tunggal", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen"
        )

        val aktaReq = LaravelAktaApplicationRequest(
            category = "Kelahiran Terlambat Tunggal", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelAktaDetailRequest("1", "Bayi", "2", "Ibu", "3")
        )

        val disReq = LaravelDisabilitasApplicationRequest(
            category = "Biometric", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen"
        )

        for (invalidUuid in invalidUuids) {
            // 1. KIA
            try {
                service.updateKiaApplication(invalidUuid, kiaReq)
                fail("updateKiaApplication dengan UUID invalid '$invalidUuid' seharusnya melempar IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("Format UUID pengajuan tidak valid") == true)
            }

            // 2. KTP
            try {
                service.updateKtpApplication(invalidUuid, ktpReq)
                fail("updateKtpApplication dengan UUID invalid '$invalidUuid' seharusnya melempar IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("Format UUID pengajuan tidak valid") == true)
            }

            // 3. KK
            try {
                service.updateKkApplication(invalidUuid, kkReq)
                fail("updateKkApplication dengan UUID invalid '$invalidUuid' seharusnya melempar IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("Format UUID pengajuan tidak valid") == true)
            }

            // 4. AKTA
            try {
                service.updateAktaApplication(invalidUuid, aktaReq)
                fail("updateAktaApplication dengan UUID invalid '$invalidUuid' seharusnya melempar IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("Format UUID pengajuan tidak valid") == true)
            }

            // 5. DISABILITAS
            try {
                service.updateDisabilitasApplication(invalidUuid, disReq)
                fail("updateDisabilitasApplication dengan UUID invalid '$invalidUuid' seharusnya melempar IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("Format UUID pengajuan tidak valid") == true)
            }
        }

        assertEquals("MockEngine harus menerima NOL request HTTP untuk seluruh 5 fungsi UPDATE dengan UUID invalid", 0, requests.size)
        client.close()
    }

    @Test
    fun testUpdateApplication_errorScenarios_401_404_409_422_500() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-123456789abc"
        val errorScenarios = listOf(
            Pair(HttpStatusCode.Unauthorized, """{"message":"Unauthenticated."}"""),
            Pair(HttpStatusCode.NotFound, """{"message":"Pengajuan tidak ditemukan."}"""),
            Pair(HttpStatusCode.Conflict, """{"message":"Pengajuan tidak dapat diubah pada status ini."}"""),
            Pair(HttpStatusCode.UnprocessableEntity, """{"message":"Validasi gagal."}"""),
            Pair(HttpStatusCode.InternalServerError, """{"message":"Server Error"}""")
        )

        val kiaReq = LaravelKiaApplicationRequest(
            category = "KIA BARU", nikPelapor = "1111111111111111", namaPelapor = "Test",
            email = "t@t.com", whatsapp = "081234567890", kecamatan = "Klojen", kelurahan = "Klojen",
            detail = LaravelKiaDetailRequest("123", "Test")
        )

        for ((status, body) in errorScenarios) {
            val (client, _) = createMockClient { Pair(status, body) }
            val service = LaravelApplicationApiService(client)

            try {
                service.updateKiaApplication(validUuid, kiaReq)
                fail("UPDATE dengan status ${status.value} seharusnya melempar LaravelApiException")
            } catch (e: LaravelApiException) {
                assertEquals(status.value, e.statusCode)
            }
            client.close()
        }
    }

    // ==========================================
    // GROUP D: DELETE APPLICATION TESTS
    // ==========================================

    @Test
    fun testDeleteApplication_successHttp200() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-123456789abc"
        val jsonResponseBody = """{"message":"Pengajuan berhasil dihapus."}"""

        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, jsonResponseBody)
        }

        val service = LaravelApplicationApiService(client)

        val response = service.deleteApplication(validUuid, token = "synthetic-delete-token")

        assertEquals(1, requests.size)
        val requestData = requests[0]

        assertEquals(HttpMethod.Delete, requestData.method)
        assertEquals("/api/applications/$validUuid", requestData.url.encodedPath)
        assertEquals("Bearer synthetic-delete-token", requestData.headers[HttpHeaders.Authorization])
        assertEquals("Pengajuan berhasil dihapus.", response.message)

        client.close()
    }

    @Test
    fun testDeleteApplication_invalidUuids_rejectedLocallyWithoutHttpRequest() = runBlocking {
        val invalidUuids = listOf("", "   ", "KIA-123456", "abc", "../applications/123", "invalid-uuid")

        val (client, requests) = createMockClient { Pair(HttpStatusCode.OK, """{"message":"OK"}""") }
        val service = LaravelApplicationApiService(client)

        for (invalidUuid in invalidUuids) {
            try {
                service.deleteApplication(invalidUuid)
                fail("DELETE dengan UUID invalid '$invalidUuid' seharusnya melempar IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("Format UUID pengajuan tidak valid") == true)
            }
        }

        assertEquals("MockEngine harus menerima NOL request HTTP untuk DELETE UUID invalid", 0, requests.size)
        client.close()
    }

    @Test
    fun testDeleteApplication_errorHandlingStatusCodes() = runBlocking {
        val validUuid = "9b1deb4d-3b7d-419b-a683-123456789abc"
        val errorScenarios = listOf(
            Pair(HttpStatusCode.Unauthorized, """{"message":"Unauthenticated."}"""),
            Pair(HttpStatusCode.Forbidden, """{"message":"Akses ditolak."}"""),
            Pair(HttpStatusCode.NotFound, """{"message":"Pengajuan tidak ditemukan."}"""),
            Pair(HttpStatusCode.InternalServerError, """{"message":"Server Error"}""")
        )

        for ((status, body) in errorScenarios) {
            val (client, requests) = createMockClient { Pair(status, body) }
            val service = LaravelApplicationApiService(client)

            try {
                service.deleteApplication(validUuid)
                fail("DELETE dengan status ${status.value} seharusnya melempar LaravelApiException")
            } catch (e: LaravelApiException) {
                assertEquals(status.value, e.statusCode)
                assertEquals(1, requests.size) // No retry loop
            }
            client.close()
        }
    }

    // ==========================================
    // GROUP E: PRIVACY & COMMON ERROR HANDLING
    // ==========================================

    @Test
    fun testErrorHandlingStatusCodes_throwsLaravelApiException() = runBlocking {
        val errorScenarios = listOf(
            Pair(HttpStatusCode.Unauthorized, """{"message":"Unauthenticated."}"""),
            Pair(HttpStatusCode.Forbidden, """{"message":"This action is unauthorized."}"""),
            Pair(HttpStatusCode.NotFound, """{"message":"Resource not found."}"""),
            Pair(HttpStatusCode.UnprocessableEntity, """{"message":"The given data was invalid.","errors":{"service_type":["Jenis layanan tidak valid."]}}"""),
            Pair(HttpStatusCode.TooManyRequests, """{"message":"Too Many Attempts."}"""),
            Pair(HttpStatusCode.InternalServerError, """{"message":"Server Error"}""")
        )

        for ((expectedStatus, jsonResponse) in errorScenarios) {
            val (client, _) = createMockClient {
                Pair(expectedStatus, jsonResponse)
            }
            val service = LaravelApplicationApiService(client)

            try {
                service.getApplications()
                fail("Request ke status ${expectedStatus.value} seharusnya melempar LaravelApiException")
            } catch (e: LaravelApiException) {
                assertEquals(expectedStatus.value, e.statusCode)
                assertNotNull(e.message)
                if (expectedStatus == HttpStatusCode.UnprocessableEntity) {
                    assertNotNull(e.errors)
                    assertTrue(e.errors?.containsKey("service_type") == true)
                    assertEquals("Jenis layanan tidak valid.", e.errors?.get("service_type")?.get(0))
                }
            }
            client.close()
        }
    }

    @Test
    fun testAuthorizationHeader_sentExplicitly() = runBlocking {
        val (client, requests) = createMockClient {
            Pair(HttpStatusCode.OK, """{"current_page":1,"data":[],"total":0}""")
        }

        val service = LaravelApplicationApiService(client)
        val dummyToken = "synthetic-test-token-abcdef-123456"

        service.getApplications(token = dummyToken)

        assertEquals(1, requests.size)
        assertEquals("Bearer $dummyToken", requests[0].headers[HttpHeaders.Authorization])

        client.close()
    }

    private fun assertServerGeneratedKeysAbsentInObject(jsonObject: JsonObject) {
        val prohibitedKeys = listOf(
            "id", "user_id", "submission_code", "submission_date",
            "status", "note", "created_at", "updated_at", "application_id",
            "doc_1_path", "doc_2_path", "doc_3_path", "doc_selfie_path"
        )
        for (key in prohibitedKeys) {
            assertFalse("Field '$key' tidak boleh ada dalam payload request", jsonObject.containsKey(key))
        }
    }
}
