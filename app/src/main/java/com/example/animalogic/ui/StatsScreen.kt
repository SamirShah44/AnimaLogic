package com.example.animalogic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animalogic.data.PlayerStat
import com.example.animalogic.data.TOTAL_NUM_OF_PUZZLES
import com.example.animalogic.ui.theme.AnimaLogicTheme

@Composable
fun StatsScreen(
    playerStat: PlayerStat,
    onBackClick: () -> Unit ={}
){
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ){
        Text(text="Player Ststistics", fontSize = 28.sp)
        Spacer(modifier= Modifier.height(32.dp))


        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        {
            Column(modifier=Modifier.padding(20.dp)){
                Text(text="Casual Puzzles", fontSize = 16.sp)
                Text(text="${playerStat.puzzlesCompleted} / $TOTAL_NUM_OF_PUZZLES" , fontSize = 20.sp)
                Spacer(modifier= Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = {
                        if(TOTAL_NUM_OF_PUZZLES > 0){
                            playerStat.puzzlesCompleted.toFloat() / TOTAL_NUM_OF_PUZZLES
                        }
                        else{
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        //Bluetooth
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        {
            Column(modifier=Modifier.padding(20.dp)){
                Text(text="Bluetooth Puzzles", fontSize = 16.sp)
                Text(text="${playerStat.score} Wins / ${playerStat.matchesPlayed} Played" , fontSize = 20.sp)
                Spacer(modifier= Modifier.height(10.dp))
                Text(
                    text = "Win Rate: ${playerStat.winRate}%",
                    fontSize = 16.sp
                )

            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ){
            Text(text = "Back to Main Screen")
        }

    }

}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun StatsScreenPreview() {
    var backClicked by remember { mutableStateOf(false) }

    val sampleStats = PlayerStat(
        completedPuzzleIds = setOf(1, 2, 3, 4, 5, 6),
        wins = 3,
        losses = 1,
        ties = 1
    )

    AnimaLogicTheme {
        if (backClicked) {
            Text("Back button callback works")
        } else {
            StatsScreen(
                playerStat = sampleStats,
                onBackClick = {
                    backClicked = true
                }
            )
        }
    }
}