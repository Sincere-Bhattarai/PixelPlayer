package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.TransferState
import com.theveloper.pixelplay.data.WearDeviceMusicRepository
import com.theveloper.pixelplay.data.WearDeviceSong
import com.theveloper.pixelplay.data.WearLocalPlayerRepository
import com.theveloper.pixelplay.data.WearQueueSong
import com.theveloper.pixelplay.data.WearOutputTarget
import com.theveloper.pixelplay.data.WearStateRepository
import com.theveloper.pixelplay.data.WearTransferRepository
import com.theveloper.pixelplay.data.local.LocalSongEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing downloaded songs and local playback on the watch.
 * Provides state for the DownloadsScreen and download indicators in SongListScreen.
 */
@HiltViewModel
class WearDownloadsViewModel @Inject constructor(
    private val transferRepository: WearTransferRepository,
    private val deviceMusicRepository: WearDeviceMusicRepository,
    private val localPlayerRepository: WearLocalPlayerRepository,
    private val stateRepository: WearStateRepository,
) : ViewModel() {

    /** All locally stored songs (for DownloadsScreen) */
    val localSongs: StateFlow<List<LocalSongEntity>> = transferRepository.localSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Active transfer states */
    val activeTransfers: StateFlow<Map<String, TransferState>> =
        transferRepository.activeTransfers

    /** Set of song IDs already downloaded (for showing download indicators) */
    val downloadedSongIds: StateFlow<Set<String>> = transferRepository.downloadedSongIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _deviceSongs = MutableStateFlow<List<WearDeviceSong>>(emptyList())
    val deviceSongs: StateFlow<List<WearDeviceSong>> = _deviceSongs.asStateFlow()

    private val _isDeviceLibraryLoading = MutableStateFlow(false)
    val isDeviceLibraryLoading: StateFlow<Boolean> = _isDeviceLibraryLoading.asStateFlow()

    private val _deviceLibraryError = MutableStateFlow<String?>(null)
    val deviceLibraryError: StateFlow<String?> = _deviceLibraryError.asStateFlow()

    /**
     * Request download of a song from the phone to the watch.
     */
    fun requestDownload(songId: String) {
        transferRepository.requestTransfer(songId)
    }

    /**
     * Play a locally stored song, starting from it within the full downloads queue.
     */
    fun playLocalSong(songId: String) {
        val allSongs = localSongs.value
        val startIndex = allSongs.indexOfFirst { it.songId == songId }
        if (startIndex == -1 || allSongs.isEmpty()) return
        localPlayerRepository.playLocalSongs(allSongs, startIndex)
        stateRepository.setOutputTarget(WearOutputTarget.WATCH)
    }

    fun playDeviceSong(songId: String) {
        val songs = deviceSongs.value
        val startIndex = songs.indexOfFirst { it.songId == songId }
        if (startIndex == -1 || songs.isEmpty()) return
        localPlayerRepository.playUriSongs(
            songs = songs.map { song ->
                WearQueueSong(
                    songId = song.songId,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    uri = song.contentUri,
                )
            },
            startIndex = startIndex,
        )
        stateRepository.setOutputTarget(WearOutputTarget.WATCH)
    }

    fun refreshDeviceLibrary(hasPermission: Boolean) {
        viewModelScope.launch {
            if (!hasPermission) {
                _deviceSongs.value = emptyList()
                _isDeviceLibraryLoading.value = false
                _deviceLibraryError.value = "Allow audio access to read watch library"
                return@launch
            }

            _isDeviceLibraryLoading.value = true
            _deviceLibraryError.value = null
            runCatching {
                deviceMusicRepository.scanDeviceSongs()
            }.onSuccess { songs ->
                _deviceSongs.value = songs
            }.onFailure { error ->
                _deviceSongs.value = emptyList()
                _deviceLibraryError.value = error.message ?: "Failed to load watch library"
            }
            _isDeviceLibraryLoading.value = false
        }
    }

    /**
     * Delete a locally stored song (file + database entry).
     */
    fun deleteSong(songId: String) {
        viewModelScope.launch {
            transferRepository.deleteSong(songId)
        }
    }

    /**
     * Cancel an in-progress transfer.
     */
    fun cancelTransfer(requestId: String) {
        transferRepository.cancelTransfer(requestId)
    }
}
