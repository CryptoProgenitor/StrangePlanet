package com.quokkalabs.strangeplanet.data.model

import kotlin.random.Random

const val SM_COLS = 7
const val SM_ROWS = 9

private var tileIdCounter = 0L
fun nextTileId(): Long = ++tileIdCounter

enum class TileType {
    STAR, DOG, ROLLSUCK, UNICORN, ALIEN_DAD, CAT, SOCKS;
    companion object {
        fun random(): TileType = values()[Random.nextInt(values().size)]
    }
}

enum class TileKind { NORMAL, BOMB }

data class Tile(
    val type: TileType,
    val kind: TileKind = TileKind.NORMAL,
    val id: Long = nextTileId(),
)

enum class StrangeMatchPhase { READY, PLAYING, ANIMATING, GAME_OVER }

data class StrangeMatchState(
    val grid: List<List<Tile?>> = emptyList(),
    val score: Int = 0,
    val highScore: Int = 0,
    val movesLeft: Int = 30,
    val scoreTarget: Int = 5000,
    val level: Int = 1,
    val phase: StrangeMatchPhase = StrangeMatchPhase.READY,
    val selectedCell: Pair<Int, Int>? = null,
    val matchedCells: Set<Pair<Int, Int>> = emptySet(),
    val bombExplosionCells: Set<Pair<Int, Int>> = emptySet(),
)
