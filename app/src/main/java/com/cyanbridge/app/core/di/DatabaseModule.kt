package com.cyanbridge.app.core.di

import android.content.Context
import androidx.room.Room
import com.cyanbridge.app.data.db.CyanBridgeDatabase
import com.cyanbridge.app.data.db.dao.MediaItemDao
import com.cyanbridge.app.data.db.dao.NoteDao
import com.cyanbridge.app.data.db.dao.VoiceInteractionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CyanBridgeDatabase =
        Room.databaseBuilder(context, CyanBridgeDatabase::class.java, "cyanbridge.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideVoiceInteractionDao(db: CyanBridgeDatabase): VoiceInteractionDao =
        db.voiceInteractionDao()

    @Provides
    fun provideNoteDao(db: CyanBridgeDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideMediaItemDao(db: CyanBridgeDatabase): MediaItemDao = db.mediaItemDao()
}
