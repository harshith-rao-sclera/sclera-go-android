package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.model.QrMeasurement
import com.sclera.sclerago.domain.model.TagCheck
import com.sclera.sclerago.domain.model.TagUpdate
import com.sclera.sclerago.domain.model.TagUse
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite
import com.sclera.sclerago.domain.repository.QrDirectionSource
import com.sclera.sclerago.domain.repository.TagServerInfo
import com.sclera.sclerago.domain.repository.TaggingRepository

/** A [TaggingRepository] that answers [check] with what the test sets and records what was registered and written. */
class FakeTaggingRepository(var check: TagCheck = freeTag()) : TaggingRepository {
    var checkError: Exception? = null
    var writeError: Exception? = null
    var update = TagUpdate(1, 0)
    val registered = mutableListOf<TagValue>()
    val written = mutableListOf<TagWrite>()
    val checked = mutableListOf<TagValue>()

    override suspend fun check(tag: TagValue, asset: String, property: String): TagCheck {
        checked += tag
        checkError?.let { throw it }
        return check
    }

    override suspend fun register(tag: TagValue, asset: String, property: String) {
        registered += tag
    }

    override suspend fun write(write: TagWrite): TagUpdate {
        writeError?.let { throw it }
        written += write
        return update
    }
}

fun freeTag() = TagCheck(TagUse.FREE, isAdc = true, heldBy = null, isOnOtherProperty = false, holderTagCount = 0, confirmsMove = true)

fun heldTag(by: String = "other", isAdc: Boolean = true, count: Int = 1, otherProperty: Boolean = false, confirms: Boolean = true) =
    TagCheck(TagUse.IN_USE, isAdc, by, otherProperty, count, confirms)

fun unknownTag() = TagCheck(TagUse.UNKNOWN, isAdc = false, heldBy = null, isOnOtherProperty = false, holderTagCount = 0, confirmsMove = true)

class FakeDirectionSource(val measurement: QrMeasurement = QrMeasurement(10.0, 20.0, 30.0, 0.9, "fake")) : QrDirectionSource {
    override suspend fun measure() = measurement
}

class FakeTagServer(override val url: String = "https://app.example.com") : TagServerInfo
