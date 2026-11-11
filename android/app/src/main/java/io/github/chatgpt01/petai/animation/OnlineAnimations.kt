package io.github.chatgpt01.petai.animation

import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class OnlineAnimation(val id: String, val name: String, val nameEn: String, val version: Int, val bytes: Long, val sha256: String, val url: String)

object OnlineAnimationCatalog {
    const val BASE = "https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.3/"
    const val INDEX_URL = BASE + "animation-packs-index.json"
    fun parse(bytes: ByteArray): List<OnlineAnimation> {
        require(bytes.size <= 1024 * 1024)
        val items = JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonArray
        require(items.size() in 1..200)
        return items.map { element ->
            val o = element.asJsonObject
            fun str(key: String): String { val p = o[key].asJsonPrimitive; require(p.isString); return p.asString }
            fun num(key: String): Long { val p = o[key].asJsonPrimitive; require(p.isNumber); return p.asBigDecimal.longValueExact() }
            val id = str("id"); val file = str("file"); val url = str("url")
            require(id.matches(Regex("ext_[a-f0-9]{24}")) && file == "$id.petanim" && url == BASE + file)
            val name = str("name"); val english = str("name_en"); val size = num("bytes"); val version = num("version")
            require(name.length in 1..100 && english.length in 1..150 && version in 1..1000000 && size in 1..300L * 1024 * 1024)
            val sha = str("sha256"); require(sha.matches(Regex("[a-f0-9]{64}")))
            OnlineAnimation(id, name, english, version.toInt(), size, sha, url)
        }.also { list -> require(list.map { it.id }.distinct().size == list.size) }
    }
    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Public resource client: never shares API model credentials/cookies or permits cleartext redirects. */
class AnimationDownloader(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false).connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()) {
    @Volatile private var active: Call? = null
    fun cancel() { active?.cancel() }
    private fun response(url: String, check: () -> Unit): okhttp3.Response {
        var next = url
        repeat(6) {
            check()
            val parsed = next.toHttpUrl()
            require(parsed.isHttps && parsed.host in setOf("github.com", "release-assets.githubusercontent.com") &&
                parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.port == 443)
            val call = client.newCall(Request.Builder().url(parsed).build()); active = call
            val result = call.execute()
            if (result.code in setOf(301,302,303,307,308)) {
                val location = result.header("Location")
                val target = location?.let { parsed.resolve(it) }
                result.close()
                next = target?.toString() ?: throw IOException("Invalid redirect")
            } else {
                if (!result.isSuccessful) { result.close(); throw IOException("Download failed") }
                return result
            }
        }
        throw IOException("Too many redirects")
    }
    fun catalog(expectedHash: String, check: () -> Unit): List<OnlineAnimation> {
        val bytes = response(OnlineAnimationCatalog.INDEX_URL, check).use { result ->
            val input = result.body?.byteStream() ?: throw IOException("Empty body")
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            while (true) { check(); val n = input.read(buffer); if (n < 0) break; require(out.size()+n <= 1024*1024); out.write(buffer,0,n) }
            out.toByteArray()
        }
        check(); require(OnlineAnimationCatalog.sha(bytes) == expectedHash)
        return OnlineAnimationCatalog.parse(bytes)
    }
    fun download(item: OnlineAnimation, target: File, check: () -> Unit, progress: (Long) -> Unit) {
        require(item.url == OnlineAnimationCatalog.BASE + item.id + ".petanim")
        try {
            response(item.url, check).use { response ->
                val body = response.body ?: throw IOException("Empty body")
                require(body.contentLength() < 0 || body.contentLength() == item.bytes)
                require(target.parentFile!!.usableSpace >= item.bytes + 32 * 1024 * 1024)
                val sha = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                body.byteStream().use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        check(); val n = input.read(buffer); if (n < 0) break
                        bytes += n; require(bytes <= item.bytes)
                        sha.update(buffer, 0, n); output.write(buffer, 0, n); progress(bytes)
                    }
                } }
                check()
                require(bytes == item.bytes && sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == item.sha256)
            }
        } catch (e: Exception) { target.delete(); throw e }
    }
}
