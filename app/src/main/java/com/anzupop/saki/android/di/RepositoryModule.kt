package com.anzupop.saki.android.di

import androidx.media3.common.util.UnstableApi
import com.anzupop.saki.android.data.remote.SubsonicConnectionTester
import com.anzupop.saki.android.data.repository.DefaultAppPreferencesRepository
import com.anzupop.saki.android.data.repository.DefaultCachedSongRepository
import com.anzupop.saki.android.data.repository.DefaultLibraryCacheRepository
import com.anzupop.saki.android.data.repository.DefaultPlaybackPreferencesRepository
import com.anzupop.saki.android.data.repository.DefaultServerConfigRepository
import com.anzupop.saki.android.data.repository.DefaultStreamCacheRepository
import com.anzupop.saki.android.data.repository.DefaultSubsonicRepository
import com.anzupop.saki.android.domain.repository.AppPreferencesRepository
import com.anzupop.saki.android.domain.repository.CachedSongRepository
import com.anzupop.saki.android.domain.repository.LibraryCacheRepository
import com.anzupop.saki.android.domain.repository.PlaybackManager
import com.anzupop.saki.android.domain.repository.PlaybackPreferencesRepository
import com.anzupop.saki.android.domain.repository.ServerConfigRepository
import com.anzupop.saki.android.domain.repository.ServerConnectionTester
import com.anzupop.saki.android.domain.repository.StreamCacheRepository
import com.anzupop.saki.android.domain.repository.SubsonicRepository
import com.anzupop.saki.android.playback.DefaultPlaybackManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindAppPreferencesRepository(
        repository: DefaultAppPreferencesRepository,
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
        repository: DefaultPlaybackPreferencesRepository,
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
