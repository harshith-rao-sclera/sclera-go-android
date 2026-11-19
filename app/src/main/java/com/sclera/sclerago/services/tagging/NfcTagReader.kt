package com.sclera.sclerago.services.tagging

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import com.sclera.sclerago.domain.model.NfcFailure

/** What an NFC tag holds. */
class NfcContent(val text: String, val isBlank: Boolean)

/**
 * Reads the link or text written on an NFC tag held to the phone, with the system's reader mode (the tag is not handed to any
 * other app while this is on). Nothing read is logged.
 */
object NfcTagReader {
    /**
     * Starts listening on [activity]. [onRead] and [onFailure] are called on the main thread, once. Returns false (after calling
     * [onFailure]) when the phone has no NFC or it is switched off. Call [stop] when done.
     */
    fun start(activity: Activity, onRead: (NfcContent) -> Unit, onFailure: (NfcFailure) -> Unit): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            onFailure(NfcFailure.UNAVAILABLE)
            return false
        }
        if (!adapter.isEnabled) {
            onFailure(NfcFailure.DISABLED)
            return false
        }
        adapter.enableReaderMode(
            activity,
            { tag -> activity.runOnUiThread { read(tag, onRead, onFailure) } },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
        return true
    }

    fun stop(activity: Activity) {
        runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
    }

    private fun read(tag: Tag, onRead: (NfcContent) -> Unit, onFailure: (NfcFailure) -> Unit) {
        val ndef = Ndef.get(tag) ?: return onFailure(NfcFailure.FAILED)
        val message = try {
            ndef.connect()
            ndef.ndefMessage
        } catch (_: Exception) {
            return onFailure(NfcFailure.FAILED)
        } finally {
            runCatching { ndef.close() }
        }
        // A chip with nothing on it has no message, or an empty one.
        val text = message?.let(::textOf)
        onRead(NfcContent(text.orEmpty(), isBlank = text == null))
    }

    /** The link or the text of the first record that has one. */
    internal fun textOf(message: NdefMessage): String? = message.records.firstNotNullOfOrNull { record ->
        val text = runCatching {
            record.toUri()?.toString()
                ?: if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) textPayload(record.payload) else null
        }.getOrNull()
        text?.takeIf { it.isNotBlank() }
    }

    /** An NDEF text record: a status byte (UTF-16 flag, language code length), the language code, then the text. */
    private fun textPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val languageLength = payload[0].toInt() and LANGUAGE_MASK
        val charset = if (payload[0].toInt() and UTF16_FLAG == 0) Charsets.UTF_8 else Charsets.UTF_16
        val start = 1 + languageLength
        return if (start <= payload.size) String(payload, start, payload.size - start, charset) else null
    }

    private const val LANGUAGE_MASK = 0x3F
    private const val UTF16_FLAG = 0x80
}
