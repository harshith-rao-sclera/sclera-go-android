package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.findNode
import com.sclera.sclerago.domain.repository.LocationRepository
import com.sclera.sclerago.domain.usecase.location.ObserveLocationTreeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The building → floor → location tree the location picker reads (Configuration → Building info changes the places at
 * the server). The tree itself is built by the offline database.
 */
class LocationUseCasesTest {
    private val repo = object : LocationRepository {
        override val tree: StateFlow<List<LocNode>> = MutableStateFlow(emptyList())
    }

    @Test
    fun observe_exposesTheRepositoryTree() = assertEquals(repo.tree, ObserveLocationTreeUseCase(repo)())

    @Test
    fun findNode_searchesNestedNodes() {
        val tree = listOf(LocNode("b", "B", NodeKind.BUILDING, listOf(LocNode("f", "F", NodeKind.FLOOR, listOf(LocNode("l", "L", NodeKind.LOCATION))))))
        assertEquals("L", tree.findNode("l")?.name)
        assertEquals(NodeKind.FLOOR, tree.findNode("f")?.kind)
        assertNull(tree.findNode("zzz"))
    }
}
