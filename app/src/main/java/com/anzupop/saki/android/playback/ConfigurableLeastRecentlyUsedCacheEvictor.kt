package com.anzupop.saki.android.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

@UnstableApi
class ConfigurableLeastRecentlyUsedCacheEvictor(
    initialMaxBytes: Long,
) : CacheEvictor {
    private val leastRecentlyUsed = TreeSet<CacheSpan>(::compareSpans)

    private var cache: Cache? = null
    private var currentSize = 0L
    private var maxBytes = initialMaxBytes

    @Synchronized
    fun updateMaxBytes(newMaxBytes: Long) {
        maxBytes = newMaxBytes.coerceAtLeast(1L)
        cache?.let { activeCache ->
            evictCache(activeCache, requiredSpace = 0L)
        }
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    @Synchronized
    override fun onStartFile(
        cache: Cache,
        key: String,
        position: Long,
        length: Long,
    ) {
        this.cache = cache
        if (length != C.LENGTH_UNSET.toLong()) {
            evictCache(cache, requiredSpace = length)
        }
    }

    @Synchronized
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        this.cache = cache
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evictCache(cache, requiredSpace = 0L)
    }

    @Synchronized
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        this.cache = cache
        if (leastRecentlyUsed.remove(span)) {
            currentSize -= span.length
        }
    }

    @Synchronized
    override fun onSpanTouched(
        cache: Cache,
        oldSpan: CacheSpan,
        newSpan: CacheSpan,
    ) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    @Synchronized
    private fun evictCache(
        cache: Cache,
        requiredSpace: Long,
    ) {
        while (currentSize + requiredSpace > maxBytes && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }
}

@UnstableApi
private fun compareSpans(lhs: CacheSpan, rhs: CacheSpan): Int {
    val timestampDelta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
    return when {
        timestampDelta == 0L -> lhs.compareTo(rhs)
        timestampDelta < 0L -> -1
        else -> 1
    }
}
