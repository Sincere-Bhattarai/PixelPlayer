package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Lightweight DTO representing the current player state, synced from phone to watch
 * via the Wear Data Layer API.
 *
 * This is intentionally a subset of the full PlayerInfo — heavy fields like
 * album art bitmap, queue, lyrics, and theme colors are excluded.
 * Album art is sent as a separate Asset attached to the DataItem.
 */
@Serializable
data class WearPlayerState(
    val songId: String = "",
    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    /** 0 = OFF, 1 = ONE, 2 = ALL */
    val repeatMode: Int = 0,
    /** STREAM_MUSIC current volume level on phone side */
    val volumeLevel: Int = 0,
    /** STREAM_MUSIC max volume level on phone side */
    val volumeMax: Int = 0,
) {
    val isEmpty: Boolean
        get() = songId.isEmpty()
}
