package com.example.animalogic.data

const val TOTAL_NUM_OF_PUZZLES = 60

enum class MatchResult { WIN, LOSS, TIE }

data class PlayerStat(
    val completedPuzzleIds: Set<Int> = emptySet(),
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0
) {
    val puzzlesCompleted: Int get() = completedPuzzleIds.size

    val matchesPlayed: Int get() = wins + losses + ties

    val score: Int get() = wins

    val winRate: Int
        get() {
            if (matchesPlayed == 0) return 0
            return (wins * 100L / matchesPlayed).toInt()
        }
}