package com.example.animalogic.game

import kotlin.random.Random

/**
 * Builds AnimaLogic boards that are guaranteed solvable.
 *
 * Board = 4 rows of 4 piece ids. Every id 0 to 15 appears exactly once.
 * (id = animal * 4 + color), Model.kt derives animal and color from the id
 *
 * The idea here is to generate the solution first. The solution is the order of removal you need
 * to solve the puzzle. Then, put that order into a board, so that there is a guaranteed solution.
 *
 * Everything random goes through ONE Random(seed), so the same seed gives the same board on every
 * device (needed for Bluetooth versus mode).
 *
 * Future programmers from other groups, I don't know if this will be allowed or not later on, but
 * I recommend not screwing around too much in this file. Feel free to ask me, Travis, any questions
 * about anything in here.
 */
object PuzzleFactory {

    private const val ROW_COUNT = 4
    private const val ROW_LENGTH = 4
    private const val PIECE_COUNT = ROW_COUNT * ROW_LENGTH
    private const val MAX_ATTEMPTS = 500

    /** Value of `last` if nothing has been removed yet (matches GameState.hint). */
    const val NO_PIECE = -1

    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 5

    /**
     * Allowed number of distinct solutions per level. Fewer solutions = fewer ways out = harder.
     * Calibrated by sampling generated boards (see PuzzleFactoryCheck).
     */
    private val LEVEL_BANDS: List<LongRange> = listOf(
        400L..Long.MAX_VALUE, // level 1: lots of ways through
        150L..399L,           // level 2
        60L..149L,            // level 3
        20L..59L,             // level 4
        1L..19L,              // level 5: very few ways through
    )

    /**
     * Use the matches() from Model.kt to precompute a lookup table to save time.
     * Otherwise, the matches() from Model.kt is gonna be called an insane amount of times.
     */
    private val matches: Array<BooleanArray> = Array(PIECE_COUNT) { a ->
        BooleanArray(PIECE_COUNT) { b -> Piece(a).matches(Piece(b)) }
    }

    // ----- Public -----

    /**
     * A solvable board for this seed (no difficulty targeting).
     * Might be easier for multiplayer?
     * */
    fun generate(seed: Long): List<List<Int>> {
        val random = Random(seed)
        return build(random)
    }

    /** A solvable board for this seed whose solution count falls in the level's band. */
    fun generate(seed: Long, level: Int): List<List<Int>> {
        // only accept 1-5
        require(level in MIN_LEVEL..MAX_LEVEL) { "level must be $MIN_LEVEL..$MAX_LEVEL" }

        // get the solution count range for whatever difficulty was chosen
        val band = LEVEL_BANDS[level - MIN_LEVEL]
        val random = Random(seed)
        var rows = build(random)
        var attempt = 1

        // Check the board to see if it fits the difficulty or not.
        // If you hit 500 attempts, something's probably wrong so just give up.
        while (countSolutions(rows) !in band && attempt < MAX_ATTEMPTS) {
            // btw, build(random) uses up numbers from the random stream, so it'll be different each loop
            rows = build(random)
            attempt++
        }
        return rows // always solvable. If it hit 500 attempts, just return the last board, even if it's the wrong difficulty
    }

    fun createPuzzle(seed: Long, level: Int): Puzzle =
        Puzzle(id = seed.toInt(), rows = generate(seed, level), level = level)

    /**
     * The row the next piece should come from so the puzzle stays finishable, or null if the
     * current state is a dead end (or already complete). Used in hint(), in GameState.kt .
     */
    fun nextMoveRow(rows: List<List<Int>>, pointers: IntArray, last: Int): Int? {
        // make copy of pointers so pointers isn't modified
        val ptrs = pointers.copyOf()
        val memo = HashMap<Int, Long>()
        for (r in 0 until ROW_COUNT) {
            // if ptrs[r] >= 4, indexing would crash
            if (ptrs[r] >= ROW_LENGTH) continue
            val piece = rows[r][ptrs[r]]
            if (last != NO_PIECE && !matches[last][piece]) continue
            ptrs[r]++  // increment to see how many ways exist to win if that piece is removed
            val ways = count(rows, ptrs, piece, memo)
            ptrs[r]--  // decrement afterward
            // if there still is a way to win, return the row we confirmed has that way
            if (ways > 0) return r
        }
        return null
    }

    /** Number of distinct removal orders that clear the board from the start. */
    fun countSolutions(rows: List<List<Int>>): Long =
        count(rows, IntArray(ROW_COUNT), NO_PIECE, HashMap())

    fun isSolvable(rows: List<List<Int>>): Boolean = countSolutions(rows) > 0

    /** 4 rows of 4, every id 0..15 exactly once. */
    fun isValidBoard(rows: List<List<Int>>): Boolean =
        rows.size == ROW_COUNT &&
                rows.all { it.size == ROW_LENGTH } &&
                rows.flatten().sorted() == (0 until PIECE_COUNT).toList()

    /** Small fingerprint of a board; host sends it with the seed so the guest can verify. */
    fun checksum(rows: List<List<Int>>): Int =
        rows.flatten().fold(17) { h, v -> h * 31 + v }

    // ----- Private, Puzzle Generation Functions -----

    private fun build(random: Random): List<List<Int>> {
        val path = randomPath(random)
        val rows = deal(path, random)
        // Check just in case. Probably don't need to though
        check(isValidBoard(rows)) { "generated an invalid board: $rows" }
        check(isSolvable(rows)) { "generated an unsolvable board: $rows" }
        return rows
    }

    /**
     * Random order of all 16 pieces where each consecutive pair matches.
     * Look up Hamiltonian Path.
     *
     * Example of why you'd need to backtrack: if position 13 is a red hippo and the only unused pieces
     * left are a blue lion and green camel, then that's not a winning solution. So, extend(14) will
     * return false, then red hippo is no longer used and can be put back in candidates.
     */
    private fun randomPath(random: Random): IntArray {
        val path = IntArray(PIECE_COUNT)
        val used = BooleanArray(PIECE_COUNT)

        fun extend(n: Int): Boolean {
            if (n == PIECE_COUNT) return true
            // get all the unused pieces that match, then shuffle them all
            val candidates = (0 until PIECE_COUNT)
                .filter { !used[it] && (n == 0 || matches[path[n - 1]][it]) }
                .shuffled(random)

            for (c in candidates) {
                // "use up" the piece, so it can't be put back into candidates
                used[c] = true
                // put it in the path
                path[n] = c
                // recursively call extend() with 1 less piece
                if (extend(n + 1)) return true
                // if extend returns false, then there's no candidates, meaning this is not a correct solution
                // So, you need to backtrack.
                used[c] = false
            }
            return false
        }

        check(extend(0)) { "no matching path exists" } // Shouldn't ever happen? I think
        return path
    }

    /**
     * Randomly split path positions 0..15 into 4 groups of 4. Each row holds its group's pieces
     * in path order, so removing path[0], path[1], and so on is always legal: when path[n] comes up,
     * every earlier piece of its row is already gone, making it the leftmost.
     */
    private fun deal(path: IntArray, random: Random): List<List<Int>> =
        // shuffle numbers 0 - 15, split (chunk) them into groups of 4, then sort each of the groups
        (0 until PIECE_COUNT).shuffled(random)
            .chunked(ROW_LENGTH)
            .map { group -> group.sorted().map { path[it] } }

    // ----- Private, Puzzle Solver Functions -----

    /**
     * Ways to finish from this state. A state is fully described by the row pointers and the last
     * removed piece (<= 5^4 * 17 keys), so it is memoized (cached).
     * In c++ terms, memo is an unordered_map.
     */
    private fun count(
        rows: List<List<Int>>,
        ptrs: IntArray,
        last: Int,
        memo: HashMap<Int, Long>,
    ): Long {
        // If every row pointer is 4, the board is empty. So, there's technically 1 way to finish, doing nothing
        if (ptrs.all { it == ROW_LENGTH }) return 1L

        val key = stateKey(ptrs, last)
        // If this state was already solved, just return the saved answer
        memo[key]?.let { return it }

        var total = 0L
        // same logic as nextMoveRow()
        for (r in 0 until ROW_COUNT) {
            if (ptrs[r] >= ROW_LENGTH) continue
            val piece = rows[r][ptrs[r]]
            if (last != NO_PIECE && !matches[last][piece]) continue
            ptrs[r]++
            total += count(rows, ptrs, piece, memo)
            ptrs[r]--
        }

        // store the total. Since the memo is a parameter, it'll save it
        memo[key] = total
        return total
    }

    /**
     * Just presses (ptrs, last) into an int so it can be a HashMap key.
     *
     * Explanation: ptrs is 0-4, 0 if none removed, 1 if one removed, ... 4 if row empty.
     * Multiply each ptr by 5 and add itself to it. Total all of that up, multiply that total by
     * 17, and add the last piece + 1.
     *
     * Does that sound dumb and confusing? Look up flattened-index formula for a 2D array. This function
     * does that for each row (ptr), then does it again for all of them.
     *
     * Why do we do that? This way, each stateKey is entirely unique among all other stateKeys.
     * This means that we are able to distinguish any state from another using a single integer,
     * which is useful for our HashMap.
     */
    private fun stateKey(ptrs: IntArray, last: Int): Int {
        var code = 0
        for (p in ptrs) code = code * (ROW_LENGTH + 1) + p
        return code * (PIECE_COUNT + 1) + (last + 1)
    }
}