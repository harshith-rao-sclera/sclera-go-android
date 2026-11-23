package com.sclera.sclerago.domain.usecase.geofence

import com.sclera.sclerago.domain.model.Fence
import com.sclera.sclerago.domain.model.GeoPoint
import javax.inject.Inject

/**
 * Whether [point] is inside any of the property's [fences] (a MultiPolygon: each fence an outer ring, then holes; a
 * point in a hole is outside). The ray-casting rule runs on longitude (x) and latitude (y) as degrees; fences are far
 * smaller than the earth, so no projection is needed. A fence that does not cross the antimeridian is assumed.
 */
class IsOnPremiseUseCase @Inject constructor() {
    operator fun invoke(point: GeoPoint, fences: List<Fence>): Boolean = fences.any { inFence(point, it) }

    private fun inFence(p: GeoPoint, fence: Fence): Boolean {
        val outer = fence.rings.firstOrNull() ?: return false
        return inRing(p, outer) && fence.rings.drop(1).none { inRing(p, it) }
    }

    private fun inRing(p: GeoPoint, ring: List<GeoPoint>): Boolean {
        if (ring.size < 3) return false
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.lat > p.lat) != (b.lat > p.lat) && p.lng < (b.lng - a.lng) * (p.lat - a.lat) / (b.lat - a.lat) + a.lng) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
