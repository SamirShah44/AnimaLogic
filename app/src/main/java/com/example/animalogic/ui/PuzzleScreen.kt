package com.example.animalogic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.animalogic.data.Preferences
import com.example.animalogic.game.GameState
import com.example.animalogic.game.PuzzleFactory
import kotlin.random.Random

@Composable
fun PuzzleScreen(prefs: Preferences, onBack: () -> Unit) {
    var seed by rememberSaveable { mutableLongStateOf(Random.nextLong()) }
    val game = remember(seed) { GameState(PuzzleFactory.generate(seed)) }
    var hints by remember(seed) { mutableStateOf<Pair<Int, Int>?>(null) }
    var message by remember(seed) { mutableStateOf("") }
    var earnedCoins by remember(seed) { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Coins: ${prefs.totalCoins}")
        }
        BoardView(game, hints) { r, c ->
            if ( earnedCoins != null) return@BoardView
            hints = null
            when (val result = game.tryRemove(r, c)) {
                is GameState.MoveResult.Invalid -> message = result.reason
                    GameState.MoveResult.Ok -> {
                        message = ""
                        if (game.isComplete) earnedCoins = prefs.completePuzzle() // checks if puzzle is complete and gives coins
                    }
            }
        }

        Text(message, color = Color(0xFFC62828), modifier = Modifier.padding(horizontal = 8.dp))
        RemovedStrip(game) // shows removed pieces, FR-09

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { hints = null; message = ""; game.undo() }) { Text("Undo") }
            OutlinedButton(onClick = { hints = null; message = ""; game.reset() }) { Text("Reset") }
            Button(onClick = {
                val hint = game.hint()
                when {
                    prefs.totalCoins < Preferences.HINTCOST ->
                        message = "A hint costs ${Preferences.HINTCOST} coins. Solve puzzles to earn more!"
                    hint == null ->
                        message = "No safe move from here. Try Undo."
                    else -> {
                        prefs.spendCoins(Preferences.HINTCOST)   // charged only when a hint is shown, FR-13
                        hints = hint
                        message = ""
                    }
                }
            }) { Text("Hint (${Preferences.HINTCOST} coins)") }

            // alerts for solving puzzle
            earnedCoins?.let { coins ->
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Solved!") },
                    text = { Text("You earned $coins coins.") },
                    confirmButton = {
                        TextButton(onClick = { seed = Random.nextLong() }) { Text("Next puzzle") }
                    },
                    dismissButton = { TextButton(onClick = onBack) { Text("Main menu") } }
                )
            }
        }

        OutlinedButton(onClick = onBack) {
            Text("Main Menu")
        }

    }
}