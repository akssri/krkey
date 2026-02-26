package com.akssri.krkey

import android.graphics.PointF
import android.graphics.Rect

class WordPredictor(
    keys: List<Pair<String, Rect>>,
    private val dictionary: List<String>,
    private val learnedWords: List<Pair<String, Int>> = emptyList()
) {

    private data class IndexedWord(val word: String, val freqIndex: Int)

    private val N = 40

    private val charCenter: Map<Char, PointF> = buildMap {
        for ((label, rect) in keys) {
            if (label.length == 1) {
                put(label[0].lowercaseChar(), PointF(rect.centerX().toFloat(), rect.centerY().toFloat()))
            }
        }
    }

    private val keyUnit: Float = run {
        if (keys.isEmpty()) return@run 1f
        val widths = keys.map { it.second.width().toFloat() }
        val heights = keys.map { it.second.height().toFloat() }
        ((widths.sum() / widths.size) + (heights.sum() / heights.size)) / 2f
    }

    private val bigramIndex: Map<Long, List<IndexedWord>> = buildMap<Long, MutableList<IndexedWord>> {
        for ((index, word) in dictionary.withIndex()) {
            if (word.length < 2) continue
            val key = bigramKey(word.first(), word.last())
            getOrPut(key) { mutableListOf() }.add(IndexedWord(word, index))
        }
        for ((word, _) in learnedWords) {
            if (word.length < 2) continue
            val key = bigramKey(word.first(), word.last())
            val list = getOrPut(key) { mutableListOf() }
            if (list.none { it.word == word }) {
                list.add(IndexedWord(word, -1))
            }
        }
    }

    private val learnedCountMap: Map<String, Int> = learnedWords.toMap()

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        val topWords = mutableListOf<String>()
    }

    private val trieRoot = TrieNode()

    init {
        for (word in dictionary) {
            val lowerWord = word.lowercase()
            var curr = trieRoot
            for (char in lowerWord) {
                curr = curr.children.getOrPut(char) { TrieNode() }
                if (curr.topWords.size < 10 && !curr.topWords.contains(word)) {
                    curr.topWords.add(word)
                }
            }
        }
    }

    fun getPrefixMatches(prefix: String): List<String> {
        if (prefix.length < 2) return emptyList()
        val lower = prefix.lowercase()
        val userMatches = learnedWords
            .filter { it.first.startsWith(lower) }
            .sortedByDescending { it.second }
            .map { it.first }
        
        var curr = trieRoot
        for (char in lower) {
            curr = curr.children[char] ?: return userMatches.take(5)
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
                val words = bigramIndex[key] ?: continue
                for (iw in words) {
                    val rawScore = scoreCandidate(iw.word, iw.freqIndex, resampledPath, pathStart, pathEnd, totalPathLen)
                    val learnedCount = learnedCountMap[iw.word] ?: 0
                    
                    val boostedScore = if (learnedCount > 0) {
                        rawScore * 0.6 / (1.0 + Math.log10(learnedCount.toDouble() + 1.0))
                    } else {
                        rawScore
                    }

                    if (boostedScore < 8.0) {
                        candidates.add(iw.word to boostedScore)
                    }
                }
            }
        }

        return candidates.sortedBy { it.second }.distinctBy { it.first }.take(5)
    }

    private fun scoreCandidate(
        word: String,
        freqIndex: Int,
        resampledPath: List<PointF>,
        pathStart: PointF,
        pathEnd: PointF,
        actualPathLen: Double
    ): Double {
        val idealPoints = mutableListOf<PointF>()
        for (ch in word) {
            val center = charCenter[ch] ?: return 100.0
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
        for (ch in word) {
            val center = charCenter[ch] ?: continue
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
            if (!foundMatch) orderScore += 1.0
            else orderScore += minCharDist * 0.2
        }

        // Length penalty
        val idealPathLen = pathLength(idealPoints)
        val lengthPenalty = Math.abs(Math.log((actualPathLen + keyUnit) / (idealPathLen + keyUnit))) * 2.5

        // Frequency weight: 1.0 for top words, up to 1.25 for rare words
        val freqWeight = if (freqIndex < 0) 1.0 else 1.0 + (freqIndex.toDouble() / dictionary.size.coerceAtLeast(1)) * 0.25

        return (startDist * 1.0 + endDist * 1.0 + avgShapeDist * 0.5 + orderScore * 0.3 + lengthPenalty * 0.4) * freqWeight
    }

    private fun nearbyChars(point: PointF, radiusKeyUnits: Float): List<Char> {
        val radius = radiusKeyUnits * keyUnit
        return charCenter.entries
            .filter { dist(point, it.value) < radius }
            .sortedBy { dist(point, it.value) }
            .map { it.key }
    }

    private fun bigramKey(first: Char, last: Char): Long {
        return first.code.toLong() shl 16 or last.code.toLong()
    }

    private fun resample(points: List<PointF>, n: Int): List<PointF> {
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

    private fun dist(p1: PointF, p2: PointF): Double {
        return Math.sqrt(Math.pow((p1.x - p2.x).toDouble(), 2.0) + Math.pow((p1.y - p2.y).toDouble(), 2.0))
    }
}
