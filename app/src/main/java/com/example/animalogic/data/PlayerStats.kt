package com.example.animalogic.data


enum class MatchResult { WIN, LOSS, TIE }

data class PlayerStat(
    val puzzlesCompleted: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0
) {
    val matchesPlayed: Int get() = wins + losses + ties

    val score: Int get() = wins

    val winRate: Int
        get() {
            if (matchesPlayed == 0) return 0
            return (wins * 100L / matchesPlayed).toInt()
        }
}