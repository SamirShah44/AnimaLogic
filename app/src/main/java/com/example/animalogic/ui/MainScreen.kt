package com.example.animalogic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults


import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8BBD0))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    )  {
        Text(
            text = "Animalogic",
            fontSize = 50.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 80.dp)

        )
        Card(
            modifier = Modifier.padding(top = 80.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = "Select a Playing mode Single-Player or Multi-Player",
                fontSize = 30.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

        }


        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { }, modifier = Modifier.width(220.dp)) {
                Text("Single Player")
            }
            Button(onClick = { }, modifier = Modifier.width(220.dp)) {
                Text("Multi Player")
            }
            Button(onClick = { }, modifier = Modifier.width(220.dp)) {
                Text("View Stats")
            }
        }
        Spacer(Modifier.weight(1f))
    }


}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen()
}
