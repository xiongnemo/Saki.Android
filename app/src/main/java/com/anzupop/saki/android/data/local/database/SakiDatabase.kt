package com.anzupop.saki.android.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.anzupop.saki.android.data.local.dao.CachedSongDao
import com.anzupop.saki.android.data.local.dao.PlaybackPreferencesDao
import com.anzupop.saki.android.data.local.dao.AppPreferencesDao
import com.anzupop.saki.android.data.local.dao.ServerConfigDao
import com.anzupop.saki.android.data.local.entity.AppPreferencesEntity
import com.anzupop.saki.android.data.local.entity.CachedSongEntity
import com.anzupop.saki.android.data.local.entity.PlaybackPreferencesEntity
import com.anzupop.saki.android.data.local.entity.ServerEndpointEntity
import com.anzupop.saki.android.data.local.entity.ServerEntity

@Database(
    entities = [
        AppPreferencesEntity::class,
        CachedSongEntity::class,
        PlaybackPreferencesEntity::class,
        ServerEntity::class,
        ServerEndpointEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class SakiDatabase : RoomDatabase() {
    abstract fun appPreferencesDao(): AppPreferencesDao

    abstract fun cachedSongDao(): CachedSongDao

    abstract fun playbackPreferencesDao(): PlaybackPreferencesDao

    abstract fun serverConfigDao(): ServerConfigDao
}
