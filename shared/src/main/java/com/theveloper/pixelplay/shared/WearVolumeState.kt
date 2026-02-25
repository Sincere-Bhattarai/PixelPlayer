package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Lightweight DTO for syncing STREAM_MUSIC volume between phone and watch.
 */
@Serializable
data class WearVolumeState(
    val level: Int = 0,
    val max: Int = 0,
)
