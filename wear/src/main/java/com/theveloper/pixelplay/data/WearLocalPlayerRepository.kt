package com.theveloper.pixelplay.data

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.theveloper.pixelplay.data.local.LocalSongDao
import com.theveloper.pixelplay.data.local.LocalSongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local player state mirroring WearPlayerState structure for unified UI.
 */
data class WearLocalPlayerState(
    val songId: String = "",
    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
) {
    val isEmpty: Boolean get() = songId.isEmpty()
}

/**
 * Repository managing ExoPlayer for standalone local playback on the watch.
 * Plays audio files that have been transferred from the phone and stored locally.
 *
 * This is a simple ExoPlayer wrapper (no MediaSession/MusicService for MVP).
 * MediaSession can be added later if media notification support is needed.
 */
@Singleton
class WearLocalPlayerRepository @Inject constructor(
    private val application: Application,
    private val localSongDao: LocalSongDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exoPlayer: ExoPlayer? = null

    private val _localPlayerState = MutableStateFlow(WearLocalPlayerState())
    val localPlayerState: StateFlow<WearLocalPlayerState> = _localPlayerState.asStateFlow()

    private val _isLocalPlaybackActive = MutableStateFlow(false)
    val isLocalPlaybackActive: StateFlow<Boolean> = _isLocalPlaybackActive.asStateFlow()

    private var positionUpdateJob: Job? = null

    companion object {
        private const val TAG = "WearLocalPlayer"
        private const val POSITION_UPDATE_INTERVAL_MS = 1000L
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(application).build().also { player ->
            exoPlayer = player
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                    if (playbackState == Player.STATE_ENDED) {
                        stopPositionUpdates()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                    if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateState()
                }
            })
            Timber.tag(TAG).d("ExoPlayer created")
        }
    }

    /**
     * Start local playback with the given songs, beginning at [startIndex].
     */
    fun playLocalSongs(songs: List<LocalSongEntity>, startIndex: Int = 0) {
        scope.launch {
            val playableSongs = songs.filter { song ->
                val file = File(song.localPath)
                file.isFile && file.length() > 0L
            }
            if (playableSongs.isEmpty()) {
                Timber.tag(TAG).w("No playable local files available")
                return@launch
            }

            val player = getOrCreatePlayer()
            val mediaItems = playableSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.songId)
                    .setUri(Uri.fromFile(File(song.localPath)))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                    .build()
            }
            val startIndexSafe = startIndex.coerceIn(0, mediaItems.lastIndex)
            player.setMediaItems(mediaItems, startIndexSafe, 0L)
            player.prepare()
            player.play()
            _isLocalPlaybackActive.value = true
            Timber.tag(TAG).d(
                "Playing locally: ${playableSongs.getOrNull(startIndexSafe)?.title}, queue=${playableSongs.size}"
            )
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun next() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    fun previous() {
        val player = exoPlayer ?: return
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    /**
     * Stop local playback and release the player.
     */
    fun release() {
        stopPositionUpdates()
        exoPlayer?.release()
        exoPlayer = null
        _isLocalPlaybackActive.value = false
        _localPlayerState.value = WearLocalPlayerState()
        Timber.tag(TAG).d("ExoPlayer released")
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem
        _localPlayerState.value = WearLocalPlayerState(
            songId = currentItem?.mediaId ?: "",
            songTitle = currentItem?.mediaMetadata?.title?.toString() ?: "",
            artistName = currentItem?.mediaMetadata?.artist?.toString() ?: "",
            albumName = currentItem?.mediaMetadata?.albumTitle?.toString() ?: "",
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            totalDurationMs = player.duration.coerceAtLeast(0L),
        )
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL_MS)
                updateState()
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
}
