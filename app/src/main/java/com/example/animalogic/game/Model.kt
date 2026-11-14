package com.example.animalogic.game

import androidx.compose.ui.graphics.Color

enum class Animal(val label: String) {
    HIPPO("Hippo"),
    GIRAFFE("Giraffe"),
    CAMEL("Camel"),
    LION("Lion"),
}

enum class PieceColor(val label: String, val color: Color, val symbol: String) {
    RED("Red", Color(0xFFE53935), "\u25CF"),
    YELLOW("Yellow", Color(0xFFFFEB3B), "\u25A0"),
    GREEN("Green", Color(0xFF4CAF50), "\u25CF"),
    BLUE("Blue", Color(0xFF2196F3), "\u25C6"),
}

data class Piece(val id: Int) {
    val animal: Animal get() = Animal.entries[id / 4]
    val color: PieceColor get() = PieceColor.entries[id % 4]
    val description: String get() = "${animal.label} ${color.label}"
    fun matches(other: Piece): Boolean = animal == other.animal || color == other.color
}

class Puzzle(val id: Int, val rows: List<List<Int>>, val level: Int)