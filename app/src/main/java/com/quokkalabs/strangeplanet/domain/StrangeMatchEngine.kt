package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.*

object StrangeMatchEngine {

    // Build a grid with no starting matches (cell-by-cell, reject conflicting types)
    fun createGrid(): List<List<Tile?>> {
        val grid = Array(SM_ROWS) { arrayOfNulls<Tile>(SM_COLS) }
        for (r in 0 until SM_ROWS) {
            for (c in 0 until SM_COLS) {
                var type: TileType
                do {
                    type = TileType.random()
                } while (
                    (c >= 2 && grid[r][c - 1]?.type == type && grid[r][c - 2]?.type == type) ||
                    (r >= 2 && grid[r - 1][c]?.type == type && grid[r - 2][c]?.type == type)
                )
                grid[r][c] = Tile(type)
            }
        }
        return grid.map { it.toList() as List<Tile?> }
    }

    fun swap(grid: List<List<Tile?>>, a: Pair<Int, Int>, b: Pair<Int, Int>): List<List<Tile?>> {
        val mut = grid.map { it.toMutableList() }.toMutableList()
        val tmp = mut[a.first][a.second]
        mut[a.first][a.second] = mut[b.first][b.second]
        mut[b.first][b.second] = tmp
        return mut.map { it.toList() }
    }

    fun isAdjacent(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean {
        val dr = kotlin.math.abs(a.first - b.first)
        val dc = kotlin.math.abs(a.second - b.second)
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1)
    }

    data class MatchResult(
        val matched: Set<Pair<Int, Int>>,
        val bombs: Map<Pair<Int, Int>, TileType>,
    )

    fun findMatchResult(grid: List<List<Tile?>>): MatchResult {
        val matched = mutableSetOf<Pair<Int, Int>>()
        val bombs = mutableMapOf<Pair<Int, Int>, TileType>()

        // Horizontal runs
        for (r in 0 until SM_ROWS) {
            var c = 0
            while (c < SM_COLS) {
                val tile = grid[r][c] ?: run { c++; continue }
                var len = 1
                while (c + len < SM_COLS && grid[r][c + len]?.type == tile.type) len++
                if (len >= 3) {
                    repeat(len) { k -> matched.add(r to c + k) }
                    if (len >= 4) bombs[r to (c + len / 2)] = tile.type
                }
                c += len
            }
        }

        // Vertical runs
        for (c in 0 until SM_COLS) {
            var r = 0
            while (r < SM_ROWS) {
                val tile = grid[r][c] ?: run { r++; continue }
                var len = 1
                while (r + len < SM_ROWS && grid[r + len][c]?.type == tile.type) len++
                if (len >= 3) {
                    repeat(len) { k -> matched.add(r + k to c) }
                    if (len >= 4) bombs[(r + len / 2) to c] = tile.type
                }
                r += len
            }
        }

        return MatchResult(matched, bombs)
    }

    fun findMatches(grid: List<List<Tile?>>): Set<Pair<Int, Int>> =
        findMatchResult(grid).matched

    // Any BOMB tile inside matched set explodes its 3×3 neighborhood
    fun expandWithBombs(grid: List<List<Tile?>>, matched: Set<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val expanded = matched.toMutableSet()
        matched.forEach { (r, c) ->
            if (grid[r][c]?.kind == TileKind.BOMB) {
                for (dr in -1..1) for (dc in -1..1) {
                    val nr = r + dr; val nc = c + dc
                    if (nr in 0 until SM_ROWS && nc in 0 until SM_COLS) expanded.add(nr to nc)
                }
            }
        }
        return expanded
    }

    fun clearCells(grid: List<List<Tile?>>, cells: Set<Pair<Int, Int>>): List<List<Tile?>> =
        List(SM_ROWS) { r -> List(SM_COLS) { c -> if (r to c in cells) null else grid[r][c] } }

    // Plant bomb tiles at the given positions (only if that cell is null / was cleared)
    fun plantBombs(grid: List<List<Tile?>>, bombs: Map<Pair<Int, Int>, TileType>): List<List<Tile?>> {
        val mut = grid.map { it.toMutableList() }.toMutableList()
        bombs.forEach { (pos, type) ->
            if (mut[pos.first][pos.second] == null)
                mut[pos.first][pos.second] = Tile(type, TileKind.BOMB)
        }
        return mut.map { it.toList() }
    }

    // Tiles fall down; nulls accumulate at the top of each column
    fun applyGravity(grid: List<List<Tile?>>): List<List<Tile?>> {
        val cols = List(SM_COLS) { c ->
            val column = List(SM_ROWS) { r -> grid[r][c] }
            val tiles = column.filterNotNull()
            List(SM_ROWS - tiles.size) { null } + tiles
        }
        return List(SM_ROWS) { r -> List(SM_COLS) { c -> cols[c][r] } }
    }

    fun refill(grid: List<List<Tile?>>): List<List<Tile?>> =
        List(SM_ROWS) { r -> List(SM_COLS) { c -> grid[r][c] ?: Tile(TileType.random()) } }

    fun scoreForMatch(matchSize: Int, bombCells: Int, cascade: Int): Int {
        val base = when {
            matchSize >= 5 -> 100
            matchSize == 4 -> 60
            else -> 30
        }
        return base * matchSize * cascade + bombCells * 20
    }
}
