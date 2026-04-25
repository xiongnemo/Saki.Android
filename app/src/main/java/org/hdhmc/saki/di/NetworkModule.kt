package org.hdhmc.saki.di

import org.hdhmc.saki.BuildConfig
import org.hdhmc.saki.data.remote.CoverArtEndpointInterceptor
import org.hdhmc.saki.data.remote.EndpointSelector
import org.hdhmc.saki.data.remote.HTTP_USER_AGENT
import org.hdhmc.saki.data.remote.subsonic.SubsonicApiService
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideUserAgentInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            chain.proceed(
                request.newBuilder()
                    .header("User-Agent", HTTP_USER_AGENT)
                    .build(),
            )
        }
    }

    @Provides
    @Singleton
    fun provideCoverArtEndpointInterceptor(
        endpointSelector: dagger.Lazy<EndpointSelector>,
    ): CoverArtEndpointInterceptor {
        return CoverArtEndpointInterceptor { endpointSelector.get() }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        userAgentInterceptor: Interceptor,
        coverArtEndpointInterceptor: CoverArtEndpointInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(coverArtEndpointInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .baseUrl("https://localhost/")
            .build()
    }

    @Provides
    @Singleton
    fun provideSubsonicApiService(retrofit: Retrofit): SubsonicApiService {
        return retrofit.create(SubsonicApiService::class.java)
    }
}
