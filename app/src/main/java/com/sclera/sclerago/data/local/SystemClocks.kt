package com.sclera.sclerago.data.local

import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import java.util.UUID
import javax.inject.Inject

/** Random UUID based ids ("u-3f2a..."); collision-free, unlike clock based ids. */
class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun next(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
