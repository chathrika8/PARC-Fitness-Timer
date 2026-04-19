package com.parc.fitnesstimer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "parc_prefs")

/**
 * Application-level DI module. Provides infrastructure objects that live
 * for the entire process lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * OkHttpClient shared across the app.
     *
     * - readTimeout = 0 → WebSocket stays open indefinitely
     * - pingInterval = 30 s → keeps the connection alive through NAT/AP idle timeouts
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

    /**
     * Lenient JSON parser — ignores unknown server fields so forward-compatible
     * with future firmware.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * DataStore instance backed by the application-level extension property.
     */
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}
