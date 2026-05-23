package com.cyanbridge.app.core.di

import com.cyanbridge.app.glasses.sdk.HeyCyanSdkBridge
import com.cyanbridge.app.glasses.sdk.HeyCyanSdkBridgeImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * FakeGlassesController, NativeBleGlassesController, and HeyCyanSdkGlassesController
 * are provided via @Inject constructor — no explicit @Provides needed for them.
 *
 * This module binds [HeyCyanSdkBridge] to its stub implementation.
 * When glasses_sdk_20250723_v01.aar is added to app/libs/, update [HeyCyanSdkBridgeImpl]
 * to delegate to LargeDataHandler and set isAarAvailable() = true.
 */
@Module
@InstallIn(SingletonComponent::class)
object GlassesModule {

    @Provides
    @Singleton
    fun provideHeyCyanSdkBridge(impl: HeyCyanSdkBridgeImpl): HeyCyanSdkBridge = impl
}
