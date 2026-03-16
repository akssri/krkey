/*
 *  Copyright (c) 2026 Akshay Srinivasan <akssri@vakra.xyz>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.akssri.krkey

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class UserDictionaryManager(context: Context) {
    private val prefs = context.getSharedPreferences("krkey_user_dict", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Word -> Frequency
    private var userWords: MutableMap<String, Int> = mutableMapOf()

    // Strong reference to listener to prevent GC
    private val prefListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "words") load()
        }

    init {
        load()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun load() {
        val json = prefs.getString("words", null)
        userWords =
            if (json != null) {
                val type = object : TypeToken<MutableMap<String, Int>>() {}.type
                try {
                    gson.fromJson(json, type) ?: mutableMapOf()
                } catch (e: Exception) {
                    mutableMapOf()
                }
            } else {
                mutableMapOf()
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

    fun clear() {
        userWords.clear()
        prefs.edit().remove("words").commit()
    }
}
