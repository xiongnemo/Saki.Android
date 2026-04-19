package com.anzupop.saki.android.data.repository

import com.anzupop.saki.android.data.remote.subsonic.EndpointFailure
import com.anzupop.saki.android.data.remote.EndpointSelector
import com.anzupop.saki.android.data.remote.subsonic.EndpointFallbackException
import com.anzupop.saki.android.data.remote.subsonic.NoEndpointsConfiguredException
import com.anzupop.saki.android.data.remote.subsonic.NoSuchServerException
import com.anzupop.saki.android.data.remote.subsonic.SubsonicApiService
import com.anzupop.saki.android.data.remote.subsonic.SubsonicApiException
import com.anzupop.saki.android.data.remote.subsonic.SubsonicAuth
import com.anzupop.saki.android.data.remote.subsonic.SubsonicHttpException
import com.anzupop.saki.android.data.remote.subsonic.SubsonicResponseDto
import com.anzupop.saki.android.data.remote.subsonic.toArtist
import com.anzupop.saki.android.data.remote.subsonic.toAlbum
import com.anzupop.saki.android.data.remote.subsonic.toAlbumSummaries
import com.anzupop.saki.android.data.remote.subsonic.toLibraryIndexes
import com.anzupop.saki.android.data.remote.subsonic.toLyrics
import com.anzupop.saki.android.data.remote.subsonic.toMusicFolders
import com.anzupop.saki.android.data.remote.subsonic.toPingResult
import com.anzupop.saki.android.data.remote.subsonic.toPlaylist
import com.anzupop.saki.android.data.remote.subsonic.toPlaylistSummaries
import com.anzupop.saki.android.data.remote.subsonic.toSavedPlayQueue
import com.anzupop.saki.android.data.remote.subsonic.toSearchResults
import com.anzupop.saki.android.data.remote.subsonic.toSong
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.Album
import com.anzupop.saki.android.domain.model.AlbumListType
import com.anzupop.saki.android.domain.model.AlbumSummary
import com.anzupop.saki.android.domain.model.Artist
import com.anzupop.saki.android.domain.model.LibraryIndexes
import com.anzupop.saki.android.domain.model.MusicFolder
import com.anzupop.saki.android.domain.model.PingResult
import com.anzupop.saki.android.domain.model.Playlist
import com.anzupop.saki.android.domain.model.PlaylistSummary
import com.anzupop.saki.android.domain.model.SavedPlayQueue
import com.anzupop.saki.android.domain.model.SearchResults
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.ServerEndpoint
import com.anzupop.saki.android.domain.model.Song
import com.anzupop.saki.android.domain.model.SongLyrics
import com.anzupop.saki.android.domain.model.SubsonicCallResult
import com.anzupop.saki.android.domain.model.SubsonicCoverArtRequest
import com.anzupop.saki.android.domain.model.SubsonicDownloadRequest
import com.anzupop.saki.android.domain.model.SubsonicStreamCandidate
import com.anzupop.saki.android.domain.model.SubsonicStreamRequest
import com.anzupop.saki.android.domain.repository.ServerConfigRepository
import com.anzupop.saki.android.domain.repository.SubsonicRepository
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class DefaultSubsonicRepository @Inject constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val subsonicApiService: SubsonicApiService,
    private val endpointSelector: EndpointSelector,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SubsonicRepository {
    override suspend fun ping(serverId: Long): SubsonicCallResult<PingResult> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "ping.view",
        ) { response ->
            response.toPingResult()
        }
    }

    override suspend fun getMusicFolders(serverId: Long): SubsonicCallResult<List<MusicFolder>> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "getMusicFolders.view",
        ) { response ->
            response.toMusicFolders()
        }
    }

    override suspend fun getIndexes(
        serverId: Long,
        musicFolderId: String?,
        ifModifiedSince: Long?,
    ): SubsonicCallResult<LibraryIndexes> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "getIndexes.view",
            extraQuery = mapOfNotNull(
                "musicFolderId" to musicFolderId,
                "ifModifiedSince" to ifModifiedSince?.toString(),
            ),
        ) { response ->
            response.toLibraryIndexes()
        }
    }

    override suspend fun getAlbumList(
        serverId: Long,
        type: AlbumListType,
        size: Int,
        offset: Int,
        fromYear: Int?,
        toYear: Int?,
        genre: String?,
        musicFolderId: String?,
    ): SubsonicCallResult<List<AlbumSummary>> = withContext(ioDispatcher) {
        validateAlbumListRequest(
            type = type,
            fromYear = fromYear,
            toYear = toYear,
            genre = genre,
        )

        executeWithFallback(
            serverId = serverId,
            path = "getAlbumList.view",
            extraQuery = mapOfNotNull(
                "type" to type.apiValue,
                "size" to size.toString(),
                "offset" to offset.toString(),
                "fromYear" to fromYear?.toString(),
                "toYear" to toYear?.toString(),
                "genre" to genre,
                "musicFolderId" to musicFolderId,
            ),
        ) { response ->
            response.toAlbumSummaries()
        }
    }

    override suspend fun getAlbum(
        serverId: Long,
        albumId: String,
    ): SubsonicCallResult<Album> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "getAlbum.view",
            extraQuery = mapOf("id" to albumId),
        ) { response ->
            response.toAlbum()
        }
    }

    override suspend fun getArtist(
        serverId: Long,
        artistId: String,
    ): SubsonicCallResult<Artist> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "getArtist.view",
            extraQuery = mapOf("id" to artistId),
        ) { response ->
            response.toArtist()
        }
    }

    override suspend fun getSong(
        serverId: Long,
        songId: String,
    ): SubsonicCallResult<Song> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "getSong.view",
            extraQuery = mapOf("id" to songId),
        ) { response ->
            response.toSong()
        }
    }

    override suspend fun getPlaylists(
        serverId: Long,
        username: String?,
    ): SubsonicCallResult<List<PlaylistSummary>> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "getPlaylists.view",
            extraQuery = mapOfNotNull("username" to username),
        ) { response ->
            response.toPlaylistSummaries()
        }
    }

    override suspend fun getPlaylist(
        serverId: Long,
        playlistId: String,
    ): SubsonicCallResult<Playlist> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "getPlaylist.view",
            extraQuery = mapOf("id" to playlistId),
        ) { response ->
            response.toPlaylist()
        }
    }

    override suspend fun search(
        serverId: Long,
        query: String,
        artistCount: Int,
        artistOffset: Int,
        albumCount: Int,
        albumOffset: Int,
        songCount: Int,
        songOffset: Int,
        musicFolderId: String?,
    ): SubsonicCallResult<SearchResults> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "search3.view",
            extraQuery = mapOfNotNull(
                "query" to query,
                "artistCount" to artistCount.toString(),
                "artistOffset" to artistOffset.toString(),
                "albumCount" to albumCount.toString(),
                "albumOffset" to albumOffset.toString(),
                "songCount" to songCount.toString(),
                "songOffset" to songOffset.toString(),
                "musicFolderId" to musicFolderId,
            ),
        ) { response ->
            response.toSearchResults()
        }
    }

    override suspend fun buildStreamRequest(
        serverId: Long,
        songId: String,
        maxBitRate: Int?,
        format: String?,
    ): SubsonicStreamRequest = withContext(ioDispatcher) {
        SubsonicStreamRequest(
            songId = songId,
            candidates = buildAuthenticatedCandidates(
                serverId = serverId,
                path = "stream.view",
                extraQuery = mapOfNotNull(
                    "id" to songId,
                    "maxBitRate" to maxBitRate?.toString(),
                    "format" to format,
                ),
            ),
        )
    }

    override suspend fun buildDownloadRequest(
        serverId: Long,
        songId: String,
    ): SubsonicDownloadRequest = withContext(ioDispatcher) {
        SubsonicDownloadRequest(
            songId = songId,
            candidates = buildAuthenticatedCandidates(
                serverId = serverId,
                path = "download.view",
                extraQuery = mapOf("id" to songId),
            ),
        )
    }

    override suspend fun buildCoverArtRequest(
        serverId: Long,
        coverArtId: String,
        size: Int?,
    ): SubsonicCoverArtRequest = withContext(ioDispatcher) {
        SubsonicCoverArtRequest(
            coverArtId = coverArtId,
            candidates = buildAuthenticatedCandidates(
                serverId = serverId,
                path = "getCoverArt.view",
                extraQuery = mapOfNotNull(
                    "id" to coverArtId,
                    "size" to size?.toString(),
                ),
            ),
        )
    }

    override suspend fun getLyrics(
        serverId: Long,
        songId: String,
    ): SubsonicCallResult<SongLyrics?> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "getLyricsBySongId.view",
            extraQuery = mapOf("id" to songId),
        ) { response ->
            response.toLyrics()
        }
    }

    override suspend fun getPlayQueue(
        serverId: Long,
    ): SubsonicCallResult<SavedPlayQueue> = withContext(ioDispatcher) {
        executeWithFallback(
            serverId = serverId,
            path = "getPlayQueue.view",
        ) { response ->
            response.toSavedPlayQueue()
        }
    }

    override suspend fun savePlayQueue(
        serverId: Long,
        songIds: List<String>,
        currentSongId: String?,
        positionMs: Long,
    ): SubsonicCallResult<Unit> = withContext(ioDispatcher) {
        val query = mutableMapOf<String, String>()
        if (currentSongId != null) query["current"] = currentSongId
        query["position"] = positionMs.toString()
        executeWithFallback(
            serverId = serverId,
            path = "savePlayQueue.view",
            extraQuery = query,
            extraQueryLists = mapOf("id" to songIds),
        ) { }
    }

    private suspend fun <T> executeWithFallback(
        serverId: Long,
        path: String,
        extraQuery: Map<String, String> = emptyMap(),
        extraQueryLists: Map<String, List<String>> = emptyMap(),
        transform: (SubsonicResponseDto) -> T,
    ): SubsonicCallResult<T> {
        val server = serverConfigRepository.getServerConfig(serverId)
            ?: throw NoSuchServerException(serverId)
        val endpoints = orderedEndpoints(server)
        if (endpoints.isEmpty()) throw NoEndpointsConfiguredException(serverId)

        val authQuery = SubsonicAuth.baseQuery(server)
        val failures = mutableListOf<EndpointFailure>()

        for (endpoint in endpoints) {
            val requestUrl = endpoint.buildRestUrl(path)
            val query = authQuery + extraQuery

            try {
                val response = performRequest(
                    endpoint = endpoint,
                    url = requestUrl,
                    query = query,
                    queryLists = extraQueryLists,
                )
                return SubsonicCallResult(
                    endpoint = endpoint,
                    data = transform(response),
                )
            } catch (exception: SubsonicApiException) {
                throw exception
            } catch (exception: SubsonicHttpException) {
                throw exception
            } catch (exception: IOException) {
                if (!exception.shouldFallback()) {
                    throw exception
                }
                failures += EndpointFailure(endpoint, exception)
                endpointSelector.invalidate(serverId, endpoint.id)
            } catch (exception: IllegalStateException) {
                throw SubsonicApiException(
                    endpoint = endpoint,
                    code = null,
                    message = exception.message ?: "Malformed Subsonic response.",
                )
            }
        }

        throw EndpointFallbackException(failures)
    }

    private suspend fun performRequest(
        endpoint: ServerEndpoint,
        url: String,
        query: Map<String, String>,
        queryLists: Map<String, List<String>> = emptyMap(),
    ): SubsonicResponseDto {
        val finalUrl = if (queryLists.isEmpty()) {
            url
        } else {
            val builder = url.toHttpUrlOrNull()?.newBuilder() ?: error("Invalid URL: $url")
            queryLists.forEach { (key, values) ->
                values.forEach { value -> builder.addQueryParameter(key, value) }
            }
            builder.build().toString()
        }
        val httpResponse = subsonicApiService.get(
            url = finalUrl,
            query = query,
        )

        if (!httpResponse.isSuccessful) {
            throw SubsonicHttpException(
                endpoint = endpoint,
                statusCode = httpResponse.code(),
                message = "HTTP ${httpResponse.code()} from ${endpoint.baseUrl}",
            )
        }

        val envelope = httpResponse.body()
            ?: throw IOException("Empty response from ${endpoint.baseUrl}")

        val response = envelope.response
        if (response.status != "ok") {
            throw SubsonicApiException(
                endpoint = endpoint,
                code = response.error?.code,
                message = response.error?.message ?: "Subsonic request failed.",
            )
        }

        return response
    }

    private suspend fun buildAuthenticatedCandidates(
        serverId: Long,
        path: String,
        extraQuery: Map<String, String>,
    ): List<SubsonicStreamCandidate> {
        val server = serverConfigRepository.getServerConfig(serverId)
            ?: throw NoSuchServerException(serverId)
        val endpoints = orderedEndpoints(server)
        if (endpoints.isEmpty()) throw NoEndpointsConfiguredException(serverId)
        val query = SubsonicAuth.baseQuery(server) + extraQuery

        return endpoints.map { endpoint ->
            SubsonicStreamCandidate(
                endpoint = endpoint,
                url = endpoint.buildRestUrl(
                    path = path,
                    query = query,
                ),
            )
        }
    }

    private fun orderedEndpoints(server: ServerConfig): List<ServerEndpoint> {
        endpointSelector.registerEndpoints(server.id, server.endpoints)
        return endpointSelector.sortedEndpoints(
            serverId = server.id,
            endpoints = server.endpoints.sortedWith(
                compareByDescending<ServerEndpoint> { it.isPrimary }
                    .thenBy(ServerEndpoint::order),
            ),
        )
    }

    private fun validateAlbumListRequest(
        type: AlbumListType,
        fromYear: Int?,
        toYear: Int?,
        genre: String?,
    ) {
        when (type) {
            AlbumListType.BY_YEAR -> {
                require(fromYear != null && toYear != null) {
                    "`fromYear` and `toYear` are required for AlbumListType.BY_YEAR."
                }
            }

            AlbumListType.BY_GENRE -> {
                require(!genre.isNullOrBlank()) {
                    "`genre` is required for AlbumListType.BY_GENRE."
                }
            }

            else -> Unit
        }
    }
}

private fun IOException.shouldFallback(): Boolean {
    return this is UnknownHostException ||
        this is ConnectException ||
        this is SocketTimeoutException ||
        this is NoRouteToHostException
}

private fun ServerEndpoint.buildRestUrl(
    path: String,
    query: Map<String, String> = emptyMap(),
): String {
    val baseUrl = requireNotNull(baseUrl.toHttpUrlOrNull()) {
        "Invalid endpoint URL: $baseUrl"
    }

    val builder = baseUrl.newBuilder()
        .addPathSegments("rest/$path")

    query.forEach { (key, value) ->
        builder.addQueryParameter(key, value)
    }

    return builder.build().toString()
}

private fun mapOfNotNull(vararg entries: Pair<String, String?>): Map<String, String> {
    return buildMap {
        entries.forEach { (key, value) ->
            if (value != null) {
                put(key, value)
            }
        }
    }
}
