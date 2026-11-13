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

    var wins by mutableIntStateOf(sp.getInt("wins", 0))
        private set

    // completed?

    // completePuzzle?? Only if keeping catalogue


    private fun save() {
        sp.edit {
            putInt("coins", coins)
                .putInt("wins", wins)
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