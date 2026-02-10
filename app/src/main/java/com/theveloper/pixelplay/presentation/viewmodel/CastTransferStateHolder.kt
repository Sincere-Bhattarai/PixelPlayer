package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.http.MediaFileHttpServerService
import com.theveloper.pixelplay.data.service.player.CastPlayer
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine

import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastTransferStateHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val castStateHolder: CastStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val dualPlayerEngine: DualPlayerEngine, // For local player control during transfer
    private val listeningStatsTracker: ListeningStatsTracker
) {
    private val CAST_LOG_TAG = "PlayerCastTransfer"

    private var scope: CoroutineScope? = null
    
    // Callbacks for interacting with PlayerViewModel
    // Provides current queue from UI state
    private var getCurrentQueue: (() -> List<Song>)? = null
    // Syncs queue updates back to UI
    private var updateQueue: ((List<Song>) -> Unit)? = null
    // Provides master song list for resolution
    private var getMasterAllSongs: (() -> List<Song>)? = null
    // Callback when transfer is finished
    private var onTransferBackComplete: (() -> Unit)? = null
    // Callback to ensure UI sheet is visible
    private var onSheetVisible: (() -> Unit)? = null
    // Callback to handle disconnection/errors
    private var onDisconnect: (() -> Unit)? = null
    // Callback to update color scheme
    private var onSongChanged: ((String?) -> Unit)? = null

    // Session Management
    private val sessionManager: SessionManager? by lazy {
        try {
            CastContext.getSharedInstance(context).sessionManager
        } catch (e: Exception) {
            Timber.tag(CAST_LOG_TAG).e(e, "Failed to get CastContext sharedInstance")
            null
        }
    }
    
    // We retain MediaRouter reference if needed, but managing routes is usually done via callbacks
    // in PlayerViewModel. We'll assume route selection logic remains there or is migrated separately.

    // State tracking variables
    private var lastRemoteMediaStatus: MediaStatus? = null
    var lastRemoteQueue: List<Song> = emptyList()
        private set
    var lastRemoteSongId: String? = null
        private set
    private var lastRemoteStreamPosition: Long = 0L
    private var lastRemoteRepeatMode: Int = MediaStatus.REPEAT_MODE_REPEAT_OFF
    private var lastKnownRemoteIsPlaying: Boolean = false
    private var lastRemoteItemId: Int? = null

    private var pendingRemoteSongId: String? = null
    private var pendingRemoteSongMarkedAt: Long = 0L
    private var pendingMismatchStatusRequestCount: Int = 0
    private var lastPendingMismatchStatusRequestAt: Long = 0L
    private var pendingForceJumpAttempts: Int = 0
    private var lastPendingForceJumpAt: Long = 0L
    private var lastRemoteItemErrorSongId: String? = null
    private var lastRemoteItemErrorLoggedAt: Long = 0L
    private var lastRemoteIdleLogKey: String? = null
    private var lastRemoteIdleLoggedAt: Long = 0L

    // Listeners
    private var remoteMediaClientCallback: RemoteMediaClient.Callback? = null
    private var remoteProgressListener: RemoteMediaClient.ProgressListener? = null
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var remoteProgressObserverJob: Job? = null
    private var remoteStatusRefreshJob: Job? = null
    private var sessionSuspendedRecoveryJob: Job? = null
    private var alignToTargetJob: Job? = null

    fun initialize(
        scope: CoroutineScope,
        getCurrentQueue: () -> List<Song>,
        updateQueue: (List<Song>) -> Unit,
        getMasterAllSongs: () -> List<Song>,
        onTransferBackComplete: () -> Unit,
        onSheetVisible: () -> Unit,
        onDisconnect: () -> Unit,
        onSongChanged: (String?) -> Unit
    ) {
        this.scope = scope
        this.getCurrentQueue = getCurrentQueue
        this.updateQueue = updateQueue
        this.getMasterAllSongs = getMasterAllSongs
        this.onTransferBackComplete = onTransferBackComplete
        this.onSheetVisible = onSheetVisible
        this.onDisconnect = onDisconnect
        this.onSongChanged = onSongChanged

        setupListeners()
    }

    private fun setupListeners() {
        remoteProgressListener = RemoteMediaClient.ProgressListener { progress, _ ->
            val isSeeking = castStateHolder.isRemotelySeeking.value
            if (!isSeeking) {
                val pendingId = pendingRemoteSongId
                if (pendingId != null && SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000) {
                    val status = castStateHolder.castSession.value?.remoteMediaClient?.mediaStatus
                    val activeId = status
                        ?.getQueueItemById(status.getCurrentItemId())
                        ?.customData
                        ?.optString("songId")
                    if (activeId == null || activeId != pendingId) {
                         Timber.tag(CAST_LOG_TAG).d("Ignoring remote progress %d while pending target %s", progress, pendingId)
                        return@ProgressListener
                    }
                }
                castStateHolder.setRemotePosition(progress)
                lastRemoteStreamPosition = progress
                listeningStatsTracker.onProgress(progress, lastKnownRemoteIsPlaying)
            }
        }

        remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                handleRemoteStatusUpdate()
            }

            override fun onMetadataUpdated() {
                handleRemoteStatusUpdate()
            }

            override fun onQueueStatusUpdated() {
                handleRemoteStatusUpdate()
            }

            override fun onPreloadStatusUpdated() {
                handleRemoteStatusUpdate()
            }
        }

        castSessionManagerListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                sessionSuspendedRecoveryJob?.cancel()
                transferPlayback(session)
            }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                sessionSuspendedRecoveryJob?.cancel()
                transferPlayback(session)
            }
            override fun onSessionEnded(session: CastSession, error: Int) {
                sessionSuspendedRecoveryJob?.cancel()
                scope?.launch { stopServerAndTransferBack() }
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                Timber.tag(CAST_LOG_TAG).w("Cast session suspended (reason=%d). Waiting for recovery.", reason)
                castStateHolder.setRemotePlaybackActive(false)
                castStateHolder.setCastConnecting(true)
                scheduleSessionSuspendedRecovery(session)
            }
            override fun onSessionStarting(session: CastSession) {
                castStateHolder.setCastConnecting(true)
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                sessionSuspendedRecoveryJob?.cancel()
                castStateHolder.setPendingCastRouteId(null)
                castStateHolder.setCastConnecting(false)
            }
            override fun onSessionEnding(session: CastSession) { }
            override fun onSessionResuming(session: CastSession, sessionId: String) {
                castStateHolder.setCastConnecting(true)
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                sessionSuspendedRecoveryJob?.cancel()
                castStateHolder.setPendingCastRouteId(null)
                castStateHolder.setCastConnecting(false)
            }
        }
        
        sessionManager?.addSessionManagerListener(castSessionManagerListener as SessionManagerListener<CastSession>, CastSession::class.java)
        
        // Sync initial state if session exists
        val currentSession = sessionManager?.currentCastSession
        castStateHolder.setCastSession(currentSession)
        castStateHolder.setRemotePlaybackActive(currentSession != null)
        
        if (currentSession != null) {
            currentSession.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
            currentSession.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)
            startRemoteProgressObserver()
            playbackStateHolder.startProgressUpdates()
            currentSession.remoteMediaClient?.requestStatus()
        }
    }

    private fun scheduleSessionSuspendedRecovery(suspendedSession: CastSession) {
        sessionSuspendedRecoveryJob?.cancel()
        sessionSuspendedRecoveryJob = scope?.launch {
            delay(12000)
            val activeSession = sessionManager?.currentCastSession
            val stillSameSession = activeSession === suspendedSession
            val hasRemoteClient = activeSession?.remoteMediaClient != null
            if (stillSameSession && !hasRemoteClient) {
                Timber.tag(CAST_LOG_TAG).w("Suspended Cast session did not recover in time. Transferring back.")
                stopServerAndTransferBack()
            }
        }
    }

    private fun handleRemoteStatusUpdate() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient ?: return
        val mediaStatus = remoteMediaClient.mediaStatus ?: return

        lastRemoteMediaStatus = mediaStatus
        
        val songMap = getMasterAllSongs?.invoke()?.associateBy { it.id } ?: emptyMap()
        
        val newQueue = mediaStatus.queueItems.mapNotNull { item ->
            item.customData?.optString("songId")?.let { songId -> songMap[songId] }
        }.toImmutableList()

        val currentItemId = mediaStatus.getCurrentItemId()
        val currentRemoteItem = mediaStatus.getQueueItemById(currentItemId)
        val currentSongId = currentRemoteItem?.customData?.optString("songId")
        val streamPosition = mediaStatus.streamPosition

        if (castStateHolder.isRemotelySeeking.value) {
            val expectedPosition = castStateHolder.remotePosition.value
            // Unlock remote seek as soon as Cast reports the new position within tolerance.
            if (abs(streamPosition - expectedPosition) <= 1500L) {
                castStateHolder.setRemotelySeeking(false)
            }
        }

        val pendingId = pendingRemoteSongId
        val now = SystemClock.elapsedRealtime()
        val pendingAgeMs = now - pendingRemoteSongMarkedAt
        val pendingIsFresh = pendingId != null && pendingAgeMs < 4000
        if (pendingIsFresh && currentSongId != null && currentSongId != pendingId) {
            pendingMismatchStatusRequestCount += 1
            val shouldAcceptRemoteState =
                pendingAgeMs > 3500L || (pendingMismatchStatusRequestCount >= 12 && pendingAgeMs > 1800L)
            if (!shouldAcceptRemoteState) {
                if (pendingAgeMs >= 700L && now - lastPendingForceJumpAt >= 900L && pendingForceJumpAttempts < 1) {
                    val pendingItemId = mediaStatus.queueItems
                        .firstOrNull { it.customData?.optString("songId") == pendingId }
                        ?.itemId
                    if (pendingItemId != null && pendingItemId != currentItemId) {
                        pendingForceJumpAttempts += 1
                        lastPendingForceJumpAt = now
                        Log.w(
                            "PX_CAST_STATE",
                            "pending_force_jump pending=$pendingId current=$currentSongId itemId=$pendingItemId ageMs=$pendingAgeMs attempt=$pendingForceJumpAttempts"
                        )
                        castStateHolder.castPlayer?.jumpToItem(pendingItemId, 0L)
                    }
                }
                if (now - lastPendingMismatchStatusRequestAt >= 600L) {
                    lastPendingMismatchStatusRequestAt = now
                    remoteMediaClient.requestStatus()
                }
                return
            }
            Timber.tag(CAST_LOG_TAG).w(
                "Pending target mismatch persisted (pending=%s current=%s ageMs=%d attempts=%d). Accepting remote state.",
                pendingId,
                currentSongId,
                pendingAgeMs,
                pendingMismatchStatusRequestCount
            )
            Log.w(
                "PX_CAST_STATE",
                "pending_mismatch pending=$pendingId current=$currentSongId ageMs=$pendingAgeMs attempts=$pendingMismatchStatusRequestCount"
            )
            pendingRemoteSongId = null
            pendingRemoteSongMarkedAt = 0L
            pendingMismatchStatusRequestCount = 0
            pendingForceJumpAttempts = 0
            lastPendingForceJumpAt = 0L
        } else if (pendingId == null || currentSongId == pendingId) {
            pendingMismatchStatusRequestCount = 0
            pendingForceJumpAttempts = 0
            lastPendingForceJumpAt = 0L
        }

        if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_IDLE &&
            mediaStatus.idleReason != MediaStatus.IDLE_REASON_NONE) {
            val idleSongId = currentSongId ?: lastRemoteSongId ?: pendingRemoteSongId
            val idleLogKey = "$idleSongId:${mediaStatus.idleReason}:$currentItemId"
            if (idleLogKey != lastRemoteIdleLogKey || now - lastRemoteIdleLoggedAt > 3000L) {
                lastRemoteIdleLogKey = idleLogKey
                lastRemoteIdleLoggedAt = now
                val durationMs = remoteMediaClient.streamDuration
                val prematureFinish = mediaStatus.idleReason == MediaStatus.IDLE_REASON_FINISHED &&
                    durationMs > 0L &&
                    streamPosition in 0 until (durationMs - 3000L)
                Log.w(
                    "PX_CAST_STATE",
                    "idle songId=$idleSongId itemId=$currentItemId reason=${mediaStatus.idleReason} streamPos=$streamPosition duration=$durationMs pending=$pendingRemoteSongId prematureFinish=$prematureFinish"
                )
                Log.w(
                    "PX_CAST_STATE",
                    "idle media title=${currentRemoteItem?.media?.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)} contentType=${currentRemoteItem?.media?.contentType} contentId=${currentRemoteItem?.media?.contentId}"
                )
            }
        }

        if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_IDLE &&
            mediaStatus.idleReason == MediaStatus.IDLE_REASON_ERROR) {
            val errorSongId = currentSongId ?: lastRemoteSongId
            if (errorSongId != null &&
                (errorSongId != lastRemoteItemErrorSongId || now - lastRemoteItemErrorLoggedAt > 4000L)
            ) {
                lastRemoteItemErrorSongId = errorSongId
                lastRemoteItemErrorLoggedAt = now
                Timber.tag(CAST_LOG_TAG).e(
                    "Remote item playback error. songId=%s itemId=%d queueRepeat=%d streamPos=%d duration=%d pending=%s queueSize=%d",
                    errorSongId,
                    currentItemId,
                    mediaStatus.queueRepeatMode,
                    streamPosition,
                    remoteMediaClient.streamDuration,
                    pendingRemoteSongId,
                    mediaStatus.queueItems.size
                )
                Log.e(
                    "PX_CAST_ITEM_ERROR",
                    "songId=$errorSongId itemId=$currentItemId idleReason=${mediaStatus.idleReason} streamPos=$streamPosition duration=${remoteMediaClient.streamDuration} queueSize=${mediaStatus.queueItems.size}"
                )
                Timber.tag(CAST_LOG_TAG).e(
                    "Remote item media info. title=%s contentType=%s contentId=%s",
                    currentRemoteItem?.media?.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE),
                    currentRemoteItem?.media?.contentType,
                    currentRemoteItem?.media?.contentId
                )
                Log.e(
                    "PX_CAST_ITEM_ERROR",
                    "media title=${currentRemoteItem?.media?.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)} contentType=${currentRemoteItem?.media?.contentType} contentId=${currentRemoteItem?.media?.contentId}"
                )
            }
        }

        val itemChanged = lastRemoteItemId != currentItemId
        if (itemChanged) {
             lastRemoteItemId = currentItemId
             if (pendingRemoteSongId != null && pendingRemoteSongId != currentSongId) {
                 pendingRemoteSongId = null
                 pendingMismatchStatusRequestCount = 0
                 pendingForceJumpAttempts = 0
                 lastPendingForceJumpAt = 0L
             }
             castStateHolder.setRemotelySeeking(false)
             castStateHolder.setRemotePosition(streamPosition)
             playbackStateHolder.updateStablePlayerState { it.copy(currentPosition = streamPosition) } // UI sync
        }

        var queueForUi = getCurrentQueue?.invoke() ?: emptyList()
        if (newQueue.isNotEmpty()) {
            val isShrunkSubset = newQueue.size < lastRemoteQueue.size && newQueue.all { song ->
                lastRemoteQueue.any { it.id == song.id }
            }
            if (!isShrunkSubset || lastRemoteQueue.isEmpty()) {
                lastRemoteQueue = newQueue
                queueForUi = newQueue
            } else {
                // Some Cast status updates report only a small window of the queue
                // (often current + next). Keep the last known full queue in UI.
                queueForUi = if (lastRemoteQueue.isNotEmpty()) lastRemoteQueue else queueForUi
            }
        } else if (lastRemoteQueue.isNotEmpty()) {
            queueForUi = lastRemoteQueue
        }
        
        // Update current song
        val reportedSong = currentSongId?.let { songMap[it] }
        val effectiveSong = resolvePendingRemoteSong(reportedSong, currentSongId, songMap)
        val effectiveSongId = effectiveSong?.id ?: currentSongId ?: lastRemoteSongId
        
        if (effectiveSongId != null) {
            lastRemoteSongId = effectiveSongId
        }
        
        val currentSongFallback = effectiveSong 
            ?: run {
                val pId = pendingRemoteSongId
                val stableSong = playbackStateHolder.stablePlayerState.value.currentSong
                if (pId != null && pId == stableSong?.id && SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000) {
                    stableSong
                } else {
                    playbackStateHolder.stablePlayerState.value.currentSong
                }
            }
            ?: lastRemoteQueue.firstOrNull { it.id == lastRemoteSongId }

        val songChanged = currentSongFallback?.id != playbackStateHolder.stablePlayerState.value.currentSong?.id

        val isPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING
        lastKnownRemoteIsPlaying = isPlaying
        lastRemoteStreamPosition = streamPosition
        lastRemoteRepeatMode = mediaStatus.queueRepeatMode
        val effectiveDurationMs = when {
            remoteMediaClient.streamDuration > 0L -> remoteMediaClient.streamDuration
            (currentSongFallback?.duration ?: 0L) > 0L -> currentSongFallback?.duration ?: 0L
            else -> playbackStateHolder.stablePlayerState.value.totalDuration.coerceAtLeast(0L)
        }

        if (!castStateHolder.isRemotelySeeking.value) {
            castStateHolder.setRemotePosition(streamPosition)
             playbackStateHolder.updateStablePlayerState {
                 it.copy(
                     currentPosition = streamPosition,
                     totalDuration = effectiveDurationMs,
                     currentSong = currentSongFallback,
                     lyrics = if (songChanged) null else it.lyrics,
                     isLoadingLyrics = if (songChanged && currentSongFallback != null) true else it.isLoadingLyrics,
                     isPlaying = isPlaying,
                     repeatMode = if (mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_SINGLE) Player.REPEAT_MODE_ONE
                                  else if (mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL || mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE) Player.REPEAT_MODE_ALL
                                  else Player.REPEAT_MODE_OFF,
                     isShuffleEnabled = mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
                 )
             }
        }

        if (songChanged) {
            // Trigger theme + dependent UI updates only after state has the new song.
            onSongChanged?.invoke(currentSongFallback?.albumArtUriString)
        }
        
        // Update Queue if changed
        val previousQueue = getCurrentQueue?.invoke() ?: emptyList()
        if (queueForUi.isNotEmpty() && queueForUi != previousQueue) {
             updateQueue?.invoke(queueForUi)
        }
        
        if (castSession != null && (newQueue.isNotEmpty() || previousQueue.isNotEmpty())) {
            onSheetVisible?.invoke()
        }
    }
    
    private fun transferPlayback(session: CastSession) {
        scope?.launch {
            castStateHolder.setPendingCastRouteId(null)
            castStateHolder.setCastConnecting(true)
            castStateHolder.setRemotelySeeking(false)
            
            if (!ensureHttpServerRunning()) {
                castStateHolder.setCastConnecting(false)
                onDisconnect?.invoke()
                return@launch
            }
            
            // Ensure no local transition is messing with the player references
            dualPlayerEngine.cancelNext()

            val serverAddress = MediaFileHttpServerService.serverAddress
            val localPlayer = dualPlayerEngine.masterPlayer // Safe now as we are on Main and cancelled transitions

            val currentQueue = getCurrentQueue?.invoke() ?: emptyList()
            
            if (serverAddress == null || currentQueue.isEmpty()) {
                castStateHolder.setCastConnecting(false)
                return@launch
            }

            val wasPlaying = localPlayer.isPlaying
            val currentSongIndex = localPlayer.currentMediaItemIndex
            val currentPosition = localPlayer.currentPosition
            
             val castRepeatMode = if (localPlayer.shuffleModeEnabled) {
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
            } else {
                when (localPlayer.repeatMode) {
                    Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                    Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                    else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                }
            }

            lastRemoteMediaStatus = null

            onSheetVisible?.invoke()
            localPlayer.pause()
            
            playbackStateHolder.stopProgressUpdates()

            castStateHolder.setCastPlayer(CastPlayer(session, context.contentResolver))
            castStateHolder.setCastSession(session)
            castStateHolder.setRemotePlaybackActive(false)

            val castPlayer = castStateHolder.castPlayer
            if (castPlayer == null) {
                Timber.tag(CAST_LOG_TAG).w("Cast player unavailable during transferPlayback.")
                castStateHolder.setRemotePlaybackActive(false)
                castStateHolder.setCastConnecting(false)
                sessionManager?.endCurrentSession(true)
                return@launch
            }

            var initialLoadAttempt = 0
            fun loadInitialQueueAttempt() {
                initialLoadAttempt += 1
                castPlayer.loadQueue(
                    songs = currentQueue,
                    startIndex = currentSongIndex,
                    startPosition = currentPosition,
                    repeatMode = castRepeatMode,
                    serverAddress = serverAddress,
                    autoPlay = wasPlaying, // Simplification
                    onComplete = loadResult@{ success ->
                        if (!success && initialLoadAttempt < 2) {
                            Timber.tag(CAST_LOG_TAG).w(
                                "Initial Cast queue load failed (attempt %d). Retrying once.",
                                initialLoadAttempt
                            )
                            session.remoteMediaClient?.requestStatus()
                            scope?.launch {
                                delay(450)
                                if (castStateHolder.castSession.value === session &&
                                    !castStateHolder.isRemotePlaybackActive.value
                                ) {
                                    loadInitialQueueAttempt()
                                }
                            }
                            return@loadResult
                        }

                        if (!success) {
                            Timber.tag(CAST_LOG_TAG).w(
                                "Initial Cast queue load failed after retry; ending session to avoid stuck route."
                            )
                            castStateHolder.setRemotePlaybackActive(false)
                            castStateHolder.setCastConnecting(false)
                            sessionManager?.endCurrentSession(true)
                            return@loadResult
                        }

                        lastRemoteQueue = currentQueue
                        lastRemoteSongId = currentQueue.getOrNull(currentSongIndex)?.id
                        lastRemoteStreamPosition = currentPosition
                        lastRemoteRepeatMode = castRepeatMode
                        playbackStateHolder.startProgressUpdates()
                        session.remoteMediaClient?.requestStatus()
                        currentQueue.getOrNull(currentSongIndex)?.id?.let(::launchAlignToTarget)

                        castStateHolder.setRemotePlaybackActive(true)
                        castStateHolder.setCastConnecting(false)
                    }
                )
            }

            loadInitialQueueAttempt()

            session.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
            session.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)
            
            startRemoteProgressObserver()
        }
    }
    
    private fun startRemoteProgressObserver() {
        remoteProgressObserverJob?.cancel()
        remoteStatusRefreshJob?.cancel()

        remoteProgressObserverJob = scope?.launch {
            castStateHolder.remotePosition.collect { position ->
                playbackStateHolder.updateStablePlayerState { it.copy(currentPosition = position) }
            }
        }

        remoteStatusRefreshJob = scope?.launch {
            while (true) {
                val remoteClient = castStateHolder.castSession.value?.remoteMediaClient
                if (remoteClient == null) {
                    delay(if (castStateHolder.isCastConnecting.value) 1000 else 2500)
                    continue
                }
                runCatching { remoteClient.requestStatus() }
                    .onFailure { throwable ->
                        Timber.tag(CAST_LOG_TAG).d(throwable, "requestStatus failed during refresh loop")
                    }
                val refreshDelayMs = when {
                    remoteClient.isPlaying -> 1500L
                    castStateHolder.isRemotePlaybackActive.value -> 2500L
                    castStateHolder.isCastConnecting.value -> 1500L
                    else -> 4000L
                }
                delay(refreshDelayMs)
            }
        }
    }
    
    suspend fun stopServerAndTransferBack() {
        sessionSuspendedRecoveryJob?.cancel()
        alignToTargetJob?.cancel()
        remoteProgressObserverJob?.cancel()
        remoteStatusRefreshJob?.cancel()
        castStateHolder.setRemotelySeeking(false)
        val session = castStateHolder.castSession.value ?: return
        val remoteMediaClient = session.remoteMediaClient
         
        // Cleanup callbacks
        remoteProgressListener?.let { listener ->
            remoteMediaClient?.removeProgressListener(listener)
        }
        remoteMediaClientCallback?.let { callback ->
            remoteMediaClient?.unregisterCallback(callback)
        }
        
        val liveStatus = remoteMediaClient?.mediaStatus
        val lastKnownStatus = liveStatus ?: lastRemoteMediaStatus
        val lastPosition = (liveStatus?.streamPosition ?: lastKnownStatus?.streamPosition ?: lastRemoteStreamPosition)
            .takeIf { it > 0 } ?: castStateHolder.remotePosition.value
            
        val wasPlaying = (liveStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING) 
            || (lastKnownStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING)
            || lastKnownRemoteIsPlaying
            
        val lastItemId = liveStatus?.currentItemId ?: lastKnownStatus?.currentItemId
        val lastRepeatMode = liveStatus?.queueRepeatMode ?: lastKnownStatus?.queueRepeatMode ?: lastRemoteRepeatMode
        val isShuffleEnabled = lastRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
        
        val transferSnapshot = TransferSnapshot(
            lastKnownStatus = lastKnownStatus,
            lastRemoteQueue = lastRemoteQueue,
            lastRemoteSongId = lastRemoteSongId,
            lastRemoteStreamPosition = lastRemoteStreamPosition,
            lastRemoteRepeatMode = lastRemoteRepeatMode,
            wasPlaying = wasPlaying,
            lastPosition = lastPosition,
            isShuffleEnabled = isShuffleEnabled
        )

        castStateHolder.setCastPlayer(null)
        castStateHolder.setCastSession(null)
        castStateHolder.setRemotePlaybackActive(false)
        
        if (castStateHolder.pendingCastRouteId == null) {
            context.stopService(Intent(context, MediaFileHttpServerService::class.java))
            // Signal disconnect to PlayerViewModel if needed, or rely on state holder
            onDisconnect?.invoke() 
        } else {
            castStateHolder.setCastConnecting(true)
        }
        
        // We defer getting the player until AFTER the suspension to avoid race conditions
        // where a transition might have released the reference we held.
        
        val queueData = withContext(Dispatchers.Default) {
            val fallbackQueue = if (transferSnapshot.lastKnownStatus?.queueItems?.isNotEmpty() == true) {
                transferSnapshot.lastKnownStatus.queueItems.mapNotNull { item ->
                    item.customData?.optString("songId")?.let { songId ->
                         getMasterAllSongs?.invoke()?.firstOrNull { it.id == songId }
                    }
                }.toImmutableList()
            } else {
                transferSnapshot.lastRemoteQueue
            }
            val chosenQueue = if (fallbackQueue.isEmpty()) transferSnapshot.lastRemoteQueue else fallbackQueue
             val songMap = getMasterAllSongs?.invoke()?.associateBy { it.id } ?: emptyMap()
            val finalQueue = chosenQueue.mapNotNull { song -> songMap[song.id] }
            
            val targetSongId = transferSnapshot.lastKnownStatus?.getQueueItemById(lastItemId ?: 0)?.customData?.optString("songId")
                ?: transferSnapshot.lastRemoteSongId
                
            QueueTransferData(
                finalQueue = finalQueue,
                targetSongId = targetSongId,
                isShuffleEnabled = transferSnapshot.isShuffleEnabled
            )
        }
        
        if (queueData.finalQueue.isNotEmpty() && queueData.targetSongId != null) {
             val desiredRepeatMode = when (lastRepeatMode) {
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            
            // Reusing local queue logic simplification: always rebuild for safety/completeness
            val rebuildResult = withContext(Dispatchers.Default) {
                val startIndex = queueData.finalQueue.indexOfFirst { it.id == queueData.targetSongId }.coerceAtLeast(0)
                val mediaItems = queueData.finalQueue.map { song -> MediaItemBuilder.build(song) }
                RebuildArtifacts(startIndex, mediaItems, queueData.finalQueue.getOrNull(startIndex))
            }
            
            // Now retrieve the FRESH local player reference
            val localPlayer = dualPlayerEngine.masterPlayer

            localPlayer.shuffleModeEnabled = queueData.isShuffleEnabled
            localPlayer.repeatMode = desiredRepeatMode
            localPlayer.setMediaItems(rebuildResult.mediaItems, rebuildResult.startIndex, transferSnapshot.lastPosition)
            localPlayer.prepare()
            
            if (wasPlaying) localPlayer.play() else localPlayer.pause()
            
            // Sync UI
            updateQueue?.invoke(queueData.finalQueue)
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = rebuildResult.targetSong,
                    isPlaying = wasPlaying,
                    totalDuration = rebuildResult.targetSong?.duration ?: it.totalDuration,
                    isShuffleEnabled = queueData.isShuffleEnabled,
                    repeatMode = desiredRepeatMode
                )
            }
            
            if (wasPlaying) {
                playbackStateHolder.startProgressUpdates()
            } else {
                playbackStateHolder.updateStablePlayerState { it.copy(currentPosition = transferSnapshot.lastPosition) }
            }
        }
        
        lastRemoteMediaStatus = null
        lastRemoteQueue = emptyList()
        lastRemoteSongId = null
        lastRemoteStreamPosition = 0L
        
        onTransferBackComplete?.invoke()
    }

    suspend fun ensureHttpServerRunning(): Boolean {
        if (MediaFileHttpServerService.isServerRunning) return true
        
        val intent = Intent(context, MediaFileHttpServerService::class.java).apply {
            action = MediaFileHttpServerService.ACTION_START_SERVER
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Timber.tag(CAST_LOG_TAG).e(e, "Failed to start media server service")
            return false
        }

        for (i in 0..20) {
            if (MediaFileHttpServerService.isServerRunning && MediaFileHttpServerService.serverAddress != null) {
                return true
            }
            if (MediaFileHttpServerService.lastFailureReason != null) {
                Timber.tag(CAST_LOG_TAG).w(
                    "Media server failed to start: %s",
                    MediaFileHttpServerService.lastFailureReason
                )
                return false
            }
            delay(100)
        }
        Timber.tag(CAST_LOG_TAG).w("Timed out waiting for media server startup")
        return false
    }

    suspend fun playRemoteQueue(
        songsToPlay: List<Song>,
        startSong: Song,
        isShuffleEnabled: Boolean
    ): Boolean {
        if (!ensureHttpServerRunning()) return false

        val serverAddress = MediaFileHttpServerService.serverAddress ?: return false
        val startIndex = songsToPlay.indexOf(startSong).coerceAtLeast(0)

        val repeatMode = playbackStateHolder.stablePlayerState.value.repeatMode
        val castRepeatMode = if (isShuffleEnabled) {
             MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
        } else {
             when (repeatMode) {
                 Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                 Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                 else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
             }
        }

        val previousStableSong = playbackStateHolder.stablePlayerState.value.currentSong
        markPendingRemoteSong(startSong)

        val castPlayer = castStateHolder.castPlayer
        if (castPlayer != null) {
            val completionDeferred = CompletableDeferred<Boolean>()
            castPlayer.loadQueue(
                songs = songsToPlay,
                startIndex = startIndex,
                startPosition = 0L,
                repeatMode = castRepeatMode,
                serverAddress = serverAddress,
                autoPlay = true,
                onComplete = { success ->
                    if (!success) {
                        pendingRemoteSongId = null
                        pendingRemoteSongMarkedAt = 0L
                        pendingMismatchStatusRequestCount = 0
                        lastPendingMismatchStatusRequestAt = 0L
                        pendingForceJumpAttempts = 0
                        lastPendingForceJumpAt = 0L
                        val currentRemoteSongId = castStateHolder.castSession.value
                            ?.remoteMediaClient
                            ?.mediaStatus
                            ?.let { status ->
                                status.getQueueItemById(status.getCurrentItemId())
                                    ?.customData
                                    ?.optString("songId")
                                    ?.takeIf { it.isNotBlank() }
                            }
                        if (currentRemoteSongId != null) {
                            lastRemoteSongId = currentRemoteSongId
                        }
                        if (previousStableSong != null) {
                            playbackStateHolder.updateStablePlayerState { state ->
                                if (state.currentSong?.id == startSong.id) {
                                    state.copy(currentSong = previousStableSong)
                                } else {
                                    state
                                }
                            }
                            onSongChanged?.invoke(previousStableSong.albumArtUriString)
                        }
                        Timber.tag(CAST_LOG_TAG).w(
                            "Remote queue load failed for songId=%s (size=%d). Session kept active.",
                            startSong.id,
                            songsToPlay.size
                        )
                        castStateHolder.castSession.value?.remoteMediaClient?.requestStatus()
                    } else {
                        lastRemoteQueue = songsToPlay
                        lastRemoteSongId = startSong.id
                        lastRemoteStreamPosition = 0L
                        lastRemoteRepeatMode = castRepeatMode
                        castStateHolder.setRemotePlaybackActive(true)
                        playbackStateHolder.startProgressUpdates()
                        castStateHolder.castSession.value?.remoteMediaClient?.requestStatus()
                        launchAlignToTarget(startSong.id)
                    }
                    completionDeferred.complete(success)
                }
            )
            return completionDeferred.await()
        }
        return false
    }

     fun markPendingRemoteSong(song: Song) {
        pendingRemoteSongId = song.id
        pendingRemoteSongMarkedAt = SystemClock.elapsedRealtime()
        pendingMismatchStatusRequestCount = 0
        lastPendingMismatchStatusRequestAt = 0L
        pendingForceJumpAttempts = 0
        lastPendingForceJumpAt = 0L
        lastRemoteSongId = song.id
        lastRemoteItemId = null
        Timber.tag(CAST_LOG_TAG).d("Marked pending remote song: %s", song.id)

        val songChanged = playbackStateHolder.stablePlayerState.value.currentSong?.id != song.id
        playbackStateHolder.updateStablePlayerState { state ->
            state.copy(
                currentSong = song,
                lyrics = if (songChanged) null else state.lyrics,
                isLoadingLyrics = if (songChanged) true else state.isLoadingLyrics
            )
        }
        onSheetVisible?.invoke()

        if (songChanged) {
            onSongChanged?.invoke(song.albumArtUriString)
        }

        val queue = getCurrentQueue?.invoke() ?: lastRemoteQueue
        val updatedQueue = if (queue.any { it.id == song.id } || queue.isEmpty()) {
            queue
        } else {
            queue + song
        }
        
        if (updatedQueue != queue) {
             updateQueue?.invoke(updatedQueue)
        }
        
        castStateHolder.setRemotePosition(0L)
        playbackStateHolder.updateStablePlayerState { it.copy(currentPosition = 0L) }
    }

    private fun launchAlignToTarget(targetSongId: String) {
        alignToTargetJob?.cancel()
        alignToTargetJob = scope?.launch {
            alignRemotePlaybackToSong(targetSongId)
        }
    }

    private suspend fun alignRemotePlaybackToSong(targetSongId: String) {
        val remoteClient = castStateHolder.castSession.value?.remoteMediaClient ?: return
        var lastObservedSongId: String? = null
        repeat(2) { attempt ->
            if (attempt > 0) delay(350L)
            runCatching { remoteClient.requestStatus() }
            delay(120L)

            val status = remoteClient.mediaStatus ?: return@repeat
            val currentSongId = status.getQueueItemById(status.currentItemId)
                ?.customData
                ?.optString("songId")
                ?.takeIf { it.isNotBlank() }
            if (currentSongId == targetSongId) {
                Log.i("PX_CAST_QLOAD", "align ok targetSongId=$targetSongId attempt=$attempt")
                return
            }

            if (attempt > 0 && currentSongId != null && currentSongId == lastObservedSongId) {
                Log.w(
                    "PX_CAST_QLOAD",
                    "align stuck targetSongId=$targetSongId currentSongId=$currentSongId attempt=$attempt"
                )
                return
            }

            val targetItemId = status.queueItems
                .firstOrNull { it.customData?.optString("songId") == targetSongId }
                ?.itemId

            if (targetItemId != null && targetItemId != status.currentItemId) {
                Log.w(
                    "PX_CAST_QLOAD",
                    "align jump targetSongId=$targetSongId currentSongId=$currentSongId itemId=$targetItemId attempt=$attempt"
                )
                castStateHolder.castPlayer?.jumpToItem(targetItemId, 0L)
            } else {
                Log.w(
                    "PX_CAST_QLOAD",
                    "align miss targetSongId=$targetSongId currentSongId=$currentSongId queueSize=${status.queueItems.size} attempt=$attempt"
                )
            }
            lastObservedSongId = currentSongId
        }
    }

    private fun resolvePendingRemoteSong(
        reportedSong: Song?,
        currentSongId: String?,
        songMap: Map<String, Song>
    ): Song? {
        if (reportedSong != null) return reportedSong

        val pendingId = pendingRemoteSongId
        if (pendingId == null) return null

        val pendingIsFresh = SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000
        if (!pendingIsFresh) return null

        if (currentSongId != pendingId) {
             return null
        }

        return songMap[pendingId]
    }
    
    // Helper Data Classes
    private data class TransferSnapshot(
        val lastKnownStatus: MediaStatus?,
        val lastRemoteQueue: List<Song>,
        val lastRemoteSongId: String?,
        val lastRemoteStreamPosition: Long,
        val lastRemoteRepeatMode: Int,
        val wasPlaying: Boolean,
        val lastPosition: Long,
        val isShuffleEnabled: Boolean
    )
    
     private data class QueueTransferData(
        val finalQueue: List<Song>,
        val targetSongId: String?,
        val isShuffleEnabled: Boolean
    )

    private data class RebuildArtifacts(
        val startIndex: Int,
        val mediaItems: List<MediaItem>,
        val targetSong: Song?
    )
}
