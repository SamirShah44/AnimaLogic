package com.example.animalogic.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animalogic.game.GameState
import com.example.animalogic.game.Piece
import com.example.animalogic.game.PieceColor

@Composable
fun PieceTile(piece: Piece, size: Dp, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val textColor = if (piece.color == PieceColor.RED) Color.Black else Color.White
    val border = if (highlighted) BorderStroke(4.dp, Color.Black) else BorderStroke(2.dp, Color.Black)
    Box(
        modifier = modifier.size(size).background(piece.color.color).border(border).semantics { contentDescription = piece.description + if (highlighted) " (highlighted)" else "" }, contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(piece.animal.label, fontSize = (size.value).sp)
            Text(piece.color.symbol, color = textColor, fontSize = (size.value).sp )
        }
    }
}

@Composable
fun BoardView(game: GameState, hintCell: Pair<Int, Int>?, onCellTap: (row: Int, col: Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in game.rows.indices) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in game.rows[row].indices) {
                    val piece = game.pieceAt(row, col)
                    if (piece != null) {
                        PieceTile(
                            piece = piece,
                            size = 100.dp,
                            highlighted = hintCell == (row to col),
                            modifier = Modifier.clickable(onClick = { onCellTap(row, col) })
                        )
                    } else {
                        Box(modifier = Modifier.size(70.dp).background(Color.LightGray))
                    }
                }
            }
        }
    }
}