package org.hdhmc.saki.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import org.hdhmc.saki.data.local.dao.AppPreferencesDao
import org.hdhmc.saki.data.local.dao.CachedSongDao
import org.hdhmc.saki.data.local.dao.LibraryCacheDao
import org.hdhmc.saki.data.local.dao.PlaybackPreferencesDao
import org.hdhmc.saki.data.local.dao.ServerConfigDao
import org.hdhmc.saki.data.local.database.SakiDatabase
import org.hdhmc.saki.domain.model.DEFAULT_STREAM_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.model.TextScale
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_songs` (
                    `cacheId` TEXT NOT NULL,
                    `serverId` INTEGER NOT NULL,
                    `songId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `album` TEXT,
                    `albumId` TEXT,
                    `artist` TEXT,
                    `artistId` TEXT,
                    `coverArtId` TEXT,
                    `coverArtPath` TEXT,
                    `localPath` TEXT NOT NULL,
                    `durationSeconds` INTEGER,
                    `track` INTEGER,
                    `discNumber` INTEGER,
                    `suffix` TEXT,
                    `contentType` TEXT,
                    `qualityKey` TEXT NOT NULL,
                    `fileSizeBytes` INTEGER NOT NULL,
                    `downloadedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`cacheId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_songs_serverId_songId`
                ON `cached_songs` (`serverId`, `songId`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playback_preferences` (
                    `id` INTEGER NOT NULL,
                    `streamQualityKey` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `playback_preferences` (`id`, `streamQualityKey`)
                VALUES (0, '${StreamQuality.ORIGINAL.storageKey}')
                """.trimIndent(),
            )
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_preferences` (
                    `id` INTEGER NOT NULL,
                    `onboardingCompleted` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `app_preferences` (`id`, `onboardingCompleted`)
                VALUES (0, 0)
                """.trimIndent(),
            )
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE `app_preferences`
                ADD COLUMN `textScaleKey` TEXT NOT NULL DEFAULT '${TextScale.DEFAULT.storageKey}'
                """.trimIndent(),
            )
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE `playback_preferences`
                ADD COLUMN `soundBalancingEnabled` INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE `playback_preferences`
                ADD COLUMN `soundBalancingModeKey` TEXT NOT NULL DEFAULT '${SoundBalancingMode.OFF.storageKey}'
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE `playback_preferences`
                ADD COLUMN `streamCacheSizeMb` INTEGER NOT NULL DEFAULT ${DEFAULT_STREAM_CACHE_SIZE_MB}
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE `playback_preferences`
                SET `soundBalancingModeKey` = '${SoundBalancingMode.MEDIUM.storageKey}'
                WHERE `soundBalancingEnabled` = 1
                """.trimIndent(),
            )
        }
    }

    private val migration6To7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_artists` (
                    `serverId` INTEGER NOT NULL,
                    `artistId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `sectionName` TEXT NOT NULL,
                    `albumCount` INTEGER,
                    `coverArtId` TEXT,
                    `artistImageUrl` TEXT,
                    PRIMARY KEY(`serverId`, `artistId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_albums` (
                    `serverId` INTEGER NOT NULL,
                    `albumId` TEXT NOT NULL,
                    `listType` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `artist` TEXT,
                    `artistId` TEXT,
                    `coverArtId` TEXT,
                    `songCount` INTEGER,
                    `durationSeconds` INTEGER,
                    `year` INTEGER,
                    `genre` TEXT,
                    `created` TEXT,
                    `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `albumId`, `listType`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_playlists` (
                    `serverId` INTEGER NOT NULL,
                    `playlistId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `owner` TEXT,
                    `isPublic` INTEGER,
                    `songCount` INTEGER,
                    `durationSeconds` INTEGER,
                    `coverArtId` TEXT,
                    `created` TEXT,
                    `changed` TEXT,
                    PRIMARY KEY(`serverId`, `playlistId`)
                )
                """.trimIndent(),
            )
        }
    }

    private val migration7To8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE `playback_preferences`
                ADD COLUMN `bluetoothLyricsEnabled` INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
        }
    }

    private val migration8To9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_library_songs` (
                    `serverId` INTEGER NOT NULL,
                    `songId` TEXT NOT NULL,
                    `parentId` TEXT,
                    `title` TEXT NOT NULL COLLATE NOCASE,
                    `album` TEXT,
                    `albumId` TEXT,
                    `artist` TEXT,
                    `artistId` TEXT,
                    `coverArtId` TEXT,
                    `durationSeconds` INTEGER,
                    `track` INTEGER,
                    `discNumber` INTEGER,
                    `year` INTEGER,
                    `genre` TEXT,
                    `bitRate` INTEGER,
                    `suffix` TEXT,
                    `contentType` TEXT,
                    `sizeBytes` INTEGER,
                    `path` TEXT,
                    `created` TEXT,
                    PRIMARY KEY(`serverId`, `songId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_cached_library_songs_serverId_title`
                ON `cached_library_songs` (`serverId`, `title`)
                """.trimIndent(),
            )
        }
    }

    private val migration9To10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `server_endpoints_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `serverId` INTEGER NOT NULL,
                    `label` TEXT NOT NULL,
                    `baseUrl` TEXT NOT NULL,
                    `displayOrder` INTEGER NOT NULL,
                    FOREIGN KEY(`serverId`) REFERENCES `servers`(`id`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `server_endpoints_new` (`id`, `serverId`, `label`, `baseUrl`, `displayOrder`)
                SELECT `id`, `serverId`, `label`, `baseUrl`, `displayOrder` FROM `server_endpoints`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `server_endpoints`")
            db.execSQL("ALTER TABLE `server_endpoints_new` RENAME TO `server_endpoints`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_server_endpoints_serverId` ON `server_endpoints` (`serverId`)")
        }
    }

    private val migration10To11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `cached_songs` ADD COLUMN `bitRate` INTEGER")
            db.execSQL("ALTER TABLE `cached_songs` ADD COLUMN `sampleRate` INTEGER")
            db.execSQL("ALTER TABLE `cached_library_songs` ADD COLUMN `sampleRate` INTEGER")
            db.execSQL(
                """
                UPDATE `cached_songs`
                SET `bitRate` = CASE
                    WHEN `qualityKey` IN ('320', '256', '192', '160', '128', '96')
                        THEN CAST(`qualityKey` AS INTEGER)
                    ELSE (
                        SELECT `bitRate`
                        FROM `cached_library_songs`
                        WHERE `cached_library_songs`.`serverId` = `cached_songs`.`serverId`
                            AND `cached_library_songs`.`songId` = `cached_songs`.`songId`
                    )
                END
                WHERE `bitRate` IS NULL
                """.trimIndent(),
            )
        }
    }

    private val migration11To12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_song_metadata` (
                    `serverId` INTEGER NOT NULL,
                    `songId` TEXT NOT NULL,
                    `parentId` TEXT,
                    `title` TEXT NOT NULL COLLATE NOCASE,
                    `album` TEXT,
                    `albumId` TEXT,
                    `artist` TEXT,
                    `artistId` TEXT,
                    `coverArtId` TEXT,
                    `durationSeconds` INTEGER,
                    `track` INTEGER,
                    `discNumber` INTEGER,
                    `year` INTEGER,
                    `genre` TEXT,
                    `bitRate` INTEGER,
                    `sampleRate` INTEGER,
                    `suffix` TEXT,
                    `contentType` TEXT,
                    `sizeBytes` INTEGER,
                    `path` TEXT,
                    `created` TEXT,
                    `cachedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `songId`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_song_metadata_serverId_albumId` ON `cached_song_metadata` (`serverId`, `albumId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_song_metadata_serverId_artistId` ON `cached_song_metadata` (`serverId`, `artistId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_artist_details` (
                    `serverId` INTEGER NOT NULL,
                    `artistId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `coverArtId` TEXT,
                    `artistImageUrl` TEXT,
                    `albumCount` INTEGER,
                    `cachedAt` INTEGER NOT NULL,
                    `isComplete` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `artistId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_artist_detail_albums` (
                    `serverId` INTEGER NOT NULL,
                    `artistId` TEXT NOT NULL,
                    `albumId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `artist` TEXT,
                    `albumArtistId` TEXT,
                    `coverArtId` TEXT,
                    `songCount` INTEGER,
                    `durationSeconds` INTEGER,
                    `year` INTEGER,
                    `genre` TEXT,
                    `created` TEXT,
                    `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `artistId`, `albumId`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_artist_detail_albums_serverId_artistId_sortOrder` ON `cached_artist_detail_albums` (`serverId`, `artistId`, `sortOrder`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_artist_detail_songs` (
                    `serverId` INTEGER NOT NULL,
                    `artistId` TEXT NOT NULL,
                    `songId` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `artistId`, `sortOrder`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_artist_detail_songs_serverId_artistId` ON `cached_artist_detail_songs` (`serverId`, `artistId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_artist_detail_songs_serverId_songId` ON `cached_artist_detail_songs` (`serverId`, `songId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_album_details` (
                    `serverId` INTEGER NOT NULL,
                    `albumId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `artist` TEXT,
                    `artistId` TEXT,
                    `coverArtId` TEXT,
                    `songCount` INTEGER,
                    `durationSeconds` INTEGER,
                    `year` INTEGER,
                    `genre` TEXT,
                    `created` TEXT,
                    `cachedAt` INTEGER NOT NULL,
                    `isComplete` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `albumId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_album_detail_songs` (
                    `serverId` INTEGER NOT NULL,
                    `albumId` TEXT NOT NULL,
                    `songId` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `albumId`, `sortOrder`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_album_detail_songs_serverId_albumId` ON `cached_album_detail_songs` (`serverId`, `albumId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_album_detail_songs_serverId_songId` ON `cached_album_detail_songs` (`serverId`, `songId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_playlist_details` (
                    `serverId` INTEGER NOT NULL,
                    `playlistId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `owner` TEXT,
                    `isPublic` INTEGER,
                    `songCount` INTEGER,
                    `durationSeconds` INTEGER,
                    `coverArtId` TEXT,
                    `created` TEXT,
                    `changed` TEXT,
                    `cachedAt` INTEGER NOT NULL,
                    `isComplete` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `playlistId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_playlist_detail_songs` (
                    `serverId` INTEGER NOT NULL,
                    `playlistId` TEXT NOT NULL,
                    `songId` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `playlistId`, `sortOrder`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_playlist_detail_songs_serverId_playlistId` ON `cached_playlist_detail_songs` (`serverId`, `playlistId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_playlist_detail_songs_serverId_songId` ON `cached_playlist_detail_songs` (`serverId`, `songId`)")
        }
    }

    @Provides
    @Singleton
    fun provideSakiDatabase(
        @ApplicationContext context: Context,
    ): SakiDatabase {
        return Room.databaseBuilder(
            context,
            SakiDatabase::class.java,
            "saki.db",
        ).addMigrations(*allMigrations())
            .build()
    }

    fun allMigrations() = arrayOf(
        migration1To2, migration2To3, migration3To4, migration4To5,
        migration5To6, migration6To7, migration7To8, migration8To9,
        migration9To10, migration10To11, migration11To12,
    )

    @Provides
    fun provideAppPreferencesDao(database: SakiDatabase): AppPreferencesDao = database.appPreferencesDao()

    @Provides
    fun provideCachedSongDao(database: SakiDatabase): CachedSongDao = database.cachedSongDao()

    @Provides
    fun provideLibraryCacheDao(database: SakiDatabase): LibraryCacheDao = database.libraryCacheDao()

    @Provides
    fun providePlaybackPreferencesDao(database: SakiDatabase): PlaybackPreferencesDao = database.playbackPreferencesDao()

    @Provides
    fun provideServerConfigDao(database: SakiDatabase): ServerConfigDao = database.serverConfigDao()
}
