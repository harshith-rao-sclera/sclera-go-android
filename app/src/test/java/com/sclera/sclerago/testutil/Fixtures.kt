package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.validation.CheckInput
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.lifecycle.RetainedLifecycle

fun asset(
    id: Int = 1,
    name: String = "Pump $id",
    prop: String = "p1",
    st: Status = Status.DRAFT,
    src: Source = Source.MANUAL,
    type: AssetType = AssetType.PUMP,
    loc: String? = "L1",
) = asset(id.toString(), name, prop, st, src, type, loc)

fun asset(
    id: String,
    name: String = "Pump $id",
    prop: String = "p1",
    st: Status = Status.DRAFT,
    src: Source = Source.MANUAL,
    type: AssetType = AssetType.PUMP,
    loc: String? = "L1",
) = Asset(id, name, "detail", loc, "Building A", src, st, type, prop)

fun checkInput(values: Map<String, String> = emptyMap(), photoCount: Int = 1, hasLocation: Boolean = true) =
    CheckInput(values, hasLocation, Status.DRAFT, fromCamera = false, photoCount = photoCount)

/** An [ActivityRetainedLifecycle] whose clearing the test triggers. */
class FakeRetainedLifecycle : ActivityRetainedLifecycle {
    private val listeners = mutableListOf<RetainedLifecycle.OnClearedListener>()

    override fun addOnClearedListener(listener: RetainedLifecycle.OnClearedListener) {
        listeners += listener
    }

    override fun removeOnClearedListener(listener: RetainedLifecycle.OnClearedListener) {
        listeners -= listener
    }

    fun clear() = listeners.toList().forEach { it.onCleared() }
}
