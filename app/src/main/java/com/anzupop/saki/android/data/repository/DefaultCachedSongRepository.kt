package com.anzupop.saki.android.data.repository

import android.content.Context
import com.anzupop.saki.android.data.local.dao.CachedSongDao
import com.anzupop.saki.android.data.local.entity.CachedSongEntity
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.AuthenticatedUrlCandidate
import com.anzupop.saki.android.domain.model.CacheStorageSummary
import com.anzupop.saki.android.domain.model.CachedSong
import com.anzupop.saki.android.domain.model.Song
import com.anzupop.saki.android.domain.model.StreamQuality
import com.anzupop.saki.android.domain.repository.CachedSongRepository
import com.anzupop.saki.android.domain.repository.PlaybackPreferencesRepository
import com.anzupop.saki.android.domain.repository.SubsonicRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class DefaultCachedSongRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val cachedSongDao: CachedSongDao,
    private val okHttpClient: OkHttpClient,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val subsonicRepository: SubsonicRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CachedSongRepository {
    override fun observeCachedSongs(): Flow<List<CachedSong>> {
        return cachedSongDao.observeCachedSongs()
            .map { songs -> songs.map(CachedSongEntity::toDomain) }
    }

    override suspend fun getCachedSong(
        serverId: Long,
        songId: String,
    ): CachedSong? = withContext(ioDispatcher) {
        cachedSongDao.getCachedSong(serverId, songId)?.toDomain()
    }

    override suspend fun cacheSong(
        serverId: Long,
        song: Song,
    ): CachedSong = withContext(ioDispatcher) {
        val quality = playbackPreferencesRepository.getPreferences().streamQuality
        val existing = cachedSongDao.getCachedSong(serverId, song.id)
        if (
            existing != null &&
            existing.qualityKey == quality.storageKey &&
            File(existing.localPath).exists()
        ) {
            return@withContext existing.toDomain()
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

private fun CachedSongEntity.toDomain(): CachedSong {
    return CachedSong(
        cacheId = cacheId,
        serverId = serverId,
        songId = songId,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        coverArtId = coverArtId,
        coverArtPath = coverArtPath,
        localPath = localPath,
        durationSeconds = durationSeconds,
        track = track,
        discNumber = discNumber,
        suffix = suffix,
        contentType = contentType,
        quality = StreamQuality.fromStorageKey(qualityKey),
        fileSizeBytes = fileSizeBytes,
        downloadedAt = downloadedAt,
    )
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
