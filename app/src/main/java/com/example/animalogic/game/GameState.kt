package com.example.animalogic.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class GameState(val rows: List<List<Int>>) {
    var removed by mutableStateOf(listOf<Int>())
            private set

    val isComplete: Boolean get() = removed.size == 16

    private fun pointers(): IntArray = IntArray(4) { r -> removed.count { it in rows[r] } }
    fun pieceAt(row: Int, col: Int): Piece? = if (col >= pointers()[row]) Piece(rows[row][col]) else null

    sealed class MoveResult {
        object Ok : MoveResult()
        class Invalid(val reason: String) : MoveResult()
    }

    fun tryRemove(row: Int, col: Int): MoveResult {
        val ptr = pointers()
        if (col < ptr[row]) return MoveResult.Invalid("Piece is already gone")
        if (col != ptr[row]) return MoveResult.Invalid("This piece cannot be removed yet")
        val piece = Piece(rows[row][col])
        val last = removed.lastOrNull()
        if (last != null && piece.matches(Piece(last))) {
            return MoveResult.Invalid("Must match color or animal")
        }
        removed = removed + piece.id
        return MoveResult.Ok
    }

    fun undo() {
        if (removed.isNotEmpty()) removed = removed.dropLast(1)
    }

    fun reset() {
        removed = emptyList()
    }

    fun hint(): Pair<Int, Int>? {
        if (isComplete) return null
        val ptr = pointers()
        val row = PuzzleFactory.nextMoveRow(rows, ptr, removed.lastOrNull() ?: -1) ?: return null
        return row to ptr[row]
    }
}