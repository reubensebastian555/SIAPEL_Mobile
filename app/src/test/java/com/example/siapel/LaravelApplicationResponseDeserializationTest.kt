package com.example.siapel

import com.example.siapel.data.remote.laravel.LaravelClientProvider
import com.example.siapel.data.remote.laravel.dto.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Local Unit Test untuk memverifikasi deserialisasi JSON Response DTO REST API Laravel 12.
 */
class LaravelApplicationResponseDeserializationTest {

    private val json = LaravelClientProvider.json

    @Test
    fun testCreateSingleResponseDeserialization() {
        val jsonString = """
            {
                "message": "Pengajuan berhasil dibuat.",
                "data": {
                    "id": "9b1deb4d-3b7d-419b-a683-123456789abc",
                    "user_id": "user-uuid-123",
                    "service_type": "KIA",
                    "category": "KIA BARU",
                    "submission_code": "KIA-123456",
                    "submission_date": "18-11-2026",
                    "status": "SUBMITTED",
                    "note": null,
                    "nik_pelapor": "1111111111111111",
                    "nama_pelapor": "Pelapor Test",
                    "email": "pelapor@example.com",
                    "whatsapp": "081234567890",
                    "kecamatan": "Klojen",
                    "kelurahan": "Klojen",
                    "keterangan": "",
                    "created_at": "2026-11-18T03:31:40.000000Z",
                    "updated_at": "2026-11-18T03:31:40.000000Z",
                    "kia_detail": {
                        "application_id": "9b1deb4d-3b7d-419b-a683-123456789abc",
                        "nik_anak": "3573010000000001",
                        "nama_anak": "Anak Test",
                        "doc_1_path": null,
                        "doc_2_path": null,
                        "doc_3_path": null,
                        "doc_selfie_path": null,
                        "created_at": "2026-11-18T03:31:40.000000Z",
                        "updated_at": "2026-11-18T03:31:40.000000Z"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<LaravelApplicationSingleResponse>(jsonString)

        assertEquals("Pengajuan berhasil dibuat.", response.message)
        assertNotNull(response.data)

        val data = response.data!!
        assertEquals("9b1deb4d-3b7d-419b-a683-123456789abc", data.id)
        assertEquals("user-uuid-123", data.userId)
        assertEquals("KIA-123456", data.submissionCode)
        assertNotEquals("UUID id dan submission_code tidak boleh sama", data.id, data.submissionCode)
        assertEquals("SUBMITTED", data.status)
        assertNull(data.note)
        assertEquals("KIA", data.serviceType)
        assertEquals("KIA BARU", data.category)

        assertNotNull(data.kiaDetail)
        assertEquals("3573010000000001", data.kiaDetail?.nikAnak)
        assertEquals("Anak Test", data.kiaDetail?.namaAnak)
        assertNull(data.kiaDetail?.doc1Path)
        assertNull(data.kiaDetail?.docSelfiePath)
    }

    @Test
    fun testDetailSingleResponseDeserialization_withoutMessage() {
        val jsonString = """
            {
                "data": {
                    "id": "ktp-app-uuid-456",
                    "user_id": "user-uuid-123",
                    "service_type": "KTP",
                    "category": "KTP PEREKAMAN",
                    "submission_code": "KTP-654321",
                    "submission_date": "18-11-2026",
                    "status": "PROCESSING",
                    "note": "Perlu verifikasi fisik",
                    "nik_pelapor": "1111111111111111",
                    "nama_pelapor": "Pelapor Test Two",
                    "email": "pelapor2@example.com",
                    "whatsapp": "081234567890",
                    "kecamatan": "Blimbing",
                    "kelurahan": "Blimbing"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<LaravelApplicationSingleResponse>(jsonString)

        assertNull("Message harus null jika tidak dikirim server pada show detail", response.message)
        assertNotNull(response.data)

        val data = response.data!!
        assertEquals("ktp-app-uuid-456", data.id)
        assertEquals("KTP-654321", data.submissionCode)
        assertEquals("PROCESSING", data.status)
        assertEquals("Perlu verifikasi fisik", data.note)
        assertEquals("KTP", data.serviceType)
        assertNull(data.ktpDetail)
    }

    @Test
    fun testPaginatedListResponseDeserialization() {
        val jsonString = """
            {
                "current_page": 1,
                "data": [
                    {
                        "id": "kk-app-uuid-789",
                        "user_id": "user-uuid-123",
                        "service_type": "KK",
                        "category": "KK Tunggal",
                        "submission_code": "KK-000001",
                        "submission_date": "18-11-2026",
                        "status": "COMPLETED",
                        "nik_pelapor": "1111111111111111",
                        "nama_pelapor": "Pelapor Test Three",
                        "email": "pelapor3@example.com",
                        "whatsapp": "081234567890",
                        "kecamatan": "Lowokwaru",
                        "kelurahan": "Lowokwaru"
                    }
                ],
                "first_page_url": "http://10.0.2.2:8000/api/applications?page=1",
                "from": 1,
                "last_page": 1,
                "last_page_url": "http://10.0.2.2:8000/api/applications?page=1",
                "next_page_url": null,
                "path": "http://10.0.2.2:8000/api/applications",
                "per_page": 20,
                "prev_page_url": null,
                "to": 1,
                "total": 1
            }
        """.trimIndent()

        val response = json.decodeFromString<LaravelApplicationPaginatedResponse>(jsonString)

        assertEquals(1, response.currentPage)
        assertEquals(20, response.perPage)
        assertEquals(1, response.lastPage)
        assertEquals(1, response.total)
        assertNull(response.nextPageUrl)
        assertNull(response.prevPageUrl)

        assertEquals(1, response.data.size)
        val item = response.data[0]
        assertEquals("kk-app-uuid-789", item.id)
        assertEquals("KK-000001", item.submissionCode)
        assertEquals("COMPLETED", item.status)
    }

    @Test
    fun testPaginatedListResponseDeserialization_emptyList() {
        val jsonString = """
            {
                "current_page": 1,
                "data": [],
                "first_page_url": "http://10.0.2.2:8000/api/applications?page=1",
                "from": null,
                "last_page": 1,
                "last_page_url": "http://10.0.2.2:8000/api/applications?page=1",
                "next_page_url": null,
                "path": "http://10.0.2.2:8000/api/applications",
                "per_page": 20,
                "prev_page_url": null,
                "to": null,
                "total": 0
            }
        """.trimIndent()

        val response = json.decodeFromString<LaravelApplicationPaginatedResponse>(jsonString)

        assertEquals(1, response.currentPage)
        assertEquals(0, response.total)
        assertTrue(response.data.isEmpty())
    }

    @Test
    fun testKiaDetailDeserialization() {
        val jsonString = """
            {
                "id": "kia-app-1",
                "user_id": "user-1",
                "service_type": "KIA",
                "category": "KIA BARU",
                "submission_code": "KIA-100001",
                "submission_date": "18-11-2026",
                "status": "SUBMITTED",
                "nik_pelapor": "1111111111111111",
                "nama_pelapor": "Pelapor KIA",
                "email": "kia@example.com",
                "whatsapp": "081234567890",
                "kecamatan": "Klojen",
                "kelurahan": "Klojen",
                "kia_detail": {
                    "application_id": "kia-app-1",
                    "nik_anak": "3573010000000010",
                    "nama_anak": "Anak KIA",
                    "doc_1_path": "user-1/kia-app-1/doc1_uuid.jpg",
                    "doc_2_path": null,
                    "doc_3_path": null,
                    "doc_selfie_path": "user-1/kia-app-1/docSelfie_uuid.jpg"
                }
            }
        """.trimIndent()

        val dto = json.decodeFromString<LaravelApplicationDto>(jsonString)

        assertEquals("KIA", dto.serviceType)
        assertNotNull(dto.kiaDetail)
        val detail = dto.kiaDetail!!
        assertEquals("kia-app-1", detail.applicationId)
        assertEquals("3573010000000010", detail.nikAnak)
        assertEquals("Anak KIA", detail.namaAnak)
        assertEquals("user-1/kia-app-1/doc1_uuid.jpg", detail.doc1Path)
        assertNull(detail.doc2Path)
        assertNull(detail.doc3Path)
        assertEquals("user-1/kia-app-1/docSelfie_uuid.jpg", detail.docSelfiePath)
    }

    @Test
    fun testKtpDetailDeserialization() {
        val jsonString = """
            {
                "id": "ktp-app-1",
                "user_id": "user-1",
                "service_type": "KTP",
                "category": "KTP PEREKAMAN",
                "submission_code": "KTP-100001",
                "submission_date": "18-11-2026",
                "status": "SUBMITTED",
                "nik_pelapor": "1111111111111111",
                "nama_pelapor": "Pelapor KTP",
                "email": "ktp@example.com",
                "whatsapp": "081234567890",
                "kecamatan": "Blimbing",
                "kelurahan": "Blimbing",
                "ktp_detail": {
                    "application_id": "ktp-app-1",
                    "doc_1_path": "user-1/ktp-app-1/doc1_uuid.jpg",
                    "doc_2_path": "user-1/ktp-app-1/doc2_uuid.jpg",
                    "doc_selfie_path": "user-1/ktp-app-1/docSelfie_uuid.jpg"
                }
            }
        """.trimIndent()

        val dto = json.decodeFromString<LaravelApplicationDto>(jsonString)

        assertEquals("KTP", dto.serviceType)
        assertNotNull(dto.ktpDetail)
        val detail = dto.ktpDetail!!
        assertEquals("ktp-app-1", detail.applicationId)
        assertEquals("user-1/ktp-app-1/doc1_uuid.jpg", detail.doc1Path)
        assertEquals("user-1/ktp-app-1/doc2_uuid.jpg", detail.doc2Path)
        assertEquals("user-1/ktp-app-1/docSelfie_uuid.jpg", detail.docSelfiePath)
    }

    @Test
    fun testKkDetailDeserialization() {
        val jsonString = """
            {
                "id": "kk-app-1",
                "user_id": "user-1",
                "service_type": "KK",
                "category": "KK + KTP",
                "submission_code": "KK-100001",
                "submission_date": "18-11-2026",
                "status": "NEED_REVISION",
                "nik_pelapor": "1111111111111111",
                "nama_pelapor": "Pelapor KK",
                "email": "kk@example.com",
                "whatsapp": "081234567890",
                "kecamatan": "Lowokwaru",
                "kelurahan": "Lowokwaru",
                "kk_detail": {
                    "application_id": "kk-app-1",
                    "nik_anak": "3573010000000020",
                    "nama_anak": "Anak KK",
                    "nomor_kk": "3573010000000030",
                    "doc_1_path": "user-1/kk-app-1/doc1_uuid.pdf",
                    "doc_2_path": null,
                    "doc_3_path": null,
                    "doc_4_path": null,
                    "doc_5_path": null,
                    "doc_6_path": null,
                    "doc_7_path": null,
                    "doc_8_path": null,
                    "doc_9_path": null,
                    "doc_10_path": null,
                    "doc_selfie_path": "user-1/kk-app-1/docSelfie_uuid.jpg"
                }
            }
        """.trimIndent()

        val dto = json.decodeFromString<LaravelApplicationDto>(jsonString)

        assertEquals("KK", dto.serviceType)
        assertEquals("NEED_REVISION", dto.status)
        assertNotNull(dto.kkDetail)
        val detail = dto.kkDetail!!
        assertEquals("kk-app-1", detail.applicationId)
        assertEquals("3573010000000020", detail.nikAnak)
        assertEquals("Anak KK", detail.namaAnak)
        assertEquals("3573010000000030", detail.nomorKk)
        assertEquals("user-1/kk-app-1/doc1_uuid.pdf", detail.doc1Path)
        assertNull(detail.doc10Path)
        assertEquals("user-1/kk-app-1/docSelfie_uuid.jpg", detail.docSelfiePath)
    }

    @Test
    fun testAktaDetailDeserialization() {
        val jsonString = """
            {
                "id": "akta-app-1",
                "user_id": "user-1",
                "service_type": "AKTA",
                "category": "Paket Kelahiran Umum + KIA + KK",
                "submission_code": "AKTA-100001",
                "submission_date": "18-11-2026",
                "status": "SUBMITTED",
                "nik_pelapor": "1111111111111111",
                "nama_pelapor": "Pelapor AKTA",
                "email": "akta@example.com",
                "whatsapp": "081234567890",
                "kecamatan": "Sukun",
                "kelurahan": "Sukun",
                "akta_detail": {
                    "application_id": "akta-app-1",
                    "nik_bayi": "3573010000000040",
                    "nama_bayi": "Bayi AKTA",
                    "nik_ibu": "3573010000000050",
                    "nama_ibu": "Ibu AKTA",
                    "nomor_kk": "3573010000000060",
                    "doc_1_path": "user-1/akta-app-1/doc1_uuid.pdf",
                    "doc_2_path": "user-1/akta-app-1/doc2_uuid.pdf",
                    "doc_3_path": null,
                    "doc_4_path": null,
                    "doc_5_path": null,
                    "doc_6_path": null,
                    "doc_7_path": null,
                    "doc_8_path": null,
                    "doc_9_path": null,
                    "doc_selfie_path": "user-1/akta-app-1/docSelfie_uuid.jpg"
                }
            }
        """.trimIndent()

        val dto = json.decodeFromString<LaravelApplicationDto>(jsonString)

        assertEquals("AKTA", dto.serviceType)
        assertNotNull(dto.aktaDetail)
        val detail = dto.aktaDetail!!
        assertEquals("akta-app-1", detail.applicationId)
        assertEquals("3573010000000040", detail.nikBayi)
        assertEquals("Bayi AKTA", detail.namaBayi)
        assertEquals("3573010000000050", detail.nikIbu)
        assertEquals("Ibu AKTA", detail.namaIbu)
        assertEquals("3573010000000060", detail.nomorKk)
        assertEquals("user-1/akta-app-1/doc1_uuid.pdf", detail.doc1Path)
        assertEquals("user-1/akta-app-1/doc2_uuid.pdf", detail.doc2Path)
        assertNull(detail.doc9Path)
    }

    @Test
    fun testDisabilitasDetailDeserialization() {
        val jsonString = """
            {
                "id": "dis-app-1",
                "user_id": "user-1",
                "service_type": "DISABILITAS",
                "category": "Biometric",
                "submission_code": "DIS-100001",
                "submission_date": "18-11-2026",
                "status": "SUBMITTED",
                "nik_pelapor": "1111111111111111",
                "nama_pelapor": "Pelapor DIS",
                "email": "dis@example.com",
                "whatsapp": "081234567890",
                "kecamatan": "Kedungkandang",
                "kelurahan": "Kedungkandang",
                "disabilitas_detail": {
                    "application_id": "dis-app-1",
                    "doc_1_path": "user-1/dis-app-1/doc1_uuid.jpg",
                    "doc_2_path": null,
                    "doc_selfie_path": "user-1/dis-app-1/docSelfie_uuid.jpg"
                }
            }
        """.trimIndent()

        val dto = json.decodeFromString<LaravelApplicationDto>(jsonString)

        assertEquals("DISABILITAS", dto.serviceType)
        assertNotNull(dto.disabilitasDetail)
        val detail = dto.disabilitasDetail!!
        assertEquals("dis-app-1", detail.applicationId)
        assertEquals("user-1/dis-app-1/doc1_uuid.jpg", detail.doc1Path)
        assertNull(detail.doc2Path)
        assertEquals("user-1/dis-app-1/docSelfie_uuid.jpg", detail.docSelfiePath)
    }

    @Test
    fun testIgnoreUnknownKeysAndNullableFields() {
        val jsonString = """
            {
                "id": "app-unknown-test",
                "user_id": "user-1",
                "service_type": "KTP",
                "category": "KTP PEREKAMAN",
                "submission_code": "KTP-999999",
                "submission_date": "18-11-2026",
                "status": "SUBMITTED",
                "nik_pelapor": "1111111111111111",
                "nama_pelapor": "Pelapor Unknown Test",
                "email": "unknown@example.com",
                "whatsapp": "081234567890",
                "kecamatan": "Klojen",
                "kelurahan": "Klojen",
                "future_laravel_backend_field": "some_new_feature_value",
                "another_nested_unknown": { "key": "value" }
            }
        """.trimIndent()

        val dto = json.decodeFromString<LaravelApplicationDto>(jsonString)

        assertEquals("app-unknown-test", dto.id)
        assertEquals("KTP-999999", dto.submissionCode)
        assertNull("Relasi detail yang tidak dikirim harus null tanpa crash", dto.kiaDetail)
        assertNull(dto.ktpDetail)
        assertNull(dto.kkDetail)
        assertNull(dto.aktaDetail)
        assertNull(dto.disabilitasDetail)
    }
}
