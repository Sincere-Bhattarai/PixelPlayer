package com.theveloper.pixelplay.data

import android.app.ActivityManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.SystemClock
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * Listens for DataItem changes from the phone app via the Wear Data Layer.
 * When the phone publishes a new player state, this service deserializes it
 * and updates the WearStateRepository.
 */
@AndroidEntryPoint
class WearDataListenerService : WearableListenerService() {

    @Inject
    lateinit var stateRepository: WearStateRepository

    @Inject
    lateinit var libraryRepository: WearLibraryRepository

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "WearDataListener"
        private const val AUTO_LAUNCH_COOLDOWN_MS = 15_000L

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
        val playbackJustStarted = !lastKnownPlaying && isNowPlaying
        val songChangedWhilePlaying =
            isNowPlaying && playerState.songId != lastAutoLaunchSongId

        lastKnownPlaying = isNowPlaying

        if (!playbackJustStarted && !songChangedWhilePlaying) return
        if (isWearPlayerOnTop()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastAutoLaunchElapsedMs < AUTO_LAUNCH_COOLDOWN_MS) return

        val intent = Intent(this, WearMainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("auto_open_reason", "phone_playback")
        }

        runCatching {
            startActivity(intent)
            lastAutoLaunchElapsedMs = now
            lastAutoLaunchSongId = playerState.songId
            Timber.tag(TAG).d("Auto-opened Wear player for active phone playback")
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to auto-open Wear player")
        }
    }

    private fun isWearPlayerOnTop(): Boolean {
        val activityManager = getSystemService(ActivityManager::class.java) ?: return false
        val topActivityClassName = activityManager
            .appTasks
            .firstOrNull()
            ?.taskInfo
            ?.topActivity
            ?.className
        return topActivityClassName == WearMainActivity::class.java.name
    }

    /**
     * Receives message responses from the phone (e.g., browse responses).
     * DataItem changes are handled by [onDataChanged]; this handles MessageClient responses.
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
            else -> {
                Timber.tag(TAG).d("Ignoring message on path: ${messageEvent.path}")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
