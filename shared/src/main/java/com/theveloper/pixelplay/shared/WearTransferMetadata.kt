package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Metadata about a song being transferred, sent from phone to watch
 * via MessageClient before the audio stream begins.
 * Contains all info needed to create a local database entry on the watch.
 */
@Serializable
data class WearTransferMetadata(
    val requestId: String,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val mimeType: String,
    val fileSize: Long,
    val bitrate: Int,
    val sampleRate: Int,
    /** Optional seed color extracted from album art on phone, used for offline watch theming. */
    val paletteSeedArgb: Int? = null,
    val error: String? = null,
)
