package com.anzupop.saki.android.data.remote.subsonic

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface SubsonicApiService {
    @GET
    suspend fun get(
        @Url url: String,
        @QueryMap query: Map<String, String>,
    ): Response<SubsonicEnvelopeDto>
}
