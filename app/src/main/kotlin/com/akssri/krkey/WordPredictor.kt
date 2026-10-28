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

/**
 * Prefix completion and swipe (gesture) decoding over a frequency-ordered dictionary.
 *
 * Swipe decoding aligns each letter of a word to a point of the resampled swipe, in order
 * (an elastic alignment), and scores:
 *   - how far each letter's key is from its aligned point,
 *   - how far the swipe strays from the straight line between consecutive letters,
 *     plus a penalty for moving against that line,
 *   - swipe length before the first / after the last letter (start hooks, overshoot),
 *   - a log-rank frequency prior, where learning a word lifts its effective rank.
 * The alignment is a dynamic program run over the dictionary trie: each node's cost row extends
 * its parent's in O(M). The trie is searched as a beam, one letter at a time, keeping the BEAM
 * best prefixes ranked by their cost plus part (BEAM_ALPHA) of the cost of leaving the rest of
 * the swipe unexplained; this bounds the work on long swipes, where exact search visits nearly
 * the whole trie. Since a row's minimum never decreases further down, subtrees that cannot beat
 * the current top candidates are also pruned. Weights are tuned with tools/swipe-sim, whose
 * decode_beam() is the reference implementation.
 */
class WordPredictor(
    keys: List<KeyBox>,
    private val dictionary: List<String>,
) {
    /** A key's label and bounds, in the same coordinates as swipe paths. */
    data class KeyBox(val label: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

    private class Pt(val x: Double, val y: Double)

    private data class IndexedWord(val word: String, val freqIndex: Int)

    private class TrieNode {
        val children = mutableMapOf<Int, TrieNode>()
        val topWords = mutableListOf<String>()
        val words = mutableListOf<IndexedWord>() // words ending here

        // Filled by compile(): children whose letter is on the keyboard, and their key indices
        var kids: Array<TrieNode> = emptyArray()
        var kidKeys: IntArray = IntArray(0)
    }

    private var learnedWords: List<Pair<String, Int>> = emptyList()
    private var learnedCountMap: Map<String, Int> = emptyMap()

    // Lowercased word -> dictionary spelling (keeps "I'm", proper-noun casing)
    private val dictSpelling: Map<String, String> =
        HashMap<String, String>(dictionary.size * 2).apply {
            for (word in dictionary) putIfAbsent(word.lowercase(), word)
        }

    // Average key size; 0 if the keys had not been laid out yet
    private val keyUnit: Float =
        run {
            if (keys.isEmpty()) return@run 0f
            val widths = keys.map { it.right - it.left }
            val heights = keys.map { it.bottom - it.top }
            ((widths.sum() / widths.size) + (heights.sum() / heights.size)) / 2f
        }

    /** False when built from unlaid-out (0x0) keys: swipes cannot be decoded and it should be rebuilt. */
    val hasGeometry: Boolean = keyUnit > MIN_KEY_UNIT_PX

    // Key centres in key units
    private val charCenter: Map<Int, Pt> =
        buildMap {
            if (!hasGeometry) return@buildMap
            for (k in keys) {
                if (k.label.isEmpty()) continue
                val cp = k.label.codePointAt(0)
                if (Character.charCount(cp) == k.label.length) {
                    put(Character.toLowerCase(cp), Pt((k.left + k.right) / 2.0 / keyUnit, (k.top + k.bottom) / 2.0 / keyUnit))
                }
            }
        }

    // Keyboard letters by index, for the per-swipe distance caches
    private val keyCodePoints: IntArray = charCenter.keys.toIntArray()
    private val keyIndex: Map<Int, Int> = keyCodePoints.withIndex().associate { (i, cp) -> cp to i }
    private val keyX = DoubleArray(keyCodePoints.size) { charCenter.getValue(keyCodePoints[it]).x }
    private val keyY = DoubleArray(keyCodePoints.size) { charCenter.getValue(keyCodePoints[it]).y }

    private val trieRoot = TrieNode()

    // Beam-search buffers, reused across decodes (decoding is single-threaded)
    private val frontNodes = arrayOfNulls<TrieNode>(BEAM)
    private val frontKeys = IntArray(BEAM)
    private var frontRows = Array(BEAM) { DoubleArray(M) }
    private var nextRows = Array(BEAM) { DoubleArray(M) }
    private var candRows = Array(0) { DoubleArray(M) }
    private var candNodes = arrayOfNulls<TrieNode>(0)
    private var candKeys = IntArray(0)
    private var candScores = DoubleArray(0)

    /** Nodes whose cost row was computed in the last predict(); for benchmarks. */
    var lastNodesVisited = 0
        private set

    // Learned words that are not in the dictionary, rebuilt when that set changes
    private var learnedTrieRoot = TrieNode()
    private var learnedOnly: List<String> = emptyList()

    init {
        for ((index, word) in dictionary.withIndex()) insert(trieRoot, word, IndexedWord(word, index))
        compile(trieRoot)
    }

    private fun insert(
        root: TrieNode,
        word: String,
        entry: IndexedWord,
    ) {
        val cps = swipeCodePoints(word)
        if (cps.isEmpty()) return
        var curr = root
        for (cp in cps) {
            curr = curr.children.getOrPut(cp) { TrieNode() }
            if (curr.topWords.size < 10 && !curr.topWords.contains(word)) {
                curr.topWords.add(word)
            }
        }
        curr.words.add(entry)
    }

    private fun compile(node: TrieNode) {
        val onKeyboard = node.children.entries.filter { it.key in keyIndex }
        node.kids = Array(onKeyboard.size) { onKeyboard[it].value }
        node.kidKeys = IntArray(onKeyboard.size) { keyIndex.getValue(onKeyboard[it].key) }
        for (child in node.children.values) compile(child)
    }

    fun setLearnedWords(words: List<Pair<String, Int>>) {
        learnedWords = words
        learnedCountMap = words.toMap()
        val only = words.map { it.first }.filter { it !in dictSpelling }.sorted()
        if (only != learnedOnly) {
            learnedOnly = only
            learnedTrieRoot = TrieNode()
            for (word in only) insert(learnedTrieRoot, word, IndexedWord(word, -1))
            compile(learnedTrieRoot)
        }
    }

    fun getPrefixMatches(prefix: String): List<String> {
        if (prefix.length < 2) return emptyList()
        val lower = prefix.lowercase()
        val userMatches =
            learnedWords
                .filter { it.first.startsWith(lower) }
                .sortedByDescending { it.second }
                .map { dictSpelling[it.first] ?: it.first }

        var curr = trieRoot
        for (cp in swipeCodePoints(lower)) {
            curr = curr.children[cp] ?: return userMatches.take(5)
        }

        val dictMatches = curr.topWords

        return (userMatches + dictMatches).distinct().take(5)
    }

    /** Swipe path state shared by the trie walk. */
    private inner class Decoding(path: List<Pt>) {
        val xs = DoubleArray(M)
        val ys = DoubleArray(M)
        val arc = DoubleArray(M) // arc length up to each point
        val step = DoubleArray(M) // arc length each point accounts for (from the previous one)
        val tail = DoubleArray(M) // cost of leaving the rest of the swipe unexplained
        val best = ArrayList<Pair<String, Double>>(TOP_K + 1)

        // W_PT * distance from each key to each point
        val keyDist = Array(keyCodePoints.size) { DoubleArray(M) }

        // Per letter pair (lazily): integrated deviation cost from the line between the two keys
        private val pairCum = arrayOfNulls<DoubleArray>(keyCodePoints.size * keyCodePoints.size)

        init {
            val pts = resample(path, M)
            for (i in 0 until M) {
                xs[i] = pts[i].x / keyUnit
                ys[i] = pts[i].y / keyUnit
                if (i > 0) {
                    step[i] = Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
                    arc[i] = arc[i - 1] + step[i]
                }
            }
            for (i in 0 until M) tail[i] = W_TAIL * (arc[M - 1] - arc[i])
            for (k in keyCodePoints.indices) {
                for (j in 0 until M) keyDist[k][j] = W_PT * norm(xs[j] - keyX[k], ys[j] - keyY[k])
            }
        }

        fun deviation(
            from: Int,
            to: Int,
        ): DoubleArray {
            val idx = from * keyCodePoints.size + to
            pairCum[idx]?.let { return it }
            val cumArr = DoubleArray(M)
            val ax = keyX[from]
            val ay = keyY[from]
            val bx = keyX[to] - ax
            val by = keyY[to] - ay
            val segLen2 = bx * bx + by * by
            val segLen = Math.sqrt(segLen2)
            var cum = 0.0
            for (j in 0 until M) {
                if (j > 0 && step[j] > 0) {
                    val px = xs[j] - ax
                    val py = ys[j] - ay
                    val t = if (segLen2 > 0) ((px * bx + py * by) / segLen2).coerceIn(0.0, 1.0) else 0.0
                    var cost = norm(px - t * bx, py - t * by)
                    if (segLen > 0) {
                        // Moving against the letter-to-letter direction
                        val along = ((xs[j] - xs[j - 1]) * bx + (ys[j] - ys[j - 1]) * by) / segLen
                        if (along < 0) cost += W_BACK * (-along / step[j])
                    }
                    cum += W_DEV * cost * step[j]
                }
                cumArr[j] = cum
            }
            pairCum[idx] = cumArr
            return cumArr
        }

        fun cap(): Double = if (best.size >= TOP_K) best[TOP_K - 1].second else Double.MAX_VALUE

        fun add(
            word: String,
            score: Double,
        ) {
            val existing = best.indexOfFirst { it.first.equals(word, ignoreCase = true) }
            if (existing >= 0) {
                if (best[existing].second <= score) return
                best.removeAt(existing)
            }
            val pos = best.indexOfFirst { it.second > score }.let { if (it < 0) best.size else it }
            best.add(pos, word to score)
            if (best.size > TOP_K) best.removeAt(TOP_K)
        }
    }

    fun predict(path: List<PointF>): List<Pair<String, Double>> = predict(FloatArray(path.size) { path[it].x }, FloatArray(path.size) { path[it].y })

    /** Decodes a swipe given as parallel x/y arrays (same coordinates as the key boxes). */
    fun predict(
        xs: FloatArray,
        ys: FloatArray,
    ): List<Pair<String, Double>> {
        if (xs.size < 2 || charCenter.isEmpty()) return emptyList()
        val dec = Decoding(List(xs.size) { Pt(xs[it].toDouble(), ys[it].toDouble()) })
        lastNodesVisited = 0
        beamSearch(dec)
        return dec.best.toList()
    }

    private fun ensureCandidates(n: Int) {
        if (candRows.size >= n) return
        val size = maxOf(n, candRows.size * 2, 64)
        candRows = Array(size) { if (it < candRows.size) candRows[it] else DoubleArray(M) }
        candNodes = candNodes.copyOf(size)
        candKeys = candKeys.copyOf(size)
        candScores = candScores.copyOf(size)
    }

    private fun beamSearch(dec: Decoding) {
        // Lowest possible frequency prior (rank 0), used to prune subtrees
        val minAdjust = FREQ_PRIOR_WEIGHT * Math.log(2.0)
        frontNodes[0] = trieRoot
        frontNodes[1] = learnedTrieRoot
        var frontCount = 2

        for (depth in 0 until MAX_WORD_LENGTH) {
            var n = 0
            for (f in 0 until frontCount) {
                val node = frontNodes[f]!!
                val parentRow = if (depth == 0) null else frontRows[f]
                ensureCandidates(n + node.kids.size)
                for (c in node.kids.indices) {
                    val k = node.kidKeys[c]
                    val row = candRows[n]
                    val dist = dec.keyDist[k]
                    var rowMin = Double.MAX_VALUE
                    if (parentRow == null) {
                        for (j in 0 until M) {
                            row[j] = W_LEAD * dec.arc[j] + dist[j]
                            if (row[j] < rowMin) rowMin = row[j]
                        }
                    } else {
                        val cum = dec.deviation(frontKeys[f], k)
                        var runningMin = Double.MAX_VALUE // min over j' <= j of (parentRow[j'] - cum[j'])
                        for (j in 0 until M) {
                            val candidate = parentRow[j] - cum[j]
                            if (candidate < runningMin) runningMin = candidate
                            row[j] = dist[j] + runningMin + cum[j]
                            if (row[j] < rowMin) rowMin = row[j]
                        }
                    }
                    lastNodesVisited++
                    if (rowMin + minAdjust >= dec.cap()) continue

                    val child = node.kids[c]
                    if (child.words.isNotEmpty() && depth >= 1) {
                        var terminal = Double.MAX_VALUE
                        for (j in 0 until M) terminal = minOf(terminal, row[j] + dec.tail[j])
                        for (iw in child.words) {
                            val score = terminal + FREQ_PRIOR_WEIGHT * Math.log(effectiveRank(iw) + 2.0)
                            if (score < dec.cap()) dec.add(dictSpelling[iw.word.lowercase()] ?: iw.word, score)
                        }
                    }
                    if (child.kids.isEmpty()) continue
                    var beamScore = Double.MAX_VALUE
                    for (j in 0 until M) beamScore = minOf(beamScore, row[j] + BEAM_ALPHA * dec.tail[j])
                    candNodes[n] = child
                    candKeys[n] = k
                    candScores[n] = beamScore
                    n++
                }
            }
            if (n == 0) break

            // Keep the BEAM best, in (stable) score order
            val keep = (0 until n).sortedBy { candScores[it] }.take(BEAM)
            for ((slot, c) in keep.withIndex()) {
                System.arraycopy(candRows[c], 0, nextRows[slot], 0, M)
                frontNodes[slot] = candNodes[c]
                frontKeys[slot] = candKeys[c]
            }
            frontCount = keep.size
            val swap = frontRows
            frontRows = nextRows
            nextRows = swap
        }
    }

    // Learning lifts a word's rank towards the top, never past it: words that are already
    // common gain nothing, rare and out-of-dictionary words gain the most
    private fun effectiveRank(iw: IndexedWord): Double {
        val rank = if (iw.freqIndex < 0) LEARNED_ONLY_RANK.toDouble() else iw.freqIndex.toDouble()
        val learnedCount = learnedCountMap[iw.word.lowercase()] ?: return rank
        return minOf(rank, LEARNED_ONLY_RANK / Math.pow(1.0 + learnedCount, LEARNED_RANK_DECAY))
    }

    // Code points a word is swiped/typed with: lowercase, apostrophes and hyphens skipped
    private fun swipeCodePoints(word: String): IntArray = codePointsOf(word.lowercase()).filter { it != '\''.code && it != '-'.code }.toIntArray()

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
        points: List<Pt>,
        n: Int,
    ): List<Pt> {
        val totalLen = pathLength(points)
        if (totalLen == 0.0) return List(n) { points[0] }

        val interval = totalLen / (n - 1)
        val result = mutableListOf<Pt>()
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
                    result.add(Pt(x, y))
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

    private fun pathLength(points: List<Pt>): Double {
        var len = 0.0
        for (i in 1 until points.size) {
            len += dist(points[i - 1], points[i])
        }
        return len
    }

    // Faster than Math.hypot, which guards against overflow we cannot hit here
    private fun norm(
        dx: Double,
        dy: Double,
    ): Double = Math.sqrt(dx * dx + dy * dy)

    private fun dist(
        p1: Pt,
        p2: Pt,
    ): Double = Math.hypot(p1.x - p2.x, p1.y - p2.y)

    private companion object {
        const val M = 48 // resampled swipe points
        const val BEAM = 200 // prefixes kept per letter (tools/swipe-sim/beam_eval.py)
        const val BEAM_ALPHA = 0.5 // share of the unexplained-swipe cost charged when ranking prefixes
        const val MIN_KEY_UNIT_PX = 1f
        const val TOP_K = 5
        const val MAX_WORD_LENGTH = 32

        // Weights, tuned with tools/swipe-sim/tune.py and refine.py (held-out top-1 94.0% vs 88.2% before)
        const val W_PT = 1.0 // letter key to aligned point
        const val W_DEV = 1.0 // deviation from the letter-to-letter line
        const val W_BACK = 3.0 // moving against that line
        const val W_LEAD = 3.0 // swipe before the first letter, per key length
        const val W_TAIL = 1.5 // swipe after the last letter, per key length
        const val FREQ_PRIOR_WEIGHT = 0.2 // per ln(rank + 2)

        const val LEARNED_ONLY_RANK = 1000 // rank of a learned word seen once is at most this / 2^1.5
        const val LEARNED_RANK_DECAY = 1.5 // rank <= LEARNED_ONLY_RANK / (1 + times learned)^decay
    }
}
