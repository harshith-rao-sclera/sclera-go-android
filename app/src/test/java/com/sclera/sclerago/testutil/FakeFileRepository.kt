package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.repository.FileRepository
import java.io.OutputStream

/** A [FileRepository] that returns what the test sets and records what was deleted or written. */
class FakeFileRepository(var sheet: List<List<String>>? = null) : FileRepository {
    var media: List<Media> = emptyList()
    var files: List<ImportedFile> = emptyList()
    val deleted = mutableListOf<Media>()
    var templateWritten = false

    override suspend fun describeMedia(uris: List<String>): List<Media> = media
    override suspend fun describeFiles(uris: List<String>): List<ImportedFile> = files
    override fun deleteOwned(media: List<Media>) {
        deleted += media.filter { it.owned }
    }

    override suspend fun readSheet(uri: String): List<List<String>>? = sheet
    override fun writePropertyImportTemplate(out: OutputStream) {
        templateWritten = true
    }
}
