package com.sclera.sclerago.data.repository

import android.content.Context
import android.net.Uri
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.services.media.captureDir
import kotlinx.coroutines.CoroutineDispatcher
import com.sclera.sclerago.services.media.describeFiles
import com.sclera.sclerago.services.media.describeVisualMedia
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Files on the device, read through the content resolver. */
@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FileRepository, UserDataCleaner {
    private val io = CoroutineScope(SupervisorJob() + ioDispatcher)

    override suspend fun describeMedia(uris: List<String>): List<Media> = describeVisualMedia(context, uris.map(Uri::parse))

    override suspend fun describeFiles(uris: List<String>): List<ImportedFile> = describeFiles(context, uris.map(Uri::parse))

    override fun deleteOwned(media: List<Media>) {
        val owned = media.filter { it.owned }
        if (owned.isEmpty()) return
        io.launch {
            owned.forEach { m -> runCatching { Uri.parse(m.uri).path?.let { File(it).delete() } } }
        }
    }

    /** Deletes the user's photos and videos (taken here) and the downloaded reference photos. */
    override fun clear() {
        captureDir(context).deleteRecursively()
        File(context.cacheDir, "web-photos").deleteRecursively()
    }


    override suspend fun writeFile(uri: String, bytes: ByteArray): Boolean = withContext(ioDispatcher) {
        runCatching {
            context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(bytes) } != null
        }.getOrDefault(false)
    }

    /** Streams the file across (a video can be large), so it is never held in memory whole. */
    override suspend fun copyFile(from: String, to: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            val resolver = context.contentResolver
            resolver.openInputStream(Uri.parse(from))?.use { input ->
                resolver.openOutputStream(Uri.parse(to), "wt")?.use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
    }
}
