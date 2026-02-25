package com.theveloper.pixelplay.data

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.theveloper.pixelplay.presentation.WearMainActivity
import com.theveloper.pixelplay.shared.WearBrowseResponse
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearPlayerState
import com.theveloper.pixelplay.shared.WearTransferMetadata
import com.theveloper.pixelplay.shared.WearTransferProgress
import com.theveloper.pixelplay.shared.WearTransferRequest
import com.theveloper.pixelplay.shared.WearVolumeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Listens for DataItem changes, MessageClient messages, and ChannelClient events
 * from the phone app via the Wear Data Layer.
 *
 * Handles:
 * - Player state updates (DataItem)
 * - Browse responses (MessageClient)
 * - Transfer metadata and progress (MessageClient)
 * - Audio file streaming (ChannelClient)
 */
@AndroidEntryPoint
class WearDataListenerService : WearableListenerService() {

    @Inject
    lateinit var stateRepository: WearStateRepository

    @Inject
    lateinit var libraryRepository: WearLibraryRepository

    @Inject
    lateinit var transferRepository: WearTransferRepository

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "WearDataListener"
        private const val AUTO_LAUNCH_COOLDOWN_MS = 2_500L
        private const val AUTO_LAUNCH_KEEP_ALIVE_MS = 7_000L

        @Volatile
        private var lastAutoLaunchElapsedMs = 0L

        @Volatile
        private var lastAutoLaunchSongId = ""

        @Volatile
        private var lastKnownPlaying = false
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Timber.tag(TAG).d("onDataChanged: ${dataEvents.count} events")

        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach

            val dataItem = event.dataItem
            if (dataItem.uri.path == WearDataPaths.PLAYER_STATE) {
                // Copy DataMap in callback thread; DataEventBuffer is invalid once callback returns.
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                scope.launch {
                    try {
                        processPlayerStateUpdate(dataMap)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to process player state update")
                    }
                }
            }
        }
    }

    private suspend fun processPlayerStateUpdate(dataMap: DataMap) {
        val stateJson = dataMap.getString(WearDataPaths.KEY_STATE_JSON).orEmpty()

        if (stateJson.isEmpty()) {
            // Empty state means playback stopped / service destroyed
            stateRepository.updatePlayerState(WearPlayerState())
            stateRepository.updateAlbumArt(null)
            Timber.tag(TAG).d("Received empty state (playback stopped)")
            return
        }

        val playerState = json.decodeFromString<WearPlayerState>(stateJson)
        stateRepository.updatePlayerState(playerState)
        stateRepository.setPhoneConnected(true)
        Timber.tag(TAG).d("Updated state: ${playerState.songTitle} (playing=${playerState.isPlaying})")
        maybeAutoLaunchPlayer(playerState)

        // Extract album art Asset
        if (dataMap.containsKey(WearDataPaths.KEY_ALBUM_ART)) {
            val asset = dataMap.getAsset(WearDataPaths.KEY_ALBUM_ART)
            if (asset != null) {
                try {
                    val dataClient = Wearable.getDataClient(this@WearDataListenerService)
                    val response = dataClient.getFdForAsset(asset).await()
                    val inputStream = response.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    stateRepository.updateAlbumArt(bitmap)
                    Timber.tag(TAG).d("Album art updated")
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to load album art asset")
                    stateRepository.updateAlbumArt(null)
                }
            }
        } else {
            stateRepository.updateAlbumArt(null)
        }
    }

    private fun maybeAutoLaunchPlayer(playerState: WearPlayerState) {
        val isNowPlaying = playerState.isPlaying && playerState.songId.isNotEmpty()
        if (!isNowPlaying) {
            lastKnownPlaying = false
            return
        }
        if (WearMainActivity.isForeground) {
            lastKnownPlaying = true
            return
        }

        val playbackJustStarted = !lastKnownPlaying
        val songChangedWhilePlaying = playerState.songId != lastAutoLaunchSongId

        val now = SystemClock.elapsedRealtime()
        val keepAliveExpired = now - lastAutoLaunchElapsedMs >= AUTO_LAUNCH_KEEP_ALIVE_MS
        val shouldAutoOpen = playbackJustStarted || songChangedWhilePlaying || keepAliveExpired
        if (!shouldAutoOpen) {
            lastKnownPlaying = true
            return
        }
        if (now - lastAutoLaunchElapsedMs < AUTO_LAUNCH_COOLDOWN_MS) {
            lastKnownPlaying = true
            return
        }

        val intent = Intent(this, WearMainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra("auto_open_reason", "phone_playback")
        }

        runCatching {
            startActivity(intent)
            lastAutoLaunchElapsedMs = now
            lastAutoLaunchSongId = playerState.songId
            lastKnownPlaying = true
            Timber.tag(TAG).d("Auto-opened Wear player for active phone playback")
        }.onFailure { e ->
            lastKnownPlaying = true
            Timber.tag(TAG).w(e, "Failed to auto-open Wear player")
        }
    }

    /**
     * Receives message responses from the phone (browse responses, transfer metadata/progress).
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearDataPaths.BROWSE_RESPONSE -> {
                try {
                    val responseJson = String(messageEvent.data, Charsets.UTF_8)
                    val response = json.decodeFromString<WearBrowseResponse>(responseJson)
                    libraryRepository.onBrowseResponseReceived(response)
                    Timber.tag(TAG).d(
                        "Received browse response: ${response.items.size} items, " +
                            "requestId=${response.requestId}"
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to process browse response")
                }
            }

            WearDataPaths.TRANSFER_METADATA -> {
                try {
                    val metadataJson = String(messageEvent.data, Charsets.UTF_8)
                    val metadata = json.decodeFromString<WearTransferMetadata>(metadataJson)
                    transferRepository.onMetadataReceived(metadata)
                    Timber.tag(TAG).d(
                        "Received transfer metadata: ${metadata.title}, " +
                            "fileSize=${metadata.fileSize}, requestId=${metadata.requestId}"
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to process transfer metadata")
                }
            }

            WearDataPaths.TRANSFER_PROGRESS -> {
                try {
                    val progressJson = String(messageEvent.data, Charsets.UTF_8)
                    val progress = json.decodeFromString<WearTransferProgress>(progressJson)
                    transferRepository.onProgressReceived(progress)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to process transfer progress")
                }
            }

            WearDataPaths.TRANSFER_REQUEST -> {
                try {
                    val requestJson = String(messageEvent.data, Charsets.UTF_8)
                    val request = json.decodeFromString<WearTransferRequest>(requestJson)
                    transferRepository.requestTransfer(request.songId)
                    Timber.tag(TAG).d("Received phone transfer request for songId=${request.songId}")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to process transfer request")
                }
            }

            WearDataPaths.VOLUME_STATE -> {
                try {
                    val volumeJson = String(messageEvent.data, Charsets.UTF_8)
                    val volumeState = json.decodeFromString<WearVolumeState>(volumeJson)
                    stateRepository.updateVolumeState(
                        level = volumeState.level,
                        max = volumeState.max,
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to process volume state update")
                }
            }

            else -> {
                Timber.tag(TAG).d("Ignoring message on path: ${messageEvent.path}")
            }
        }
    }

    /**
     * Called when a ChannelClient channel is opened by the phone for audio file transfer.
     * Reads the requestId header from the stream, then delegates to WearTransferRepository
     * to write the audio data to local storage.
     */
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != WearDataPaths.TRANSFER_CHANNEL) {
            Timber.tag(TAG).d("Ignoring channel on path: ${channel.path}")
            return
        }

        Timber.tag(TAG).d("Transfer channel opened")

        scope.launch {
            try {
                val channelClient = Wearable.getChannelClient(this@WearDataListenerService)
                val inputStream = channelClient.getInputStream(channel).await()

                // Read header: requestId length (4 bytes big-endian) + requestId bytes
                val lengthBytes = ByteArray(4)
                var totalRead = 0
                while (totalRead < 4) {
                    val read = inputStream.read(lengthBytes, totalRead, 4 - totalRead)
                    if (read == -1) throw Exception("Stream ended before requestId length")
                    totalRead += read
                }
                val idLength = ByteBuffer.wrap(lengthBytes).int

                val requestIdBytes = ByteArray(idLength)
                totalRead = 0
                while (totalRead < idLength) {
                    val read = inputStream.read(requestIdBytes, totalRead, idLength - totalRead)
                    if (read == -1) throw Exception("Stream ended before requestId")
                    totalRead += read
                }
                val requestId = String(requestIdBytes, Charsets.UTF_8)

                Timber.tag(TAG).d("Transfer channel: requestId=$requestId")

                // Delegate to repository to write the audio data to disk
                transferRepository.onChannelOpened(requestId, inputStream)

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to receive transfer channel")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
