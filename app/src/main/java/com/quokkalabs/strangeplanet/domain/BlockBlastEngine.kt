package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.BlockBlastPhase
import com.quokkalabs.strangeplanet.data.model.BlockBlastState
import com.quokkalabs.strangeplanet.data.model.BlockColor
import com.quokkalabs.strangeplanet.data.model.BlockPiece
import kotlin.random.Random

class BlockBlastEngine {

    companion object {
        const val GRID = 8

        private val ALL_SHAPES: List<List<Pair<Int, Int>>> = listOf(
            // 1-cell
            listOf(0 to 0),
            // Dominoes
            listOf(0 to 0, 0 to 1),
            listOf(0 to 0, 1 to 0),
            // Triominoes
            listOf(0 to 0, 0 to 1, 0 to 2),
            listOf(0 to 0, 1 to 0, 2 to 0),
            listOf(0 to 0, 0 to 1, 1 to 0),
            listOf(0 to 0, 0 to 1, 1 to 1),
            // 2×2 square
            listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
            // L shapes (4 rotations)
            listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1),
            listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0),
            listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(0 to 2, 1 to 0, 1 to 1, 1 to 2),
            // J shapes (4 rotations)
            listOf(0 to 1, 1 to 1, 2 to 0, 2 to 1),
            listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2),
            listOf(0 to 0, 0 to 1, 1 to 0, 2 to 0),
            listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2),
            // T shapes (4 rotations)
            listOf(0 to 0, 0 to 1, 0 to 2, 1 to 1),
            listOf(0 to 0, 1 to 0, 1 to 1, 2 to 0),
            listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2),
            listOf(0 to 1, 1 to 0, 1 to 1, 2 to 1),
            // S / Z shapes
            listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1),
            listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
            listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2),
            listOf(0 to 1, 1 to 0, 1 to 1, 2 to 0),
            // Lines
            listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
            listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0),
            listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4),
            listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0),
            // Large L
            listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2),
            listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 2),
            // Rectangles
            listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1, 1 to 2),
            listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0, 2 to 1),
        )

        private val COLORS = BlockColor.entries.toTypedArray()
    }

    fun generateTray(): List<BlockPiece> = List(3) {
        BlockPiece(
            ALL_SHAPES[Random.nextInt(ALL_SHAPES.size)],
            COLORS[Random.nextInt(COLORS.size)],
        )
    }

    fun canPlace(grid: List<List<BlockColor?>>, piece: BlockPiece, row: Int, col: Int): Boolean =
        piece.cells.all { (dr, dc) ->
            val r = row + dr; val c = col + dc
            r in 0 until GRID && c in 0 until GRID && grid[r][c] == null
        }

    fun place(grid: List<List<BlockColor?>>, piece: BlockPiece, row: Int, col: Int): List<List<BlockColor?>> {
        val g = grid.map { it.toMutableList() }
        piece.cells.forEach { (dr, dc) -> g[row + dr][col + dc] = piece.color }
        return g.map { it.toList() }
    }

    /** Returns (clearedGrid, clearedCells, lineCount). */
    fun clearLines(grid: List<List<BlockColor?>>): Triple<List<List<BlockColor?>>, Set<Pair<Int, Int>>, Int> {
        val fullRows = (0 until GRID).filter { r -> grid[r].all { it != null } }
        val fullCols = (0 until GRID).filter { c -> (0 until GRID).all { r -> grid[r][c] != null } }
        val cleared = mutableSetOf<Pair<Int, Int>>()
        fullRows.forEach { r -> repeat(GRID) { c -> cleared += r to c } }
        fullCols.forEach { c -> repeat(GRID) { r -> cleared += r to c } }
        val lineCount = fullRows.size + fullCols.size
        if (lineCount == 0) return Triple(grid, emptySet(), 0)
        val newGrid = grid.mapIndexed { r, row ->
            row.mapIndexed { c, cell -> if ((r to c) in cleared) null else cell }
        }
        return Triple(newGrid, cleared, lineCount)
    }

    fun score(cellsPlaced: Int, linesCleared: Int, combo: Int): Int {
        val lineScore = when {
            linesCleared <= 0 -> 0
            linesCleared == 1 -> 10
            linesCleared == 2 -> 25
            linesCleared == 3 -> 45
            linesCleared == 4 -> 70
            else -> 70 + (linesCleared - 4) * 30
        }
        val comboBonus = if (linesCleared > 0 && combo > 0) combo * 10 else 0
        return cellsPlaced + lineScore + comboBonus
    }

    /** True if at least one remaining tray piece can be placed somewhere. */
    fun canAnyFit(grid: List<List<BlockColor?>>, tray: List<BlockPiece?>): Boolean {
        val remaining = tray.filterNotNull()
        if (remaining.isEmpty()) return true
        return remaining.any { piece ->
            (0 until GRID).any { r -> (0 until GRID).any { c -> canPlace(grid, piece, r, c) } }
        }
    }

    fun initial(highScore: Int) = BlockBlastState(
        tray = generateTray(),
        score = 0,
        highScore = highScore,
        phase = BlockBlastPhase.PLAYING,
    )
}
