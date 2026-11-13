package com.example.animalogic.data

import android.content.Context
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit

class Preferences(context: Context) {
    private val sp = context.getSharedPreferences("AnimaLogic", Context.MODE_PRIVATE)


    // val username: String

    var coins by mutableIntStateOf(sp.getInt("coins", 0))
        private set

    // single player puzzle completions
    var completed by mutableIntStateOf(sp.getInt("completed", 0))
        private set

    // used to display bluetooth wins
    var wins by mutableIntStateOf(sp.getInt("wins", 0))
        private set

    // completePuzzle?? Only if keeping catalogue

    private fun save() {
        sp.edit {
            putInt("coins", coins)
                .putInt("wins", wins)
                .putInt("completed", completed)
        }
    }

    fun spendCoins(amount: Int): Boolean {
        if (coins < amount) return false
        coins -= amount
        save()
        return true
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