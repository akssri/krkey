package com.akssri.krkey.prediction

import android.content.Context
import android.content.res.AssetManager
import android.graphics.PointF
import android.graphics.Rect
import android.view.ViewGroup
import com.akssri.krkey.BrahmiScript
import com.akssri.krkey.FlickKeyView
import com.akssri.krkey.WordPredictor
import com.akssri.krkey.ScriptManager
import com.akssri.krkey.getAvailableDictionaries
import com.akssri.krkey.getDefaultDictionaries
import com.akssri.krkey.toBrahmiScript
import com.akssri.krkey.location.KeyLocator
import com.akssri.krkey.state.KeyboardLayer

/**
 * Centralizes word predictor lifecycle management.
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

    fun ensurePredictor(
        isLatin: Boolean,
        script: BrahmiScript,
        learnedWords: List<Pair<String, Int>>
    ) {
        val key = PredictorKey(isLatin, script)
        if (currentKey == key && currentPredictor != null) {
            currentPredictor?.setLearnedWords(learnedWords)
            return
        }

        var predictor = predictors[key]
        if (predictor == null) {
            predictor = createPredictor(isLatin, script)
            predictors[key] = predictor
        }
        predictor.setLearnedWords(learnedWords)
        currentPredictor = predictor
        currentKey = key
    }

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

    private fun createPredictor(isLatin: Boolean, script: BrahmiScript): WordPredictor {
        val dictFiles = if (isLatin) listOf("en_dict.txt") else getEnabledDictionaries(script).toList()

        val staticDict = dictFiles.flatMap { dictFile ->
            val rawDict = staticDictCache.getOrPut(dictFile) {
                try { assets.open(dictFile).bufferedReader().useLines { it.toList() } } 
                catch (e: Exception) { emptyList() }
            }
            if (isLatin) rawDict else rawDict.map { it.toBrahmiScript(script) }
        }.distinct()

        val targetLayer = if (isLatin) KeyboardLayer.LATIN else KeyboardLayer.INDIC
        
        val locs = allKeys.mapNotNull { k ->
            val tuple = k.tag as? Pair<*, *> ?: return@mapNotNull null
            val char = tuple.first as? String ?: ""
            if (char.isEmpty()) return@mapNotNull null

            val r = Rect()
            k.getDrawingRect(r)
            container.offsetDescendantRectToMyCoords(k, r)
            char.lowercase() to r
        }

        return WordPredictor(locs, staticDict)
    }

    fun clearCache() {
        predictors.clear()
        currentPredictor = null
        currentKey = null
    }
}
