package com.sclera.sclerago.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sclera.sclerago.domain.model.UserSession
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraTheme

private const val PHONE = "spec:width=390dp,height=844dp"
private const val TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,dpi=240"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

internal val SampleSession = UserSession(
    email = "masteruser.brijesh@sclera.com", displayName = "Master User", role = "Admin", organisation = "Sclera",
)

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun SideMenuPreview() {
    ScleraTheme {
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            SideMenuPanel(
                open = true, session = SampleSession, propertyName = "Test 861", assetCount = 25,
                active = MenuItem.ASSETS, onClose = {}, onPick = {}, onSignOut = {},
            )
        }
    }
}

/** Before any property is downloaded: only Property list and Users, no current property. */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun SideMenuNoPropertyPreview() {
    ScleraTheme {
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            SideMenuPanel(
                open = true, session = SampleSession, propertyName = null, assetCount = 0,
                active = MenuItem.PROPERTY, onClose = {}, onPick = {}, onSignOut = {},
            )
        }
    }
}
