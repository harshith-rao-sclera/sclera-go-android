package com.sclera.sclerago.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every translation must have exactly the default locale's translatable keys, with the same
 * format placeholders, and must actually resolve at runtime.
 */
@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    private val expectedLocales = listOf(
        "es", "fr", "de", "pt-rBR", "it", "nl", "ru", "pl", "tr", "ar",
        "iw", "hi", "ja", "ko", "zh-rCN", "zh-rTW", "in", "vi", "th", "uk",
    )

    /** Unit tests run with the module directory as the working directory. */
    private val resDir = File("src/main/res").also { check(it.isDirectory) { "run from the app module: ${it.absolutePath}" } }

    private val placeholder = Regex("""%(\d+\$)?[sd]""")

    private fun strings(dir: String, translatableOnly: Boolean = true): Map<String, String> {
        val file = File(resDir, "$dir/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { !translatableOnly || it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun allExpectedLocalesExist() {
        val present = resDir.listFiles()!!
            .filter { it.name.startsWith("values-") && File(it, "strings.xml").exists() }
            .map { it.name.removePrefix("values-") }
        assertEquals(expectedLocales.sorted(), present.sorted())
    }

    @Test
    fun everyLocaleHasEveryKey_andNoExtras() {
        val keys = strings("values").keys
        expectedLocales.forEach { locale ->
            val translated = strings("values-$locale", translatableOnly = false).keys
            assertEquals("missing in $locale", emptySet<String>(), keys - translated)
            assertEquals("unknown keys in $locale", emptySet<String>(), translated - keys)
        }
    }

    @Test
    fun placeholdersMatchDefault() {
        val base = strings("values")
        expectedLocales.forEach { locale ->
            strings("values-$locale").forEach { (key, value) ->
                assertEquals(
                    "placeholders of $key in $locale",
                    placeholder.findAll(base.getValue(key)).map { it.value }.toList(),
                    placeholder.findAll(value).map { it.value }.toList(),
                )
            }
        }
    }

    @Test
    fun noUnescapedApostrophes() {
        // A bare ' breaks aapt string parsing; translations use ’ instead.
        (listOf("values") + expectedLocales.map { "values-$it" }).forEach { dir ->
            strings(dir).forEach { (key, value) -> assertTrue("$key in $dir has '", "'" !in value) }
        }
    }

    @Test
    fun everyLocaleResolvesAtRuntime() {
        val english = context().getString(R.string.login_title)
        expectedLocales.forEach { locale ->
            RuntimeEnvironment.setQualifiers(locale)
            val ctx = context()
            assertNotEquals("login_title not translated for $locale", english, ctx.getString(R.string.login_title))
            assertTrue(ctx.getString(R.string.auth_error_server, "DETAIL-42").contains("DETAIL-42"))
            assertTrue(ctx.getString(R.string.sso_error_unavailable, 503).contains("503"))
            assertNotEquals("login_continue not translated for $locale", "Continue", ctx.getString(R.string.login_continue))
            assertNotEquals("login_tagline not translated for $locale", "Know every asset on site.", ctx.getString(R.string.login_tagline))
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()
}
