package com.sclera.sclerago.data.mapper

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The Move to Managed Assets request, as the web sends it: `{ assetIds, networkName, assignee? }` (no assignee: left out). */
fun moveBody(assetIds: Collection<String>, networkName: String, assignee: String?): JsonObject = buildJsonObject {
    putJsonArray("assetIds") { assetIds.forEach { add(it) } }
    put("networkName", networkName)
    assignee?.let { put("assignee", it) }
}

/** What moving [assetIds] would take, asked before the network is chosen: `{ assetIds }` (the network is optional there). */
fun movePreviewBody(assetIds: Collection<String>): JsonObject = buildJsonObject {
    putJsonArray("assetIds") { assetIds.forEach { add(it) } }
}
