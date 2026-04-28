package org.hdhmc.saki.presentation.library

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
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
    requestSizePx: Int? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val fallbackColors = remember(colorScheme.primary, colorScheme.tertiary) {
        listOf(
            colorScheme.primary.copy(alpha = 0.9f),
            colorScheme.tertiary.copy(alpha = 0.7f),
        )
    }
    val containerColor = colorScheme.surfaceVariant.copy(alpha = 0.34f)
    val fallbackIconTint = colorScheme.onPrimary
    val context = LocalContext.current
    val imageModel = remember(model, requestSizePx, context) {
        if (model != null && requestSizePx != null) {
            ImageRequest.Builder(context)
                .data(model)
                .size(requestSizePx.coerceAtLeast(1))
                .build()
        } else {
            model
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadiusDp.dp),
        color = containerColor,
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadiusDp.dp))
                    .drawWithCache {
                        val fallbackBrush = Brush.linearGradient(fallbackColors)
                        onDrawBehind {
                            drawRect(fallbackBrush)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = fallbackIconTint,
                )
            }
        }
    }
}

fun resolveArtworkModel(
    server: ServerConfig?,
    coverArtId: String?,
    cachedSong: CachedSong?,
): Any? {
    val localCoverPath = cachedSong?.coverArtPath
    if (!localCoverPath.isNullOrBlank() && File(localCoverPath).exists()) {
        return File(localCoverPath)
    }
    return server?.buildCoverArtUrl(coverArtId)
}

/**
 * Builds a deterministic cover art URL using the first endpoint by order and a salt derived
 * from the cover art ID. Stable for a fixed server configuration and coverArtId, enabling
 * Coil's disk cache to reuse entries across thumbnail and full-size UI requests. At request
 * time, [CoverArtEndpointInterceptor] rewrites the base URL to the current best endpoint.
 */
private fun ServerConfig.buildCoverArtUrl(coverArtId: String?): String? {
    if (coverArtId.isNullOrBlank()) return null
    val endpoint = endpoints.sortedBy(ServerEndpoint::order).firstOrNull() ?: return null
    val baseUrl = endpoint.baseUrl.toHttpUrlOrNull() ?: return null
    val salt = md5(coverArtId).take(8)
    val hash = md5("$password$salt")

    return baseUrl.newBuilder()
        .addPathSegments("rest/getCoverArt.view")
        .addQueryParameter("id", coverArtId)
        .addQueryParameter("size", FULL_COVER_ART_SIZE_PX.toString())
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
