package com.example.animalogic.game

import kotlin.random.Random

object PuzzleFactory {

    // MAYBE NOT USEFUL ANYMORE
    // const val catalogue = 60
    // const val level = 5
    /* val catalogue = list<Puzzle> by lazy {
        // increase variable until 60 total generation calls
    }*/

    //fun generate(seed: Long): List<List<Int>>) {
        // val random = Random(seed)
        // Array
        // Bool array

        //Depth First Search?

        // labels
        // val rows = List(4) { mutableListOf<Int>() }
        // path indexed
        //return rows
    // }

    // solution counts fucntion if we do difficulty??

    // will return the row that the next piece should be removed from to stay solvable
    // oh and this is helpful for finding next hint
    fun nextMoveRow(rows: List<List<Int>>, pointers: IntArray, last: Int): Int? {
        // hash map??
        return null
    }

    /* private fun count(rows: List<List<Int>>, ptrs: IntArray, last: Int, memo: HashMap<Int, Long>): Long {
        using pointers to see how many way the puzzle can be finished, this would also be used for difficulty
        return total
    } */
}