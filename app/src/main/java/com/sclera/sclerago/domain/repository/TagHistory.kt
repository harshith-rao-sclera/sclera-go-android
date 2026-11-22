package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.TagKind

/** The lines tagging adds to an asset's history, as the old app wrote them. Writing one never fails the tagging. */
interface TagHistory {
    /** "QR code tagged": the asset was tagged with a QR code. */
    fun qrTagged(asset: String)

    /** "<kind> retag requested": the tag is on another unsubmitted asset and the user is asked whether to move it to [asset]. */
    fun retagRequested(asset: String, kind: TagKind)
}
