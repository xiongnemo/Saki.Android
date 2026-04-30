package org.hdhmc.saki.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.hdhmc.saki.data.local.entity.CachedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedSongDao {
    @Query("SELECT * FROM cached_songs ORDER BY downloadedAt DESC")
    fun observeCachedSongs(): Flow<List<CachedSongEntity>>

    @Query("SELECT * FROM cached_songs ORDER BY downloadedAt DESC")
    suspend fun getCachedSongs(): List<CachedSongEntity>

    @Query("SELECT * FROM cached_songs WHERE serverId = :serverId ORDER BY downloadedAt DESC")
    suspend fun getCachedSongsForServer(serverId: Long): List<CachedSongEntity>

    @Query("SELECT * FROM cached_songs WHERE serverId = :serverId AND songId IN (:songIds)")
    suspend fun getCachedSongs(
        serverId: Long,
        songIds: List<String>,
    ): List<CachedSongEntity>

    @Query("SELECT * FROM cached_songs WHERE serverId = :serverId AND songId = :songId LIMIT 1")
    suspend fun getCachedSong(
        serverId: Long,
        songId: String,
    ): CachedSongEntity?

    @Query("SELECT * FROM cached_songs WHERE cacheId = :cacheId LIMIT 1")
    suspend fun getCachedSongById(cacheId: String): CachedSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedSong(song: CachedSongEntity)

    @Query("DELETE FROM cached_songs WHERE cacheId = :cacheId")
    suspend fun deleteCachedSong(cacheId: String)

    @Query("DELETE FROM cached_songs WHERE serverId = :serverId")
    suspend fun deleteCachedSongsForServer(serverId: Long)

    @Query("DELETE FROM cached_songs")
    suspend fun deleteAllCachedSongs()
}
