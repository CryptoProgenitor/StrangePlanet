package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.SM_COLS
import com.quokkalabs.strangeplanet.data.model.SM_ROWS
import com.quokkalabs.strangeplanet.data.model.Tile
import com.quokkalabs.strangeplanet.data.model.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrangeMatchEngineTest {

    private val e = StrangeMatchEngine

    /**
     * A full board with no runs at all, built only from non-STAR types:
     * horizontal neighbours differ by 2 (mod 6) and vertical neighbours by 1.
     */
    private fun noMatchGrid(): List<List<Tile?>> {
        val others = TileType.entries.filter { it != TileType.STAR }
        return List(SM_ROWS) { r -> List(SM_COLS) { c -> Tile(others[(r + 2 * c) % others.size]) } }
    }

    private fun List<List<Tile?>>.withTiles(vararg cells: Pair<Int, Int>, type: TileType = TileType.STAR): List<List<Tile?>> {
        val set = cells.toSet()
        return mapIndexed { r, row -> row.mapIndexed { c, t -> if (r to c in set) Tile(type) else t } }
    }

    @Test
    fun baseGrid_hasNoMatches() {
        assertTrue(e.findMatches(noMatchGrid()).isEmpty())
    }

    @Test
    fun findMatches_findsHorizontalRunOfThree() {
        val g = noMatchGrid().withTiles(2 to 1, 2 to 2, 2 to 3)
        assertEquals(setOf(2 to 1, 2 to 2, 2 to 3), e.findMatches(g))
    }

    @Test
    fun findMatches_findsVerticalRunOfThree() {
        val g = noMatchGrid().withTiles(4 to 5, 5 to 5, 6 to 5)
        assertEquals(setOf(4 to 5, 5 to 5, 6 to 5), e.findMatches(g))
    }

    @Test
    fun findMatches_ignoresRunsOfTwo_andFindsBothDirectionsAtOnce() {
        val g = noMatchGrid()
            .withTiles(0 to 0, 0 to 1) // only two: not a match
            .withTiles(8 to 4, 8 to 5, 8 to 6) // horizontal along the bottom edge
            .withTiles(1 to 6, 2 to 6, 3 to 6) // vertical along the right edge
        assertEquals(setOf(8 to 4, 8 to 5, 8 to 6, 1 to 6, 2 to 6, 3 to 6), e.findMatches(g))
    }

    @Test
    fun applyGravityThenRefill_leavesNoNullCells() {
        val g = noMatchGrid()
        val cleared = e.clearCells(g, setOf(8 to 0, 7 to 0, 3 to 3, 0 to 6, 4 to 2, 5 to 2, 6 to 2))
        assertEquals(7, cleared.sumOf { row -> row.count { it == null } })

        val fallen = e.applyGravity(cleared)
        for (c in 0 until SM_COLS) {
            val column = (0 until SM_ROWS).map { r -> fallen[r][c] }
            val firstTile = column.indexOfFirst { it != null }.let { if (it < 0) SM_ROWS else it }
            assertTrue("column $c: nulls must all be on top", column.drop(firstTile).all { it != null })
            // Surviving tiles keep their top-to-bottom order.
            val before = (0 until SM_ROWS).mapNotNull { r -> cleared[r][c]?.id }
            assertEquals(before, column.mapNotNull { it?.id })
        }

        val refilled = e.refill(fallen)
        assertEquals(SM_ROWS, refilled.size)
        assertTrue(refilled.all { it.size == SM_COLS })
        refilled.forEach { row -> row.forEach { assertNotNull(it) } }
        // Existing tiles are untouched by the refill.
        for (r in 0 until SM_ROWS) for (c in 0 until SM_COLS) {
            fallen[r][c]?.let { assertEquals(it.id, refilled[r][c]!!.id) }
        }
    }

    @Test
    fun isAdjacent_onlyAcceptsOrthogonalNeighbours() {
        assertTrue(e.isAdjacent(3 to 3, 3 to 4))
        assertTrue(e.isAdjacent(3 to 3, 2 to 3))
        assertFalse("diagonal", e.isAdjacent(3 to 3, 4 to 4))
        assertFalse("two apart", e.isAdjacent(3 to 3, 3 to 5))
        assertFalse("far apart", e.isAdjacent(0 to 0, 8 to 6))
        assertFalse("same cell", e.isAdjacent(3 to 3, 3 to 3))
    }

    @Test
    fun swap_exchangesExactlyTwoCells() {
        val g = noMatchGrid()
        val s = e.swap(g, 1 to 1, 1 to 2)
        assertEquals(g[1][1], s[1][2])
        assertEquals(g[1][2], s[1][1])
        for (r in 0 until SM_ROWS) for (c in 0 until SM_COLS) {
            if ((r to c) != (1 to 1) && (r to c) != (1 to 2)) assertEquals(g[r][c], s[r][c])
        }
    }
}
