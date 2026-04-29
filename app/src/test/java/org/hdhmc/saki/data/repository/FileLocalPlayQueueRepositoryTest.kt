package org.hdhmc.saki.data.repository

import com.squareup.moshi.Moshi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshot
import org.hdhmc.saki.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileLocalPlayQueueRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `save get and clear round trip snapshot`() = runTest {
        val repository = createRepository()
        val snapshot = snapshot(
            serverId = 1L,
            songs = listOf(song("song-1"), song("song-2")),
            currentSongId = "song-2",
            positionMs = 42_000L,
        )

        repository.save(snapshot)

        assertEquals(snapshot, repository.get(1L))

        repository.clear(1L)

        assertNull(repository.get(1L))
    }

    @Test
    fun `get returns null for corrupted json`() = runTest {
        val directory = temporaryFolder.newFolder()
        val repository = createRepository(directory)
        File(directory, "server_1.json").writeText("{not json")

        assertNull(repository.get(1L))
    }

    @Test
    fun `save overwrites previous snapshot`() = runTest {
        val repository = createRepository()
        val first = snapshot(
            serverId = 1L,
            songs = listOf(song("song-1")),
            currentSongId = "song-1",
            positionMs = 1_000L,
        )
        val second = snapshot(
            serverId = 1L,
            songs = listOf(song("song-2"), song("song-3")),
            currentSongId = "song-3",
            positionMs = 3_000L,
        )

        repository.save(first)
        repository.save(second)

        assertEquals(second, repository.get(1L))
    }

    @Test
    fun `get can recover backup when target is missing`() = runTest {
        val directory = temporaryFolder.newFolder()
        val repository = createRepository(directory)
        val snapshot = snapshot(
            serverId = 1L,
            songs = listOf(song("song-1")),
            currentSongId = "song-1",
            positionMs = 1_000L,
        )

        repository.save(snapshot)
        val target = File(directory, "server_1.json")
        val backup = File(directory, "server_1.json.bak")
        assertTrue(target.renameTo(backup))

        assertEquals(snapshot, repository.get(1L))
    }

    private fun createRepository(
        directory: File = temporaryFolder.newFolder(),
    ): FileLocalPlayQueueRepository = FileLocalPlayQueueRepository(
        directory = directory,
        moshi = Moshi.Builder().build(),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun snapshot(
        serverId: Long,
        songs: List<Song>,
        currentSongId: String?,
        positionMs: Long,
    ) = LocalPlayQueueSnapshot(
        serverId = serverId,
        songs = songs,
        currentSongId = currentSongId,
        positionMs = positionMs,
        updatedAt = 123_456L,
    )

    private fun song(id: String) = Song(
        id = id,
        parentId = null,
        title = "Title $id",
        album = "Album",
        albumId = "album-1",
        artist = "Artist",
        artistId = "artist-1",
        coverArtId = "cover-1",
        durationSeconds = 180,
        track = 1,
        discNumber = 1,
        year = 2026,
        genre = "Genre",
        bitRate = 320,
        sampleRate = 44_100,
        suffix = "mp3",
        contentType = "audio/mpeg",
        sizeBytes = 1_024L,
        path = "path/$id.mp3",
        created = "2026-04-29T00:00:00Z",
    )
}
