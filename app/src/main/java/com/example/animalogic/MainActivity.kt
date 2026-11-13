package com.example.animalogic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.animalogic.data.Preferences
import com.example.animalogic.ui.theme.AnimaLogicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnimaLogicTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

/*
@Composable
private fun App(prefs: Preferences) {) {
    var screen by rememberSaveable  { mutableStateOf("main") }
    when (screen) {
        "solo" -> PuzzleScreen(prefs) { screen = "main" }
        "versus" -> VersusScreen(prefs) { screen = "main" }
        "stats" -> StatsScreen(prefs) { screen = "main" }
        else -> MainScreen(
            prefs = prefs,
            SoloPlay = { screen = "solo" },
            VersusPlay = { screen = "versus" },
            Stats = { screen = "stats" }
        )
    }
} */