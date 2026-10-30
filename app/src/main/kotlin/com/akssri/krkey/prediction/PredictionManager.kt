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

package com.akssri.krkey.prediction

import android.content.Context
import android.content.res.AssetManager
import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.akssri.krkey.BrahmiScript
import com.akssri.krkey.FlickKeyView
import com.akssri.krkey.WordPredictor
import com.akssri.krkey.getAvailableDictionaries
import com.akssri.krkey.getDefaultDictionaries
import com.akssri.krkey.location.KeyLocator
import com.akssri.krkey.toBrahmiScript
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Centralizes word predictor lifecycle management.
 *
 * Building a predictor (reading the dictionaries, transliterating them and building the trie)
 * takes seconds on slow devices, so it runs on a background thread: [prepare] starts a build,
 * and until it is ready [getPrefixMatches] and [predictGesture] return nothing. Only the key
 * geometry is read on the UI thread. All fields are touched on the UI thread only; a finished
 * build is handed back through the main looper.
 */
class PredictionManager(
    private val context: Context,
    private val assets: AssetManager,
    private val keyLocator: KeyLocator,
    // The character keys of the shown grid
    private val currentKeys: () -> List<FlickKeyView>,
    private val container: ViewGroup,
) {
    private data class PredictorKey(
        val isLatin: Boolean,
        val script: BrahmiScript,
    )

    private val predictors = mutableMapOf<PredictorKey, WordPredictor>()
    private var currentPredictor: WordPredictor? = null
    private var currentKey: PredictorKey? = null

    // Builds in flight, with the generation they were started in; clearCache() bumps the
    // generation so builds from before a settings change are dropped
    private val pending = mutableMapOf<PredictorKey, Int>()
    private var generation = 0

    // Modes whose build failed (e.g. out of memory): not retried on every keystroke, only after
    // clearCache() or with a new input view's manager
    private val failed = mutableSetOf<PredictorKey>()

    /** Called on the UI thread when a predictor finishes building. */
    var onPredictorReady: (() -> Unit)? = null

    /**
     * Uses the predictor for this mode if it is built; otherwise starts building it and leaves
     * no current predictor, so predictions are empty until it is ready.
     */
    fun ensurePredictor(
        isLatin: Boolean,
        script: BrahmiScript,
        learnedWords: List<Pair<String, Int>>,
    ) {
        val key = PredictorKey(isLatin, script)
        val predictor = predictors[key]
        if (predictor == null) {
            currentPredictor = null
            currentKey = null
            prepare(isLatin, script)
            return
        }
        predictor.setLearnedWords(learnedWords)
        currentPredictor = predictor
        currentKey = key
    }

    /**
     * Starts building the predictor for this mode in the background, unless it is built or
     * being built. The current grid must be that mode's letter layer, laid out: its keys are
     * the predictor's geometry. Does nothing before layout; a later call builds it.
     */
    fun prepare(
        isLatin: Boolean,
        script: BrahmiScript,
    ) {
        val key = PredictorKey(isLatin, script)
        if (key in predictors || key in pending || key in failed) return
        val keys = keyBoxes()
        // Not laid out yet (0x0 keys): a predictor built now could not decode swipes
        if (!WordPredictor.hasGeometry(keys)) return
        val dictFiles = if (isLatin) listOf("en_dict.txt") else getEnabledDictionaries(script).toList()

        val gen = generation
        pending[key] = gen
        builder.execute {
            // An uncaught throwable here would kill the keyboard process, and a build that never
            // posts back would leave its mode pending; a failed build just leaves no predictor
            val predictor =
                try {
                    WordPredictor(keys, loadDictionary(isLatin, script, dictFiles))
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
            mainHandler.post {
                if (pending[key] == gen) pending.remove(key)
                if (gen != generation) return@post
                if (predictor == null) {
                    failed.add(key)
                    return@post
                }
                predictors[key] = predictor
                onPredictorReady?.invoke()
            }
        }
    }

    /** Whether the predictor for this mode is being built (predictions are empty until then). */
    fun isBuilding(
        isLatin: Boolean,
        script: BrahmiScript,
    ): Boolean = PredictorKey(isLatin, script) in pending

    fun getPrefixMatches(prefix: String): List<String> = currentPredictor?.getPrefixMatches(prefix) ?: emptyList()

    fun predictGesture(path: List<PointF>): List<Pair<String, Double>> = currentPredictor?.predict(path) ?: emptyList()

    private fun getEnabledDictionaries(script: BrahmiScript): Set<String> {
        val prefs = context.getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        val available = script.getAvailableDictionaries()
        val defaults = script.getDefaultDictionaries()

        return available.map { it.first }.filter { dictFile ->
            prefs.getBoolean("dict_${script.name}_$dictFile", dictFile in defaults)
        }.toSet()
    }

    // The current grid's letter keys, in the container's coordinates (UI thread)
    private fun keyBoxes(): List<WordPredictor.KeyBox> =
        currentKeys().mapNotNull { k ->
            if (k.parent == null || k.visibility != android.view.View.VISIBLE) return@mapNotNull null
            val tuple = k.tag as? Pair<*, *> ?: return@mapNotNull null
            val char = tuple.first as? String ?: ""
            if (char.isEmpty()) return@mapNotNull null

            val r = Rect()
            k.getDrawingRect(r)
            try {
                container.offsetDescendantRectToMyCoords(k, r)
            } catch (e: IllegalArgumentException) {
                return@mapNotNull null
            }
            WordPredictor.KeyBox(char.lowercase(), r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
        }

    // Builder thread only
    private fun loadDictionary(
        isLatin: Boolean,
        script: BrahmiScript,
        dictFiles: List<String>,
    ): List<String> =
        dictFiles.flatMap { dictFile ->
            val rawDict =
                rawDictCache.getOrPut(dictFile) {
                    try {
                        assets.open(dictFile).bufferedReader().useLines { it.toList() }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            if (isLatin) rawDict else rawDict.map { it.toBrahmiScript(script) }
        }.distinct()

    fun clearCache() {
        generation++
        predictors.clear()
        pending.clear()
        failed.clear()
        currentPredictor = null
        currentKey = null
    }

    private companion object {
        // One thread for all builds, shared by the managers of successive input views
        val builder: ExecutorService =
            Executors.newSingleThreadExecutor { r -> Thread(r, "krkey-predictor").apply { isDaemon = true } }
        val mainHandler = Handler(Looper.getMainLooper())

        // Dictionary files as read from the assets; touched on the builder thread only
        val rawDictCache = HashMap<String, List<String>>()
    }
}
