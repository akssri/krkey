package com.akssri.krkey.prediction

import android.content.Context
import android.content.res.AssetManager
import android.graphics.PointF
import android.graphics.Rect
import android.view.ViewGroup
import com.akssri.krkey.BrahmiScript
import com.akssri.krkey.FlickKeyView
import com.akssri.krkey.WordPredictor
import com.akssri.krkey.configMap
import com.akssri.krkey.getAvailableDictionaries
import com.akssri.krkey.getDefaultDictionaries
import com.akssri.krkey.toBrahmiScript
import com.akssri.krkey.location.KeyLocator

/**
 * Centralizes word predictor lifecycle management.
 * Performance: Caches predictors per mode, eliminates rebuilding on mode switch.
 */
class PredictionManager(
    private val context: Context,
    private val assets: AssetManager,
    private val keyLocator: KeyLocator,
    private val allKeys: List<FlickKeyView>,
    private val container: ViewGroup
) {
    private data class PredictorKey(
        val isLatin: Boolean,
        val script: BrahmiScript
    )

    private var predictors = mutableMapOf<PredictorKey, WordPredictor>()
    private var staticDictCache = mutableMapOf<String, List<String>>()
    private var currentPredictor: WordPredictor? = null
    private var currentKey: PredictorKey? = null

    /**
     * Ensure predictor is ready for current mode.
     * Lazy initialization and caching - no rebuilding on mode switches.
     *
     * @param isLatin Latin mode active
     * @param script Current Brahmi script
     * @param learnedWords User's learned words (word, count pairs)
     */
    fun ensurePredictor(
        isLatin: Boolean,
        script: BrahmiScript,
        learnedWords: List<Pair<String, Int>>
    ) {
        val key = PredictorKey(isLatin, script)

        // Check if we already have the right predictor
        if (currentKey == key && currentPredictor != null) {
            // Just refresh learned words (cheap operation)
            currentPredictor?.setLearnedWords(learnedWords)
            return
        }

        // Try to get cached predictor
        var predictor = predictors[key]

        if (predictor == null) {
            // Create new predictor
            predictor = createPredictor(isLatin, script)
            predictors[key] = predictor
        }

        // Update learned words
        predictor.setLearnedWords(learnedWords)

        currentPredictor = predictor
        currentKey = key
    }

    /**
     * Get prefix matches for current word being typed.
     */
    fun getPrefixMatches(prefix: String): List<String> {
        return currentPredictor?.getPrefixMatches(prefix) ?: emptyList()
    }

    /**
     * Predict word from gesture path.
     * @return List of (word, score) pairs
     */
    fun predictGesture(path: List<PointF>): List<Pair<String, Double>> {
        return currentPredictor?.predict(path) ?: emptyList()
    }

    /**
     * Get enabled dictionaries for a script from preferences.
     */
    private fun getEnabledDictionaries(script: BrahmiScript): Set<String> {
        val prefs = context.getSharedPreferences("krkey_prefs", Context.MODE_PRIVATE)
        val available = script.getAvailableDictionaries()
        val defaults = script.getDefaultDictionaries()

        return available.map { it.first }.filter { dictFile ->
            prefs.getBoolean("dict_${script.name}_$dictFile", dictFile in defaults)
        }.toSet()
    }

    /**
     * Create a new predictor for the given mode.
     */
    private fun createPredictor(isLatin: Boolean, script: BrahmiScript): WordPredictor {
        // Determine dictionary files based on mode and preferences
        val dictFiles = when {
            isLatin -> listOf("en_dict.txt")
            else -> getEnabledDictionaries(script).toList()
        }

        // Load and merge all enabled dictionaries
        // IMPORTANT: Convert dictionary words to target script
        val staticDict = dictFiles.flatMap { dictFile ->
            val rawDict = staticDictCache.getOrPut(dictFile) {
                try {
                    assets.open(dictFile).bufferedReader().useLines { it.toList() }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            // Transliterate each word to target script (e.g., Sanskrit देव → Kannada ದೇವ)
            if (isLatin) {
                rawDict // No transliteration for Latin
            } else {
                rawDict.map { it.toBrahmiScript(script) }
            }
        }.distinct() // Remove duplicates

        // Build key locations for gesture typing
        val locs = allKeys.mapNotNull { k ->
            val cfg = configMap[k.id] ?: return@mapNotNull null
            val rawChar = if (isLatin) cfg.latinBase else cfg.base
            if (rawChar == null || rawChar.isEmpty()) return@mapNotNull null

            val char = if (isLatin) rawChar else rawChar.toBrahmiScript(script)
            if (char.length != 1) return@mapNotNull null

            val r = Rect()
            k.getDrawingRect(r)
            container.offsetDescendantRectToMyCoords(k, r)

            char.lowercase() to r
        }

        return WordPredictor(locs, staticDict)
    }

    /**
     * Clear all cached predictors (e.g., on memory pressure).
     */
    fun clearCache() {
        predictors.clear()
        currentPredictor = null
        currentKey = null
    }

    /**
     * Get current predictor (for direct access if needed).
     */
    fun getCurrentPredictor(): WordPredictor? = currentPredictor
}
