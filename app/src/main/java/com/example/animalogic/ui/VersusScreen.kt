package com.example.animalogic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animalogic.bluetooth.BluetoothSession
import com.example.animalogic.bluetooth.GUEST_PERMISSIONS
import com.example.animalogic.bluetooth.HOST_PERMISSIONS
import com.example.animalogic.bluetooth.LinkState
import com.example.animalogic.bluetooth.Role
import com.example.animalogic.data.MatchResult
import com.example.animalogic.data.Preferences
import com.example.animalogic.game.DID_NOT_FINISH
import com.example.animalogic.game.VersusMatch
import kotlinx.coroutines.delay

@Composable
fun VersusScreen(prefs: Preferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val session = remember { BluetoothSession(context, prefs.username) }
    val match = remember { VersusMatch(session, onWin = { prefs.addWin() }) }
    val bt by session.state.collectAsState()
    val opponent = bt.peerName ?: "your friend"

    DisposableEffect(Unit) { onDispose { session.close() } }   // leaving the screen ends the link
    LaunchedEffect(match) { match.run() }                      // receive the opponent's messages
    LaunchedEffect(bt) { match.onLinkChanged(bt) }      // react to connect / disconnect

    // Bluetooth permissions are asked for when the player picks Host or Join
    var pendingRole by remember { mutableStateOf<Role?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val role = pendingRole
        pendingRole = null
        if (granted.values.all { it }) {
            if (role == Role.HOST) match.host() else if (role == Role.GUEST) match.join()
        } else {
            match.message = "Bluetooth permission is needed to play with a friend."
        }
    }
    fun choose(role: Role) {
        val needed = if (role == Role.HOST) HOST_PERMISSIONS else GUEST_PERMISSIONS
        if (needed.isEmpty()) {
            if (role == Role.HOST) match.host() else match.join()
        } else {
            pendingRole = role
            permissionLauncher.launch(needed)
        }
    }

    // live race timer
    var shownTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(match.phase) {
        while (match.phase == VersusMatch.Phase.PLAYING) {
            shownTime = match.elapsedMs()
            delay(100)
        }
        shownTime = match.elapsedMs()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Playing as ${prefs.username}")

        when {
            // lobby
            match.role == null -> {
                Text("Race a friend nearby! One phone hosts, the other joins.", textAlign = TextAlign.Center)
                Button(onClick = { choose(Role.HOST) }, modifier = Modifier.width(220.dp)) { Text("Host a Game") }
                Button(onClick = { choose(Role.GUEST) }, modifier = Modifier.width(220.dp)) { Text("Join a Game") }
            }

            // finding each other
            bt.link != LinkState.CONNECTED -> when (bt.link) {
                LinkState.ADVERTISING -> {
                    Text("Waiting for a friend to join...")
                    Text("Ask them to tap Join a Game and pick ${prefs.username}.", textAlign = TextAlign.Center)
                    CircularProgressIndicator()
                }
                LinkState.SCANNING, LinkState.IDLE -> {
                    Text(if (bt.link == LinkState.SCANNING) "Looking for games nearby..." else "Pick a game to join")
                    if (bt.link == LinkState.SCANNING) CircularProgressIndicator()
                    bt.hosts.forEach { host ->
                        Button(onClick = { match.connect(host.address) }, modifier = Modifier.width(220.dp)) {
                            Text(host.name)
                        }
                    }
                    if (bt.link == LinkState.IDLE) {
                        OutlinedButton(onClick = { match.searchAgain() }) { Text("Search Again") }
                    }
                }
                else -> {
                    Text("Connecting...")
                    CircularProgressIndicator()
                }
            }

            // once connected:
            else -> {
                Text("You vs $opponent", fontSize = 20.sp)
                val game = match.game
                if (match.phase == VersusMatch.Phase.WAITING || game == null) {
                    when {
                        match.role == Role.GUEST -> Text("Waiting for $opponent to start...")
                        game != null -> Text("Getting $opponent ready...")
                        else -> Button(onClick = { match.startMatch() }) { Text("Start Match") }
                    }
                } else {
                    Text("Time: ${formatTime(shownTime)}", fontSize = 20.sp)
                    BoardView(game, null) { r, c -> match.tap(r, c) }
                    RemovedStrip(game)
                    if (match.phase == VersusMatch.Phase.PLAYING) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { match.undo() }) { Text("Undo") }
                            OutlinedButton(onClick = { match.resetBoard() }) { Text("Reset") }
                        }
                    } else if (match.phase == VersusMatch.Phase.DONE) {
                        Text("Finished! Waiting for $opponent...")
                    }
                }
            }
        }

        Text(match.message, color = Color(0xFFC62828), modifier = Modifier.padding(horizontal = 8.dp))

        if (match.role != null) {
            OutlinedButton(onClick = { match.leave() }) { Text("Leave Game") }
        }
        OutlinedButton(onClick = onBack) {
            Text("Main Menu")
        }
    }

    if (match.phase == VersusMatch.Phase.RESULT) {
        match.result?.let { result ->
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(when (result) {
                        MatchResult.WIN -> "You win!"
                        MatchResult.LOSS -> "$opponent wins!"
                        MatchResult.TIE -> "It's a tie!"

                        else -> "Match over" // added because it said 'when' needs to be exhaustive
                    })
                },
                text = { Text("You: ${formatTime(match.myTime)}\n$opponent: ${formatTime(match.theirTime)}") },
                confirmButton = {
                    if (match.role == Role.HOST) {
                        TextButton(onClick = { match.startMatch() }) { Text("Rematch") }
                    } else {
                        TextButton(onClick = { match.dismissResult() }) { Text("OK") }
                    }
                },
                dismissButton = { TextButton(onClick = onBack) { Text("Main menu") } }
            )
        }
    }
}

private fun formatTime(ms: Long?): String = when (ms) {
    null -> "--"
    DID_NOT_FINISH -> "Didn't finish"
    else -> "%d.%d s".format(ms / 1000, (ms % 1000) / 100)
}
