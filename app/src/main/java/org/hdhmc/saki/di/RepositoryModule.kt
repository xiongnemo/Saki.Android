package org.hdhmc.saki.di

import androidx.media3.common.util.UnstableApi
import org.hdhmc.saki.data.remote.SubsonicConnectionTester
import org.hdhmc.saki.data.repository.DataStoreAppPreferencesRepository
import org.hdhmc.saki.data.repository.DataStorePlaybackPreferencesRepository
import org.hdhmc.saki.data.repository.DefaultCachedSongRepository
import org.hdhmc.saki.data.repository.DefaultLibraryCacheRepository
import org.hdhmc.saki.data.repository.DefaultServerConfigRepository
import org.hdhmc.saki.data.repository.DefaultStreamCacheRepository
import org.hdhmc.saki.data.repository.DefaultSubsonicRepository
import org.hdhmc.saki.domain.repository.AppPreferencesRepository
import org.hdhmc.saki.domain.repository.CachedSongRepository
import org.hdhmc.saki.domain.repository.LibraryCacheRepository
import org.hdhmc.saki.domain.repository.PlaybackManager
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import org.hdhmc.saki.domain.repository.ServerConfigRepository
import org.hdhmc.saki.domain.repository.ServerConnectionTester
import org.hdhmc.saki.domain.repository.StreamCacheRepository
import org.hdhmc.saki.domain.repository.SubsonicRepository
import org.hdhmc.saki.playback.DefaultPlaybackManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindAppPreferencesRepository(
        repository: DataStoreAppPreferencesRepository,
    ): AppPreferencesRepository

    @Binds
    abstract fun bindServerConfigRepository(
        repository: DefaultServerConfigRepository,
    ): ServerConfigRepository

    @Binds
    abstract fun bindServerConnectionTester(
        tester: SubsonicConnectionTester,
    ): ServerConnectionTester

    @Binds
    abstract fun bindSubsonicRepository(
        repository: DefaultSubsonicRepository,
    ): SubsonicRepository

    @Binds
    abstract fun bindPlaybackPreferencesRepository(
        repository: DataStorePlaybackPreferencesRepository,
    ): PlaybackPreferencesRepository

    @Binds
    abstract fun bindCachedSongRepository(
        repository: DefaultCachedSongRepository,
    ): CachedSongRepository

    @Binds
    abstract fun bindLibraryCacheRepository(
        repository: DefaultLibraryCacheRepository,
    ): LibraryCacheRepository

    @Binds
    @UnstableApi
    abstract fun bindStreamCacheRepository(
        repository: DefaultStreamCacheRepository,
    ): StreamCacheRepository

    @Binds
    @UnstableApi
    abstract fun bindPlaybackManager(
        manager: DefaultPlaybackManager,
    ): PlaybackManager
}
