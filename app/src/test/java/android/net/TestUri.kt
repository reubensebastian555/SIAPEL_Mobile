package android.net

import android.os.Parcel

/**
 * Helper class TestUri berada di package `android.net` agar dapat mengakses package-private constructor [Uri].
 */
class TestUri : Uri() {
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = "content"
    override fun getSchemeSpecificPart(): String = "//siapel/test/doc1"
    override fun getEncodedSchemeSpecificPart(): String = "//siapel/test/doc1"
    override fun getAuthority(): String = "siapel"
    override fun getEncodedAuthority(): String = "siapel"
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String = "siapel"
    override fun getPort(): Int = -1
    override fun getPath(): String = "/test/doc1"
    override fun getEncodedPath(): String = "/test/doc1"
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = listOf("test", "doc1")
    override fun getLastPathSegment(): String = "doc1"
    override fun toString(): String = "content://siapel/test/doc1"
    override fun compareTo(other: Uri?): Int = 0
    override fun buildUpon(): Builder? = null
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}
}
