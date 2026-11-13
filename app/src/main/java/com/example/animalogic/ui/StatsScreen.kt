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
        Text(text="Player Statistics", fontSize = 28.sp)
        Spacer(modifier= Modifier.height(32.dp))


        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        {
            Column(modifier=Modifier.padding(20.dp)){
                Text(text="Puzzles Completed", fontSize = 16.sp)
                Text(text="${playerStat.puzzlesCompleted}" , fontSize = 20.sp)
                Spacer(modifier= Modifier.height(10.dp))
            }
        }

        //Bluetooth
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        {
            Column(modifier=Modifier.padding(20.dp)){
                Text(text="Bluetooth Puzzles", fontSize = 16.sp)
                Text(text="${playerStat.score} Wins / ${playerStat.matchesPlayed} Played" , fontSize = 20.sp)
                Spacer(modifier= Modifier.height(10.dp))
                Text("Losses: ${playerStat.losses}")
                Text("Ties: ${playerStat.ties}")
                Text("Score: ${playerStat.score}")
                Text("Win Rate: ${playerStat.winRate}%")

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

