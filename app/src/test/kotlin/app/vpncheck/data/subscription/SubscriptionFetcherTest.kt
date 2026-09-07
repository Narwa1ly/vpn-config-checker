package app.vpncheck.data.subscription

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubscriptionFetcherTest {
    private lateinit var server: MockWebServer
    private val src = SubscriptionSource.ALL.first()
    private val good = "# profile-title: T\n# Количество: 2\n\nvless://u@h.com:443?type=tcp#a\nvmess://eyJhZGQiOiJ4In0=\n"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun fetcher(vararg paths: String) =
        SubscriptionFetcher(urlsFor = { paths.map { server.url(it).toString() } })

    private fun dispatcher(map: Map<String, MockResponse>) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            map[request.path] ?: MockResponse().setResponseCode(404)
    }

    @Test
    fun `first valid mirror wins and failures are described`() = runBlocking {
        server.dispatcher = dispatcher(
            mapOf(
                "/a" to MockResponse().setResponseCode(500),
                "/b" to MockResponse().setBody(good),
            )
        )
        val r = fetcher("/a", "/b").fetch(src)
        assertTrue(r.ok)
        assertEquals(2, r.links.size)
        assertTrue(r.usedUrl!!.endsWith("/b"))
        assertTrue(r.mirrorErrors.all { it.contains("HTTP 500") })
    }

    @Test
    fun `all mirrors failing yields error with per-mirror details`() = runBlocking {
        server.dispatcher = dispatcher(
            mapOf(
                "/a" to MockResponse().setResponseCode(403),
                "/b" to MockResponse().setResponseCode(502),
            )
        )
        val r = fetcher("/a", "/b").fetch(src)
        assertFalse(r.ok)
        assertEquals(2, r.mirrorErrors.size)
        assertTrue(r.error!!.contains("2 зеркал"))
        assertTrue(r.mirrorErrors.any { it.contains("HTTP 403") } && r.mirrorErrors.any { it.contains("HTTP 502") })
    }

    @Test
    fun `html block page is a failure but header-only subscription is an empty success`() = runBlocking {
        server.dispatcher = dispatcher(
            mapOf(
                "/html" to MockResponse().setBody("<html><body>Access blocked by provider</body></html>"),
                "/empty" to MockResponse().setBody("# profile-title: 🏳️ WHITE LISTS | SNI-RU\n# Количество: 0\n"),
            )
        )
        val blocked = fetcher("/html").fetch(src)
        assertFalse(blocked.ok)
        assertTrue(blocked.mirrorErrors.single().contains("нет конфигов"))

        val empty = fetcher("/empty").fetch(src)
        assertTrue(empty.ok)
        assertTrue(empty.links.isEmpty())
        assertTrue(empty.usedUrl!!.endsWith("/empty"))
    }

    @Test
    fun `no mirrors is an error`() = runBlocking {
        val r = SubscriptionFetcher(urlsFor = { emptyList() }).fetch(src)
        assertFalse(r.ok)
    }
}
