package com.sclera.sclerago.data.repository

import android.content.Context
import android.net.Uri
import com.sclera.sclerago.data.readSheet
import com.sclera.sclerago.data.writeSamplePropertyXlsx
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
import java.io.OutputStream
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

    override suspend fun readSheet(uri: String): List<List<String>>? =
        withContext(ioDispatcher) { runCatching { readSheet(context, Uri.parse(uri)) }.getOrNull() }

    /** Deletes the user's photos and videos (taken here) and the downloaded reference photos. */
    override fun clear() {
        captureDir(context).deleteRecursively()
        File(context.cacheDir, "web-photos").deleteRecursively()
    }

    override fun writePropertyImportTemplate(out: OutputStream) = writeSamplePropertyXlsx(out)
}
