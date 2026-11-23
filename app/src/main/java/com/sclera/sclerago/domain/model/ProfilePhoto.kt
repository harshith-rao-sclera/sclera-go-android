package com.sclera.sclerago.domain.model

private val PHOTO_LINK = Regex("^https://\\S+$", RegexOption.IGNORE_CASE)

/**
 * A person's profile photo (the API's `imageUrl`, the link Sclera has for it) when it is an https address, trimmed; anything
 * else counts as no photo, so their initials are shown (the web's `photoOf`).
 */
fun profilePhoto(link: String?): String? = link?.trim()?.takeIf { PHOTO_LINK.matches(it) }
