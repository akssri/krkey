/*
 *  Copyright (c) 2026 Akshay Srinivasan <akssri@vakra.xyz>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.akssri.krkey

/**
 * Utilities for Indic orthographic syllables (aksharas) and conjunct clusters.
 *
 * In Brahmic scripts, the fundamental visual and orthographic unit is the akshara:
 * 1. Consonant syllable:
 *    [Consonant (+ Nukta)? + Virama]* + Consonant (+ Nukta)? (+ Matra)? (+ Modifiers)*
 * 2. Vowel syllable:
 *    IndependentVowel (+ Nukta)? (+ Modifiers)*
 *
 * Navigation and backspace operations must respect akshara boundaries to avoid
 * leaving orphaned combining marks or corrupting adjacent consonants.
 * Conjunct clusters (consonants joined by virama) can be visually expanded using
 * ZWNJ (U+200C) after a brief dwell delay so sub-components become visually distinct
 * and directly editable.
 */
object IndicClusterUtils {
    const val ZWNJ = "\u200C"
    const val ZWJ = "\u200D"

    private const val CP_ZWNJ = 0x200C
    private const val CP_ZWJ = 0x200D

    fun isVirama(
        cp: Int,
        scriptData: ScriptData,
    ): Boolean {
        if (scriptData.virama.isEmpty()) return false
        return String(Character.toChars(cp)) == scriptData.virama
    }

    fun isNukta(
        cp: Int,
        scriptData: ScriptData,
    ): Boolean {
        if (scriptData.nukta.isEmpty()) return false
        return String(Character.toChars(cp)) == scriptData.nukta
    }

    fun isConsonant(
        cp: Int,
        scriptData: ScriptData,
    ): Boolean {
        return String(Character.toChars(cp)) in scriptData.consonants
    }

    fun isVowel(
        cp: Int,
        scriptData: ScriptData,
    ): Boolean {
        val s = String(Character.toChars(cp))
        return s in scriptData.prefixableChars && s !in scriptData.consonants
    }

    fun isZwnj(cp: Int): Boolean = cp == CP_ZWNJ

    fun isZwj(cp: Int): Boolean = cp == CP_ZWJ

    /**
     * Checks if [cp] is an Indic combining mark (matra, anusvara, visarga, candrabindu, Vedic mark).
     */
    fun isCombining(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    /**
     * Finds the UTF-16 character ranges ([IntRange]) of all aksharas in [text].
     */
    fun findAksharas(
        text: CharSequence,
        scriptData: ScriptData,
    ): List<IntRange> {
        if (text.isEmpty()) return emptyList()

        val n = text.length
        val cps = ArrayList<Int>()
        val offsets = ArrayList<Int>()
        var idx = 0
        while (idx < n) {
            offsets.add(idx)
            val cp = Character.codePointAt(text, idx)
            cps.add(cp)
            idx += Character.charCount(cp)
        }
        offsets.add(n)

        val ranges = ArrayList<IntRange>()
        var i = 0
        val totalCps = cps.size

        while (i < totalCps) {
            val startIdx = offsets[i]
            val cp = cps[i++]

            if (isConsonant(cp, scriptData)) {
                if (i < totalCps && isNukta(cps[i], scriptData)) {
                    i++
                }
                while (i < totalCps && isVirama(cps[i], scriptData)) {
                    // Check if virama is followed by ZWNJ
                    if (i + 1 < totalCps && isZwnj(cps[i + 1])) {
                        // Explicit halant consonant (C + Virama + ZWNJ): breaks cluster
                        i += 2
                        break
                    }
                    if (i + 1 < totalCps && isZwj(cps[i + 1])) {
                        // Half-form (C + Virama + ZWJ)
                        i += 2
                        if (i < totalCps && isConsonant(cps[i], scriptData)) {
                            i++
                            if (i < totalCps && isNukta(cps[i], scriptData)) i++
                            continue
                        }
                        break
                    }
                    if (i + 1 < totalCps && isConsonant(cps[i + 1], scriptData)) {
                        i += 2
                        if (i < totalCps && isNukta(cps[i], scriptData)) i++
                    } else {
                        // Trailing virama at word boundary (e.g. vidvan)
                        i++
                        break
                    }
                }
                // Combining marks (matras, anusvara, visarga, candrabindu, etc.)
                while (i < totalCps && isCombining(cps[i]) && !isVirama(cps[i], scriptData)) {
                    i++
                }
            } else if (isVowel(cp, scriptData)) {
                if (i < totalCps && isNukta(cps[i], scriptData)) {
                    i++
                }
                while (i < totalCps && isCombining(cps[i]) && !isVirama(cps[i], scriptData)) {
                    i++
                }
            } else {
                // Non-Indic, Latin, punctuation, symbols, whitespace
                while (i < totalCps && isCombining(cps[i])) {
                    i++
                }
            }
            val endIdx = offsets[i]
            ranges.add(startIdx until endIdx)
        }

        return ranges
    }

    /**
     * Returns the trailing akshara in [text], or empty string if [text] is empty.
     */
    fun getTrailingAkshara(
        text: CharSequence,
        scriptData: ScriptData,
    ): String {
        if (text.isEmpty()) return ""
        val window = if (text.length > 64) text.subSequence(text.length - 64, text.length) else text
        val aksharas = findAksharas(window, scriptData)
        if (aksharas.isEmpty()) return ""
        val lastRange = aksharas.last()
        return window.subSequence(lastRange.first, lastRange.last + 1).toString()
    }

    /**
     * Returns the leading akshara in [text], or empty string if [text] is empty.
     * If [text] begins with combining marks (e.g. cursor was inside an akshara),
     * returns the remaining combining marks so the cursor can step to the akshara end.
     */
    fun getLeadingAkshara(
        text: CharSequence,
        scriptData: ScriptData,
    ): String {
        if (text.isEmpty()) return ""
        val firstCp = Character.codePointAt(text, 0)
        if (isCombining(firstCp) || isVirama(firstCp, scriptData) || isNukta(firstCp, scriptData)) {
            val markLen = getLeadingCombiningMarksLength(text, scriptData)
            if (markLen > 0) return text.subSequence(0, markLen).toString()
        }
        val window = if (text.length > 64) text.subSequence(0, 64) else text
        val aksharas = findAksharas(window, scriptData)
        if (aksharas.isEmpty()) return ""
        val firstRange = aksharas.first()
        return window.subSequence(firstRange.first, firstRange.last + 1).toString()
    }

    /**
     * Returns the UTF-16 length of leading combining marks at the start of [textAfter].
     * Used by backspace to ensure orphaned combining marks do not merge into preceding characters.
     */
    fun getLeadingCombiningMarksLength(
        textAfter: CharSequence,
        scriptData: ScriptData,
    ): Int {
        var idx = 0
        val n = textAfter.length
        while (idx < n) {
            val cp = Character.codePointAt(textAfter, idx)
            if (isCombining(cp) || isVirama(cp, scriptData) || isNukta(cp, scriptData) || isZwnj(cp) || isZwj(cp)) {
                idx += Character.charCount(cp)
            } else {
                break
            }
        }
        return idx
    }

    /**
     * Returns true if [akshara] is a conjunct cluster (contains two or more consonants
     * joined by virama without ZWNJ).
     */
    fun isConjunctCluster(
        akshara: String,
        scriptData: ScriptData,
    ): Boolean {
        if (scriptData.virama.isEmpty() || akshara.length < 2) return false
        val cps = ArrayList<Int>()
        var idx = 0
        while (idx < akshara.length) {
            val cp = Character.codePointAt(akshara, idx)
            cps.add(cp)
            idx += Character.charCount(cp)
        }
        for (i in 0 until cps.size - 1) {
            if (isVirama(cps[i], scriptData)) {
                // Must not be followed by ZWNJ
                if (isZwnj(cps[i + 1])) continue
                // Must be followed by a consonant (possibly after nukta or ZWJ)
                var j = i + 1
                if (j < cps.size && isZwj(cps[j])) j++
                if (j < cps.size && isConsonant(cps[j], scriptData)) return true
            }
        }
        return false
    }

    /**
     * Expands an unexpanded conjunct cluster by inserting ZWNJ (\u200C) after each virama
     * joining two consonants, rendering the components as distinct glyphs.
     */
    fun expandCluster(
        akshara: String,
        scriptData: ScriptData,
    ): String {
        if (!isConjunctCluster(akshara, scriptData)) return akshara
        val sb = java.lang.StringBuilder()
        val cps = ArrayList<Int>()
        var idx = 0
        while (idx < akshara.length) {
            val cp = Character.codePointAt(akshara, idx)
            cps.add(cp)
            idx += Character.charCount(cp)
        }
        for (i in 0 until cps.size) {
            val cp = cps[i]
            sb.appendCodePoint(cp)
            if (isVirama(cp, scriptData)) {
                val nextIsZwnj = i + 1 < cps.size && isZwnj(cps[i + 1])
                val nextIsConsonant = i + 1 < cps.size && isConsonant(cps[i + 1], scriptData)
                if (!nextIsZwnj && nextIsConsonant) {
                    sb.append(ZWNJ)
                }
            }
        }
        return sb.toString()
    }

    /**
     * Collapses an expanded cluster by removing ZWNJ (\u200C).
     */
    fun collapseCluster(text: String): String {
        return text.replace(ZWNJ, "")
    }

    /**
     * Returns the UTF-16 length that backspace deletes from the end of [textBefore]: the last
     * vowel sign or other mark (anusvara, visarga, ...) if the syllable ends with one, so marks
     * come off one at a time, otherwise the whole trailing akshara: the consonant or conjunct,
     * expanded or not (a half-consonant with its ZWNJ), or one character in other text.
     */
    fun getBackspaceLength(
        textBefore: CharSequence,
        scriptData: ScriptData,
    ): Int {
        if (textBefore.isEmpty()) return 0
        val lastCp = Character.codePointBefore(textBefore, textBefore.length)
        val lastLen = Character.charCount(lastCp)
        if (isCombining(lastCp) && !isVirama(lastCp, scriptData) && !isNukta(lastCp, scriptData)) return lastLen
        return maxOf(getTrailingAkshara(textBefore, scriptData).length, lastLen)
    }
}
