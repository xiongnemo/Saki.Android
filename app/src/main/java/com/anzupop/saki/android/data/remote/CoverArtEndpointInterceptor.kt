package com.anzupop.saki.android.data.remote

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that rewrites Coil cover art request URLs to use the best available endpoint.
 *
 * Coil generates cover art URLs with a fixed base (first endpoint by order) for stable caching.
 * When the actual HTTP request is made (cache miss), this interceptor replaces the base URL
 * with the current best endpoint so the request reaches a reachable server.
 *
 * Only rewrites requests whose host+port matches the canonical (first-by-order) endpoint,
 * so repository-generated multi-endpoint fallback requests are not affected.
 */
class CoverArtEndpointInterceptor(
    private val endpointSelectorProvider: () -> EndpointSelector,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (!url.encodedPath.contains("getCoverArt.view")) {
            return chain.proceed(request)
        }

        val endpointSelector = endpointSelectorProvider()

        val matchedServerId = endpointSelector.findServerByHostPort(url.host, url.port)
            ?: return chain.proceed(request)

        // Only rewrite if this request uses the canonical (first-by-order) endpoint.
        // Non-canonical requests come from SubsonicRepository fallback and should not be rewritten.
        val canonicalEndpoint = endpointSelector.getCanonicalEndpoint(matchedServerId)
            ?: return chain.proceed(request)
        val canonicalBase = canonicalEndpoint.baseUrl.trimEnd('/').toHttpUrlOrNull()
            ?: return chain.proceed(request)
        if (url.host != canonicalBase.host || url.port != canonicalBase.port || url.scheme != canonicalBase.scheme) {
            return chain.proceed(request)
        }

        val bestEndpointId = endpointSelector.getActiveEndpointId(matchedServerId)
            ?: return chain.proceed(request)
        if (canonicalEndpoint.id == bestEndpointId) {
            return chain.proceed(request)
        }

        val results = endpointSelector.getLastProbeResults(matchedServerId)
        val bestEndpoint = results.find { it.endpoint.id == bestEndpointId }?.endpoint
            ?: return chain.proceed(request)
        val bestBase = bestEndpoint.baseUrl.trimEnd('/').toHttpUrlOrNull()
            ?: return chain.proceed(request)

        // Extract the relative path after /rest/ and rebuild with best endpoint's base path
        val restIndex = url.encodedPath.indexOf("/rest/")
        if (restIndex == -1) return chain.proceed(request)
        val relativePath = url.encodedPath.substring(restIndex)

        val bestBasePath = bestBase.encodedPath.trimEnd('/')
        val newUrl = url.newBuilder()
            .scheme(bestBase.scheme)
            .host(bestBase.host)
            .port(bestBase.port)
            .encodedPath(bestBasePath + relativePath)
            .build()

        return try {
            chain.proceed(request.newBuilder().url(newUrl).build())
        } catch (e: IllegalStateException) {
            // Stale connection after network switch — rethrow as IOException so OkHttp retries
            throw IOException("Stale connection after endpoint rewrite", e)
        }
    }
}
