package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.formatDbTime
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.repository.TagHistory
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.system.next
import javax.inject.Inject

/** Writes tagging's history lines to the open property database (`asset_activity`), the way the other asset history lines are written. */
class AdcTagHistory @Inject constructor(
    private val db: AdcDatabase,
    private val time: TimeProvider,
    private val ids: IdGenerator,
    private val user: SyncCredentialsProvider,
) : TagHistory {
    override fun qrTagged(asset: String) = add(asset, "tagged", "QR code tagged")

    override fun retagRequested(asset: String, kind: TagKind) = add(asset, "retag_requested", "${name(kind)} retag requested")

    private fun add(asset: String, action: String, text: String) {
        // A history line is a record, not a part of the tag: a closed database or a missing table must not undo or block it.
        runCatching {
            val now = formatDbTime(time.nowMillis())
            val changes = if (db.hasColumn("asset_activity", "field_changes")) "field_changes" else "changes"
            db.execute(
                ACTIVITY_INSERT.replace("%CHANGES%", changes),
                listOf(ids.next(), asset, action, text, null, user.userId(), now, now),
                setOf("asset_activity"),
            )
        }
    }

    private fun name(kind: TagKind) = when (kind) {
        TagKind.QR -> "QR code"
        TagKind.BARCODE -> "Barcode"
        TagKind.NFC -> "NFC"
    }
}
