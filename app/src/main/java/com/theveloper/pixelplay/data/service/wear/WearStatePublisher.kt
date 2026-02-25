package com.theveloper.pixelplay.data.service.wear

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes player state to the Wear Data Layer so the watch app can display it.
 *
 * Album art is sent as an Asset (compressed 100x100 JPEG) to save battery and bandwidth.
 */
@Singleton
class WearStatePublisher @Inject constructor(
    private val application: Application,
) {
    private val dataClient by lazy { Wearable.getDataClient(application) }
    private val audioManager by lazy {
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "WearStatePublisher"
        private const val ART_SIZE = 100 // px, small for watch
        private const val ART_QUALITY = 80 // JPEG quality
    }

    /**
     * Publish the current player state to Wear Data Layer.
     * Converts PlayerInfo -> WearPlayerState (lightweight DTO) and sends as DataItem.
     *
     * @param songId The current media item's ID
     * @param playerInfo The full player info from MusicService
     */
    fun publishState(songId: String?, playerInfo: PlayerInfo) {
        scope.launch {
            try {
                publishStateInternal(songId, playerInfo)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to publish state to Wear Data Layer")
            }
        }
    }

    /**
     * Clear state from the Data Layer (e.g. when service is destroyed).
     */
    fun clearState() {
        scope.launch {
            try {
                val request = PutDataMapRequest.create(WearDataPaths.PLAYER_STATE).apply {
                    dataMap.putString(WearDataPaths.KEY_STATE_JSON, "")
                    dataMap.putLong(WearDataPaths.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                dataClient.putDataItem(request)
                Timber.tag(TAG).d("Cleared Wear player state")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to clear Wear state")
            }
        }
    }

    private suspend fun publishStateInternal(songId: String?, playerInfo: PlayerInfo) {
        val volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val wearState = WearPlayerState(
            songId = songId.orEmpty(),
            songTitle = playerInfo.songTitle,
            artistName = playerInfo.artistName,
            albumName = "", // Album name not in PlayerInfo; will be enriched in future phases
            isPlaying = playerInfo.isPlaying,
            currentPositionMs = playerInfo.currentPositionMs,
            totalDurationMs = playerInfo.totalDurationMs,
            isFavorite = playerInfo.isFavorite,
            isShuffleEnabled = playerInfo.isShuffleEnabled,
            repeatMode = playerInfo.repeatMode,
            volumeLevel = volumeLevel,
            volumeMax = volumeMax,
        )

        val stateJson = json.encodeToString(wearState)

        val request = PutDataMapRequest.create(WearDataPaths.PLAYER_STATE).apply {
            dataMap.putString(WearDataPaths.KEY_STATE_JSON, stateJson)
            dataMap.putLong(WearDataPaths.KEY_TIMESTAMP, System.currentTimeMillis())

            // Attach album art as Asset if available
            val artAsset = createAlbumArtAsset(playerInfo.albumArtBitmapData)
            if (artAsset != null) {
                dataMap.putAsset(WearDataPaths.KEY_ALBUM_ART, artAsset)
            } else {
                dataMap.remove(WearDataPaths.KEY_ALBUM_ART)
            }
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
        Timber.tag(TAG).d("Published state to Wear: ${wearState.songTitle} (playing=${wearState.isPlaying})")
    }

    /**
     * Compress album art to a small JPEG suitable for Wear OS display.
     */
    private fun createAlbumArtAsset(artBitmapData: ByteArray?): Asset? {
        if (artBitmapData == null || artBitmapData.isEmpty()) return null

        return try {
            val original = BitmapFactory.decodeByteArray(artBitmapData, 0, artBitmapData.size)
                ?: return null

            // Scale down to ART_SIZE x ART_SIZE for watch display
            val scaled = Bitmap.createScaledBitmap(original, ART_SIZE, ART_SIZE, true)
            if (scaled !== original) {
                original.recycle()
            }

            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, ART_QUALITY, stream)
            scaled.recycle()

            Asset.createFromBytes(stream.toByteArray())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to create album art asset")
            null
        }
    }
}
