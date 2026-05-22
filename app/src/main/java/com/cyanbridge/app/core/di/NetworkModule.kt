package com.cyanbridge.app.core.di

import com.cyanbridge.app.network.DynamicBaseUrlInterceptor
import com.cyanbridge.app.network.HermesApiClientImpl
import com.cyanbridge.app.network.api.HermesApi
import com.cyanbridge.app.domain.interfaces.HermesApiClient
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        baseUrlInterceptor: DynamicBaseUrlInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            // Placeholder host — replaced at runtime by DynamicBaseUrlInterceptor
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create(GsonBuilder().setLenient().create())
            )
            .build()

    @Provides
    @Singleton
    fun provideHermesApi(retrofit: Retrofit): HermesApi =
        retrofit.create(HermesApi::class.java)

    @Provides
    @Singleton
    fun provideHermesApiClient(impl: HermesApiClientImpl): HermesApiClient = impl
}
