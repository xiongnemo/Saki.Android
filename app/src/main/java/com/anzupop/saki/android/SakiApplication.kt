package com.anzupop.saki.android

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.anzupop.saki.android.data.remote.EndpointSelector
import com.anzupop.saki.android.domain.model.DEFAULT_IMAGE_CACHE_SIZE_MB
import com.anzupop.saki.android.domain.repository.PlaybackPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@HiltAndroidApp
class SakiApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var endpointSelector: EndpointSelector

    @Inject
    lateinit var playbackPreferencesRepository: PlaybackPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        endpointSelector.start()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                val imageCacheSizeMb = runBlocking {
                    runCatching { playbackPreferencesRepository.getPreferences().imageCacheSizeMb }
                        .getOrDefault(DEFAULT_IMAGE_CACHE_SIZE_MB)
                }
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(imageCacheSizeMb.toLong() * 1024L * 1024L)
                    .build()
            }
            .build()
    }
}
