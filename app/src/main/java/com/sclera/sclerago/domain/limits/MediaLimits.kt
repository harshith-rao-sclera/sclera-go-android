package com.sclera.sclerago.domain.limits

/** Longest video or voice memo kept on an asset: 10 seconds (recordings stop there; picked videos are cut to it). */
const val MAX_CLIP_MS = 10_000L

/** Largest photo or file kept on an asset: 5MB (a bigger photo is made smaller; any other bigger file is refused). */
const val MAX_MEDIA_BYTES = 5L * 1024 * 1024
