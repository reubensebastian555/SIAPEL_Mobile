package com.example.siapel.data

object WilayahData {

    val kecamatanKelurahan = mapOf(
        "Klojen" to listOf(
            "Klojen",
            "Rampal Celaket",
            "Samaan",
            "Kiduldalem",
            "Sukoharjo",
            "Kasin",
            "Oro-oro Dowo",
            "Bareng",
            "Gading Kasri",
            "Penanggungan",
            "Kauman"
        ),
        "Blimbing" to listOf(
            "Blimbing",
            "Balearjosari",
            "Arjosari",
            "Purwodadi",
            "Polowijen",
            "Pandanwangi",
            "Purwantoro",
            "Bunulrejo",
            "Kesatrian",
            "Polehan",
            "Jodipan"
        ),
        "Lowokwaru" to listOf(
            "Tasikmadu",
            "Tunggulwulung",
            "Merjosari",
            "Tlogomas",
            "Dinoyo",
            "Sumbersari",
            "Ketawanggede",
            "Jatimulyo",
            "Tunjungsekar",
            "Mojolangu",
            "Tulusrejo",
            "Lowokwaru"
        ),
        "Sukun" to listOf(
            "Ciptomulyo",
            "Gadang",
            "Bandungrejosari",
            "Sukun",
            "Tanjungrejo",
            "Pisangcandi",
            "Bandulan",
            "Karangbesuki",
            "Mulyorejo",
            "Bakalan Krajan",
            "Kebonsari"
        ),
        "Kedungkandang" to listOf(
            "Kotalama",
            "Mergosono",
            "Bumiayu",
            "Wonokoyo",
            "Buring",
            "Kedungkandang",
            "Lesanpuro",
            "Sawojajar",
            "Madyopuro",
            "Cemorokandang",
            "Arjowinangun",
            "Tlogowaru"
        )
    )

    val daftarKecamatan: List<String>
        get() = kecamatanKelurahan.keys.toList().sorted()

    fun getKelurahan(kecamatan: String): List<String> {
        return kecamatanKelurahan[kecamatan]?.sorted() ?: emptyList()
    }
}
