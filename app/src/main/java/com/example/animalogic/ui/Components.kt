package com.example.animalogic.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.animalogic.game.GameState

@Composable
fun PieceTile(piece: Piece, size: Dp, highlighted: Boolean = false, modifier: Modifier = Modifier) {

}

@Composable
fun BoardView(game: GameState, hintCell: Pair<Int, Int>?, onCellTap: (row: Int, col: Int) -> Unit) {

}