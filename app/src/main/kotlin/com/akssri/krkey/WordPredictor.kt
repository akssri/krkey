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

import android.graphics.PointF
import android.graphics.Rect

class WordPredictor(
    keys: List<Pair<String, Rect>>,
    private val dictionary: List<String>,
) {
    private data class IndexedWord(val word: String, val freqIndex: Int)

    private val N = 40
    private var learnedWords: List<Pair<String, Int>> = emptyList()
    private var learnedCountMap: Map<String, Int> = emptyMap()
    private val userBigramIndex = mutableMapOf<Long, MutableList<IndexedWord>>()

    private val charCenter: Map<Int, PointF> =
        buildMap {
            for ((label, rect) in keys) {
                val cp = label.codePointAt(0)
                if (Character.charCount(cp) == label.length) {
                    put(Character.toLowerCase(cp), PointF(rect.centerX().toFloat(), rect.centerY().toFloat()))
                }
            }
        }

    private val keyUnit: Float =
        run {
            if (keys.isEmpty()) return@run 1f
            val widths = keys.map { it.second.width().toFloat() }
            val heights = keys.map { it.second.height().toFloat() }
            ((widths.sum() / widths.size) + (heights.sum() / heights.size)) / 2f
        }

    private val dictBigramIndex: Map<Long, List<IndexedWord>> =
        buildMap<Long, MutableList<IndexedWord>> {
            for ((index, word) in dictionary.withIndex()) {
                if (word.length < 2) continue
                val key = bigramKey(word.codePointAt(0), word.codePointBefore(word.length))
                getOrPut(key) { mutableListOf() }.add(IndexedWord(word, index))
            }
        }

    private class TrieNode {
        val children = mutableMapOf<Int, TrieNode>()
        val topWords = mutableListOf<String>()
    }

    private val trieRoot = TrieNode()

    init {
        for (word in dictionary) {
            val lowerWord = word.lowercase()
            var curr = trieRoot
            for (cp in codePointsOf(lowerWord)) {
                curr = curr.children.getOrPut(cp) { TrieNode() }
                if (curr.topWords.size < 10 && !curr.topWords.contains(word)) {
                    curr.topWords.add(word)
                }
            }
        }
    }

    fun setLearnedWords(words: List<Pair<String, Int>>) {
        learnedWords = words
        learnedCountMap = words.toMap()
        userBigramIndex.clear()
        for ((word, _) in words) {
            if (word.length < 2) continue
            val key = bigramKey(word.codePointAt(0), word.codePointBefore(word.length))
            userBigramIndex.getOrPut(key) { mutableListOf() }.add(IndexedWord(word, -1))
        }
    }

    fun getPrefixMatches(prefix: String): List<String> {
        if (prefix.length < 2) return emptyList()
        val lower = prefix.lowercase()
        val userMatches =
            learnedWords
                .filter { it.first.startsWith(lower) }
                .sortedByDescending { it.second }
                .map { it.first }

        var curr = trieRoot
        for (cp in codePointsOf(lower)) {
            curr = curr.children[cp] ?: return userMatches.take(5)
        }

        val dictMatches = curr.topWords

        return (userMatches + dictMatches).distinct().take(5)
    }

    fun predict(path: List<PointF>): List<Pair<String, Double>> {
        if (path.size < 2) return emptyList()

        val resampledPath = resample(path, N)
        val pathStart = path.first()
        val pathEnd = path.last()
        val totalPathLen = pathLength(path)

        val startChars = nearbyChars(pathStart, 1.5f)
        val endChars = nearbyChars(pathEnd, 1.5f)

        if (startChars.isEmpty() || endChars.isEmpty()) return emptyList()

        val candidates = mutableListOf<Pair<String, Double>>()

        for (sc in startChars) {
            for (ec in endChars) {
                val key = bigramKey(sc, ec)

                // Check user words first
                val uWords = userBigramIndex[key]
                if (uWords != null) {
                    for (iw in uWords) {
                        scoreAndAdd(iw, resampledPath, pathStart, pathEnd, totalPathLen, candidates)
                    }
                }

                // Check dict words
                val dWords = dictBigramIndex[key] ?: continue
                for (iw in dWords) {
                    scoreAndAdd(iw, resampledPath, pathStart, pathEnd, totalPathLen, candidates)
                }
            }
        }

        return candidates.sortedBy { it.second }.distinctBy { it.first }.take(5)
    }

    private fun scoreAndAdd(
        iw: IndexedWord,
        resampledPath: List<PointF>,
        pathStart: PointF,
        pathEnd: PointF,
        totalPathLen: Double,
        candidates: MutableList<Pair<String, Double>>,
    ) {
        val rawScore = scoreCandidate(iw.word, iw.freqIndex, resampledPath, pathStart, pathEnd, totalPathLen)
        val learnedCount = learnedCountMap[iw.word] ?: 0

        val boostedScore =
            if (learnedCount > 0) {
                rawScore * 0.6 / (1.0 + Math.log10(learnedCount.toDouble() + 1.0))
            } else {
                rawScore
            }

        if (boostedScore < 8.0) {
            candidates.add(iw.word to boostedScore)
        }
    }

    private fun scoreCandidate(
        word: String,
        freqIndex: Int,
        resampledPath: List<PointF>,
        pathStart: PointF,
        pathEnd: PointF,
        actualPathLen: Double,
    ): Double {
        val wordCodePoints = codePointsOf(word)
        val idealPoints = mutableListOf<PointF>()
        for (cp in wordCodePoints) {
            val center = charCenter[cp] ?: return 100.0
            idealPoints.add(center)
        }

        // Terminal anchoring
        val startDist = dist(pathStart, idealPoints.first()) / keyUnit
        val endDist = dist(pathEnd, idealPoints.last()) / keyUnit
        if (startDist > 2.0 || endDist > 2.0) return 100.0

        // Shape distance
        val resampledIdeal = resample(idealPoints, N)
        var shapeDist = 0.0
        for (i in 0 until N) {
            shapeDist += dist(resampledPath[i], resampledIdeal[i]) / keyUnit
        }
        val avgShapeDist = shapeDist / N

        // Order validation
        var orderScore = 0.0
        var pathIdx = 0
        for (cp in wordCodePoints) {
            val center = charCenter[cp] ?: continue
            var foundMatch = false
            var minCharDist = Double.MAX_VALUE

            for (i in pathIdx until resampledPath.size) {
                val d = dist(resampledPath[i], center) / keyUnit
                if (d < minCharDist) minCharDist = d
                if (d < 1.2) {
                    foundMatch = true
                    pathIdx = i
                    break
                }
            }
            if (!foundMatch) {
                orderScore += 1.0
            } else {
                orderScore += minCharDist * 0.2
            }
        }

        // Length penalty
        val idealPathLen = pathLength(idealPoints)
        val lengthPenalty = Math.abs(Math.log((actualPathLen + keyUnit) / (idealPathLen + keyUnit))) * 2.5

        // Frequency weight: 1.0 for top words, up to 1.25 for rare words
        val freqWeight = if (freqIndex < 0) 1.0 else 1.0 + (freqIndex.toDouble() / dictionary.size.coerceAtLeast(1)) * 0.25

        return (startDist * 1.0 + endDist * 1.0 + avgShapeDist * 0.5 + orderScore * 0.3 + lengthPenalty * 0.4) * freqWeight
    }

    private fun nearbyChars(
        point: PointF,
        radiusKeyUnits: Float,
    ): List<Int> {
        val radius = radiusKeyUnits * keyUnit
        return charCenter.entries
            .filter { dist(point, it.value) < radius }
            .sortedBy { dist(point, it.value) }
            .map { it.key }
    }

    private fun bigramKey(
        first: Int,
        last: Int,
    ): Long {
        return first.toLong() shl 21 or last.toLong()
    }

    private fun codePointsOf(s: String): IntArray {
        val result = IntArray(s.codePointCount(0, s.length))
        var i = 0
        var idx = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            result[idx++] = cp
            i += Character.charCount(cp)
        }
        return result
    }

    private fun resample(
        points: List<PointF>,
        n: Int,
    ): List<PointF> {
        val totalLen = pathLength(points)
        if (totalLen == 0.0) return List(n) { points[0] }

        val interval = totalLen / (n - 1)
        val result = mutableListOf<PointF>()
        result.add(points[0])

        var currentPathIdx = 1
        var accumulatedLen = 0.0

        for (i in 1 until n - 1) {
            val targetLen = i * interval
            while (currentPathIdx < points.size) {
                val segmentLen = dist(points[currentPathIdx - 1], points[currentPathIdx])
                if (accumulatedLen + segmentLen >= targetLen) {
                    val ratio = (targetLen - accumulatedLen) / segmentLen
                    val x = points[currentPathIdx - 1].x + ratio * (points[currentPathIdx].x - points[currentPathIdx - 1].x)
                    val y = points[currentPathIdx - 1].y + ratio * (points[currentPathIdx].y - points[currentPathIdx - 1].y)
                    result.add(PointF(x.toFloat(), y.toFloat()))
                    break
                }
                accumulatedLen += segmentLen
                currentPathIdx++
            }
        }
        if (result.size < n) result.add(points.last())
        while (result.size < n) result.add(points.last())
        return result.take(n)
    }

    private fun pathLength(points: List<PointF>): Double {
        var len = 0.0
        for (i in 1 until points.size) {
            len += dist(points[i - 1], points[i])
        }
        return len
    }

    private fun dist(
        p1: PointF,
        p2: PointF,
    ): Double {
        return Math.sqrt(Math.pow((p1.x - p2.x).toDouble(), 2.0) + Math.pow((p1.y - p2.y).toDouble(), 2.0))
    }
}
