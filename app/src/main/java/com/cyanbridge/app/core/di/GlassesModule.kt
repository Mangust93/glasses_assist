package com.cyanbridge.app.core.di

import com.cyanbridge.app.glasses.ble.NativeBleGlassesController
import com.cyanbridge.app.glasses.fake.FakeGlassesController
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Both controllers are provided via @Inject constructor — no explicit @Provides needed.
 * GlassesControllerSelector in the ViewModel picks the active one based on settings.
 */
@Module
@InstallIn(SingletonComponent::class)
object GlassesModule
