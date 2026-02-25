package com.theveloper.pixelplay.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a song that has been transferred from the phone
 * and stored locally on the watch for offline playback.
 */
@Entity(tableName = "local_songs")
data class LocalSongEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val mimeType: String,
    val fileSize: Long,
    val bitrate: Int,
    val sampleRate: Int,
    val paletteSeedArgb: Int? = null,
    val localPath: String,
    val transferredAt: Long,
)
