package org.hdhmc.saki.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.hdhmc.saki.data.local.dao.CachedSongDao
import org.hdhmc.saki.data.local.dao.LibraryCacheDao
import org.hdhmc.saki.data.local.dao.PlaybackPreferencesDao
import org.hdhmc.saki.data.local.dao.AppPreferencesDao
import org.hdhmc.saki.data.local.dao.ServerConfigDao
import org.hdhmc.saki.data.local.entity.AppPreferencesEntity
import org.hdhmc.saki.data.local.entity.CachedAlbumEntity
import org.hdhmc.saki.data.local.entity.CachedAlbumDetailEntity
import org.hdhmc.saki.data.local.entity.CachedAlbumDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailAlbumEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedArtistEntity
import org.hdhmc.saki.data.local.entity.CachedLibrarySongEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistDetailEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistEntity
import org.hdhmc.saki.data.local.entity.CachedSongEntity
import org.hdhmc.saki.data.local.entity.CachedSongMetadataEntity
import org.hdhmc.saki.data.local.entity.PlaybackPreferencesEntity
import org.hdhmc.saki.data.local.entity.ServerEndpointEntity
import org.hdhmc.saki.data.local.entity.ServerEntity

@Database(
    entities = [
        AppPreferencesEntity::class,
        CachedAlbumEntity::class,
        CachedAlbumDetailEntity::class,
        CachedAlbumDetailSongEntity::class,
        CachedArtistDetailAlbumEntity::class,
        CachedArtistDetailEntity::class,
        CachedArtistDetailSongEntity::class,
        CachedArtistEntity::class,
        CachedLibrarySongEntity::class,
        CachedPlaylistDetailEntity::class,
        CachedPlaylistDetailSongEntity::class,
        CachedPlaylistEntity::class,
        CachedSongEntity::class,
        CachedSongMetadataEntity::class,
        PlaybackPreferencesEntity::class,
        ServerEntity::class,
        ServerEndpointEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class SakiDatabase : RoomDatabase() {
    abstract fun appPreferencesDao(): AppPreferencesDao

    abstract fun cachedSongDao(): CachedSongDao

    abstract fun libraryCacheDao(): LibraryCacheDao

    abstract fun playbackPreferencesDao(): PlaybackPreferencesDao

    abstract fun serverConfigDao(): ServerConfigDao
}
