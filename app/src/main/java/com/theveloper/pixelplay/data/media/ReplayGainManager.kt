package com.theveloper.pixelplay.data.media

import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Reads ReplayGain metadata from audio files and computes volume multipliers.
 *
 * Supports standard tags:
 * - REPLAYGAIN_TRACK_GAIN (e.g. "-6.54 dB")
 * - REPLAYGAIN_ALBUM_GAIN (e.g. "-8.20 dB")
 *
 * The gain is converted to a linear volume multiplier: 10^(gainDb / 20)
 * and clamped to [0.0, 1.0] to avoid clipping.
 */
@Singleton
class ReplayGainManager @Inject constructor() {

    companion object {
        private const val TAG = "ReplayGainManager"

        // Standard ReplayGain tag keys (TagLib uses uppercase property map keys)
        private val TRACK_GAIN_KEYS = listOf(
            "REPLAYGAIN_TRACK_GAIN",
            "REPLAYGAIN_TRACK_GAIN_DB",  // Some taggers use this variant
            "R128_TRACK_GAIN"            // Opus R128 normalization
        )

        private val ALBUM_GAIN_KEYS = listOf(
            "REPLAYGAIN_ALBUM_GAIN",
            "REPLAYGAIN_ALBUM_GAIN_DB",
            "R128_ALBUM_GAIN"
        )

        // Pre-amp to apply when no ReplayGain tag is found (dB)
        // 0.0 means no change
        const val DEFAULT_PRE_AMP_DB = 0.0f
    }

    data class ReplayGainValues(
        val trackGainDb: Float? = null,
        val albumGainDb: Float? = null
    )

    /**
     * Reads ReplayGain tags from the audio file at the given path.
     * Returns null if the file can't be read or no RG tags are found.
     */
    fun readReplayGain(filePath: String): ReplayGainValues? {
        if (filePath.isBlank()) return null

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null

        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val metadata = TagLib.getMetadata(fd.detachFd(), readPictures = false)
                val propertyMap = metadata?.propertyMap ?: return null

                val trackGain = extractGainValue(propertyMap, TRACK_GAIN_KEYS)
                val albumGain = extractGainValue(propertyMap, ALBUM_GAIN_KEYS)

                if (trackGain == null && albumGain == null) {
                    return null
                }

                ReplayGainValues(trackGainDb = trackGain, albumGainDb = albumGain).also {
                    Timber.tag(TAG).d("ReplayGain for ${file.name}: track=${it.trackGainDb}dB, album=${it.albumGainDb}dB")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to read ReplayGain from: $filePath")
            null
        }
    }

    /**
     * Converts a dB gain value to a linear volume multiplier, clamped to [0, 1].
     * Negative gain values reduce volume (which is the common case for loud tracks).
     * Positive gain values would increase volume but we cap at 1.0 to prevent clipping.
     */
    fun gainDbToVolume(gainDb: Float, preAmpDb: Float = DEFAULT_PRE_AMP_DB): Float {
        val totalGainDb = gainDb + preAmpDb
        val linear = 10f.pow(totalGainDb / 20f)
        return linear.coerceIn(0f, 1f)
    }

    /**
     * Returns the volume multiplier for a track given its ReplayGain values and the selected mode.
     * Falls back to: track -> album -> 1.0 (no adjustment)
     */
    fun getVolumeMultiplier(
        values: ReplayGainValues?,
        useAlbumGain: Boolean = false,
        preAmpDb: Float = DEFAULT_PRE_AMP_DB
    ): Float {
        if (values == null) return 1f

        val gainDb = if (useAlbumGain) {
            values.albumGainDb ?: values.trackGainDb
        } else {
            values.trackGainDb ?: values.albumGainDb
        }

        return if (gainDb != null) {
            gainDbToVolume(gainDb, preAmpDb)
        } else {
            1f
        }
    }

    private fun extractGainValue(propertyMap: Map<String, Array<String>>, keys: List<String>): Float? {
        for (key in keys) {
            val rawValue = propertyMap[key]?.firstOrNull() ?: continue
            return parseGainString(rawValue)
        }
        return null
    }

    /**
     * Parses a ReplayGain string like "-6.54 dB" or "+3.21 dB" into a Float.
     * Handles variants with or without "dB" suffix and various whitespace.
     */
    private fun parseGainString(raw: String): Float? {
        val cleaned = raw.trim()
            .replace(Regex("[dD][bB]"), "")  // Remove "dB" suffix
            .trim()
        return cleaned.toFloatOrNull()
    }
}
