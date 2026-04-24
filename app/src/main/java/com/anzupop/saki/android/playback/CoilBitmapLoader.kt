package com.anzupop.saki.android.playback

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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A [BitmapLoader] that uses Coil to load artwork bitmaps. This ensures notification
 * artwork goes through our OkHttp pipeline (including [CoverArtEndpointInterceptor]),
 * so artwork loads correctly after network/endpoint changes.
 */
@UnstableApi
class CoilBitmapLoader(private val context: Context) : BitmapLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bitmapCache = android.util.LruCache<String, Bitmap>(20)

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        try {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalArgumentException("Failed to decode bitmap"))
        } catch (e: Exception) {
            future.setException(e)
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val key = uri.toString()
        bitmapCache.get(key)?.let { return Futures.immediateFuture(it) }

        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val bitmap = loadWithCoil(key) ?: run {
                    // Retry once after a short delay — EndpointSelector may need time to reprobe
                    kotlinx.coroutines.delay(2000)
                    loadWithCoil(key)
                }
                if (bitmap != null) {
                    bitmapCache.put(key, bitmap)
                    future.set(bitmap)
                } else {
                    future.setException(IllegalStateException("Coil failed to load $uri"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private suspend fun loadWithCoil(url: String): Bitmap? {
        val result = SingletonImageLoader.get(context)
            .execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(600)
                    .build(),
            )
        return if (result is SuccessResult) result.image.toBitmap() else null
    }
}
