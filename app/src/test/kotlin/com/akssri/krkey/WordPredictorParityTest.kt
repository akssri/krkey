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

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the Kotlin swipe decoder against the Python prototype (tools/swipe-sim/elastic.py),
 * using swipes exported by tools/swipe-sim/export_fixture.py.
 */
class WordPredictorParityTest {
    @Test
    fun matchesPrototype() {
        val fixture = JSONObject(javaClass.getResource("/swipe_parity.json")!!.readText())
        val keys =
            fixture.getJSONArray("keys").let { arr ->
                List(arr.length()) { i ->
                    val k = arr.getJSONArray(i)
                    WordPredictor.KeyBox(k.getString(0), k.getDouble(1).toFloat(), k.getDouble(2).toFloat(), k.getDouble(3).toFloat(), k.getDouble(4).toFloat())
                }
            }
        val dictionary = File("src/main/assets/en_dict.txt").readLines().filter { it.isNotBlank() }
        val predictor = WordPredictor(keys, dictionary)

        val cases = fixture.getJSONArray("cases")
        var sameTop1 = 0
        val mismatches = mutableListOf<String>()
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val path = c.getJSONArray("path")
            val xs = FloatArray(path.length()) { path.getJSONArray(it).getDouble(0).toFloat() }
            val ys = FloatArray(path.length()) { path.getJSONArray(it).getDouble(1).toFloat() }
            val expected = c.getJSONArray("top").let { t -> List(t.length()) { t.getString(it) } }
            val actual = predictor.predict(xs, ys).map { it.first.lowercase() }
            if (actual.firstOrNull() == expected.firstOrNull()?.lowercase()) {
                sameTop1++
            } else {
                mismatches += "${c.getString("word")}: kotlin=$actual python=$expected"
            }
        }
        val start = System.nanoTime()
        repeat(3) {
            for (i in 0 until cases.length()) {
                val path = cases.getJSONObject(i).getJSONArray("path")
                predictor.predict(FloatArray(path.length()) { path.getJSONArray(it).getDouble(0).toFloat() }, FloatArray(path.length()) { path.getJSONArray(it).getDouble(1).toFloat() })
            }
        }
        println("decode time: %.1f ms/swipe (JVM)".format((System.nanoTime() - start) / 1e6 / (3 * cases.length())))
        val rate = sameTop1.toDouble() / cases.length()
        println("top-1 agreement: $sameTop1/${cases.length()}")
        mismatches.take(10).forEach { println("  $it") }
        assertTrue("top-1 agreement $rate below 0.97", rate >= 0.97)
    }
}
