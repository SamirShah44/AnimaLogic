package com.example.animalogic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.animalogic.data.Preferences
import com.example.animalogic.ui.PuzzleScreen
import com.example.animalogic.ui.VersusScreen
import com.example.animalogic.ui.MainScreen
import com.example.animalogic.ui.StatsScreen
import com.example.animalogic.ui.theme.AnimaLogicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Preferences(this)
        setContent {
            AnimaLogicTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    App(prefs, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun App(prefs: Preferences, modifier: Modifier = Modifier) {
    var screen by rememberSaveable  { mutableStateOf("main") }
    BackHandler(enabled = screen != "main") { screen = "main" }
    when (screen) {
        "solo" -> PuzzleScreen(prefs) { screen = "main" }
        "versus" -> VersusScreen(prefs) { screen = "main" }
        "stats" -> StatsScreen(prefs) { screen = "main" }
        else -> MainScreen(
            prefs = prefs,
            SoloPlay = { screen = "solo" },
            VersusPlay = { screen = "versus" },
            Stats = { screen = "stats" },
            modifier = modifier
        )
    }
}