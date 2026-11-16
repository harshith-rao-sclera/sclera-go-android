package com.sclera.sclerago.data.local

import com.sclera.sclerago.domain.model.PhotoReq

/** The photos each asset needs until they are configured; the offline database does not hold them yet. */
val DEFAULT_PHOTO_REQS = listOf(
    PhotoReq("plate", "Nameplate", "Manufacturer's plate with model and serial number", 1, 2),
    PhotoReq("asset", "Asset", "The equipment itself, in place", 1, 3),
    PhotoReq("label", "Label / tag", "The asset tag or QR label", 0, 1),
    PhotoReq("site", "Surroundings", "Where the asset sits, to find it again", 0, 2),
)
