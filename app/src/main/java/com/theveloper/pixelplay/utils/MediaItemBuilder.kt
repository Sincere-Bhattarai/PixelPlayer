package com.theveloper.pixelplay.utils

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.theveloper.pixelplay.data.model.Song
import java.io.File

object MediaItemBuilder {
    private const val EXTERNAL_MEDIA_ID_PREFIX = "external:"
    private const val EXTERNAL_EXTRA_PREFIX = "com.theveloper.pixelplay.external."
    private val SUPPORTED_ARTWORK_SCHEMES = setOf(
        "content",
        "file",
        "android.resource",
        "http",
        "https",
    )
    const val EXTERNAL_EXTRA_FLAG = EXTERNAL_EXTRA_PREFIX + "FLAG"
    const val EXTERNAL_EXTRA_ALBUM = EXTERNAL_EXTRA_PREFIX + "ALBUM"
    const val EXTERNAL_EXTRA_DURATION = EXTERNAL_EXTRA_PREFIX + "DURATION"
    const val EXTERNAL_EXTRA_CONTENT_URI = EXTERNAL_EXTRA_PREFIX + "CONTENT_URI"
    const val EXTERNAL_EXTRA_ALBUM_ART = EXTERNAL_EXTRA_PREFIX + "ALBUM_ART"
    const val EXTERNAL_EXTRA_GENRE = EXTERNAL_EXTRA_PREFIX + "GENRE"
    const val EXTERNAL_EXTRA_TRACK = EXTERNAL_EXTRA_PREFIX + "TRACK"
    const val EXTERNAL_EXTRA_YEAR = EXTERNAL_EXTRA_PREFIX + "YEAR"
    const val EXTERNAL_EXTRA_DATE_ADDED = EXTERNAL_EXTRA_PREFIX + "DATE_ADDED"
    const val EXTERNAL_EXTRA_MIME_TYPE = EXTERNAL_EXTRA_PREFIX + "MIME_TYPE"
    const val EXTERNAL_EXTRA_BITRATE = EXTERNAL_EXTRA_PREFIX + "BITRATE"
    const val EXTERNAL_EXTRA_SAMPLE_RATE = EXTERNAL_EXTRA_PREFIX + "SAMPLE_RATE"
    const val EXTERNAL_EXTRA_FILE_PATH = EXTERNAL_EXTRA_PREFIX + "FILE_PATH"

    fun build(song: Song): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(playbackUri(song.contentUriString))
            .setMediaMetadata(buildMediaMetadataForSong(song))
            .build()
    }

    fun playbackUri(contentUriString: String): Uri {
        val uri = contentUriString.toUri()
        // Telegram downloaded files can be stored as absolute paths (without file://).
        // Normalize them so ExoPlayer always gets a canonical local-file URI.
        return if (uri.scheme.isNullOrBlank() && contentUriString.startsWith("/")) {
            Uri.fromFile(File(contentUriString))
        } else {
            uri
        }
    }

    /**
     * Artwork URIs are surfaced to external controllers (Android Auto, widgets, etc.).
     * Keep only schemes that these surfaces can usually resolve, and normalize raw paths.
     */
    fun artworkUri(rawArtworkUri: String?): Uri? {
        if (rawArtworkUri.isNullOrBlank()) {
            return null
        }

        if (rawArtworkUri.startsWith("/")) {
            return Uri.fromFile(File(rawArtworkUri))
        }

        val uri = rawArtworkUri.toUri()
        val scheme = uri.scheme?.lowercase()
        return if (scheme != null && scheme in SUPPORTED_ARTWORK_SCHEMES) {
            uri
        } else {
            null
        }
    }

    private fun buildMediaMetadataForSong(song: Song): MediaMetadata {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.album)

        artworkUri(song.albumArtUriString)?.let { artworkUri ->
            metadataBuilder.setArtworkUri(artworkUri)
        }

        val extras = Bundle().apply {
            putBoolean(EXTERNAL_EXTRA_FLAG, song.id.startsWith(EXTERNAL_MEDIA_ID_PREFIX))
            putString(EXTERNAL_EXTRA_ALBUM, song.album)
            putLong(EXTERNAL_EXTRA_DURATION, song.duration)
            putString(EXTERNAL_EXTRA_CONTENT_URI, song.contentUriString)
            song.albumArtUriString?.let { putString(EXTERNAL_EXTRA_ALBUM_ART, it) }
            song.genre?.let { putString(EXTERNAL_EXTRA_GENRE, it) }
            putInt(EXTERNAL_EXTRA_TRACK, song.trackNumber)
            putInt(EXTERNAL_EXTRA_YEAR, song.year)
            putLong(EXTERNAL_EXTRA_DATE_ADDED, song.dateAdded)
            putString(EXTERNAL_EXTRA_MIME_TYPE, song.mimeType)
            putInt(EXTERNAL_EXTRA_BITRATE, song.bitrate ?: 0)
            putInt(EXTERNAL_EXTRA_SAMPLE_RATE, song.sampleRate ?: 0)
            putString(EXTERNAL_EXTRA_FILE_PATH, song.path)
        }

        metadataBuilder.setExtras(extras)
        return metadataBuilder.build()
    }
}
