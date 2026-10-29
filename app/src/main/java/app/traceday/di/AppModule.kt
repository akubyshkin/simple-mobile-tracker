package app.traceday.di

import android.content.Context
import androidx.room.Room
import app.traceday.core.Clock
import app.traceday.core.retention.RetentionPolicy
import app.traceday.core.WallClock
import app.traceday.data.local.AppDatabase
import app.traceday.data.local.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = WallClock()

    @Provides
    @Singleton
    fun provideRetentionPolicy(): RetentionPolicy = RetentionPolicy()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideTrackDao(database: AppDatabase): TrackDao = database.trackDao()

    @Provides
    @Singleton
    @Named("plain")
    fun providePlainOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
}
