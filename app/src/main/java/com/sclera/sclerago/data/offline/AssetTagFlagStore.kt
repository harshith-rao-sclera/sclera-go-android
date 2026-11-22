package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.TagKind
import javax.inject.Inject

/** The `tagged` / `untagged` flags the `assets` table keeps for each kind of tag. */
fun interface AssetTagFlagStore {
    /** Sets the asset's [kind] flag to `tagged` or, when [isTagged] is false, `untagged`, in the phone's copy of the data. */
    fun mark(asset: String, kind: TagKind, isTagged: Boolean)
}

/** Writes the `qr_code_tag`, `barcode_tag` and `nfc_tag` columns of the open property database. */
class AdcAssetTagFlagStore @Inject constructor(private val db: AdcDatabase) : AssetTagFlagStore {
    override fun mark(asset: String, kind: TagKind, isTagged: Boolean) {
        val column = column(kind)
        // The flag is only a cache of what the server will send: a database without the column, or a failed update, must not undo the tag.
        runCatching {
            if (db.hasColumn("assets", column)) {
                db.execute("UPDATE assets SET $column = ? WHERE id = ?", listOf(if (isTagged) TAGGED else UNTAGGED, asset), setOf("assets"))
            }
        }
    }

    private fun column(kind: TagKind) = when (kind) {
        TagKind.QR -> "qr_code_tag"
        TagKind.BARCODE -> "barcode_tag"
        TagKind.NFC -> "nfc_tag"
    }

    private companion object {
        const val TAGGED = "tagged"
        const val UNTAGGED = "untagged"
    }
}
