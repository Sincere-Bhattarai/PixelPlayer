package com.theveloper.pixelplay.data.service.wear

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.service.MusicNotificationProvider
import com.theveloper.pixelplay.shared.WearBrowseRequest
import com.theveloper.pixelplay.shared.WearBrowseResponse
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearLibraryItem
import com.theveloper.pixelplay.shared.WearPlaybackCommand
import com.theveloper.pixelplay.shared.WearVolumeCommand
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * WearableListenerService that receives commands from the Wear OS watch app.
 * Handles playback commands (play, pause, next, prev, etc.), volume commands,
 * and library browse requests.
 *
 * Commands are received via the Wear Data Layer MessageClient and forwarded
 * to the MusicService via MediaController or processed directly.
 */
@AndroidEntryPoint
class WearCommandReceiver : WearableListenerService() {

    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    private val json = Json { ignoreUnknownKeys = true }
    private var mediaController: MediaController? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "WearCommandReceiver"
        private const val MAX_SONGS = 500
        private const val MAX_ALBUMS = 200
        private const val MAX_ARTISTS = 200
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.tag(TAG).d("Received message on path: ${messageEvent.path}")

        when (messageEvent.path) {
            WearDataPaths.PLAYBACK_COMMAND -> handlePlaybackCommand(messageEvent)
            WearDataPaths.VOLUME_COMMAND -> handleVolumeCommand(messageEvent)
            WearDataPaths.BROWSE_REQUEST -> handleBrowseRequest(messageEvent)
            else -> Timber.tag(TAG).w("Unknown message path: ${messageEvent.path}")
        }
    }

    private fun handlePlaybackCommand(messageEvent: MessageEvent) {
        val commandJson = String(messageEvent.data, Charsets.UTF_8)
        val command = try {
            json.decodeFromString<WearPlaybackCommand>(commandJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse playback command")
            return
        }

        Timber.tag(TAG).d("Playback command: ${command.action}")

        when (command.action) {
            WearPlaybackCommand.PLAY_FROM_CONTEXT -> {
                handlePlayFromContext(command)
            }
            else -> {
                getOrBuildMediaController { controller ->
                    when (command.action) {
                        WearPlaybackCommand.PLAY -> controller.play()
                        WearPlaybackCommand.PAUSE -> controller.pause()
                        WearPlaybackCommand.TOGGLE_PLAY_PAUSE -> {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                        WearPlaybackCommand.NEXT -> controller.seekToNext()
                        WearPlaybackCommand.PREVIOUS -> controller.seekToPrevious()
                        WearPlaybackCommand.TOGGLE_SHUFFLE -> {
                            controller.sendCustomCommand(
                                SessionCommand(
                                    MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE,
                                    Bundle.EMPTY
                                ),
                                Bundle.EMPTY
                            )
                        }
                        WearPlaybackCommand.CYCLE_REPEAT -> {
                            controller.sendCustomCommand(
                                SessionCommand(
                                    MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE,
                                    Bundle.EMPTY
                                ),
                                Bundle.EMPTY
                            )
                        }
                        WearPlaybackCommand.TOGGLE_FAVORITE -> {
                            val targetEnabled = command.targetEnabled
                            if (targetEnabled == null) {
                                val sessionCommand = SessionCommand(
                                    MusicNotificationProvider.CUSTOM_COMMAND_LIKE,
                                    Bundle.EMPTY
                                )
                                controller.sendCustomCommand(sessionCommand, Bundle.EMPTY)
                            } else {
                                val args = Bundle().apply {
                                    putBoolean(MusicNotificationProvider.EXTRA_FAVORITE_ENABLED, targetEnabled)
                                }
                                val sessionCommand = SessionCommand(
                                    MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE,
                                    Bundle.EMPTY
                                )
                                controller.sendCustomCommand(sessionCommand, args)
                            }
                        }
                        else -> Timber.tag(TAG).w("Unknown playback action: ${command.action}")
                    }
                }
            }
        }
    }

    /**
     * Handle PLAY_FROM_CONTEXT: load songs for the given context, build a queue,
     * find the start index, and start playback.
     */
    private fun handlePlayFromContext(command: WearPlaybackCommand) {
        val songId = command.songId
        val contextType = command.contextType
        if (songId == null || contextType == null) {
            Timber.tag(TAG).w("PLAY_FROM_CONTEXT missing songId or contextType")
            return
        }

        scope.launch {
            try {
                val songs = getSongsForContext(contextType, command.contextId)
                if (songs.isEmpty()) {
                    Timber.tag(TAG).w("No songs found for context: $contextType / ${command.contextId}")
                    return@launch
                }

                val mediaItems = songs.map { MediaItemBuilder.build(it) }
                val startIndex = songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)

                getOrBuildMediaController { controller ->
                    controller.setMediaItems(mediaItems, startIndex, 0L)
                    controller.prepare()
                    controller.play()
                    Timber.tag(TAG).d(
                        "Playing from context: $contextType, song=${songs[startIndex].title}, " +
                            "queue size=${songs.size}"
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to handle PLAY_FROM_CONTEXT")
            }
        }
    }

    /**
     * Get songs for a given context type and optional context ID.
     */
    private suspend fun getSongsForContext(contextType: String, contextId: String?): List<Song> {
        return when (contextType) {
            "album" -> {
                val albumId = contextId?.toLongOrNull() ?: return emptyList()
                musicRepository.getSongsForAlbum(albumId).first()
            }
            "artist" -> {
                val artistId = contextId?.toLongOrNull() ?: return emptyList()
                musicRepository.getSongsForArtist(artistId).first()
            }
            "playlist" -> {
                val playlistId = contextId ?: return emptyList()
                val playlist = userPreferencesRepository.userPlaylistsFlow.first()
                    .find { it.id == playlistId } ?: return emptyList()
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                // Maintain playlist order
                val songsById = songs.associateBy { it.id }
                playlist.songIds.mapNotNull { id -> songsById[id] }
            }
            "favorites" -> {
                musicRepository.getFavoriteSongsOnce()
            }
            "all_songs" -> {
                musicRepository.getAllSongsOnce().take(MAX_SONGS)
            }
            else -> {
                Timber.tag(TAG).w("Unknown context type: $contextType")
                emptyList()
            }
        }
    }

    // ---- Browse request handling ----

    private fun handleBrowseRequest(messageEvent: MessageEvent) {
        val requestJson = String(messageEvent.data, Charsets.UTF_8)
        val request = try {
            json.decodeFromString<WearBrowseRequest>(requestJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse browse request")
            return
        }

        Timber.tag(TAG).d("Browse request: type=${request.browseType}, contextId=${request.contextId}")

        scope.launch {
            val response = try {
                val items = getBrowseItems(request.browseType, request.contextId)
                WearBrowseResponse(requestId = request.requestId, items = items)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to process browse request")
                WearBrowseResponse(
                    requestId = request.requestId,
                    error = e.message ?: "Unknown error"
                )
            }

            // Send response back to the watch
            try {
                val responseBytes = json.encodeToString(response).toByteArray(Charsets.UTF_8)
                val messageClient = Wearable.getMessageClient(this@WearCommandReceiver)
                messageClient.sendMessage(
                    messageEvent.sourceNodeId,
                    WearDataPaths.BROWSE_RESPONSE,
                    responseBytes
                ).await()
                Timber.tag(TAG).d(
                    "Sent browse response: ${response.items.size} items for ${request.browseType}"
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to send browse response")
            }
        }
    }

    /**
     * Get browse items based on the browse type, following the same pattern
     * as AutoMediaBrowseTree.
     */
    private suspend fun getBrowseItems(browseType: String, contextId: String?): List<WearLibraryItem> {
        return when (browseType) {
            WearBrowseRequest.ROOT -> listOf(
                WearLibraryItem("favorites", "Favorites", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("playlists", "Playlists", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("albums", "Albums", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("artists", "Artists", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("all_songs", "All Songs", "", WearLibraryItem.TYPE_CATEGORY),
            )

            WearBrowseRequest.ALBUMS -> {
                musicRepository.getAllAlbumsOnce()
                    .take(MAX_ALBUMS)
                    .map { album ->
                        WearLibraryItem(
                            id = album.id.toString(),
                            title = album.title,
                            subtitle = "${album.artist} · ${album.songCount} songs",
                            type = WearLibraryItem.TYPE_ALBUM,
                        )
                    }
            }

            WearBrowseRequest.ARTISTS -> {
                musicRepository.getAllArtistsOnce()
                    .take(MAX_ARTISTS)
                    .map { artist ->
                        WearLibraryItem(
                            id = artist.id.toString(),
                            title = artist.name,
                            subtitle = "${artist.songCount} songs",
                            type = WearLibraryItem.TYPE_ARTIST,
                        )
                    }
            }

            WearBrowseRequest.PLAYLISTS -> {
                userPreferencesRepository.userPlaylistsFlow.first()
                    .map { playlist ->
                        WearLibraryItem(
                            id = playlist.id,
                            title = playlist.name,
                            subtitle = "${playlist.songIds.size} songs",
                            type = WearLibraryItem.TYPE_PLAYLIST,
                        )
                    }
            }

            WearBrowseRequest.FAVORITES -> {
                musicRepository.getFavoriteSongsOnce()
                    .take(MAX_SONGS)
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.ALL_SONGS -> {
                musicRepository.getAllSongsOnce()
                    .take(MAX_SONGS)
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.ALBUM_SONGS -> {
                val albumId = contextId?.toLongOrNull()
                    ?: throw IllegalArgumentException("Missing albumId for ALBUM_SONGS")
                musicRepository.getSongsForAlbum(albumId).first()
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.ARTIST_SONGS -> {
                val artistId = contextId?.toLongOrNull()
                    ?: throw IllegalArgumentException("Missing artistId for ARTIST_SONGS")
                musicRepository.getSongsForArtist(artistId).first()
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.PLAYLIST_SONGS -> {
                val playlistId = contextId
                    ?: throw IllegalArgumentException("Missing playlistId for PLAYLIST_SONGS")
                val playlist = userPreferencesRepository.userPlaylistsFlow.first()
                    .find { it.id == playlistId }
                    ?: throw IllegalArgumentException("Playlist not found: $playlistId")
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                // Maintain playlist order
                val songsById = songs.associateBy { it.id }
                playlist.songIds
                    .mapNotNull { id -> songsById[id] }
                    .map { song -> song.toWearLibraryItem() }
            }

            else -> {
                Timber.tag(TAG).w("Unknown browse type: $browseType")
                emptyList()
            }
        }
    }

    private fun Song.toWearLibraryItem(): WearLibraryItem {
        return WearLibraryItem(
            id = id,
            title = title,
            subtitle = displayArtist,
            type = WearLibraryItem.TYPE_SONG,
        )
    }

    // ---- Volume handling ----

    private fun handleVolumeCommand(messageEvent: MessageEvent) {
        val commandJson = String(messageEvent.data, Charsets.UTF_8)
        val command = try {
            json.decodeFromString<WearVolumeCommand>(commandJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse volume command")
            return
        }

        Timber.tag(TAG).d("Volume command: direction=${command.direction}, value=${command.value}")

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val absoluteValue = command.value
        if (absoluteValue != null) {
            // Set absolute volume (scaled from 0-100 to device range)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (absoluteValue * maxVolume / 100).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        } else {
            when (command.direction) {
                WearVolumeCommand.UP -> audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    0
                )
                WearVolumeCommand.DOWN -> audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    0
                )
            }
        }
    }

    // ---- MediaController management ----

    /**
     * Get existing MediaController or build a new one, then execute the action.
     */
    private fun getOrBuildMediaController(action: (MediaController) -> Unit) {
        runOnMainThread {
            val existing = mediaController
            if (existing != null && existing.isConnected) {
                action(existing)
                return@runOnMainThread
            }

            val inFlight = mediaControllerFuture
            if (inFlight != null && !inFlight.isDone) {
                inFlight.addListener(
                    {
                        try {
                            action(inFlight.get())
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Failed to reuse pending MediaController")
                        }
                    },
                    ContextCompat.getMainExecutor(this)
                )
                return@runOnMainThread
            }

            val sessionToken = SessionToken(
                this,
                ComponentName(this, MusicService::class.java)
            )
            val future = MediaController.Builder(this, sessionToken)
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync()
            mediaControllerFuture = future
            future.addListener(
                {
                    try {
                        val controller = future.get()
                        mediaController = controller
                        action(controller)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to build MediaController")
                    }
                },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun releaseController() {
        val controller = mediaController
        mediaController = null
        mediaControllerFuture = null
        if (controller != null) {
            try {
                controller.release()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to release MediaController")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runOnMainThread { releaseController() }
        super.onDestroy()
    }
}
