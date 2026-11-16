package com.example.animalogic.game

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.animalogic.bluetooth.BluetoothSession
import com.example.animalogic.bluetooth.LinkState
import com.example.animalogic.bluetooth.Message
import com.example.animalogic.bluetooth.Role
import com.example.animalogic.bluetooth.SessionState
import com.example.animalogic.data.MatchResult
import kotlin.random.Random

/**
 * Every versus match uses the same mid difficulty so it's the same difficulty for both players.
 * Totally could've added a choice or voting system somehow, but can be added later.
 *
 * Why difficulty 3 out of the 5 options? I dunno, it's in the middle so it's probably a decent choice.
 */
const val VERSUS_LEVEL = 3

/** Sent instead of a time when the opponent finished first and this player hadn't. */
const val DID_NOT_FINISH = 0xFFFF_FFFFL

/**
 * The rules of a Bluetooth race, kept out of the screen so it can be tested without a phone.
 *
 * How it should go:  host sends Start (seed) -> guest builds the same board and answers Ready
 *                    -> host sends Go -> both play -> each side sends exactly one Finished.
 *
 * How to keep it fair: each phone times itself from Go. When you finish you send your time. When the
 * opponent's time arrives while you're still playing, you lost the race, so you send
 * DID_NOT_FINISH. Both phones then compare the same two numbers, so they always agree on the
 * winner (smaller time wins, equal = tie, (likely won't tie though)).
 */
class VersusMatch(
    private val session: BluetoothSession,
    private val onWin: () -> Unit,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val random: Random = Random.Default,
) {
    enum class Phase { WAITING, PLAYING, DONE, RESULT }

    var role by mutableStateOf<Role?>(null)
        private set
    var phase by mutableStateOf(Phase.WAITING)
        private set
    var game by mutableStateOf<GameState?>(null)
        private set
    var myTime by mutableStateOf<Long?>(null)
        private set
    var theirTime by mutableStateOf<Long?>(null)
        private set
    var result by mutableStateOf<MatchResult?>(null)
        private set

    /** Red text under the board, same as PuzzleScreen. */
    var message by mutableStateOf("")

    private var startedAt = 0L

    // ----- Lobby stuff -----

    fun host() {
        message = ""
        role = Role.HOST
        clearMatch()
        session.host()
        onLinkChanged(session.state.value) // in case it failed right away (e.g. Bluetooth off)
    }

    fun join() {
        message = ""
        role = Role.GUEST
        clearMatch()
        session.scan()
        onLinkChanged(session.state.value)
    }

    fun searchAgain() {
        message = ""
        session.scan()
    }

    fun connect(address: String) = session.connect(address)

    fun leave() {
        session.close()
        role = null
        clearMatch()
    }

    /** Call whenever [BluetoothSession.state] changes. */
    fun onLinkChanged(state: SessionState) {
        val r = role ?: return
        when (state.link) {
            // first match starts as soon as the guest is connected
            LinkState.CONNECTED ->
                if (r == Role.HOST && game == null && phase == Phase.WAITING && result == null) startMatch()
            LinkState.IDLE -> {
                // a guest whose scan ended but who found hosts is still picking one
                val pickingHost = r == Role.GUEST && state.hosts.isNotEmpty() && state.message == null
                if (!pickingHost) {
                    message = state.message ?: ""
                    role = null
                    clearMatch()
                }
            }
            else -> {}
        }
    }

    // ----- Match logic -----

    /** HOST: generate a new puzzle and send it. Also used for rematches. */
    fun startMatch() {
        if (role != Role.HOST || session.state.value.link != LinkState.CONNECTED) return
        clearMatch()
        val seed = random.nextLong()
        val rows = PuzzleFactory.generate(seed, VERSUS_LEVEL)
        game = GameState(rows)
        message = ""
        session.send(Message.Start(seed, VERSUS_LEVEL, PuzzleFactory.checksum(rows)))
    }

    /** Touch a tile, then the screen wires it to the board */
    fun tap(row: Int, col: Int) {
        val g = game ?: return
        if (phase != Phase.PLAYING) return
        when (val move = g.tryRemove(row, col)) {
            is GameState.MoveResult.Invalid -> message = move.reason
            GameState.MoveResult.Ok -> {
                message = ""
                if (g.isComplete) finish(clock() - startedAt)
                else if (g.hint() == null) message = "No moves can finish from here. Try Undo."
            }
        }
    }

    fun undo() {
        if (phase != Phase.PLAYING) return
        game?.undo()
        message = ""
    }

    fun resetBoard() {
        if (phase != Phase.PLAYING) return
        game?.reset()
        message = ""
    }

    /** Guest: close the result and wait for the host's rematch. */
    fun dismissResult() {
        if (phase == Phase.RESULT) clearMatch()
    }

    /** Milliseconds on this player's clock (live while playing). */
    fun elapsedMs(): Long = when (phase) {
        Phase.PLAYING -> clock() - startedAt
        else -> myTime?.takeIf { it != DID_NOT_FINISH } ?: 0L
    }

    /** The screen calls this from a LaunchedEffect for as long as it's open. */
    suspend fun run() {
        session.incoming.collect { handle(it) }
    }

    fun handle(msg: Message) {
        when (msg) {
            is Message.Start -> if (role == Role.GUEST) {
                clearMatch()
                val rows = PuzzleFactory.generate(msg.seed, msg.level)
                val same = PuzzleFactory.checksum(rows) == msg.checksum
                if (same) {
                    game = GameState(rows)
                    message = ""
                } else {
                    // shouldn't ever happen
                    message = "Your puzzle doesn't match your friend's. Update the app on both phones."
                }
                session.send(Message.Ready(same))
            }

            is Message.Ready -> if (role == Role.HOST && phase == Phase.WAITING && game != null) {
                if (msg.ok) {
                    session.send(Message.Go)
                    begin()
                } else {
                    game = null
                    message = "Your friend's puzzle didn't match. Update the app on both phones."
                }
            }

            Message.Go -> if (role == Role.GUEST && phase == Phase.WAITING && game != null) begin()

            is Message.Finished -> when (phase) {
                Phase.PLAYING -> { theirTime = msg.elapsedMs; finish(DID_NOT_FINISH) } // they beat us
                Phase.DONE -> { theirTime = msg.elapsedMs; decide() }
                else -> {}
            }

            is Message.Hello -> {} // handled inside BluetoothSession
        }
    }

    // ----- Helpers -----

    private fun begin() {
        startedAt = clock()
        phase = Phase.PLAYING
        message = ""
    }

    private fun finish(time: Long) {
        myTime = time
        session.send(Message.Finished(time))
        phase = Phase.DONE
        decide()
    }

    private fun decide() {
        val mine = myTime ?: return
        val theirs = theirTime ?: return
        val r = when {
            mine < theirs -> MatchResult.WIN
            mine > theirs -> MatchResult.LOSS
            else -> MatchResult.TIE
        }
        result = r
        phase = Phase.RESULT
        if (r == MatchResult.WIN) onWin()
    }

    private fun clearMatch() {
        game = null
        phase = Phase.WAITING
        myTime = null
        theirTime = null
        result = null
    }
}
