package com.sclera.sclerago.security

import com.sclera.sclerago.services.media.MediaLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MASVS-NETWORK: reference photos are only fetched over https from the allowed hosts. */
class MediaDownloadPolicyTest {
    @Test
    fun allowsHttpsOnWikimedia() {
        assertTrue(MediaLoader.isAllowedPhotoUrl("https://upload.wikimedia.org/a/b.jpg"))
        assertTrue(MediaLoader.isAllowedPhotoUrl("https://thumb.wikimedia.org/x/300px-y.jpg"))
    }

    @Test
    fun refusesPlainHttp() {
        assertFalse(MediaLoader.isAllowedPhotoUrl("http://upload.wikimedia.org/a/b.jpg"))
    }

    @Test
    fun refusesOtherHostsAndOddSchemes() {
        listOf(
            "https://evil.example.com/a.jpg", "https://wikimedia.org.evil.com/a.jpg", "https://upload.wikimedia.org.evil.com/a.jpg",
            "ftp://upload.wikimedia.org/a.jpg", "file:///sdcard/a.jpg", "content://media/1", "not a url", "",
        ).forEach { assertFalse(it, MediaLoader.isAllowedPhotoUrl(it)) }
    }

    @Test
    fun cap_isReasonable() {
        assertTrue(MediaLoader.MAX_PHOTO_BYTES in 1..(50L * 1024 * 1024))
    }
}
