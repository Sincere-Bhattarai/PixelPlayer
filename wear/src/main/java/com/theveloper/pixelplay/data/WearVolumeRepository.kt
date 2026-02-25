package com.theveloper.pixelplay.data

import android.app.Application
import android.content.Context
import android.media.AudioManager
import com.theveloper.pixelplay.shared.WearVolumeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides local (watch-side) STREAM_MUSIC volume state and controls.
 */
@Singleton
class WearVolumeRepository @Inject constructor(
    private val application: Application,
) {
    private val audioManager by lazy {
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val _watchVolumeState = MutableStateFlow(readCurrentVolumeState())
    val watchVolumeState: StateFlow<WearVolumeState> = _watchVolumeState.asStateFlow()

    fun refreshWatchVolumeState() {
        _watchVolumeState.value = readCurrentVolumeState()
    }

    fun volumeUpOnWatch() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            0,
        )
        refreshWatchVolumeState()
    }

    fun volumeDownOnWatch() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            0,
        )
        refreshWatchVolumeState()
    }

    private fun readCurrentVolumeState(): WearVolumeState {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return WearVolumeState(
            level = level.coerceIn(0, max.coerceAtLeast(0)),
            max = max.coerceAtLeast(0),
        )
    }
}
