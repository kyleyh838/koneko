package io.nekohasekai.sagernet.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSelectionRefreshTest {

    @Test
    fun `changing selection refreshes both old and new cards`() {
        assertEquals(listOf(5L, 9L), selectionRefreshIds(5L, 9L))
    }

    @Test
    fun `nothing previously selected refreshes only the new card`() {
        assertEquals(listOf(9L), selectionRefreshIds(0L, 9L))
    }

    @Test
    fun `tapping the already selected card refreshes it once`() {
        assertEquals(listOf(5L), selectionRefreshIds(5L, 5L))
    }

    @Test
    fun `deselecting to none refreshes only the old card`() {
        assertEquals(listOf(9L), selectionRefreshIds(9L, 0L))
    }

    @Test
    fun `no selection at all refreshes nothing`() {
        assertEquals(emptyList<Long>(), selectionRefreshIds(0L, 0L))
    }
}
