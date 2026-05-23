package com.cyanbridge.app

import android.app.Application
import com.cyanbridge.app.glasses.sync.DeviceSyncManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class CyanBridgeApplication : Application() {
    @Inject lateinit var deviceSyncManager: DeviceSyncManager

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
