package com.anzupop.saki.android.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import com.anzupop.saki.android.domain.model.DEFAULT_STREAM_CACHE_SIZE_MB
import com.anzupop.saki.android.playback.ConfigurableLeastRecentlyUsedCacheEvictor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaCacheModule {
    @Provides
    @Singleton
    @UnstableApi
    fun provideMediaCacheDatabaseProvider(
        @ApplicationContext context: Context,
    ): StandaloneDatabaseProvider {
        return StandaloneDatabaseProvider(context)
    }

    @Provides
    @Singleton
    @UnstableApi
    fun provideStreamCacheEvictor(): ConfigurableLeastRecentlyUsedCacheEvictor {
        return ConfigurableLeastRecentlyUsedCacheEvictor(
            initialMaxBytes = DEFAULT_STREAM_CACHE_SIZE_MB.toLong() * 1024L * 1024L,
        )
    }

    @Provides
    @Singleton
    @UnstableApi
    fun provideStreamCache(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider,
        cacheEvictor: ConfigurableLeastRecentlyUsedCacheEvictor,
    ): SimpleCache {
        val cacheDirectory = File(context.filesDir, "stream-cache").apply {
            mkdirs()
        }

        return SimpleCache(
            cacheDirectory,
            cacheEvictor,
            databaseProvider,
        )
    }
}
