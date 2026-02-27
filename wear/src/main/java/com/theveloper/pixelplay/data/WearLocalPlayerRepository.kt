package com.theveloper.pixelplay.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
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
import kotlinx.coroutines.withContext
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

data class WearQueueSong(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
)

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

    private val _localPaletteSeedArgb = MutableStateFlow<Int?>(null)
    val localPaletteSeedArgb: StateFlow<Int?> = _localPaletteSeedArgb.asStateFlow()

    private val _localAlbumArt = MutableStateFlow<Bitmap?>(null)
    val localAlbumArt: StateFlow<Bitmap?> = _localAlbumArt.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var currentQueueSongsById: Map<String, LocalSongEntity> = emptyMap()
    private var currentQueueItemsById: Map<String, WearQueueSong> = emptyMap()
    private var lastPaletteSongId: String = ""
    private var lastArtworkSongId: String = ""

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

            val queueSongs = playableSongs.map { song ->
                WearQueueSong(
                    songId = song.songId,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    uri = Uri.fromFile(File(song.localPath)),
                )
            }
            startPlayback(
                queueSongs = queueSongs,
                queueSongIdToLocal = playableSongs.associateBy { it.songId },
                startIndex = startIndex,
            )
        }
    }

    /**
     * Start local playback from watch MediaStore songs.
     */
    fun playUriSongs(songs: List<WearQueueSong>, startIndex: Int = 0) {
        scope.launch {
            if (songs.isEmpty()) {
                Timber.tag(TAG).w("No watch library songs available")
                return@launch
            }
            startPlayback(
                queueSongs = songs,
                queueSongIdToLocal = emptyMap(),
                startIndex = startIndex,
            )
        }
    }

    private suspend fun startPlayback(
        queueSongs: List<WearQueueSong>,
        queueSongIdToLocal: Map<String, LocalSongEntity>,
        startIndex: Int,
    ) {
        withContext(Dispatchers.Main) {
            val player = getOrCreatePlayer()
            currentQueueSongsById = queueSongIdToLocal
            currentQueueItemsById = queueSongs.associateBy { it.songId }
            if (queueSongIdToLocal.isEmpty()) {
                lastPaletteSongId = ""
                lastArtworkSongId = ""
                _localPaletteSeedArgb.value = null
                _localAlbumArt.value = null
            }

            val mediaItems = queueSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.songId)
                    .setUri(song.uri)
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
            updateState()
            Timber.tag(TAG).d(
                "Playing locally: ${queueSongs.getOrNull(startIndexSafe)?.title}, queue=${queueSongs.size}"
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
        _localPaletteSeedArgb.value = null
        _localAlbumArt.value = null
        currentQueueSongsById = emptyMap()
        currentQueueItemsById = emptyMap()
        lastPaletteSongId = ""
        lastArtworkSongId = ""
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
        updatePaletteForSong(currentItem?.mediaId.orEmpty())
        updateArtworkForSong(currentItem?.mediaId.orEmpty())
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

    private fun updatePaletteForSong(songId: String) {
        if (songId.isBlank()) {
            lastPaletteSongId = ""
            _localPaletteSeedArgb.value = null
            return
        }
        if (songId == lastPaletteSongId) return
        lastPaletteSongId = songId

        val queueSong = currentQueueSongsById[songId]
        val cachedSeed = queueSong?.paletteSeedArgb
        if (cachedSeed != null) {
            _localPaletteSeedArgb.value = cachedSeed
            return
        }

        _localPaletteSeedArgb.value = null
        if (queueSong != null) {
            scope.launch(Dispatchers.IO) {
                val extractedSeed = extractSeedFromLocalSong(queueSong)
                if (extractedSeed != null) {
                    runCatching { localSongDao.updatePaletteSeed(queueSong.songId, extractedSeed) }
                        .onFailure { error ->
                            Timber.tag(TAG).w(error, "Failed to persist local palette seed")
                        }
                }

                withContext(Dispatchers.Main) {
                    if (lastPaletteSongId != queueSong.songId) return@withContext
                    if (extractedSeed != null) {
                        currentQueueSongsById = currentQueueSongsById.toMutableMap().apply {
                            put(queueSong.songId, queueSong.copy(paletteSeedArgb = extractedSeed))
                        }
                    }
                    _localPaletteSeedArgb.value = extractedSeed
                }
            }
            return
        }

        val queueItem = currentQueueItemsById[songId] ?: return
        scope.launch(Dispatchers.IO) {
            val extractedSeed = extractSeedFromUri(queueItem.uri, queueItem.songId)
            withContext(Dispatchers.Main) {
                if (lastPaletteSongId != queueItem.songId) return@withContext
                _localPaletteSeedArgb.value = extractedSeed
            }
        }
    }

    private fun updateArtworkForSong(songId: String) {
        if (songId.isBlank()) {
            lastArtworkSongId = ""
            _localAlbumArt.value = null
            return
        }
        if (songId == lastArtworkSongId) return
        lastArtworkSongId = songId

        val queueSong = currentQueueSongsById[songId]
        if (queueSong != null) {
            scope.launch(Dispatchers.IO) {
                val bitmap = loadLocalAlbumArtBitmap(queueSong)
                withContext(Dispatchers.Main) {
                    if (lastArtworkSongId != queueSong.songId) return@withContext
                    _localAlbumArt.value = bitmap
                }
            }
            return
        }

        val queueItem = currentQueueItemsById[songId]
        if (queueItem == null) {
            _localAlbumArt.value = null
            return
        }
        scope.launch(Dispatchers.IO) {
            val bitmap = loadArtworkBitmapFromUri(queueItem.uri, queueItem.songId)
            withContext(Dispatchers.Main) {
                if (lastArtworkSongId != queueItem.songId) return@withContext
                _localAlbumArt.value = bitmap
            }
        }
    }

    private fun loadLocalAlbumArtBitmap(song: LocalSongEntity): Bitmap? {
        val fromStoredArtwork = song.artworkPath
            ?.takeIf { it.isNotBlank() }
            ?.let { artworkPath ->
                decodeBoundedBitmapFromFile(artworkPath, maxDimension = 1024)
            }
        if (fromStoredArtwork != null) return fromStoredArtwork

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(song.localPath)
            val embedded = retriever.embeddedPicture ?: return null
            decodeBoundedBitmapFromBytes(embedded, maxDimension = 1024)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load local artwork for songId=${song.songId}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeBoundedBitmapFromFile(path: String, maxDimension: Int): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            },
        )
    }

    private fun decodeBoundedBitmapFromBytes(data: ByteArray, maxDimension: Int): Bitmap? {
        if (data.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            },
        )
    }

    private fun extractSeedFromLocalSong(song: LocalSongEntity): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(song.localPath)
            val embedded = retriever.embeddedPicture ?: return null
            val bitmap = BitmapFactory.decodeByteArray(
                embedded,
                0,
                embedded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                },
            ) ?: return null

            try {
                extractSeedColorArgb(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to extract local artwork seed for songId=${song.songId}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractSeedFromUri(uri: Uri, songId: String): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(application, uri)
            val embedded = retriever.embeddedPicture ?: return null
            val bitmap = BitmapFactory.decodeByteArray(
                embedded,
                0,
                embedded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                },
            ) ?: return null

            try {
                extractSeedColorArgb(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to extract artwork seed from URI for songId=$songId")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun loadArtworkBitmapFromUri(uri: Uri, songId: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(application, uri)
            val embedded = retriever.embeddedPicture ?: return null
            decodeBoundedBitmapFromBytes(embedded, maxDimension = 1024)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load artwork from URI for songId=$songId")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractSeedColorArgb(bitmap: Bitmap): Int? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val step = (minOf(bitmap.width, bitmap.height) / 24).coerceAtLeast(1)
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0L

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= 28) {
                    val red = Color.red(pixel)
                    val green = Color.green(pixel)
                    val blue = Color.blue(pixel)
                    if (red + green + blue > 36) {
                        redSum += red
                        greenSum += green
                        blueSum += blue
                        count++
                    }
                }
                x += step
            }
            y += step
        }

        if (count == 0L) return null
        return Color.rgb(
            (redSum / count).toInt(),
            (greenSum / count).toInt(),
            (blueSum / count).toInt(),
        )
    }
}
