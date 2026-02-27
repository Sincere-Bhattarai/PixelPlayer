package com.theveloper.pixelplay.presentation.viewmodel

import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.WearLocalPlayerRepository
import com.theveloper.pixelplay.data.WearOutputTarget
import com.theveloper.pixelplay.data.WearPlaybackController
import com.theveloper.pixelplay.data.WearStateRepository
import com.theveloper.pixelplay.data.WearVolumeRepository
import com.theveloper.pixelplay.shared.WearPlayerState
import com.theveloper.pixelplay.shared.WearThemePalette
import com.theveloper.pixelplay.shared.WearVolumeState
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Wear player screen.
 * Supports dual-mode playback:
 * - Remote: phone playback via WearPlaybackController (Phase 1)
 * - Local: standalone ExoPlayer via WearLocalPlayerRepository (Phase 3)
 *
 * When local playback is active, commands are routed to the local player.
 * The [playerState] flow combines both sources into a unified WearPlayerState.
 */
@HiltViewModel
class WearPlayerViewModel @Inject constructor(
    private val stateRepository: WearStateRepository,
    private val playbackController: WearPlaybackController,
    private val localPlayerRepository: WearLocalPlayerRepository,
    private val volumeRepository: WearVolumeRepository,
) : ViewModel() {
    companion object {
        private const val PHONE_SYNC_BOOTSTRAP_ATTEMPTS = 3
        private const val PHONE_SYNC_BOOTSTRAP_RETRY_DELAY_MS = 1200L
    }

    private val _sleepTimerUiState = MutableStateFlow(WearSleepTimerUiState())
    val sleepTimerUiState: StateFlow<WearSleepTimerUiState> = _sleepTimerUiState.asStateFlow()

    /** Whether local playback is currently active on the watch */
    val isLocalPlaybackActive: StateFlow<Boolean> = localPlayerRepository.isLocalPlaybackActive

    /** Current target selected by the user in Output screen */
    val outputTarget: StateFlow<WearOutputTarget> = stateRepository.outputTarget

    /** Whether UI is currently controlling the watch output */
    val isWatchOutputSelected: StateFlow<Boolean> = outputTarget
        .map { it == WearOutputTarget.WATCH }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Unified player state based on selected output target.
     */
    val playerState: StateFlow<WearPlayerState> = combine(
        stateRepository.outputTarget,
        localPlayerRepository.localPlayerState,
        stateRepository.playerState,
    ) { target, localState, remoteState ->
        when (target) {
            WearOutputTarget.WATCH -> {
                WearPlayerState(
                    songId = localState.songId,
                    songTitle = localState.songTitle,
                    artistName = localState.artistName,
                    albumName = localState.albumName,
                    isPlaying = localState.isPlaying,
                    currentPositionMs = localState.currentPositionMs,
                    totalDurationMs = localState.totalDurationMs,
                )
            }

            WearOutputTarget.PHONE -> remoteState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WearPlayerState())

    val albumArt: StateFlow<Bitmap?> = combine(
        stateRepository.outputTarget,
        stateRepository.albumArt,
        localPlayerRepository.localAlbumArt,
    ) { target, remoteAlbumArt, localAlbumArt ->
        if (target == WearOutputTarget.PHONE) remoteAlbumArt else localAlbumArt
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val paletteSeedArgb: StateFlow<Int?> = combine(
        stateRepository.outputTarget,
        localPlayerRepository.localPaletteSeedArgb,
    ) { target, localSeed ->
        if (target == WearOutputTarget.WATCH) localSeed else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val phoneThemePalette: StateFlow<WearThemePalette?> = playerState
        .map { it.themePalette }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPhoneConnected: StateFlow<Boolean> = stateRepository.isPhoneConnected
    val phoneVolumeState: StateFlow<WearVolumeState> = stateRepository.volumeState
    val watchVolumeState: StateFlow<WearVolumeState> = volumeRepository.watchVolumeState

    val activeVolumeState: StateFlow<WearVolumeState> = combine(
        isWatchOutputSelected,
        phoneVolumeState,
        watchVolumeState,
    ) { isWatchOutput, phoneState, watchState ->
        if (isWatchOutput) watchState else phoneState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WearVolumeState())

    val activeVolumePercent: StateFlow<Int> = activeVolumeState
        .map { state ->
            if (state.max <= 0) 0 else ((state.level.toFloat() / state.max.toFloat()) * 100f).toInt()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeVolumeDeviceName: StateFlow<String> = combine(
        isWatchOutputSelected,
        stateRepository.phoneDeviceName,
    ) { isWatchOutput, phoneDeviceName ->
        if (isWatchOutput) {
            Build.MODEL.takeIf { it.isNotBlank() } ?: "Watch"
        } else {
            phoneDeviceName.ifBlank { "Phone" }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Phone")

    init {
        viewModelScope.launch {
            outputTarget.collect {
                refreshActiveVolumeState()
            }
        }
        bootstrapPhoneStateSync()
    }

    private fun bootstrapPhoneStateSync() {
        viewModelScope.launch {
            if (isWatchOutputSelected.value) return@launch
            repeat(PHONE_SYNC_BOOTSTRAP_ATTEMPTS) {
                playbackController.requestPhoneVolumeState()
                if (hasRemotePhoneState()) {
                    return@launch
                }
                delay(PHONE_SYNC_BOOTSTRAP_RETRY_DELAY_MS)
            }
        }
    }

    private fun hasRemotePhoneState(): Boolean {
        val state = stateRepository.playerState.value
        return state.songId.isNotBlank() ||
            state.songTitle.isNotBlank() ||
            state.artistName.isNotBlank() ||
            state.isPlaying
    }

    fun selectOutput(target: WearOutputTarget) {
        if (target == WearOutputTarget.WATCH && !localPlayerRepository.isLocalPlaybackActive.value) {
            return
        }
        stateRepository.setOutputTarget(target)
        if (target == WearOutputTarget.PHONE && localPlayerRepository.localPlayerState.value.isPlaying) {
            localPlayerRepository.pause()
        }
    }

    fun togglePlayPause() {
        if (isWatchOutputSelected.value) {
            localPlayerRepository.togglePlayPause()
        } else {
            val current = stateRepository.playerState.value
            stateRepository.updatePlayerState(
                current.copy(isPlaying = !current.isPlaying)
            )
            playbackController.togglePlayPause()
        }
    }

    fun next() {
        if (isWatchOutputSelected.value) localPlayerRepository.next()
        else playbackController.next()
    }

    fun previous() {
        if (isWatchOutputSelected.value) localPlayerRepository.previous()
        else playbackController.previous()
    }

    fun toggleFavorite() {
        if (isWatchOutputSelected.value) return // Not supported for local playback
        val current = stateRepository.playerState.value
        playbackController.toggleFavorite(targetEnabled = !current.isFavorite)
    }

    fun toggleShuffle() {
        if (isWatchOutputSelected.value) return // Not supported for local playback
        playbackController.toggleShuffle()
    }

    fun cycleRepeat() {
        if (isWatchOutputSelected.value) return // Not supported for local playback
        playbackController.cycleRepeat()
    }

    fun volumeUp() {
        if (isWatchOutputSelected.value) {
            volumeRepository.volumeUpOnWatch()
        } else {
            stateRepository.nudgePhoneVolumeLevel(delta = 1)
            playbackController.volumeUp()
        }
    }

    fun volumeDown() {
        if (isWatchOutputSelected.value) {
            volumeRepository.volumeDownOnWatch()
        } else {
            stateRepository.nudgePhoneVolumeLevel(delta = -1)
            playbackController.volumeDown()
        }
    }

    fun refreshActiveVolumeState() {
        if (isWatchOutputSelected.value) {
            volumeRepository.refreshWatchVolumeState()
        } else {
            playbackController.requestPhoneVolumeState()
        }
    }

    fun setSleepTimerDuration(durationMinutes: Int) {
        if (durationMinutes <= 0 || !isPhoneConnected.value) return
        playbackController.setSleepTimerDuration(durationMinutes)
        _sleepTimerUiState.value = WearSleepTimerUiState(
            mode = WearSleepTimerMode.DURATION,
            durationMinutes = durationMinutes,
        )
    }

    fun setSleepTimerEndOfTrack(enabled: Boolean = true) {
        if (!isPhoneConnected.value) return
        playbackController.setSleepTimerEndOfTrack(enabled)
        _sleepTimerUiState.value = if (enabled) {
            WearSleepTimerUiState(mode = WearSleepTimerMode.END_OF_TRACK)
        } else {
            WearSleepTimerUiState()
        }
    }

    fun cancelSleepTimer() {
        if (!isPhoneConnected.value) return
        playbackController.cancelSleepTimer()
        _sleepTimerUiState.value = WearSleepTimerUiState()
    }

    /** Stop local playback and switch back to remote mode */
    fun stopLocalPlayback() {
        localPlayerRepository.release()
    }
}

data class WearSleepTimerUiState(
    val mode: WearSleepTimerMode = WearSleepTimerMode.OFF,
    val durationMinutes: Int = 0,
)

enum class WearSleepTimerMode {
    OFF,
    DURATION,
    END_OF_TRACK,
}
