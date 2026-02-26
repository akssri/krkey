package com.akssri.krkey

import android.graphics.PointF
import android.graphics.Rect

class WordPredictor(
    private val keys: List<Pair<String, Rect>>,
    private val learnedWords: List<Pair<String, Int>> = emptyList()
) {
    
    private val N = 40 // Points to resample

    fun predict(path: List<PointF>): List<Pair<String, Double>> {
        if (path.size < 2) return emptyList()

        val resampledPath = resample(path, N)
        val pathStart = path.first()
        val pathEnd = path.last()
        
        val candidates = mutableListOf<Pair<String, Double>>()

        // 1. Process Learned Words (Higher Priority)
        for ((word, count) in learnedWords) {
            val score = calculateRobustScore(word, resampledPath, pathStart, pathEnd, 0) // Treat as index 0
            if (score < 1200.0) {
                // Give learned words a massive boost based on count
                val boost = 0.6 / (1.0 + Math.log10(count.toDouble() + 1.0))
                candidates.add(word to score * boost)
            }
        }

        // 2. Process Static Dictionary
        for ((index, word) in DICTIONARY.withIndex()) {
            val score = calculateRobustScore(word, resampledPath, pathStart, pathEnd, index)
            if (score < 1000.0) { 
                candidates.add(word to score)
            }
        }

        return candidates.sortedBy { it.second }.distinctBy { it.first }.take(5)
    }

    private fun calculateRobustScore(
        word: String, 
        resampledPath: List<PointF>, 
        pathStart: PointF, 
        pathEnd: PointF, 
        freqIndex: Int
    ): Double {
        val idealPathPoints = mutableListOf<PointF>()
        for (char in word) {
            val keyRect = findKey(char.toString()) ?: return 5000.0
            idealPathPoints.add(PointF(keyRect.centerX().toFloat(), keyRect.centerY().toFloat()))
        }
        
        // 1. Terminal Anchoring (CRITICAL)
        // Check if the gesture starts and ends near the intended keys
        val startDist = dist(pathStart, idealPathPoints.first())
        val endDist = dist(pathEnd, idealPathPoints.last())
        
        // Prune candidates that are wildly off at the start/end
        if (startDist > 180 || endDist > 180) return 5000.0

        // 2. Shape Comparison
        val resampledIdeal = resample(idealPathPoints, N)
        var shapeDist = 0.0
        for (i in 0 until N) {
            shapeDist += dist(resampledPath[i], resampledIdeal[i])
        }
        val avgShapeDist = shapeDist / N

        // 3. Order Validation (Check if path passes through middle keys)
        var orderScore = 0.0
        var pathIdx = 0
        for (char in word) {
            val keyRect = findKey(char.toString())!!
            var foundMatch = false
            var minCharDist = Double.MAX_VALUE
            
            // Search ahead in the path for this character
            for (i in pathIdx until resampledPath.size) {
                val d = dist(resampledPath[i], PointF(keyRect.centerX().toFloat(), keyRect.centerY().toFloat()))
                if (d < minCharDist) minCharDist = d
                if (d < 80) { // Found reasonable proximity
                    foundMatch = true
                    pathIdx = i
                    break
                }
            }
            if (!foundMatch) orderScore += 100.0 // Penalty for skipping a required key
            else orderScore += minCharDist * 0.2
        }

        // 4. Combined Weighting
        // Lower is better. Terminal distances are heavily weighted.
        val freqWeight = 1.0 + (freqIndex.toDouble() / 3000.0) * 0.3
        
        val finalScore = (avgShapeDist * 0.4 + startDist * 0.8 + endDist * 0.8 + orderScore) * freqWeight
        
        return finalScore
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

    private fun findKey(char: String): Rect? {
        return keys.find { it.first.equals(char, ignoreCase = true) }?.second
    }

    private fun dist(p1: PointF, p2: PointF): Double {
        return Math.sqrt(Math.pow((p1.x - p2.x).toDouble(), 2.0) + Math.pow((p1.y - p2.y).toDouble(), 2.0))
    }
}