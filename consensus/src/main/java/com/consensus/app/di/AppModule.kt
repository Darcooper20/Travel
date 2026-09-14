package com.consensus.app.di

import android.content.Context
import androidx.room.Room
import com.consensus.app.data.local.AppDatabase
import com.consensus.app.data.local.dao.ExchangeDao
import com.consensus.app.data.local.dao.ThreadDao
import com.consensus.app.data.remote.AnthropicClient
import com.consensus.app.data.remote.GeminiClient
import com.consensus.app.data.remote.OpenAiCompatibleClient
import com.consensus.app.domain.LlmClient
import com.consensus.app.domain.ProviderId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC only: BODY/HEADERS would print API keys to logcat.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Web-search-backed answers from a flagship model can take minutes.
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideClients(http: OkHttpClient, json: Json): Map<ProviderId, LlmClient> = mapOf(
        ProviderId.ANTHROPIC to AnthropicClient(http, json),
        ProviderId.OPENAI to OpenAiCompatibleClient(ProviderId.OPENAI, "ChatGPT", "https://api.openai.com", http, json),
        ProviderId.XAI to OpenAiCompatibleClient(ProviderId.XAI, "Grok", "https://api.x.ai", http, json),
        ProviderId.GEMINI to GeminiClient(http, json),
    )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "consensus.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideThreadDao(db: AppDatabase): ThreadDao = db.threadDao()

    @Provides
    fun provideExchangeDao(db: AppDatabase): ExchangeDao = db.exchangeDao()
}
