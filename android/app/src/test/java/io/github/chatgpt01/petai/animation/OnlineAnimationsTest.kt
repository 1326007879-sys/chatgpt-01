package io.github.chatgpt01.petai.animation

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

class OnlineAnimationsTest {
    private fun bundled() = File("src/main/res/raw/online_animations.json").readBytes()
    private fun client(body: ByteArray, location: String? = null, seen: MutableList<Request> = mutableListOf()) =
        OkHttpClient.Builder().addInterceptor { chain ->
            seen.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("test")
                .code(if (location == null) 200 else 302).apply { if (location != null) header("Location", location) }
                .body(body.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("online-packs").toFile()
        try { block(File(root,"download.part")) } finally { root.deleteRecursively() }
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }
    @Test fun bundledCatalogContainsExactlyPublishedPacksAndBoundedUrls() {
        val list = OnlineAnimationCatalog.parse(bundled())
        assertEquals(95,list.size); assertEquals(95,list.map { it.id }.distinct().size)
        assertEquals(1346820754L,list.sumOf { it.bytes })
        list.forEach { assertEquals(OnlineAnimationCatalog.BASE + it.id + ".petanim",it.url) }
        rejected { OnlineAnimationCatalog.parse(String(bundled()).replace("https://github.com/", "http://example.com/").toByteArray()) }
        rejected { OnlineAnimationCatalog.parse("[]".toByteArray()) }
    }
    @Test fun publicDownloaderChecksBytesAndHashWithoutCredentials() = fixture { target ->
        val bytes = "verified bytes".toByteArray()
        val seen = mutableListOf<Request>()
        val item = OnlineAnimationCatalog.parse(bundled()).first().copy(bytes = bytes.size.toLong(),sha256 = OnlineAnimationCatalog.sha(bytes))
        AnimationDownloader(client(bytes,seen = seen)).download(item,target,{}, {})
        assertArrayEquals(bytes,target.readBytes())
        assertNull(seen.single().header("Authorization")); assertNull(seen.single().header("Cookie"))
    }
    @Test fun corruptedOrTruncatedDownloadIsDeleted() = fixture { target ->
        val bytes = byteArrayOf(1,2,3)
        val item = OnlineAnimationCatalog.parse(bundled()).first().copy(bytes = 3)
        rejected { AnimationDownloader(client(bytes)).download(item,target,{}, {}) }
        assertFalse(target.exists())
        rejected { AnimationDownloader(client(bytes)).download(item.copy(bytes = 4),target,{}, {}) }
        assertFalse(target.exists())
    }
    @Test fun cancellationDeletesPartialFile() = fixture { target ->
        val bytes = ByteArray(100000) { 7 }
        val item = OnlineAnimationCatalog.parse(bundled()).first().copy(bytes = bytes.size.toLong(),sha256 = OnlineAnimationCatalog.sha(bytes))
        var progressed = false
        try {
            AnimationDownloader(client(bytes)).download(item,target,{ if (progressed) throw CancellationException() },{ progressed = true })
            fail()
        } catch (_: CancellationException) { }
        assertFalse(target.exists())
    }
    @Test fun redirectsCannotLeakToUnknownHostOrHttp() = fixture { target ->
        val item = OnlineAnimationCatalog.parse(bundled()).first()
        for (redirect in listOf("http://github.com/unsafe", "https://example.com/file", "https://user:pass@github.com/file")) {
            val seen = mutableListOf<Request>()
            rejected { AnimationDownloader(client(byteArrayOf(),redirect,seen)).download(item,target,{}, {}) }
            assertEquals(1,seen.size); assertFalse(target.exists())
        }
    }
    @Test fun refreshedCatalogMustMatchPinnedPublishedIndex() {
        val bytes = bundled()
        val downloader = AnimationDownloader(client(bytes))
        assertEquals(95,downloader.catalog(OnlineAnimationCatalog.sha(bytes)) {}.size)
        rejected { downloader.catalog("0".repeat(64)) {} }
    }
}
