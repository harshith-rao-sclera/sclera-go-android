package com.sclera.sclerago.presentation.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContract
import com.sclera.sclerago.domain.model.Media

/**
 * Download for an asset's photo, video, voice memo or file: the system's "save as" dialog, suggesting the file's own
 * name and type. The user picks where the copy goes, so no storage permission is needed; the result is the document
 * to copy the file into (null: cancelled).
 */
class SaveMediaAs : ActivityResultContract<Media, Uri?>() {
    override fun createIntent(context: Context, input: Media): Intent {
        val uri = Uri.parse(input.uri)
        val name = displayName(context, uri) ?: input.fileName ?: uri.lastPathSegment ?: FALLBACK_NAME
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: ANY_FILE
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type)
            .putExtra(Intent.EXTRA_TITLE, name)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data?.takeIf { resultCode == Activity.RESULT_OK }

    /** The file's name as its provider gives it (the app's media provider: the name it was uploaded with). */
    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    private companion object {
        const val FALLBACK_NAME = "asset-media"
        const val ANY_FILE = "application/octet-stream"
    }
}
