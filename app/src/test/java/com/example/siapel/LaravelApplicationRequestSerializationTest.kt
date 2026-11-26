package com.example.siapel

import com.example.siapel.data.remote.laravel.LaravelClientProvider
import com.example.siapel.data.remote.laravel.dto.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * Local Unit Test untuk memverifikasi serialisasi JSON Request DTO REST API Laravel 12.
 */
class LaravelApplicationRequestSerializationTest {

    private val json = LaravelClientProvider.json

    @Test
    fun testKkRequestWithoutDetail_detailKeyIsAbsent() {
        val request = LaravelKkApplicationRequest(
            category = "KK Tunggal",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor DUMMY Satu",
            email = "dummy1@example.com",
            whatsapp = "081234567890",
            kecamatan = "Klojen",
            kelurahan = "Klojen",
            keterangan = null,
            detail = null
        )

        val jsonString = json.encodeToString(LaravelKkApplicationRequest.serializer(), request)
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

        assertEquals("KK", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertEquals("KK Tunggal", jsonObject["category"]?.jsonPrimitive?.content)
        assertFalse("Key 'detail' harus ABSENT saat detail null", jsonObject.containsKey("detail"))
        assertFalse("Key 'keterangan' harus ABSENT saat keterangan null", jsonObject.containsKey("keterangan"))

        assertServerGeneratedKeysAbsent(jsonObject)
    }

    @Test
    fun testKkRequestWithDetail_detailKeyIsPresent() {
        val request = LaravelKkApplicationRequest(
            category = "KK + KTP",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor DUMMY Dua",
            email = "dummy2@example.com",
            whatsapp = "081234567890",
            kecamatan = "Blimbing",
            kelurahan = "Blimbing",
            keterangan = "Catatan DUMMY",
            detail = LaravelKkDetailRequest(
                nikAnak = "3573010000000001",
                namaAnak = "Anak DUMMY Satu",
                nomorKk = "3573010000000002"
            )
        )

        val jsonString = json.encodeToString(LaravelKkApplicationRequest.serializer(), request)
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

        assertEquals("KK", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertEquals("Catatan DUMMY", jsonObject["keterangan"]?.jsonPrimitive?.content)
        assertTrue("Key 'detail' harus PRESENT saat detail diisi", jsonObject.containsKey("detail"))

        val detailObj = jsonObject["detail"]!!.jsonObject
        assertEquals("3573010000000001", detailObj["nik_anak"]?.jsonPrimitive?.content)
        assertEquals("Anak DUMMY Satu", detailObj["nama_anak"]?.jsonPrimitive?.content)
        assertEquals("3573010000000002", detailObj["nomor_kk"]?.jsonPrimitive?.content)

        assertServerGeneratedKeysAbsent(jsonObject)
    }

    @Test
    fun testKiaRequestSerialization() {
        val request = LaravelKiaApplicationRequest(
            category = "KIA BARU",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor DUMMY Tiga",
            email = "dummy3@example.com",
            whatsapp = "081234567890",
            kecamatan = "Lowokwaru",
            kelurahan = "Lowokwaru",
            detail = LaravelKiaDetailRequest(
                nikAnak = "3573010000000003",
                namaAnak = "Anak DUMMY Dua"
            )
        )

        val jsonString = json.encodeToString(LaravelKiaApplicationRequest.serializer(), request)
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

        assertEquals("KIA", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertEquals("KIA BARU", jsonObject["category"]?.jsonPrimitive?.content)
        assertTrue(jsonObject.containsKey("detail"))

        val detailObj = jsonObject["detail"]!!.jsonObject
        assertEquals("3573010000000003", detailObj["nik_anak"]?.jsonPrimitive?.content)
        assertEquals("Anak DUMMY Dua", detailObj["nama_anak"]?.jsonPrimitive?.content)

        assertServerGeneratedKeysAbsent(jsonObject)
    }

    @Test
    fun testKtpRequestSerialization_noDetailKey() {
        val request = LaravelKtpApplicationRequest(
            category = "KTP PEREKAMAN",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor DUMMY Empat",
            email = "dummy4@example.com",
            whatsapp = "081234567890",
            kecamatan = "Sukun",
            kelurahan = "Sukun"
        )

        val jsonString = json.encodeToString(LaravelKtpApplicationRequest.serializer(), request)
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

        assertEquals("KTP", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertEquals("KTP PEREKAMAN", jsonObject["category"]?.jsonPrimitive?.content)
        assertFalse("KTP tidak boleh memiliki key detail", jsonObject.containsKey("detail"))

        assertServerGeneratedKeysAbsent(jsonObject)
    }

    @Test
    fun testAktaRequestSerialization() {
        val request = LaravelAktaApplicationRequest(
            category = "Kelahiran Terlambat Tunggal",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor DUMMY Lima",
            email = "dummy5@example.com",
            whatsapp = "081234567890",
            kecamatan = "Kedungkandang",
            kelurahan = "Kedungkandang",
            detail = LaravelAktaDetailRequest(
                nikBayi = "3573010000000004",
                namaBayi = "Bayi DUMMY",
                nikIbu = "3573010000000005",
                namaIbu = "Ibu DUMMY",
                nomorKk = "3573010000000006"
            )
        )

        val jsonString = json.encodeToString(LaravelAktaApplicationRequest.serializer(), request)
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

        assertEquals("AKTA", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertTrue(jsonObject.containsKey("detail"))

        val detailObj = jsonObject["detail"]!!.jsonObject
        assertEquals("3573010000000004", detailObj["nik_bayi"]?.jsonPrimitive?.content)
        assertEquals("Bayi DUMMY", detailObj["nama_bayi"]?.jsonPrimitive?.content)
        assertEquals("3573010000000005", detailObj["nik_ibu"]?.jsonPrimitive?.content)
        assertEquals("Ibu DUMMY", detailObj["nama_ibu"]?.jsonPrimitive?.content)
        assertEquals("3573010000000006", detailObj["nomor_kk"]?.jsonPrimitive?.content)

        assertServerGeneratedKeysAbsent(jsonObject)
    }

    @Test
    fun testDisabilitasRequestSerialization_noDetailKey() {
        val request = LaravelDisabilitasApplicationRequest(
            category = "Perekaman KTP",
            nikPelapor = "1111111111111111",
            namaPelapor = "Pelapor DUMMY Enam",
            email = "dummy6@example.com",
            whatsapp = "081234567890",
            kecamatan = "Klojen",
            kelurahan = "Klojen"
        )

        val jsonString = json.encodeToString(LaravelDisabilitasApplicationRequest.serializer(), request)
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

        assertEquals("DISABILITAS", jsonObject["service_type"]?.jsonPrimitive?.content)
        assertFalse("DISABILITAS tidak boleh memiliki key detail", jsonObject.containsKey("detail"))

        assertServerGeneratedKeysAbsent(jsonObject)
    }

    private fun assertServerGeneratedKeysAbsent(jsonObject: JsonObject) {
        val prohibitedKeys = listOf(
            "id", "user_id", "submission_code", "submission_date",
            "status", "note", "created_at", "updated_at",
            "application_id", "doc_1_path", "doc_2_path", "doc_3_path",
            "doc_4_path", "doc_5_path", "doc_6_path", "doc_7_path",
            "doc_8_path", "doc_9_path", "doc_10_path", "doc_selfie_path"
        )
        for (key in prohibitedKeys) {
            assertFalse("Properti server-generated '$key' tidak boleh ada dalam request payload", jsonObject.containsKey(key))
        }
    }
}
