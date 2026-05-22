package com.cyanbridge.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cyanbridge.app.data.repository.MediaRepositoryImpl
import com.cyanbridge.app.data.repository.NotesRepositoryImpl
import com.cyanbridge.app.data.repository.SettingsRepositoryImpl
import com.cyanbridge.app.data.repository.VoiceInteractionRepositoryImpl
import com.cyanbridge.app.domain.interfaces.MediaRepository
import com.cyanbridge.app.domain.interfaces.NotesRepository
import com.cyanbridge.app.domain.interfaces.SettingsRepository
import com.cyanbridge.app.domain.interfaces.VoiceInteractionRepository
import com.cyanbridge.app.domain.interfaces.VoiceRecorder
import com.cyanbridge.app.voice.VoiceRecorderImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository = impl

    @Provides
    @Singleton
    fun provideVoiceInteractionRepository(impl: VoiceInteractionRepositoryImpl): VoiceInteractionRepository = impl

    @Provides
    @Singleton
    fun provideNotesRepository(impl: NotesRepositoryImpl): NotesRepository = impl

    @Provides
    @Singleton
    fun provideMediaRepository(impl: MediaRepositoryImpl): MediaRepository = impl

    @Provides
    @Singleton
    fun provideVoiceRecorder(impl: VoiceRecorderImpl): VoiceRecorder = impl
}
