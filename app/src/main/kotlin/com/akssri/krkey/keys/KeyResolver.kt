package com.akssri.krkey.keys

import com.akssri.krkey.BrahmiScript
import com.akssri.krkey.toBrahmiScript

/**
 * Resolves key data to display/commit text based on keyboard state.
 * Replaces the monolithic 45-line getResolvedStrings() method with
 * focused, testable implementations.
 */
interface KeyResolver {
    /**
     * Resolve key data to (display, commit) text pair.
     *
     * @param data The key data
     * @param isLatin Latin mode active
     * @param isSymbol Symbol mode active
     * @param isShifted Shifted state
     * @param isLatinSymbol Latin symbol mode
     * @param currentBaseChar Base character for composition
     * @param script Current Brahmi script
     * @return Pair of (baseText, flickText) to display
     */
    fun resolve(
        data: KeyData,
        isLatin: Boolean,
        isSymbol: Boolean,
        isShifted: Boolean,
        isLatinSymbol: Boolean,
        currentBaseChar: String,
        script: BrahmiScript
    ): Pair<String, String>
}

/**
 * Simple key - just returns base/flick text in all modes.
 * Used for punctuation, simple symbols, etc.
 */
class SimpleKeyResolver : KeyResolver {
    override fun resolve(
        data: KeyData,
        isLatin: Boolean,
        isSymbol: Boolean,
        isShifted: Boolean,
        isLatinSymbol: Boolean,
        currentBaseChar: String,
        script: BrahmiScript
    ): Pair<String, String> {
        val v = data.variants

        return when {
            isSymbol -> {
                val b = if (isShifted) (v.sym2Base ?: v.symBase ?: data.baseText)
                        else if (isLatinSymbol) (v.latinSymBase ?: v.symBase ?: data.baseText)
                        else (v.symBase ?: data.baseText)
                val f = if (isShifted) (v.sym2Flick ?: v.symFlick ?: data.flickText)
                        else if (isLatinSymbol) (v.latinSymFlick ?: v.symFlick ?: data.flickText)
                        else (v.symFlick ?: data.flickText)
                Pair(b.toBrahmiScript(script), f.toBrahmiScript(script))
            }
            isLatin -> {
                val b = v.latinBase ?: data.baseText
                val f = v.latinFlick ?: data.flickText
                if (b.equals(f, ignoreCase = true)) {
                    if (isShifted) Pair(b.uppercase(), f.lowercase())
                    else Pair(b.lowercase(), f.uppercase())
                } else {
                    if (isShifted) Pair(b.uppercase(), f)
                    else Pair(b.lowercase(), f)
                }
            }
            else -> Pair(data.baseText.toBrahmiScript(script), data.flickText.toBrahmiScript(script))
        }
    }
}

/**
 * Vowel key - shows matra form when after consonant.
 */
class VowelKeyResolver : KeyResolver {
    private val vyanjanas = "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळ"

    override fun resolve(
        data: KeyData,
        isLatin: Boolean,
        isSymbol: Boolean,
        isShifted: Boolean,
        isLatinSymbol: Boolean,
        currentBaseChar: String,
        script: BrahmiScript
    ): Pair<String, String> {
        val v = data.variants

        // Symbol and Latin modes use simple resolution
        if (isSymbol || isLatin) {
            return SimpleKeyResolver().resolve(data, isLatin, isSymbol, isShifted, isLatinSymbol, currentBaseChar, script)
        }

        // Indic mode: check if base is consonant
        val isConsonantBase = currentBaseChar.isNotEmpty() &&
                              vyanjanas.toBrahmiScript(script).contains(currentBaseChar)

        return if (isConsonantBase) {
            // Show consonant + matra
            val matraB = v.matraBase ?: ""
            val matraF = v.matraFlick ?: ""
            Pair(
                (currentBaseChar + matraB).toBrahmiScript(script),
                (currentBaseChar + matraF).toBrahmiScript(script)
            )
        } else {
            // Show standalone vowel
            Pair(data.baseText.toBrahmiScript(script), data.flickText.toBrahmiScript(script))
        }
    }
}

/**
 * Modifier key - prefixes with base char or dotted circle.
 * Used for anusvara, visarga, nukta, etc.
 */
class ModifierKeyResolver : KeyResolver {
    override fun resolve(
        data: KeyData,
        isLatin: Boolean,
        isSymbol: Boolean,
        isShifted: Boolean,
        isLatinSymbol: Boolean,
        currentBaseChar: String,
        script: BrahmiScript
    ): Pair<String, String> {
        // Symbol and Latin modes use simple resolution
        if (isSymbol || isLatin) {
            return SimpleKeyResolver().resolve(data, isLatin, isSymbol, isShifted, isLatinSymbol, currentBaseChar, script)
        }

        // Indic mode: prefix with base or circle
        val prefix = if (currentBaseChar.isNotEmpty()) currentBaseChar else "◌"
        return Pair(
            (prefix + data.baseText).toBrahmiScript(script),
            (prefix + data.flickText).toBrahmiScript(script)
        )
    }
}

/**
 * Consonant key - standard behavior, just converts to target script.
 */
class ConsonantKeyResolver : KeyResolver {
    override fun resolve(
        data: KeyData,
        isLatin: Boolean,
        isSymbol: Boolean,
        isShifted: Boolean,
        isLatinSymbol: Boolean,
        currentBaseChar: String,
        script: BrahmiScript
    ): Pair<String, String> {
        return SimpleKeyResolver().resolve(data, isLatin, isSymbol, isShifted, isLatinSymbol, currentBaseChar, script)
    }
}
