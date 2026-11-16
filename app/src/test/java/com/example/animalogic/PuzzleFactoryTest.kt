package com.example.animalogic

import com.example.animalogic.game.GameState
import com.example.animalogic.game.PuzzleFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A few tests for PuzzleFactory just to make sure some core features are actually working as intended.
 * Like, if a higher difficulty level actually has more solutions than a lower level, a user and even
 * a programmer might not notice while playing or manually testing.
 */

class PuzzleFactoryTest {

    // Btw, deterministic algorithms means they always produce the same output no matter the input
    @Test
    fun boardsAreValidSolvableAndDeterministic() {
        for (seed in 0L until 5000L) {
            val rows = PuzzleFactory.generate(seed)
            assertTrue("invalid board, seed $seed", PuzzleFactory.isValidBoard(rows))
            assertTrue("unsolvable board, seed $seed", PuzzleFactory.isSolvable(rows))
            assertEquals("not deterministic, seed $seed", rows, PuzzleFactory.generate(seed))
        }
    }

    // Plays every board to the end using only hints, through the real GameState.
    @Test
    fun hintsSolveTheWholeBoardThroughGameState() {
        for (seed in 0L until 2000L) {
            val game = GameState(PuzzleFactory.generate(seed))
            var steps = 0
            while (!game.isComplete && steps < 16) {
                val (row, col) = game.hint() ?: break
                assertTrue(
                    "hint move rejected, seed $seed",
                    game.tryRemove(row, col) is GameState.MoveResult.Ok
                )
                steps++
            }
            assertTrue("not completed, seed $seed", game.isComplete)
        }
    }

    // Proves the checker can identify an invalid board: finds a valid-but-unsolvable board and checks it is caught.
    @Test
    fun solverRejectsUnsolvableBoards() {
        val random = Random(1)
        var found = false
        repeat(1000) {
            val ids = (0 until 16).shuffled(random)
            val rows = ids.chunked(4)
            if (!found && PuzzleFactory.countSolutions(rows) == 0L) {
                found = true
                assertTrue("should be a valid board", PuzzleFactory.isValidBoard(rows))
                assertTrue("hint should be null", PuzzleFactory.nextMoveRow(rows, IntArray(4), -1) == null)
            }
        }
        assertTrue("never found an unsolvable board to test", found)
    }

    @Test
    fun higherLevelsHaveFewerSolutions() {
        fun avg(level: Int): Double =
            (0L until 200L).map { PuzzleFactory.countSolutions(PuzzleFactory.generate(it, level)) }.average()
        val averages = (1..5).map { avg(it) }
        for (i in 0 until 4) assertTrue("level ${i + 1} should have more solutions than ${i + 2}: $averages", averages[i] > averages[i + 1])
        for (level in 1..5) {
            assertEquals("level $level not deterministic", PuzzleFactory.generate(7L, level), PuzzleFactory.generate(7L, level))
        }
    }
}