package com.travelbenefits.app.di

import com.travelbenefits.app.data.remote.anthropic.AnthropicApi
import com.travelbenefits.app.data.remote.gmail.GmailApi
import com.travelbenefits.app.data.remote.plaid.PlaidBackendApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BODY logging would print API keys/OAuth tokens in headers - keep it off
            // even in debug builds; bump to BASIC if you need to see request URLs.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @Named("anthropic")
    fun provideAnthropicRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(AnthropicApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @Named("gmail")
    fun provideGmailRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(GmailApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAnthropicApi(@Named("anthropic") retrofit: Retrofit): AnthropicApi =
        retrofit.create(AnthropicApi::class.java)

    @Provides
    @Singleton
    fun provideGmailApi(@Named("gmail") retrofit: Retrofit): GmailApi =
        retrofit.create(GmailApi::class.java)

    /** The backend URL is user-configured, so every call passes a full @Url; the base URL here is a placeholder Retrofit requires. */
    @Provides
    @Singleton
    fun providePlaidBackendApi(client: OkHttpClient, json: Json): PlaidBackendApi =
        Retrofit.Builder()
            .baseUrl("https://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PlaidBackendApi::class.java)
}
