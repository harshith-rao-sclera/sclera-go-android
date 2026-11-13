package com.sclera.sclerago.data.local

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.sclera.sclerago.domain.repository.UserDataCleaner
import javax.inject.Inject
import javax.inject.Singleton

/** Clears what the SSO and map WebViews keep: cookies and DOM storage (local storage, databases). */
@Singleton
class WebDataCleaner @Inject constructor() : UserDataCleaner {
    override fun clear() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
    }
}
