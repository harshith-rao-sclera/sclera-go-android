package com.sclera.sclerago.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.services.media.MediaLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** MASVS-NETWORK: photos are only fetched over https from the Sclera domain. */
@RunWith(AndroidJUnit4::class) // the Sclera domain check reads the accounts URL with android.net.Uri
class MediaDownloadPolicyTest {
    @Test
    fun allowsHttpsOnTheScleraDomain() {
        assertTrue(MediaLoader.isAllowedPhotoUrl("https://qa-app.sclera.com/vdms/profile/images/a.jpg"))
        assertTrue(MediaLoader.isAllowedPhotoUrl("https://app.sclera.com/p.jpg"))
    }

    @Test
    fun refusesPlainHttp() {
        assertFalse(MediaLoader.isAllowedPhotoUrl("http://qa-app.sclera.com/p.jpg"))
    }

    @Test
    fun refusesTheOldSamplePhotoHosts() {
        assertFalse(MediaLoader.isAllowedPhotoUrl("https://upload.wikimedia.org/a/b.jpg"))
        assertFalse(MediaLoader.isAllowedPhotoUrl("https://thumb.wikimedia.org/x/300px-y.jpg"))
    }

    @Test
    fun refusesOtherHostsAndOddSchemes() {
        listOf(
            "https://evil.example.com/a.jpg", "https://sclera.com.evil.com/a.jpg", "https://evil-sclera.com/a.jpg",
            "ftp://qa-app.sclera.com/a.jpg", "file:///sdcard/a.jpg", "content://media/1", "not a url", "",
        ).forEach { assertFalse(it, MediaLoader.isAllowedPhotoUrl(it)) }
    }

    @Test
    fun cap_isReasonable() {
        assertTrue(MediaLoader.MAX_PHOTO_BYTES in 1..(50L * 1024 * 1024))
    }
}
