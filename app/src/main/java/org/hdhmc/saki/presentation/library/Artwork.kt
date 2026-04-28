package org.hdhmc.saki.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.ServerEndpoint
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val THUMBNAIL_COVER_ART_SIZE_PX = 256
const val PALETTE_COVER_ART_SIZE_PX = 300
const val FULL_COVER_ART_SIZE_PX = 768

@Composable
fun ArtworkCard(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 24,
) {
    val fallbackBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
        ),
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackBrush)
                    .clip(RoundedCornerShape(cornerRadiusDp.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

fun resolveArtworkModel(
    server: ServerConfig?,
    coverArtId: String?,
    cachedSong: CachedSong?,
    sizePx: Int = FULL_COVER_ART_SIZE_PX,
): Any? {
    val localCoverPath = cachedSong?.coverArtPath
    if (!localCoverPath.isNullOrBlank() && File(localCoverPath).exists()) {
        return File(localCoverPath)
    }
    return server?.buildCoverArtUrl(coverArtId, sizePx)
}

/**
 * Builds a deterministic cover art URL using the first endpoint by order and a salt derived
 * from the cover art ID. Stable for a fixed (server configuration, coverArtId, sizePx) tuple,
 * enabling Coil's disk cache to reuse entries. Different [sizePx] values produce different
 * URLs and therefore separate cache entries. At request time, [CoverArtEndpointInterceptor]
 * rewrites the base URL to the current best endpoint.
 */
private fun ServerConfig.buildCoverArtUrl(coverArtId: String?, sizePx: Int): String? {
    if (coverArtId.isNullOrBlank()) return null
    val endpoint = endpoints.sortedBy(ServerEndpoint::order).firstOrNull() ?: return null
    val baseUrl = endpoint.baseUrl.toHttpUrlOrNull() ?: return null
    val salt = md5(coverArtId).take(8)
    val hash = md5("$password$salt")

    return baseUrl.newBuilder()
        .addPathSegments("rest/getCoverArt.view")
        .addQueryParameter("id", coverArtId)
        .addQueryParameter("size", sizePx.coerceAtLeast(1).toString())
        .addQueryParameter("u", username)
        .addQueryParameter("t", hash)
        .addQueryParameter("s", salt)
        .addQueryParameter("v", apiVersion)
        .addQueryParameter("c", clientName)
        .build()
        .toString()
}

private fun md5(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    return digest.joinToString(separator = "") { byte ->
        "%02x".format(Locale.US, byte)
    }
}
