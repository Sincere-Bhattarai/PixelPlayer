package com.theveloper.pixelplay.shared

/**
 * Shared constants for Wear Data Layer API paths.
 * Used by both the phone app and the Wear OS app for communication.
 */
object WearDataPaths {
    /** DataItem path for player state (phone -> watch) */
    const val PLAYER_STATE = "/player_state"

    /** Message path for playback commands (watch -> phone) */
    const val PLAYBACK_COMMAND = "/playback_command"

    /** Message path for volume commands (watch -> phone) */
    const val VOLUME_COMMAND = "/volume_command"

    /** Message path for current volume state (phone -> watch) */
    const val VOLUME_STATE = "/volume_state"

    /** Key for the album art Asset within a DataItem */
    const val KEY_ALBUM_ART = "album_art"

    /** Key for the JSON state payload within a DataItem */
    const val KEY_STATE_JSON = "state_json"

    /** Key for timestamp to force DataItem updates */
    const val KEY_TIMESTAMP = "timestamp"

    /** Message path for library browse requests (watch -> phone) */
    const val BROWSE_REQUEST = "/browse_request"

    /** Message path for library browse responses (phone -> watch) */
    const val BROWSE_RESPONSE = "/browse_response"

    /** Message path for transfer requests (watch -> phone) */
    const val TRANSFER_REQUEST = "/transfer_request"

    /** Message path for transfer metadata (phone -> watch, sent before channel stream) */
    const val TRANSFER_METADATA = "/transfer_metadata"

    /** ChannelClient path for audio file streaming (phone -> watch) */
    const val TRANSFER_CHANNEL = "/transfer_audio"

    /** Message path for transfer progress updates (phone -> watch) */
    const val TRANSFER_PROGRESS = "/transfer_progress"

    /** Message path for transfer cancellation (watch -> phone) */
    const val TRANSFER_CANCEL = "/transfer_cancel"
}
