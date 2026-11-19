package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.remote.AuthConfig
import com.sclera.sclerago.domain.repository.TagServerInfo
import javax.inject.Inject

class TagServerInfoImpl @Inject constructor() : TagServerInfo {
    override val url: String = AuthConfig.APP_URL
}
