package com.theveloper.pixelplay.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMetaUtilsTest {

    @Test
    fun mimeTypeToFormat_mapsM4aVariants() {
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/mp4"))
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/m4a"))
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/x-m4a"))
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/mp4a-latm"))
    }

    @Test
    fun mimeTypeToFormat_mapsCommonAudioFormats() {
        assertEquals("mp3", AudioMetaUtils.mimeTypeToFormat("audio/mpeg"))
        assertEquals("flac", AudioMetaUtils.mimeTypeToFormat("audio/flac"))
        assertEquals("wav", AudioMetaUtils.mimeTypeToFormat("audio/x-wav"))
        assertEquals("ogg", AudioMetaUtils.mimeTypeToFormat("audio/ogg"))
        assertEquals("opus", AudioMetaUtils.mimeTypeToFormat("audio/opus"))
        assertEquals("aac", AudioMetaUtils.mimeTypeToFormat("audio/aac"))
        assertEquals("amr", AudioMetaUtils.mimeTypeToFormat("audio/amr-wb"))
        assertEquals("alac", AudioMetaUtils.mimeTypeToFormat("audio/alac"))
        assertEquals("aiff", AudioMetaUtils.mimeTypeToFormat("audio/x-aiff"))
        assertEquals("wma", AudioMetaUtils.mimeTypeToFormat("audio/x-ms-wma"))
        assertEquals("ac3", AudioMetaUtils.mimeTypeToFormat("audio/eac3"))
        assertEquals("dts", AudioMetaUtils.mimeTypeToFormat("audio/vnd.dts"))
    }
}

