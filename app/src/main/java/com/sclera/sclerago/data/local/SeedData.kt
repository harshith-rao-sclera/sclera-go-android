package com.sclera.sclerago.data.local

import com.sclera.sclerago.domain.model.ADMINS_ID
import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Property

// Mock data the in-memory property and access repositories start from, until they come from the Sclera API.

val PROPERTIES = listOf(
    Property("p1", "Test 861", true),
    Property("p2", "Dhanush 16/10 2.1.0", false),
    Property("p3", "Post production 12/10", false),
    Property("p4", "Sample Property 5", false),
    Property("p5", "Harbour Point Office Tower", false, "Office", Address("1 Harbour Point", country = "Singapore")),
    Property("p6", "Northgate Logistics Hub", false, "Warehouse", Address("Northgate Industrial Estate", country = "United Kingdom", state = "England")),
    Property("p7", "Riverside Medical Centre", false, "Hospital", Address("12 Riverside Road", country = "Australia", state = "Victoria")),
)

val DEFAULT_GROUPS = listOf(
    Group(ADMINS_ID, "Admins", "Can do everything.", Permission.entries.toSet(), locked = true),
    Group("g-collectors", "Collectors", "Collect assets.", setOf(Permission.COLLECT)),
    Group("g-reviewers", "Reviewers", "Collect assets and validate them.", setOf(Permission.COLLECT, Permission.VALIDATE)),
    Group("g-viewers", "Viewers", "Look, but not change anything.", emptySet()),
)
