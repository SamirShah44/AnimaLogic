package com.example.animalogic.data

import android.content.Context
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import kotlin.random.Random

class Preferences(context: Context) {
    private val sp = context.getSharedPreferences("AnimaLogic", Context.MODE_PRIVATE)


    // randomly generated username from scope
    val username: String = sp.getString("username", null) ?: run {
        val adjectives = listOf("Fast", "Smart", "Brave", "Happy")
        val animals = listOf("Hippo", "Giraffe", "Camel", "Lion")
        val name = "${adjectives.random()}${animals.random()}${Random.nextInt(100, 1000)}"
        sp.edit { putString("username", name) }
        name
    }


    // total coins earned in the game
    var totalCoins by mutableIntStateOf(sp.getInt("coins", 0))
        private set

    // single player puzzle completions
    var completed by mutableIntStateOf(sp.getInt("completed", 0))
        private set

    // used to display bluetooth wins
    var wins by mutableIntStateOf(sp.getInt("wins", 0))
        private set

    // call when puzzle is solved, deals coin to player
    fun completePuzzle(): Int {
        completed += 1
        totalCoins += COINSPERWIN
        save()
        return COINSPERWIN
    }

    // call when spending hint, subtracts coin if valid
    fun spendCoins(amount: Int): Boolean {
        if (totalCoins < amount) return false
        totalCoins -= amount
        save()
        return true
    }
    private fun save() {
        sp.edit {
            putInt("coins", totalCoins)
                .putInt("wins", wins)
                .putInt("completed", completed)
        }
    }

    fun addWin() {
        wins += 1
        save()
    }

    companion object {
        const val COINSPERWIN = 10
        const val HINTCOST = 5
    }
}