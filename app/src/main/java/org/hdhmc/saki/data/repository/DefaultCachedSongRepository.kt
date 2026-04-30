package org.hdhmc.saki.data.repository

import android.content.Context
import org.hdhmc.saki.data.local.dao.CachedSongDao
import org.hdhmc.saki.data.local.dao.LibraryCacheDao
import org.hdhmc.saki.data.local.entity.CachedSongEntity
import org.hdhmc.saki.data.local.entity.CachedSongMetadataEntity
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.AuthenticatedUrlCandidate
import org.hdhmc.saki.domain.model.CacheStorageSummary
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.repository.CachedSongRepository
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import org.hdhmc.saki.domain.repository.SubsonicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URLConnection
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class DefaultCachedSongRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val cachedSongDao: CachedSongDao,
    private val libraryCacheDao: LibraryCacheDao,
    private val okHttpClient: OkHttpClient,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val subsonicRepository: SubsonicRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CachedSongRepository {
    override fun observeCachedSongs(): Flow<List<CachedSong>> {
        return combine(
            cachedSongDao.observeCachedSongs(),
            libraryCacheDao.observeSongMetadataInvalidations(),
        ) { songs, _ -> songs }
            .map { songs -> songs.toDomainWithMetadata(libraryCacheDao) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getCachedSong(
        serverId: Long,
        songId: String,
    ): CachedSong? = withContext(ioDispatcher) {
        cachedSongDao.getCachedSong(serverId, songId)
            ?.let { entity -> entity.toDomainWithMetadata(libraryCacheDao) }
    }

    override suspend fun getPlayableCachedSong(
        serverId: Long,
        songId: String,
        preferredQuality: StreamQuality,
    ): CachedSong? = withContext(ioDispatcher) {
        cachedSongDao.getCachedSong(serverId, songId)
            ?.let { entity -> entity.toDomainWithMetadata(libraryCacheDao) }
            ?.takeIf { song -> song.canPlayAt(preferredQuality) }
    }

    override suspend fun getPlayableCachedSongs(
        serverId: Long,
        preferredQuality: StreamQuality,
    ): Map<String, CachedSong> = withContext(ioDispatcher) {
        cachedSongDao.getCachedSongsForServer(serverId)
            .toDomainWithMetadata(libraryCacheDao)
            .asSequence()
            .filter { song -> song.canPlayAt(preferredQuality) }
            .associateBy(CachedSong::songId)
    }

    override suspend fun getPlayableCachedSongs(
        serverId: Long,
        songIds: List<String>,
        preferredQuality: StreamQuality,
    ): Map<String, CachedSong> = withContext(ioDispatcher) {
        if (songIds.isEmpty()) return@withContext emptyMap()
        songIds.distinct()
            .chunked(IN_CLAUSE_QUERY_CHUNK_SIZE)
            .flatMap { chunk -> cachedSongDao.getCachedSongs(serverId, chunk) }
            .toDomainWithMetadata(libraryCacheDao)
            .asSequence()
            .filter { song -> song.canPlayAt(preferredQuality) }
            .associateBy(CachedSong::songId)
    }

    override suspend fun cacheSong(
        serverId: Long,
        song: Song,
    ): CachedSong = withContext(ioDispatcher) {
        val quality = playbackPreferencesRepository.getPreferences().downloadQuality
        val existing = cachedSongDao.getCachedSong(serverId, song.id)
        if (
            existing != null &&
            existing.qualityKey == quality.storageKey &&
            File(existing.localPath).exists()
        ) {
            val enriched = existing.withPlaybackMetadataFrom(song, quality)
            if (enriched != existing) {
                cachedSongDao.upsertCachedSong(enriched)
            }
            return@withContext enriched.toDomain()
        }

        val audioDirectory = File(appContext.filesDir, "offline/audio/$serverId").apply {
            mkdirs()
        }
        val coverDirectory = File(appContext.filesDir, "offline/cover/$serverId").apply {
            mkdirs()
        }

        val audioDownload = downloadAudio(
            serverId = serverId,
            song = song,
            quality = quality,
            destinationDirectory = audioDirectory,
        )
        val coverArtPath = song.coverArtId?.let { coverArtId ->
            runCatching {
                downloadCoverArt(
                    serverId = serverId,
                    coverArtId = coverArtId,
                    destinationDirectory = coverDirectory,
                ).absolutePath
            }.getOrNull()
        }

        val entity = CachedSongEntity(
            cacheId = existing?.cacheId ?: "$serverId:${song.id}",
            serverId = serverId,
            songId = song.id,
            title = song.title,
            album = song.album,
            albumId = song.albumId,
            artist = song.artist,
            artistId = song.artistId,
            coverArtId = song.coverArtId,
            coverArtPath = coverArtPath,
            localPath = audioDownload.file.absolutePath,
            durationSeconds = song.durationSeconds,
            track = song.track,
            discNumber = song.discNumber,
            suffix = audioDownload.suffix ?: song.suffix,
            contentType = audioDownload.contentType ?: song.contentType,
            bitRate = song.cachedBitRateKbps(quality),
            sampleRate = song.sampleRate,
            qualityKey = quality.storageKey,
            fileSizeBytes = audioDownload.file.length(),
            downloadedAt = System.currentTimeMillis(),
        )

        existing?.let { stale ->
            if (stale.localPath != entity.localPath) {
                File(stale.localPath).delete()
            }
            if (stale.coverArtPath != null && stale.coverArtPath != entity.coverArtPath) {
                File(stale.coverArtPath).delete()
            }
        }

        cachedSongDao.upsertCachedSong(entity)
        entity.toDomain()
    }

    override suspend fun deleteCachedSong(cacheId: String): Unit = withContext(ioDispatcher) {
        cachedSongDao.getCachedSongById(cacheId)?.let { cachedSong ->
            File(cachedSong.localPath).delete()
            cachedSong.coverArtPath?.let { File(it).delete() }
        }
        cachedSongDao.deleteCachedSong(cacheId)
    }

    override suspend fun clearCachedSongs(serverId: Long?): Int = withContext(ioDispatcher) {
        val cachedSongs = cachedSongDao.getScopedCachedSongs(serverId)
        if (cachedSongs.isEmpty()) {
            return@withContext 0
        }

        cachedSongs
            .map(CachedSongEntity::localPath)
            .distinct()
            .forEach { path -> File(path).delete() }
        cachedSongs
            .mapNotNull(CachedSongEntity::coverArtPath)
            .distinct()
            .forEach { path -> File(path).delete() }

        if (serverId != null) {
            cachedSongDao.deleteCachedSongsForServer(serverId)
        } else {
            cachedSongDao.deleteAllCachedSongs()
        }

        cachedSongs.size
    }

    override suspend fun getCacheStorageSummary(serverId: Long?): CacheStorageSummary = withContext(ioDispatcher) {
        val cachedSongs = cachedSongDao.getScopedCachedSongs(serverId)
        val downloadedBytes = cachedSongs.sumOf { cachedSong ->
            File(cachedSong.localPath).length().takeIf { it > 0 } ?: cachedSong.fileSizeBytes
        } + cachedSongs
            .mapNotNull(CachedSongEntity::coverArtPath)
            .distinct()
            .sumOf { path -> File(path).length().takeIf { it > 0 } ?: 0L }

        CacheStorageSummary(
            downloadedSongCount = cachedSongs.size,
            downloadedBytes = downloadedBytes,
            streamCacheBytes = 0,
            hasStreamingCache = false,
        )
    }

    private suspend fun downloadAudio(
        serverId: Long,
        song: Song,
        quality: StreamQuality,
        destinationDirectory: File,
    ): DownloadedBinary {
        val candidates = if (quality.preferOriginalDownload) {
            subsonicRepository.buildDownloadRequest(
                serverId = serverId,
                songId = song.id,
            ).candidates
        } else {
            subsonicRepository.buildStreamRequest(
                serverId = serverId,
                songId = song.id,
                maxBitRate = quality.maxBitRate,
                format = quality.format,
            ).candidates
        }

        val requestedSuffix = when {
            quality.preferOriginalDownload -> song.suffix
            quality.format.isNullOrBlank() || quality.format == "raw" -> song.suffix
            else -> quality.format
        }

        return downloadBinary(
            candidates = candidates,
            destinationDirectory = destinationDirectory,
            destinationBaseName = buildCacheFileStem(song.title, song.id, quality.storageKey),
            preferredSuffix = requestedSuffix,
        )
    }

    private suspend fun downloadCoverArt(
        serverId: Long,
        coverArtId: String,
        destinationDirectory: File,
    ): File {
        val request = subsonicRepository.buildCoverArtRequest(
            serverId = serverId,
            coverArtId = coverArtId,
            size = 720,
        )
        return downloadBinary(
            candidates = request.candidates,
            destinationDirectory = destinationDirectory,
            destinationBaseName = buildCacheFileStem("cover", coverArtId, "art"),
            preferredSuffix = "jpg",
        ).file
    }

    private fun downloadBinary(
        candidates: List<AuthenticatedUrlCandidate>,
        destinationDirectory: File,
        destinationBaseName: String,
        preferredSuffix: String?,
    ): DownloadedBinary {
        var lastTransportFailure: IOException? = null

        for (candidate in candidates) {
            try {
                val request = Request.Builder()
                    .url(candidate.url)
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} from ${candidate.endpoint.baseUrl}")
                    }

                    val responseBody = response.body ?: throw IOException(
                        "Empty response from ${candidate.endpoint.baseUrl}",
                    )
                    val contentType = responseBody.contentType()?.toString()
                    val suffix = preferredSuffix
                        ?: responseBody.contentType()?.subtype
                        ?: URLConnection.guessContentTypeFromName(candidate.url)?.substringAfter('/')
                        ?: "bin"
                    val targetFile = File(destinationDirectory, "$destinationBaseName.${suffix.normalizeSuffix()}")
                    val tempFile = File(destinationDirectory, "$destinationBaseName.tmp")
                    responseBody.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    tempFile.renameTo(targetFile)

                    return DownloadedBinary(
                        file = targetFile,
                        contentType = contentType,
                        suffix = suffix.normalizeSuffix(),
                    )
                }
            } catch (exception: IOException) {
                if (!exception.shouldRetryNextEndpoint()) {
                    throw exception
                }
                lastTransportFailure = exception
            }
        }

        throw lastTransportFailure ?: IOException("No endpoint could provide the requested file.")
    }
}

private suspend fun CachedSongDao.getScopedCachedSongs(serverId: Long?): List<CachedSongEntity> {
    return if (serverId != null) {
        getCachedSongsForServer(serverId)
    } else {
        getCachedSongs()
    }
}

private data class DownloadedBinary(
    val file: File,
    val contentType: String?,
    val suffix: String?,
)

private data class CachedSongMetadataKey(
    val serverId: Long,
    val songId: String,
)

private suspend fun CachedSongEntity.toDomainWithMetadata(
    libraryCacheDao: LibraryCacheDao,
): CachedSong {
    val metadata = libraryCacheDao.getSongMetadata(serverId, listOf(songId)).firstOrNull()
    return toDomain(metadata)
}

private suspend fun List<CachedSongEntity>.toDomainWithMetadata(
    libraryCacheDao: LibraryCacheDao,
): List<CachedSong> {
    if (isEmpty()) return emptyList()

    val metadataByKey = groupBy(CachedSongEntity::serverId)
        .flatMap { (serverId, songs) ->
            songs.map(CachedSongEntity::songId)
                .distinct()
                .chunked(IN_CLAUSE_QUERY_CHUNK_SIZE)
                .flatMap { songIds ->
                    libraryCacheDao.getSongMetadata(serverId, songIds)
                        .map { metadata -> CachedSongMetadataKey(serverId, metadata.songId) to metadata }
                }
        }
        .toMap()

    return map { entity ->
        entity.toDomain(metadataByKey[CachedSongMetadataKey(entity.serverId, entity.songId)])
    }
}

private fun CachedSongEntity.toDomain(metadata: CachedSongMetadataEntity? = null): CachedSong {
    val quality = StreamQuality.fromStorageKey(qualityKey)
    return CachedSong(
        cacheId = cacheId,
        serverId = serverId,
        songId = songId,
        title = metadata?.title ?: title,
        album = metadata?.album ?: album,
        albumId = metadata?.albumId ?: albumId,
        artist = metadata?.artist ?: artist,
        artistId = metadata?.artistId ?: artistId,
        coverArtId = metadata?.coverArtId ?: coverArtId,
        coverArtPath = coverArtPath,
        localPath = localPath,
        durationSeconds = metadata?.durationSeconds ?: durationSeconds,
        track = metadata?.track ?: track,
        discNumber = metadata?.discNumber ?: discNumber,
        suffix = suffix ?: metadata?.suffix.takeIf { quality.preferOriginalDownload },
        contentType = contentType ?: metadata?.contentType.takeIf { quality.preferOriginalDownload },
        bitRateKbps = cachedDisplayBitRateKbps(
            storedBitRate = bitRate,
            sourceBitRate = metadata?.bitRate,
            quality = quality,
        ),
        sampleRate = sampleRate ?: metadata?.sampleRate,
        quality = quality,
        fileSizeBytes = fileSizeBytes,
        downloadedAt = downloadedAt,
    )
}

private fun CachedSong.canPlayAt(preferredQuality: StreamQuality): Boolean {
    return File(localPath).isFile && quality.isAtLeast(preferredQuality)
}

private fun Song.cachedBitRateKbps(quality: StreamQuality): Int? {
    val requestedMaxBitRate = quality.maxBitRate
        ?.takeIf { bitrate -> bitrate > 0 && !quality.preferOriginalDownload }
        ?: return bitRate?.takeIf { bitrate -> bitrate > 0 }
    return bitRate?.takeIf { bitrate -> bitrate > 0 }?.coerceAtMost(requestedMaxBitRate)
        ?: requestedMaxBitRate
}

private fun cachedDisplayBitRateKbps(
    storedBitRate: Int?,
    sourceBitRate: Int?,
    quality: StreamQuality,
): Int? {
    val requestedMaxBitRate = quality.maxBitRate
        ?.takeIf { bitrate -> bitrate > 0 && !quality.preferOriginalDownload }
        ?: return storedBitRate?.takeIf { bitrate -> bitrate > 0 }
            ?: sourceBitRate?.takeIf { bitrate -> bitrate > 0 }
    val knownSourceBitRate = sourceBitRate?.takeIf { bitrate -> bitrate > 0 }
    if (knownSourceBitRate != null && knownSourceBitRate <= requestedMaxBitRate) {
        return knownSourceBitRate
    }
    return storedBitRate?.takeIf { bitrate -> bitrate > 0 } ?: requestedMaxBitRate
}

private fun CachedSongEntity.withPlaybackMetadataFrom(
    song: Song,
    quality: StreamQuality,
): CachedSongEntity {
    return copy(
        bitRate = bitRate ?: song.cachedBitRateKbps(quality),
        sampleRate = sampleRate ?: song.sampleRate,
    )
}

private fun StreamQuality.isAtLeast(preferredQuality: StreamQuality): Boolean {
    return ordinal <= preferredQuality.ordinal
}

private fun buildCacheFileStem(
    title: String,
    uniqueId: String,
    variant: String,
): String {
    val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "track" }
    return "${safeTitle}_${uniqueId}_$variant"
}

private fun String.normalizeSuffix(): String {
    return trim()
        .trimStart('.')
        .lowercase()
        .ifBlank { "bin" }
}

private fun IOException.shouldRetryNextEndpoint(): Boolean {
    return this is UnknownHostException ||
        this is ConnectException ||
        this is SocketTimeoutException ||
        this is NoRouteToHostException
}

private const val IN_CLAUSE_QUERY_CHUNK_SIZE = 500
