package org.hdhmc.saki.data.repository

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshot
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.repository.LocalPlayQueueRepository

@Singleton
class FileLocalPlayQueueRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    moshi: Moshi,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LocalPlayQueueRepository {
    private val directory = File(context.filesDir, "play_queue_snapshots")
    private val adapter = moshi.adapter(LocalPlayQueueSnapshotDto::class.java)

    override suspend fun get(serverId: Long): LocalPlayQueueSnapshot? = withContext(ioDispatcher) {
        val file = snapshotFile(serverId)
        if (!file.isFile) return@withContext null
        runCatching {
            adapter.fromJson(file.readText())?.toDomain()
        }.getOrNull()
    }

    override suspend fun save(snapshot: LocalPlayQueueSnapshot): Unit = withContext(ioDispatcher) {
        if (snapshot.songs.isEmpty()) {
            snapshotFile(snapshot.serverId).delete()
            return@withContext
        }
        directory.mkdirs()
        val target = snapshotFile(snapshot.serverId)
        val temp = File(directory, "${target.name}.tmp")
        temp.writeText(adapter.toJson(snapshot.toDto()))
        if (target.exists() && !target.delete()) {
            temp.delete()
            error("Unable to replace local play queue snapshot for server ${snapshot.serverId}.")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Unable to write local play queue snapshot for server ${snapshot.serverId}.")
        }
    }

    override suspend fun clear(serverId: Long): Unit = withContext(ioDispatcher) {
        snapshotFile(serverId).delete()
    }

    private fun snapshotFile(serverId: Long): File = File(directory, "server_$serverId.json")
}

@JsonClass(generateAdapter = true)
internal data class LocalPlayQueueSnapshotDto(
    val serverId: Long,
    val songs: List<LocalPlayQueueSongDto>,
    val currentSongId: String?,
    val positionMs: Long,
    val updatedAt: Long,
)

@JsonClass(generateAdapter = true)
internal data class LocalPlayQueueSongDto(
    val id: String,
    val parentId: String?,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val coverArtId: String?,
    val durationSeconds: Int?,
    val track: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val bitRate: Int?,
    val sampleRate: Int?,
    val suffix: String?,
    val contentType: String?,
    val sizeBytes: Long?,
    val path: String?,
    val created: String?,
)

private fun LocalPlayQueueSnapshot.toDto() = LocalPlayQueueSnapshotDto(
    serverId = serverId,
    songs = songs.map(Song::toDto),
    currentSongId = currentSongId,
    positionMs = positionMs,
    updatedAt = updatedAt,
)

private fun LocalPlayQueueSnapshotDto.toDomain() = LocalPlayQueueSnapshot(
    serverId = serverId,
    songs = songs.map(LocalPlayQueueSongDto::toDomain),
    currentSongId = currentSongId,
    positionMs = positionMs,
    updatedAt = updatedAt,
)

private fun Song.toDto() = LocalPlayQueueSongDto(
    id = id,
    parentId = parentId,
    title = title,
    album = album,
    albumId = albumId,
    artist = artist,
    artistId = artistId,
    coverArtId = coverArtId,
    durationSeconds = durationSeconds,
    track = track,
    discNumber = discNumber,
    year = year,
    genre = genre,
    bitRate = bitRate,
    sampleRate = sampleRate,
    suffix = suffix,
    contentType = contentType,
    sizeBytes = sizeBytes,
    path = path,
    created = created,
)

private fun LocalPlayQueueSongDto.toDomain() = Song(
    id = id,
    parentId = parentId,
    title = title,
    album = album,
    albumId = albumId,
    artist = artist,
    artistId = artistId,
    coverArtId = coverArtId,
    durationSeconds = durationSeconds,
    track = track,
    discNumber = discNumber,
    year = year,
    genre = genre,
    bitRate = bitRate,
    sampleRate = sampleRate,
    suffix = suffix,
    contentType = contentType,
    sizeBytes = sizeBytes,
    path = path,
    created = created,
)
