package com.quokkalabs.strangeplanet.data.model

enum class TetroType { I, O, T, S, Z, J, L }

enum class TetrisPhase { IDLE, PLAYING, LOCKING, CLEARING, GAME_OVER }

data class Tetromino(
    val type: TetroType,
    val rotation: Int = 0,  // 0..3
    val row: Int = 0,
    val col: Int = 0,
)

data class TetrisInput(
    val leftDown: Boolean = false,
    val rightDown: Boolean = false,
    val softDrop: Boolean = false,
    val rotate: Boolean = false,    // consumed each frame
    val hardDrop: Boolean = false,  // consumed each frame
)

data class TetrisState(
    val grid: List<List<TetroType?>> = List(20) { List(10) { null } },
    val active: Tetromino? = null,
    val next: TetroType = TetroType.I,
    val score: Int = 0,
    val highScore: Int = 0,
    val level: Int = 1,
    val lines: Int = 0,
    val phase: TetrisPhase = TetrisPhase.IDLE,
    val clearingRows: List<Int> = emptyList(),
    val gravityFrames: Int = 0,
    val lockFrames: Int = 0,
    val leftHeld: Int = 0,
    val rightHeld: Int = 0,
    val backToBack: Boolean = false,
)
