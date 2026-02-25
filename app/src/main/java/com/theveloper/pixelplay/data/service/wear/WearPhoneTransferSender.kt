package com.theveloper.pixelplay.data.service.wear

import android.app.Application
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.theveloper.pixelplay.shared.WearCapabilities
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearTransferRequest
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearPhoneTransferSender @Inject constructor(
    private val application: Application,
) {
    private val capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(application) }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun isPixelPlayWatchAvailable(): Boolean {
        return runCatching {
            val capability = capabilityClient.getCapability(
                WearCapabilities.PIXELPLAY_WEAR_APP,
                CapabilityClient.FILTER_REACHABLE,
            ).await()
            capability.nodes.isNotEmpty()
        }.getOrElse { error ->
            Timber.tag(TAG).w(error, "Failed checking PixelPlay Wear availability")
            false
        }
    }

    suspend fun requestSongTransfer(songId: String): Result<Int> {
        return runCatching {
            val capability = capabilityClient.getCapability(
                WearCapabilities.PIXELPLAY_WEAR_APP,
                CapabilityClient.FILTER_REACHABLE,
            ).await()

            val nodes = capability.nodes
            if (nodes.isEmpty()) {
                error("No reachable watch with PixelPlay")
            }

            val request = WearTransferRequest(
                requestId = UUID.randomUUID().toString(),
                songId = songId,
            )
            val payload = json.encodeToString(request).toByteArray(Charsets.UTF_8)

            nodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    WearDataPaths.TRANSFER_REQUEST,
                    payload,
                ).await()
            }
            nodes.size
        }
    }

    private companion object {
        const val TAG = "WearPhoneTransfer"
    }
}
