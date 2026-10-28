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
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Times the swipe decoder on real swipes logged by SwipeLogger. Skipped unless a log has been
 * copied to app/build/swipe_bench.jsonl (kept out of the repo: it contains typed words):
 *   adb shell run-as com.akssri.krkey cat files/swipes.jsonl > app/build/swipe_bench.jsonl
 */
class SwipeBenchmarkTest {
    @Test
    fun timeLoggedSwipes() {
        val log = File("build/swipe_bench.jsonl")
        assumeTrue("no swipe log at ${log.absolutePath}", log.exists())
        val dictionary = File("src/main/assets/en_dict.txt").readLines().filter { it.isNotBlank() }
        val rows = mutableListOf<Triple<Double, Double, String>>() // ms, path length in keys, label
        var predictor: WordPredictor? = null
        for (line in log.readLines().filter { it.startsWith("{") }) {
            val rec = JSONObject(line)
            if (predictor == null) {
                val keys = rec.getJSONArray("keys")
                val boxes =
                    List(keys.length()) { i ->
                        val k = keys.getJSONArray(i)
                        WordPredictor.KeyBox(k.getString(0), k.getDouble(1).toFloat(), k.getDouble(2).toFloat(), k.getDouble(3).toFloat(), k.getDouble(4).toFloat())
                    }
                repeat(3) { WordPredictor(boxes, dictionary) } // warm up
                val tb = System.nanoTime()
                WordPredictor(boxes, dictionary)
                println("build: %.1f ms for %d words".format((System.nanoTime() - tb) / 1e6, dictionary.size))
                predictor =
                    WordPredictor(
                        List(keys.length()) { i ->
                            val k = keys.getJSONArray(i)
                            WordPredictor.KeyBox(k.getString(0), k.getDouble(1).toFloat(), k.getDouble(2).toFloat(), k.getDouble(3).toFloat(), k.getDouble(4).toFloat())
                        },
                        dictionary,
                    )
            }
            val path = rec.getJSONArray("path")
            val xs = FloatArray(path.length()) { path.getJSONArray(it).getDouble(0).toFloat() }
            val ys = FloatArray(path.length()) { path.getJSONArray(it).getDouble(1).toFloat() }
            repeat(20) { predictor.predict(xs, ys) } // warm up (JIT)
            val t = System.nanoTime()
            var top: String? = null
            repeat(10) { top = predictor.predict(xs, ys).firstOrNull()?.first }
            val ms = (System.nanoTime() - t) / 1e6 / 10
            var len = 0.0
            for (i in 1 until xs.size) len += Math.hypot((xs[i] - xs[i - 1]).toDouble(), (ys[i] - ys[i - 1]).toDouble())
            rows += Triple(ms, len, "${rec.opt("label")}/$top nodes=${predictor.lastNodesVisited}")
        }
        rows.sortedByDescending { it.second }.take(8).forEach { (ms, len, w) -> println("%7.1f ms  len=%5.0fpx  %s".format(ms, len, w)) }
        println("median %.1f ms over %d swipes".format(rows.map { it.first }.sorted()[rows.size / 2], rows.size))
    }
}
