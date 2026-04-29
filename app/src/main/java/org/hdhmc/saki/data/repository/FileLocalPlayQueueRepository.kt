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
class FileLocalPlayQueueRepository internal constructor(
    private val directory: File,
    moshi: Moshi,
    private val ioDispatcher: CoroutineDispatcher,
) : LocalPlayQueueRepository {
    private val adapter = moshi.adapter(LocalPlayQueueSnapshotDto::class.java)

    @Inject
    constructor(
        @ApplicationContext context: Context,
        moshi: Moshi,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        directory = File(context.filesDir, SNAPSHOT_DIRECTORY),
        moshi = moshi,
        ioDispatcher = ioDispatcher,
    )

    override suspend fun get(serverId: Long): LocalPlayQueueSnapshot? = withContext(ioDispatcher) {
        val file = readableSnapshotFile(serverId) ?: return@withContext null
        runCatching {
            adapter.fromJson(file.readText())?.toDomain()
        }.getOrNull()
    }

    override suspend fun save(snapshot: LocalPlayQueueSnapshot): Unit = withContext(ioDispatcher) {
        if (snapshot.songs.isEmpty()) {
            snapshotFile(snapshot.serverId).delete()
            snapshotBackupFile(snapshot.serverId).delete()
            return@withContext
        }
        directory.mkdirs()
        val target = snapshotFile(snapshot.serverId)
        writeSnapshotFile(
            target = target,
            backup = snapshotBackupFile(snapshot.serverId),
            json = adapter.toJson(snapshot.toDto()),
            serverId = snapshot.serverId,
        )
    }

    override suspend fun clear(serverId: Long): Unit = withContext(ioDispatcher) {
        snapshotFile(serverId).delete()
        snapshotBackupFile(serverId).delete()
    }

    private fun snapshotFile(serverId: Long): File = File(directory, "server_$serverId.json")

    private fun snapshotBackupFile(serverId: Long): File = File(directory, "server_$serverId.json.bak")

    private fun readableSnapshotFile(serverId: Long): File? {
        val target = snapshotFile(serverId)
        if (target.isFile) return target

        val backup = snapshotBackupFile(serverId)
        if (!backup.isFile) return null
        backup.renameTo(target)
        return if (target.isFile) target else backup
    }

    private fun writeSnapshotFile(
        target: File,
        backup: File,
        json: String,
        serverId: Long,
    ) {
        val temp = File.createTempFile("${target.name}.", ".tmp", directory)
        try {
            temp.writeText(json)
            if (backup.exists() && !backup.delete()) {
                error("Unable to clear stale local play queue snapshot backup for server $serverId.")
            }
            if (target.exists() && !target.renameTo(backup)) {
                error("Unable to prepare local play queue snapshot replacement for server $serverId.")
            }
            if (!temp.renameTo(target)) {
                if (backup.exists() && !target.exists()) {
                    backup.renameTo(target)
                }
                error("Unable to write local play queue snapshot for server $serverId.")
            }
            backup.delete()
        } finally {
            temp.delete()
        }
    }

    private companion object {
        const val SNAPSHOT_DIRECTORY = "play_queue_snapshots"
    }
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
