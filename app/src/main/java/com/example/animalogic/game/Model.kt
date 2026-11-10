package com.example.animalogic.game

enum class Animal(val label: String) {
    HIPPO("Hippo"),
    GIRAFFE("Giraffe"),
    CAMEL("Camel"),
    LION("Lion"),
}

enum class PieceColor(val label: String) {
    RED("Red"),
    YELLOW("Yellow"),
    GREEN("Green"),
    BLUE("Blue"),
}

data class Piece(val id: Int) {
    val animal: Animal get() = Animal.values()[id / 4]
    val color: PieceColor get() = PieceColor.values()[id % 4]
    val description: String get() = "${animal.label} ${color.label}"
    fun matches(other: Piece): Boolean = animal == other.animal || color == other.color
}

class Puzzle(val id: Int, val rows: List<List<Int>>, val level: Int)