package com.theveloper.pixelplay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for locally stored songs on the watch.
 * Tracks songs that have been transferred from the phone for offline playback.
 */
@Database(entities = [LocalSongEntity::class], version = 2, exportSchema = false)
abstract class WearMusicDatabase : RoomDatabase() {
    abstract fun localSongDao(): LocalSongDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_songs ADD COLUMN paletteSeedArgb INTEGER")
            }
        }
    }
}
