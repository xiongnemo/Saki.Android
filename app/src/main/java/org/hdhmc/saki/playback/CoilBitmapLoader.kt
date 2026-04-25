package org.hdhmc.saki.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.BitmapLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A [BitmapLoader] that uses Coil to load artwork bitmaps. This ensures notification
 * artwork goes through our OkHttp pipeline (including [CoverArtEndpointInterceptor]),
 * so artwork loads correctly after network/endpoint changes.
 */
@UnstableApi
class CoilBitmapLoader(context: Context) : BitmapLoader {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Cache sized by memory (max 8 MB), not entry count
    private val bitmapCache = object : android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun release() { scope.cancel() }

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        return if (bitmap != null) {
            Futures.immediateFuture(bitmap)
        } else {
            Futures.immediateFailedFuture(IllegalArgumentException("Failed to decode bitmap"))
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val key = uri.toString()
        bitmapCache.get(key)?.let { return Futures.immediateFuture(it) }

        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch {
            val bitmap = loadWithCoil(key) ?: run {
                delay(2000)
                loadWithCoil(key)
            }
            if (future.isCancelled) return@launch
            if (bitmap != null) {
                bitmapCache.put(key, bitmap)
                future.set(bitmap)
            } else {
                future.setException(IllegalStateException("Coil failed to load $uri"))
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, Runnable::run)
        return future
    }

    private suspend fun loadWithCoil(url: String): Bitmap? {
        val result = SingletonImageLoader.get(appContext)
            .execute(
                ImageRequest.Builder(appContext)
                    .data(url)
                    .size(600)
                    .build(),
            )
        return if (result is SuccessResult) result.image.toBitmap() else null
    }
}
