package com.quokkalabs.strangeplanet.data.model

enum class BlockColor { PINK, CORAL, CYAN, GOLD, SLATE, VIOLET }

data class BlockPiece(
    val cells: List<Pair<Int, Int>>,  // (row, col) offsets, top-left normalised
    val color: BlockColor,
)

enum class BlockBlastPhase { IDLE, PLAYING, GAME_OVER }

data class BlockBlastState(
    val grid: List<List<BlockColor?>> = List(8) { List(8) { null } },
    val tray: List<BlockPiece?> = listOf(null, null, null),
    val score: Int = 0,
    val highScore: Int = 0,
    val phase: BlockBlastPhase = BlockBlastPhase.IDLE,
    val justCleared: Set<Pair<Int, Int>> = emptySet(),
    val combo: Int = 0,
)
