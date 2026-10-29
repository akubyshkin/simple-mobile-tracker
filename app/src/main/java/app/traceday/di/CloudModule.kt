package app.traceday.di

import app.traceday.cloud.defaultJson
import app.traceday.cloud.format.TrackFileCodec
import app.traceday.cloud.google.DriveApi
import app.traceday.cloud.google.GoogleAuthInterceptor
import app.traceday.cloud.google.GoogleAuthManager
import app.traceday.cloud.yandex.YandexAuthInterceptor
import app.traceday.cloud.yandex.YandexAuthManager
import app.traceday.cloud.yandex.YandexDiskApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudModule {

    @Provides
    @Singleton
    fun provideTrackFileCodec(): TrackFileCodec = TrackFileCodec()

    @Provides
    @Singleton
    @Named("google")
    fun provideGoogleHttp(
        @Named("plain") plain: OkHttpClient,
        auth: GoogleAuthManager
    ): OkHttpClient = plain.newBuilder()
        .addInterceptor(GoogleAuthInterceptor(auth))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDriveApi(@Named("google") client: OkHttpClient): DriveApi =
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(client)
            .addConverterFactory(defaultJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DriveApi::class.java)

    @Provides
    @Singleton
    @Named("yandex")
    fun provideYandexHttp(
        @Named("plain") plain: OkHttpClient,
        auth: YandexAuthManager
    ): OkHttpClient = plain.newBuilder()
        .addInterceptor(YandexAuthInterceptor(auth))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideYandexDiskApi(@Named("yandex") client: OkHttpClient): YandexDiskApi =
        Retrofit.Builder()
            .baseUrl("https://cloud-api.yandex.net/")
            .client(client)
            .addConverterFactory(defaultJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YandexDiskApi::class.java)
}
