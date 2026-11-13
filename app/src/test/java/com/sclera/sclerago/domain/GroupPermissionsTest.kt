package com.sclera.sclerago.domain

import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a group can do: its own permissions, or everything when it is locked (Admins). */
class GroupPermissionsTest {
    private val reviewers = Group("g-r", "Reviewers", "", setOf(Permission.VALIDATE, Permission.COLLECT))
    private val admins = Group("g-a", "Admins", "", emptySet(), locked = true)
    private val viewers = Group("g-v", "Viewers", "", emptySet())

    @Test
    fun can_followsThePermissions() {
        assertTrue(reviewers.can(Permission.VALIDATE))
        assertFalse(reviewers.can(Permission.CONFIGURE))
        assertFalse(viewers.can(Permission.COLLECT))
    }

    @Test
    fun lockedGroup_canDoEverything() {
        assertTrue(Permission.entries.all(admins::can))
        assertEquals(Permission.entries, admins.effectivePermissions)
    }

    @Test
    fun effectivePermissions_areInPermissionOrder() {
        assertEquals(listOf(Permission.COLLECT, Permission.VALIDATE), reviewers.effectivePermissions)
        assertTrue(viewers.effectivePermissions.isEmpty())
    }

    @Test
    fun topPermission_isTheStrongestOneItCanDo() {
        assertEquals(Permission.VALIDATE, reviewers.topPermission)
        assertEquals(Permission.MANAGE_ACCESS, admins.topPermission)
        assertNull(viewers.topPermission)
    }
}
