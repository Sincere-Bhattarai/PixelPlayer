package com.theveloper.pixelplay.data

import android.app.Application
import android.webkit.MimeTypeMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.theveloper.pixelplay.data.local.LocalSongDao
import com.theveloper.pixelplay.data.local.LocalSongEntity
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearTransferMetadata
import com.theveloper.pixelplay.shared.WearTransferProgress
import com.theveloper.pixelplay.shared.WearTransferRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the current state of an active transfer.
 */
data class TransferState(
    val requestId: String,
    val songId: String,
    val songTitle: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val status: String,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f
}

/**
 * Repository managing song transfers from phone to watch.
 *
 * Transfer flow:
 * 1. [requestTransfer] sends a WearTransferRequest to the phone
 * 2. Phone validates and sends WearTransferMetadata back ([onMetadataReceived])
 * 3. Phone opens a ChannelClient stream and sends audio data
 * 4. Watch receives via [onChannelOpened], writes to disk, inserts into Room
 * 5. Progress updates arrive via [onProgressReceived] during streaming
 */
@Singleton
class WearTransferRepository @Inject constructor(
    private val application: Application,
    private val localSongDao: LocalSongDao,
    private val messageClient: MessageClient,
    private val nodeClient: NodeClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    /** Observable map of active transfers: requestId -> TransferState */
    private val _activeTransfers = MutableStateFlow<Map<String, TransferState>>(emptyMap())
    val activeTransfers: StateFlow<Map<String, TransferState>> = _activeTransfers.asStateFlow()

    /**
     * All locally stored songs that still have a valid file on disk.
     * Any stale DB rows (missing/empty files) are cleaned up automatically.
     */
    val localSongs: Flow<List<LocalSongEntity>> = localSongDao.getAllSongs()
        .transform { songs ->
            val (validSongs, staleSongs) = songs.partition { it.hasPlayableLocalFile() }
            if (staleSongs.isNotEmpty()) {
                staleSongs.forEach { stale ->
                    stale.artworkPath?.let { artworkPath ->
                        runCatching { File(artworkPath).delete() }
                    }
                    localSongDao.deleteById(stale.songId)
                }
                Timber.tag(TAG).w("Removed ${staleSongs.size} stale local song entries from DB")
            }
            emit(validSongs)
        }

    /** Set of song IDs that are already downloaded and still valid on disk */
    val downloadedSongIds: Flow<Set<String>> = localSongs
        .map { songs -> songs.map { it.songId }.toSet() }

    /** Pending metadata awaiting channel stream: requestId -> metadata */
    private val pendingMetadata = ConcurrentHashMap<String, WearTransferMetadata>()

    /** Mapping from songId -> requestId for tracking which song is being transferred */
    private val songToRequestId = ConcurrentHashMap<String, String>()

    /** Artwork bytes received before/while audio transfer: requestId -> bytes */
    private val pendingArtworkByRequestId = ConcurrentHashMap<String, ByteArray>()

    /** Failsafe timeout per transfer to avoid hanging states at 0%. */
    private val transferWatchdogs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val TAG = "WearTransferRepo"
        private const val ARTWORK_FILE_EXTENSION = "jpg"
        private const val TRANSFER_IDLE_TIMEOUT_MS = 20_000L
    }

    /**
     * Request transfer of a song from the phone.
     * Sends a WearTransferRequest via MessageClient.
     */
    fun requestTransfer(songId: String) {
        // Don't request if already transferring this song
        if (songToRequestId.containsKey(songId)) {
            Timber.tag(TAG).d("Transfer already in progress for songId=$songId")
            return
        }

        scope.launch {
            val requestId = UUID.randomUUID().toString()
            songToRequestId[songId] = requestId

            _activeTransfers.update { map ->
                map + (requestId to TransferState(
                    requestId = requestId,
                    songId = songId,
                    songTitle = "",
                    bytesTransferred = 0,
                    totalBytes = 0,
                    status = WearTransferProgress.STATUS_TRANSFERRING,
                ))
            }
            armTransferWatchdog(requestId, songId)

            try {
                val request = WearTransferRequest(requestId, songId)
                val requestBytes = json.encodeToString(request).toByteArray(Charsets.UTF_8)

                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    handleTransferError(requestId, songId, "Phone not connected")
                    return@launch
                }

                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WearDataPaths.TRANSFER_REQUEST,
                        requestBytes,
                    ).await()
                }
                Timber.tag(TAG).d("Transfer requested: songId=$songId, requestId=$requestId")
            } catch (e: Exception) {
                handleTransferError(requestId, songId, e.message ?: "Failed to send request")
            }
        }
    }

    /**
     * Called when metadata arrives from the phone (before the audio channel opens).
     */
    fun onMetadataReceived(metadata: WearTransferMetadata) {
        val errorMsg = metadata.error
        if (errorMsg != null) {
            Timber.tag(TAG).w("Transfer rejected by phone: $errorMsg")
            handleTransferError(metadata.requestId, metadata.songId, errorMsg)
            return
        }

        pendingMetadata[metadata.requestId] = metadata
        armTransferWatchdog(metadata.requestId, metadata.songId)
        _activeTransfers.update { map ->
            val current = map[metadata.requestId] ?: return@update map
            map + (metadata.requestId to current.copy(
                songTitle = metadata.title,
                totalBytes = metadata.fileSize,
            ))
        }
        Timber.tag(TAG).d(
            "Metadata received: ${metadata.title} (${metadata.fileSize} bytes)"
        )
    }

    /**
     * Called when progress updates arrive from the phone during streaming.
     */
    fun onProgressReceived(progress: WearTransferProgress) {
        _activeTransfers.update { map ->
            val current = map[progress.requestId] ?: return@update map
            map + (progress.requestId to current.copy(
                bytesTransferred = progress.bytesTransferred,
                totalBytes = progress.totalBytes,
                status = progress.status,
                error = progress.error,
            ))
        }

        if (progress.status == WearTransferProgress.STATUS_FAILED) {
            handleTransferError(progress.requestId, progress.songId, progress.error ?: "Transfer failed")
        } else if (
            progress.status == WearTransferProgress.STATUS_COMPLETED ||
            progress.status == WearTransferProgress.STATUS_CANCELLED
        ) {
            clearTransferWatchdog(progress.requestId)
        } else {
            armTransferWatchdog(progress.requestId, progress.songId)
        }
    }

    /**
     * Called when a ChannelClient channel is opened by the phone.
     * Reads the audio stream, writes it to local storage, and inserts into Room.
     */
    suspend fun onChannelOpened(requestId: String, inputStream: InputStream) {
        val metadata = pendingMetadata.remove(requestId)
        if (metadata == null) {
            Timber.tag(TAG).w("No pending metadata for requestId=$requestId")
            inputStream.close()
            return
        }

        val musicDir = File(application.filesDir, "music")
        if (!musicDir.exists()) musicDir.mkdirs()

        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(metadata.mimeType) ?: "mp3"
        val localFile = File(musicDir, "${metadata.songId}.$extension")
        val previousSong = localSongDao.getSongById(metadata.songId)

        try {
            armTransferWatchdog(requestId, metadata.songId)
            localFile.outputStream().use { fileOut ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fileOut.write(buffer, 0, bytesRead)
                }
            }
            inputStream.close()

            // Verify file size
            val actualSize = localFile.length()
            if (actualSize == 0L) {
                localFile.delete()
                handleTransferError(requestId, metadata.songId, "Empty file received")
                return
            }

            val artworkPath = consumeAndPersistPendingArtwork(
                requestId = requestId,
                songId = metadata.songId,
            )

            // Insert into Room database
            localSongDao.insert(
                LocalSongEntity(
                    songId = metadata.songId,
                    title = metadata.title,
                    artist = metadata.artist,
                    album = metadata.album,
                    albumId = metadata.albumId,
                    duration = metadata.duration,
                    mimeType = metadata.mimeType,
                    fileSize = actualSize,
                    bitrate = metadata.bitrate,
                    sampleRate = metadata.sampleRate,
                    paletteSeedArgb = metadata.paletteSeedArgb,
                    artworkPath = artworkPath,
                    localPath = localFile.absolutePath,
                    transferredAt = System.currentTimeMillis(),
                )
            )

            cleanupReplacedSongFiles(
                previousSong = previousSong,
                currentAudioPath = localFile.absolutePath,
                currentArtworkPath = artworkPath,
            )

            // Clean up transfer state
            _activeTransfers.update { it - requestId }
            songToRequestId.remove(metadata.songId)
            clearTransferWatchdog(requestId)

            Timber.tag(TAG).d(
                "Transfer complete: ${metadata.title} ($actualSize bytes) → ${localFile.absolutePath}"
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to write transferred file")
            localFile.delete()
            handleTransferError(requestId, metadata.songId, e.message ?: "Write failed")
        }
    }

    /**
     * Delete a locally stored song (file + Room entry).
     */
    suspend fun deleteSong(songId: String) {
        val song = localSongDao.getSongById(songId) ?: return
        val file = File(song.localPath)
        if (file.exists()) file.delete()
        song.artworkPath?.let { artwork ->
            val artworkFile = File(artwork)
            if (artworkFile.exists()) artworkFile.delete()
        }
        localSongDao.deleteById(songId)
        Timber.tag(TAG).d("Deleted local song: ${song.title}")
    }

    /**
     * Get total storage used by transferred songs.
     */
    suspend fun getStorageUsed(): Long {
        return localSongDao.getTotalStorageUsed() ?: 0L
    }

    /**
     * Cancel an in-progress transfer.
     */
    fun cancelTransfer(requestId: String) {
        scope.launch {
            val state = _activeTransfers.value[requestId] ?: return@launch
            try {
                val cancelRequest = WearTransferRequest(requestId, state.songId)
                val cancelBytes = json.encodeToString(cancelRequest).toByteArray(Charsets.UTF_8)
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WearDataPaths.TRANSFER_CANCEL,
                        cancelBytes,
                    ).await()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to send cancel request")
            }
            _activeTransfers.update { it - requestId }
            songToRequestId.remove(state.songId)
            pendingMetadata.remove(requestId)
            pendingArtworkByRequestId.remove(requestId)
            clearTransferWatchdog(requestId)
        }
    }

    /**
     * Called when artwork bytes arrive over the dedicated artwork channel.
     * If song row exists, artwork is persisted immediately; otherwise cached until audio finishes.
     */
    suspend fun onArtworkReceived(requestId: String, songId: String, artworkBytes: ByteArray) {
        if (artworkBytes.isEmpty()) return
        armTransferWatchdog(requestId, songId)

        val existing = localSongDao.getSongById(songId)
        if (existing != null) {
            val artworkPath = persistArtwork(songId, artworkBytes)
            if (artworkPath != null) {
                if (existing.artworkPath != null && existing.artworkPath != artworkPath) {
                    runCatching { File(existing.artworkPath).delete() }
                }
                localSongDao.updateArtworkPath(songId, artworkPath)
                Timber.tag(TAG).d("Artwork updated for existing local song: songId=$songId")
            }
            return
        }

        pendingArtworkByRequestId[requestId] = artworkBytes
        Timber.tag(TAG).d("Artwork cached for pending transfer: requestId=$requestId")
    }

    private fun handleTransferError(requestId: String, songId: String, message: String) {
        Timber.tag(TAG).e("Transfer error: $message (requestId=$requestId, songId=$songId)")
        _activeTransfers.update { map ->
            val current = map[requestId]
            if (current != null) {
                map + (requestId to current.copy(
                    status = WearTransferProgress.STATUS_FAILED,
                    error = message,
                ))
            } else {
                map
            }
        }
        songToRequestId.remove(songId)
        pendingMetadata.remove(requestId)
        pendingArtworkByRequestId.remove(requestId)
        clearTransferWatchdog(requestId)
    }

    private fun LocalSongEntity.hasPlayableLocalFile(): Boolean {
        val file = File(localPath)
        return file.isFile && file.length() > 0L
    }

    private fun consumeAndPersistPendingArtwork(requestId: String, songId: String): String? {
        val artworkBytes = pendingArtworkByRequestId.remove(requestId) ?: return null
        return persistArtwork(songId, artworkBytes)
    }

    private fun persistArtwork(songId: String, artworkBytes: ByteArray): String? {
        return try {
            val artworkDir = File(application.filesDir, "artwork")
            if (!artworkDir.exists()) artworkDir.mkdirs()
            val artworkFile = File(artworkDir, "$songId.$ARTWORK_FILE_EXTENSION")
            artworkFile.outputStream().use { output ->
                output.write(artworkBytes)
                output.flush()
            }
            if (artworkFile.length() <= 0L) {
                artworkFile.delete()
                null
            } else {
                artworkFile.absolutePath
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to persist artwork for songId=$songId")
            null
        }
    }

    private fun cleanupReplacedSongFiles(
        previousSong: LocalSongEntity?,
        currentAudioPath: String,
        currentArtworkPath: String?,
    ) {
        if (previousSong == null) return
        if (previousSong.localPath != currentAudioPath) {
            runCatching { File(previousSong.localPath).delete() }
        }
        val oldArtwork = previousSong.artworkPath
        if (oldArtwork != null && oldArtwork != currentArtworkPath) {
            runCatching { File(oldArtwork).delete() }
        }
    }

    private fun armTransferWatchdog(requestId: String, songId: String) {
        clearTransferWatchdog(requestId)
        transferWatchdogs[requestId] = scope.launch {
            delay(TRANSFER_IDLE_TIMEOUT_MS)
            if (_activeTransfers.value.containsKey(requestId)) {
                handleTransferError(requestId, songId, "Transfer timed out")
            }
        }
    }

    private fun clearTransferWatchdog(requestId: String) {
        transferWatchdogs.remove(requestId)?.cancel()
    }
}
