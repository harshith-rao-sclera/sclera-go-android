package com.sclera.sclerago.data.repository

import com.sclera.sclerago.domain.model.Fence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiseEnforcementTest {
    @Test
    fun locationIsRequiredOnlyForRestrictedUsersWithAnEnforcedFence() {
        val fences = listOf(Fence(emptyList()))
        assertTrue(requiresPremiseCheck(true, true, fences))
        assertFalse(requiresPremiseCheck(true, false, fences))
        assertFalse(requiresPremiseCheck(false, true, fences))
        assertFalse(requiresPremiseCheck(true, true, emptyList()))
    }
}
