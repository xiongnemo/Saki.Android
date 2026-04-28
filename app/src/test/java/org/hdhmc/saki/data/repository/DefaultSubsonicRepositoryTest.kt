package org.hdhmc.saki.data.repository

import org.hdhmc.saki.data.remote.EndpointSelector
import org.hdhmc.saki.data.remote.subsonic.SubsonicApiException
import org.hdhmc.saki.data.remote.subsonic.SubsonicApiService
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.DEFAULT_SUBSONIC_API_VERSION
import org.hdhmc.saki.domain.model.DEFAULT_SUBSONIC_CLIENT
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.ServerEndpoint
import org.hdhmc.saki.domain.repository.ServerConfigRepository
import java.net.ServerSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class DefaultSubsonicRepositoryTest {
    private lateinit var primaryServer: MockWebServer
    private lateinit var secondaryServer: MockWebServer
    private lateinit var repository: DefaultSubsonicRepository

    @Before
    fun setUp() {
        primaryServer = MockWebServer()
        secondaryServer = MockWebServer()
        primaryServer.start()
        secondaryServer.start()

        repository = DefaultSubsonicRepository(
            serverConfigRepository = FakeServerConfigRepository(),
            subsonicApiService = createApiService(),
            endpointSelector = EndpointSelector(
                context = android.content.ContextWrapper(null),
                okHttpClient = okhttp3.OkHttpClient(),
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            ),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        primaryServer.shutdown()
        secondaryServer.shutdown()
    }

    @Test
    fun `ping falls back to secondary endpoint when primary connection is refused`() = runTest {
        secondaryServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "subsonic-response": {
                        "status": "ok",
                        "version": "1.16.1",
                        "type": "navidrome",
                        "serverVersion": "0.61.1",
                        "openSubsonic": true
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.ping(serverId = FALLBACK_SERVER_ID)

        assertEquals(secondaryBaseUrl(), result.endpoint.baseUrl)
        assertEquals("1.16.1", result.data.apiVersion)
        assertEquals("navidrome", result.data.serverType)
        assertEquals("0.61.1", result.data.serverVersion)
        assertEquals(1, secondaryServer.requestCount)

        val request = secondaryServer.takeRequest()
        val url = request.requestUrl
        assertNotNull(url)
        assertEquals("/rest/ping.view", url?.encodedPath)
        assertEquals("nemo", url?.queryParameter("u"))
        assertEquals(DEFAULT_SUBSONIC_API_VERSION, url?.queryParameter("v"))
        assertEquals(DEFAULT_SUBSONIC_CLIENT, url?.queryParameter("c"))
        assertFalse(url?.queryParameter("t").isNullOrBlank())
        assertFalse(url?.queryParameter("s").isNullOrBlank())
    }

    @Test
    fun `ping does not fall back when server returns Subsonic auth error`() = runTest {
        primaryServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "subsonic-response": {
                        "status": "failed",
                        "version": "1.16.1",
                        "error": {
                          "code": 40,
                          "message": "Wrong username or password"
                        }
                      }
                    }
                    """.trimIndent(),
                ),
        )

        try {
            repository.ping(serverId = PRIMARY_ONLY_SERVER_ID)
            fail("Expected SubsonicApiException")
        } catch (exception: SubsonicApiException) {
            assertEquals(40, exception.code)
            assertEquals(primaryBaseUrl(), exception.endpoint.baseUrl)
        }

        assertEquals(1, primaryServer.requestCount)
        assertEquals(0, secondaryServer.requestCount)
    }

    @Test
    fun `buildStreamRequest returns candidate URLs in endpoint priority order`() = runTest {
        val request = repository.buildStreamRequest(
            serverId = PRIMARY_ONLY_SERVER_ID,
            songId = "song-42",
            maxBitRate = 320,
            format = "raw",
        )

        assertEquals("song-42", request.songId)
        assertEquals(2, request.candidates.size)
        assertEquals(primaryBaseUrl(), request.candidates.first().endpoint.baseUrl)
        assertTrue(request.candidates.first().url.contains("/rest/stream.view"))
        assertTrue(request.candidates.first().url.contains("id=song-42"))
        assertTrue(request.candidates.first().url.contains("maxBitRate=320"))
        assertTrue(request.candidates.first().url.contains("format=raw"))
        assertTrue(request.candidates.first().url.contains("u=nemo"))
    }

    @Test
    fun `search3 returns grouped artist album and song results`() = runTest {
        primaryServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "subsonic-response": {
                        "status": "ok",
                        "version": "1.16.1",
                        "searchResult3": {
                          "artist": [
                            {
                              "id": "artist-1",
                              "name": "Portishead",
                              "albumCount": 3
                            }
                          ],
                          "album": [
                            {
                              "id": "album-1",
                              "name": "Dummy",
                              "artist": "Portishead",
                              "artistId": "artist-1",
                              "songCount": 10,
                              "year": 1994
                            }
                          ],
                          "song": [
                            {
                              "id": "song-1",
                              "title": "Roads",
                              "album": "Dummy",
                              "albumId": "album-1",
                              "artist": "Portishead",
                              "artistId": "artist-1",
                              "bitRate": 320,
                              "samplingRate": 44100
                            }
                          ]
                        }
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.search(
            serverId = PRIMARY_ONLY_SERVER_ID,
            query = "roads",
            artistCount = 5,
            albumCount = 6,
            songCount = 7,
        )

        assertEquals(primaryBaseUrl(), result.endpoint.baseUrl)
        assertEquals(1, result.data.artists.size)
        assertEquals(1, result.data.albums.size)
        assertEquals(1, result.data.songs.size)
        assertEquals("Portishead", result.data.artists.first().name)
        assertEquals("Dummy", result.data.albums.first().name)
        assertEquals("Roads", result.data.songs.first().title)
        assertEquals(44_100, result.data.songs.first().sampleRate)

        val request = primaryServer.takeRequest()
        val url = request.requestUrl
        assertEquals("/rest/search3.view", url?.encodedPath)
        assertEquals("roads", url?.queryParameter("query"))
        assertEquals("5", url?.queryParameter("artistCount"))
        assertEquals("6", url?.queryParameter("albumCount"))
        assertEquals("7", url?.queryParameter("songCount"))
    }

    @Test
    fun `getAlbumList2 sends pagination parameters`() = runTest {
        primaryServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "subsonic-response": {
                        "status": "ok",
                        "version": "1.16.1",
                        "albumList2": {
                          "album": [
                            {
                              "id": "album-1",
                              "name": "Dummy",
                              "artist": "Portishead",
                              "artistId": "artist-1",
                              "songCount": 10
                            }
                          ]
                        }
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.getAlbumList(
            serverId = PRIMARY_ONLY_SERVER_ID,
            type = AlbumListType.NEWEST,
            size = 36,
            offset = 72,
        )

        assertEquals(primaryBaseUrl(), result.endpoint.baseUrl)
        assertEquals(1, result.data.size)
        assertEquals("Dummy", result.data.first().name)

        val request = primaryServer.takeRequest()
        val url = request.requestUrl
        assertEquals("/rest/getAlbumList2.view", url?.encodedPath)
        assertEquals("newest", url?.queryParameter("type"))
        assertEquals("36", url?.queryParameter("size"))
        assertEquals("72", url?.queryParameter("offset"))
    }

    private fun createApiService(): SubsonicApiService {
        return Retrofit.Builder()
            .baseUrl("https://localhost/")
            .client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SubsonicApiService::class.java)
    }

    private fun primaryBaseUrl(): String = primaryServer.url("/").toString().trimEnd('/')

    private fun secondaryBaseUrl(): String = secondaryServer.url("/").toString().trimEnd('/')

    private inner class FakeServerConfigRepository : ServerConfigRepository {
        private val configs = mutableMapOf(
            PRIMARY_ONLY_SERVER_ID to ServerConfig(
                id = PRIMARY_ONLY_SERVER_ID,
                name = "Home",
                username = "nemo",
                password = "secret",
                endpoints = listOf(
                    ServerEndpoint(
                        id = 1,
                        label = "Primary",
                        baseUrl = primaryBaseUrl(),
                        order = 0,
                    ),
                    ServerEndpoint(
                        id = 2,
                        label = "Secondary",
                        baseUrl = secondaryBaseUrl(),
                        order = 1,
                    ),
                ),
            ),
            FALLBACK_SERVER_ID to ServerConfig(
                id = FALLBACK_SERVER_ID,
                name = "Fallback",
                username = "nemo",
                password = "secret",
                endpoints = listOf(
                    ServerEndpoint(
                        id = 3,
                        label = "Unavailable",
                        baseUrl = unavailableBaseUrl(),
                        order = 0,
                    ),
                    ServerEndpoint(
                        id = 4,
                        label = "Secondary",
                        baseUrl = secondaryBaseUrl(),
                        order = 1,
                    ),
                ),
            ),
        )

        override fun observeServerConfigs(): Flow<List<ServerConfig>> = flowOf(configs.values.toList())

        override suspend fun getServerConfig(serverId: Long): ServerConfig? = configs[serverId]

        override suspend fun saveServerConfig(config: ServerConfig): Long {
            configs[config.id] = config
            return config.id
        }

        override suspend fun deleteServerConfig(serverId: Long) {
            configs.remove(serverId)
        }

        private fun unavailableBaseUrl(): String {
            val port = ServerSocket(0).use { it.localPort }
            return "http://127.0.0.1:$port"
        }
    }

    private companion object {
        const val PRIMARY_ONLY_SERVER_ID = 1L
        const val FALLBACK_SERVER_ID = 2L
    }
}
