package com.sclera.sclerago.data.remote.tagging

import android.net.Uri
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.TagValue

/** Where each kind of tag lives on the server, relative to `/vdms/api/`. */
internal object TagPaths {
    private fun folder(tag: TagValue) = when (tag.kind) {
        TagKind.QR -> if (tag.isSystem) "qrCode" else "clientQrCode"
        TagKind.BARCODE -> "clientBarCode"
        TagKind.NFC -> "clientNfc"
    }

    private fun checkAction(tag: TagValue) = when (tag.kind) {
        TagKind.QR -> if (tag.isSystem) "getAdcCheckByQrCodeId" else "getAdcCheckByClientQrCodeId"
        TagKind.BARCODE -> "getAdcCheckByClientBarCodeId"
        TagKind.NFC -> "getAdcCheckByClientNfcId"
    }

    private fun registerAction(tag: TagValue) = when (tag.kind) {
        TagKind.QR -> "addClientQrCode"
        TagKind.BARCODE -> "addClientBarCode"
        TagKind.NFC -> "addClientNfc"
    }

    private fun updateAction(tag: TagValue) = when (tag.kind) {
        TagKind.QR -> if (tag.isSystem) "updateQrcodeById" else "updateClientQrCodeById"
        TagKind.BARCODE -> "updateClientBarCodeById"
        TagKind.NFC -> "updateClientNfcById"
    }

    fun check(tag: TagValue) = "${folder(tag)}/${Uri.encode(tag.id)}/${checkAction(tag)}"
    fun register(tag: TagValue) = "${folder(tag)}/${registerAction(tag)}"
    fun update(tag: TagValue) = "${folder(tag)}/${Uri.encode(tag.id)}/${updateAction(tag)}"
}
