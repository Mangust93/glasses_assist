package com.cyanbridge.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cyanbridge.app.data.db.dao.MediaItemDao
import com.cyanbridge.app.data.db.dao.NoteDao
import com.cyanbridge.app.data.db.dao.VoiceInteractionDao
import com.cyanbridge.app.data.db.entity.MediaItemEntity
import com.cyanbridge.app.data.db.entity.NoteEntity
import com.cyanbridge.app.data.db.entity.VoiceInteractionEntity

@Database(
    entities = [
        VoiceInteractionEntity::class,
        NoteEntity::class,
        MediaItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CyanBridgeDatabase : RoomDatabase() {
    abstract fun voiceInteractionDao(): VoiceInteractionDao
    abstract fun noteDao(): NoteDao
    abstract fun mediaItemDao(): MediaItemDao
}
