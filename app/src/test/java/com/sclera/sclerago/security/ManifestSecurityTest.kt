package com.sclera.sclerago.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** MASVS-PLATFORM / STORAGE / NETWORK: what the manifest and network config promise. */
@RunWith(AndroidJUnit4::class)
class ManifestSecurityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun backupIsOff() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun cleartextTrafficIsOff() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
    }

    @Test
    fun onlyTheLauncherActivityIsExported() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
        val exported = (info.activities.orEmpty().toList() + info.services.orEmpty() + info.receivers.orEmpty() + info.providers.orEmpty())
            .filter { it.exported }.map { it.name }
        // The app's own components: only the launcher is reachable from other apps.
        assertEquals(listOf("com.sclera.sclerago.MainActivity"), exported.filter { it.startsWith("com.sclera.") })
        // Library components that are exported by design (debug-only tooling/test hosts; the profile installer
        // receiver is guarded by the system-only DUMP permission). Anything new here needs a review.
        val knownLibraryComponents = setOf(
            "androidx.compose.ui.tooling.PreviewActivity",
            "androidx.activity.ComponentActivity",
            "androidx.profileinstaller.ProfileInstallReceiver",
        )
        assertEquals(emptyList<String>(), exported.filter { !it.startsWith("com.sclera.") && it !in knownLibraryComponents })
    }

    @Test
    fun networkSecurityConfig_forbidsCleartextAndPinsTheSecureHosts() {
        val parser = context.resources.getXml(R.xml.network_security_config)
        var cleartext = mutableListOf<String?>()
        var pins = 0
        var userCertsOutsideDebug = false
        var inDebug = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                "base-config", "domain-config" -> cleartext.add(parser.getAttributeValue(null, "cleartextTrafficPermitted"))
                "pin" -> pins++
                "debug-overrides" -> inDebug = true
                "certificates" -> if (!inDebug && parser.getAttributeValue(null, "src") == "user") userCertsOutsideDebug = true
            }
            parser.next()
        }
        assertTrue(cleartext.isNotEmpty() && cleartext.all { it == "false" })
        assertTrue("expected at least two pins (one plus a backup)", pins >= 2)
        assertFalse("user-installed CAs must only be trusted in debug builds", userCertsOutsideDebug)
    }
}
