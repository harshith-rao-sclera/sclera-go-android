package com.sclera.sclerago.domain.usecase.media

import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.repository.FileRepository
import java.io.OutputStream
import javax.inject.Inject

/** Photos / videos picked from the gallery, by content URI. */
class DescribePickedMediaUseCase @Inject constructor(private val files: FileRepository) {
    suspend operator fun invoke(uris: List<String>): List<Media> = files.describeMedia(uris)
}

/** Documents picked in the system file manager, by content URI. */
class DescribePickedFilesUseCase @Inject constructor(private val files: FileRepository) {
    suspend operator fun invoke(uris: List<String>): List<ImportedFile> = files.describeFiles(uris)
}

/** Throws away camera shots that never became part of an asset. */
class DeleteOwnedMediaUseCase @Inject constructor(private val files: FileRepository) {
    operator fun invoke(media: List<Media>) = files.deleteOwned(media)
}

/** The sample spreadsheet for importing properties. */
class WritePropertyTemplateUseCase @Inject constructor(private val files: FileRepository) {
    operator fun invoke(out: OutputStream) = files.writePropertyImportTemplate(out)
}

/** The device-file use cases, for injecting as one. */
class FileUseCases @Inject constructor(
    val describeMedia: DescribePickedMediaUseCase,
    val describeFiles: DescribePickedFilesUseCase,
    val deleteOwned: DeleteOwnedMediaUseCase,
    val writePropertyTemplate: WritePropertyTemplateUseCase,
)
