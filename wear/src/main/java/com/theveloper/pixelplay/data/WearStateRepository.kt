package com.theveloper.pixelplay.data

import android.graphics.Bitmap
import com.theveloper.pixelplay.shared.WearPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class WearOutputTarget {
    PHONE,
    WATCH,
}

/**
 * Singleton repository that holds the current player state received from the phone.
 * Acts as the single source of truth for the Wear UI layer.
 */
@Singleton
class WearStateRepository @Inject constructor() {

    private val _playerState = MutableStateFlow(WearPlayerState())
    val playerState: StateFlow<WearPlayerState> = _playerState.asStateFlow()

    private val _albumArt = MutableStateFlow<Bitmap?>(null)
    val albumArt: StateFlow<Bitmap?> = _albumArt.asStateFlow()

    private val _isPhoneConnected = MutableStateFlow(false)
    val isPhoneConnected: StateFlow<Boolean> = _isPhoneConnected.asStateFlow()

    private val _outputTarget = MutableStateFlow(WearOutputTarget.PHONE)
    val outputTarget: StateFlow<WearOutputTarget> = _outputTarget.asStateFlow()

    fun updatePlayerState(state: WearPlayerState) {
        _playerState.value = state
    }

    fun updateAlbumArt(bitmap: Bitmap?) {
        _albumArt.value = bitmap
    }

    fun setPhoneConnected(connected: Boolean) {
        _isPhoneConnected.value = connected
    }

    fun setOutputTarget(target: WearOutputTarget) {
        _outputTarget.value = target
    }
}
