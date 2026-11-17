package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackedChannelEntity::class,
        IngestedVideoEntity::class,
        GeneratedClipEntity::class,
        NichePlaybookEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ClipForgeDatabase : RoomDatabase() {
    abstract fun trackedChannelDao(): TrackedChannelDao
    abstract fun ingestedVideoDao(): IngestedVideoDao
    abstract fun generatedClipDao(): GeneratedClipDao
    abstract fun nichePlaybookDao(): NichePlaybookDao

    companion object {
        @Volatile
        private var INSTANCE: ClipForgeDatabase? = null

        fun getInstance(context: Context): ClipForgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClipForgeDatabase::class.java,
                    "clipforge_studio.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
