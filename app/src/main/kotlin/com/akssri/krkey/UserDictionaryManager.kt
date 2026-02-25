package com.akssri.krkey

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class UserDictionaryManager(context: Context) {
    private val prefs = context.getSharedPreferences("krkey_user_dict", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Word -> Frequency
    private var userWords: MutableMap<String, Int> = mutableMapOf()

    init {
        load()
    }

    private fun load() {
        val json = prefs.getString("words", null)
        if (json != null) {
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            userWords = gson.fromJson(json, type) ?: mutableMapOf()
        }
    }

    private fun save() {
        val json = gson.toJson(userWords)
        prefs.edit().putString("words", json).apply()
    }

    fun learnWord(word: String) {
        if (word.length < 2) return
        val lower = word.lowercase()
        userWords[lower] = (userWords[lower] ?: 0) + 1
        if (userWords.size > 1000) {
            // Simple pruning: remove lowest frequency words if dictionary gets too big
            val minFreq = userWords.values.minOrNull() ?: 0
            val iterator = userWords.iterator()
            while (iterator.hasNext() && userWords.size > 800) {
                if (iterator.next().value == minFreq) iterator.remove()
            }
        }
        save()
    }

    fun getLearnedWords(): List<Pair<String, Int>> {
        return userWords.toList().sortedByDescending { it.second }
    }
}
