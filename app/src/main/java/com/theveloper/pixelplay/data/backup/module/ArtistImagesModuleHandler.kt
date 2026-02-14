package com.theveloper.pixelplay.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.backup.model.ArtistImageBackupEntry
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistImagesModuleHandler @Inject constructor(
    private val musicDao: MusicDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.ARTIST_IMAGES

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val artists = musicDao.getAllArtistsListRaw()
        val entries = artists
            .filter { !it.imageUrl.isNullOrEmpty() }
            .map { ArtistImageBackupEntry(artistName = it.name, imageUrl = it.imageUrl!!) }
        gson.toJson(entries)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        musicDao.getAllArtistsListRaw().count { !it.imageUrl.isNullOrEmpty() }
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, ArtistImageBackupEntry::class.java).type
        val entries: List<ArtistImageBackupEntry> = gson.fromJson(payload, type)
        entries.forEach { entry ->
            val artistId = musicDao.getArtistIdByName(entry.artistName) ?: return@forEach
            musicDao.updateArtistImageUrl(artistId, entry.imageUrl)
        }
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)
}
