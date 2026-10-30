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

import com.akssri.krkey.state.KeyboardLayer

enum class SpecialKey {
    SHIFT,
    BACKSPACE,
    SYMBOL,
    GLOBE,
    SPACE,
    ENTER,
}

enum class BrahmiScript(val scriptName: String, val nativeName: String, val iastName: String, val firstSyllable: String, val isExperimental: Boolean = false) {
    DEVANAGARI("Devanagari", "देवनागरी", "Devanāgarī", "दे"),
    KANNADA("Kannada", "ಕನ್ನಡ", "Kannaḍa", "ಕ"),
    TAMIL("Tamil", "தமிழ்", "Tamiḻ", "த"),
    GRANTHA("Grantha", "𑌗𑍍𑌰𑌨𑍍𑌥", "Grantha", "𑌗"),
    MALAYALAM("Malayalam", "മലയാളം", "Malayāḷam", "മ"),
    TELUGU("Telugu", "తెలుగు", "Telugu", "తె"),
    BENGALI("Bengali", "বাংলা", "Bāṅglā", "বা", true),
    GUJARATI("Gujarati", "ગુજરાતી", "Gujarātī", "ગુ", true),
    ORIYA("Oriya", "ଓଡ଼ିଆ", "Oṛiā", "ଓ", true),
    GURMUKHI("Gurmukhi", "ਗੁਰਮੁਖੀ", "Gurmukhī", "ਗੁ", true),
    SINHALA("Sinhala", "සිංහල", "Siṃhala", "සි", true),
    SHARADA("Sharada", "𑆯𑆳𑆫𑆢𑆳", "Śāradā", "𑆯"),
    BRAHMI("Brahmi", "𑀩𑁆𑀭𑀸𑀳𑁆𑀫𑀻", "Brāhmī", "𑀩", true),
    SIDDHAM("Siddham", "𑖭𑖰𑖟𑖿𑖠𑖦𑖿", "Siddham", "𑖭", true),
    BALINESE("Balinese", "ᬩᬮᬶ", "Bali", "ᬩ", true),
    SAURASHTRA("Saurashtra", "ꢱꣃꢬꢵꢰ꣄ꢜ꣄ꢬꢵ", "Saurashtra", "ꢱ", true),
    KAITHI("Kaithi", "𑂍𑂶𑂟𑂲", "Kaithi", "𑂍", true),
    KHUDAWADI(" Khudawadi", "𑊻𑋣𑋏𑋢𑋔𑋠𑋑𑋢", "Khudawadi", "𑊻", true),
    TULU_TIGALARI("Tulu-Tigalari", "𑒞𑒳𑒪𑒳", "Tulu-Tigalari", "𑒞", true),
    NEWA("Newa", "𑐣𑐾𑐰𑐵", "Newa", "𑐣", true),
    TIRHUTA("Tirhuta", "𑒞𑒱𑒩𑒯𑒳𑒞𑒰", "Tirhuta", "𑒞", true),
    MODI("Modi", "𑘦𑘻𑘚𑘲", "Modi", "𑘦", true),
    TAKRI("Takri", "𑚔𑚭𑚊𑚤𑚯", "Takri", "𑚔", true),
    DOGRA("Dogra", "𑠖𑠵𑠌𑠤𑠭", "Dogra", "𑠖", true),
    NANDINAGARI("Nandinagari", "𑦾𑧞𑧑𑧁𑧕𑧈𑧞𑧒", "Nandinagari", "𑦾", true),
    BHAIKSUKI("Bhaiksuki", "𑰥𑰺𑰎𑰿𑰬𑰲𑰎𑰱", "Bhaiksuki", "𑰥", true),
    MASARAM_GONDI("Masaram Gondi", "Masaram Gondi", "Masaram Gondi", "M", true),
    GUNJALA_GONDI("Gunjala Gondi", "Gunjala Gondi", "Gunjala Gondi", "G", true),
    KAWI("Kawi", "Kawi", "Kawi", "K", true),
    GURUNG_KHEMA("Gurung Khema", "Gurung Khema", "Gurung Khema", "G", true),
    KIRAT_RAI("Kirat Rai", "Kirat Rai", "Kirat Rai", "K", true),
    AHOM("Ahom", "Ahom", "Ahom", "A", true),
}

internal fun buildMap(
    src: String,
    dst: String,
): Map<Char, String> {
    val srcList: List<Char> = src.toCharArray().toList()
    val dstList: List<String> =
        mutableListOf<String>().apply {
            var i = 0
            while (i < dst.length) {
                val cp = dst.codePointAt(i)
                add(String(Character.toChars(cp)))
                i += Character.charCount(cp)
            }
        }
    return srcList.zip(dstList).toMap()
}

fun BrahmiScript.getAvailableDictionaries(): List<Pair<String, String>> {
    val sanskrit = "sa_dict.txt" to "संस्कृत".toBrahmiScript(this) + " (Sanskrit)"
    val localDicts =
        when (this) {
            BrahmiScript.DEVANAGARI -> listOf("hi_dict.txt" to "हिन्दी (Hindi)", "mr_dict.txt" to "मराठी (Marathi)")
            BrahmiScript.KANNADA -> listOf("kn_dict.txt" to "ಕನ್ನಡ (Kannada)")
            BrahmiScript.MALAYALAM -> listOf("ml_dict.txt" to "മലയാളം (Malayalam)")
            BrahmiScript.TAMIL -> listOf("ta_dict.txt" to "தமிழ் (Tamil)")
            BrahmiScript.TELUGU -> listOf("te_dict.txt" to "ತೆಲುಗು (Telugu)")
            BrahmiScript.BENGALI -> listOf("bn_dict.txt" to "বাংলা (Bengali)", "as_dict.txt" to "অসমীয়া (Assamese)")
            BrahmiScript.GUJARATI -> listOf("gu_dict.txt" to "ગુજરાતી (Gujarati)")
            BrahmiScript.ORIYA -> listOf("or_dict.txt" to "ଓଡ଼ିଆ (Oṛiā)")
            BrahmiScript.GURMUKHI -> listOf("pa_dict.txt" to "ਪੰਜਾਬੀ (Punjabi)")
            BrahmiScript.SINHALA -> listOf("si_dict.txt" to "සිංහල (Sinhala)")
            BrahmiScript.SHARADA -> listOf("ks_dict_devanagari.txt" to "𑆑𑆳𑆯𑆴𑆫𑆵 (Kashmiri)")
            else -> emptyList()
        }
    return listOf(sanskrit) + localDicts
}

fun BrahmiScript.getDefaultDictionaries(): Set<String> {
    val defaults = mutableSetOf("sa_dict.txt")
    when (this) {
        BrahmiScript.DEVANAGARI -> defaults.add("hi_dict.txt")
        BrahmiScript.KANNADA -> defaults.add("kn_dict.txt")
        BrahmiScript.MALAYALAM -> defaults.add("ml_dict.txt")
        BrahmiScript.TAMIL -> defaults.add("ta_dict.txt")
        BrahmiScript.TELUGU -> defaults.add("te_dict.txt")
        BrahmiScript.BENGALI -> defaults.add("bn_dict.txt")
        BrahmiScript.GUJARATI -> defaults.add("gu_dict.txt")
        BrahmiScript.ORIYA -> defaults.add("or_dict.txt")
        BrahmiScript.GURMUKHI -> defaults.add("pa_dict.txt")
        BrahmiScript.SINHALA -> defaults.add("si_dict.txt")
        BrahmiScript.SHARADA -> defaults.add("ks_dict_devanagari.txt")
        else -> {}
    }
    return defaults
}

fun String.toBrahmiScript(targetScript: BrahmiScript): String {
    if (targetScript == BrahmiScript.DEVANAGARI) return this
    val map = SCRIPT_MAPS[targetScript] ?: return this
    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        val cp = this.codePointAt(i)
        val char = cp.toChar()
        if (map.containsKey(char)) {
            sb.append(map[char])
        } else {
            if (char == 'ॐ') {
                sb.append(
                    when (targetScript) {
                        BrahmiScript.TAMIL -> "ௐ"
                        BrahmiScript.GUJARATI -> "ૐ"
                        else -> "ॐ"
                    },
                )
            } else if (char == '।' || char == '॥') {
                sb.append(
                    when (targetScript) {
                        BrahmiScript.KANNADA, BrahmiScript.TELUGU, BrahmiScript.MALAYALAM,
                        BrahmiScript.TAMIL, BrahmiScript.GUJARATI, BrahmiScript.SINHALA,
                        -> "."
                        else -> char.toString()
                    },
                )
            } else {
                sb.appendCodePoint(cp)
            }
        }
        i += Character.charCount(cp)
    }
    return sb.toString()
}

// -------------------------------------------------------------------
// LAYOUT DEFINITIONS (INDEPENDENT GRIDS)
// -------------------------------------------------------------------

/**
 * A key with flicks in four directions (only for the bottom-row punctuation keys; other keys are
 * `base to up` pairs). An empty direction falls back to the base character.
 */
data class FlickKey(
    val base: String,
    val up: String,
    val left: String = "",
    val right: String = "",
    val down: String = "",
)

// No down flicks: on the bottom row they are hard to hit (a down flick gives the base)
val COMMA_KEY = FlickKey(",", up = "'", left = "-", right = "\"")
val DANDA_KEY = FlickKey("\u0964", up = "?", left = "!", right = "-")
val PERIOD_KEY = FlickKey(".", up = "?", left = "!", right = "-")

val INDIC_GRID =
    listOf(
        listOf("ओ" to "ऒ", "क" to "ख", "ब" to "भ", "ड" to "ढ", "ट" to "ठ", "ऋ" to "ॠ", "ह" to "ङ", "ग" to "घ", "द" to "ध", "ज" to "झ"),
        listOf("उ" to "ऊ", "ए" to "ऎ", "अ" to "आ", "इ" to "ई", "य" to "ळ", "प" to "फ", "र" to "ष", "व" to "ल", "त" to "थ"),
        listOf(SpecialKey.SHIFT, "ऐ" to "औ", "ं" to "ँ", "म" to "ण", "न" to "ञ", "ः" to "ऽ", "च" to "छ", "स" to "श", SpecialKey.BACKSPACE),
        listOf(SpecialKey.SYMBOL, COMMA_KEY, SpecialKey.GLOBE, SpecialKey.SPACE, DANDA_KEY, SpecialKey.ENTER),
    )

val LATIN_GRID =
    listOf(
        listOf("q" to "Q", "w" to "W", "e" to "E", "r" to "R", "t" to "T", "y" to "Y", "u" to "U", "i" to "I", "o" to "O", "p" to "P"),
        listOf("a" to "A", "s" to "S", "d" to "D", "f" to "F", "g" to "G", "h" to "H", "j" to "J", "k" to "K", "l" to "L"),
        listOf(SpecialKey.SHIFT, "z" to "Z", "x" to "X", "c" to "C", "v" to "V", "b" to "B", "n" to "N", "m" to "M", SpecialKey.BACKSPACE),
        listOf(SpecialKey.SYMBOL, COMMA_KEY, SpecialKey.GLOBE, SpecialKey.SPACE, PERIOD_KEY, SpecialKey.ENTER),
    )

val SYM_GRID =
    listOf(
        listOf("१" to "1", "२" to "2", "३" to "3", "४" to "4", "५" to "5", "६" to "6", "७" to "7", "८" to "8", "९" to "9", "०" to "0"),
        listOf("*" to "`", "#" to "^", "+" to "|", "-" to "_", "=" to "§", "(" to "{", ")" to "}", "@" to "%", ";" to ":"),
        listOf(SpecialKey.SHIFT, "ऌ" to "ॡ", "़" to "ॐ", "/" to "\\", "[" to "&", "]" to "~", "₹" to "$", SpecialKey.BACKSPACE),
        listOf(SpecialKey.SYMBOL, "," to "\"", SpecialKey.GLOBE, SpecialKey.SPACE, "." to "!", SpecialKey.ENTER),
    )

// Numeric keypads for number/date and phone fields, after FUTO's number_basic and phone layouts
// (a 3x3 digit block with "-", space and backspace on the right). There is no key to leave the
// keypad: the full keyboard comes back when a text field is focused.
//
// Number/date: tap gives ASCII digits, which every field accepts; flicking up gives the script's
// own digit (transliterated per script). Date/time separators are on flicks.
val NUMBER_GRID =
    listOf(
        listOf("1" to "१", "2" to "२", "3" to "३", "-" to "/"),
        listOf("4" to "४", "5" to "५", "6" to "६", SpecialKey.SPACE),
        listOf("7" to "७", "8" to "८", "9" to "९", SpecialKey.BACKSPACE),
        listOf("," to ":", "0" to "०", "." to "+", SpecialKey.ENTER),
    )

// Phone: FUTO's separate phone-symbols page is on flicks instead ("," = pause, ";" = wait), and
// its symbols-page key is a "*" key (flick "#"), the most used dialler symbols.
val PHONE_GRID =
    listOf(
        listOf("1" to "(", "2" to "/", "3" to ")", "-" to "-"),
        listOf("4" to "N", "5" to ",", "6" to ";", SpecialKey.SPACE),
        listOf("7" to "*", "8" to "#", "9" to "+", SpecialKey.BACKSPACE),
        listOf("*" to "#", "0" to "+", "." to ".", SpecialKey.ENTER),
    )

val SYM2_GRID =
    listOf(
        listOf("∂" to "¹", "∫" to "²", "ε" to "³", "ℝ" to "⁴", "⊕" to "⁵", "⊗" to "⁶", "∇" to "⁷", "ⅈ" to "⁸", "ω" to "⁹", "π" to "⁰"),
        listOf("α" to "∏", "σ" to "∑", "δ" to "⁺", "φ" to "⁻", "γ" to "⁼", "η" to "⁽", "±" to ")", "κ" to "∅", "λ" to "√"),
        listOf(SpecialKey.SHIFT, "ψ" to "∛", "χ" to "×", "∈" to "∉", "⊆" to "⊈", "β" to "⊂", "ν" to "⋃", "μ" to "⋂", SpecialKey.BACKSPACE),
        listOf(SpecialKey.SYMBOL, "," to "\"", SpecialKey.GLOBE, SpecialKey.SPACE, "." to "!", SpecialKey.ENTER),
    )

val TAMIL_INDIC_GRID =
    listOf(
        listOf("ஒ" to "ஓ", "ஹ" to "ஷ", "ய" to "ஜ", "ஸ" to "ஶ", "ள" to "ழ", "ட" to "ண", "ச" to "ஞ"),
        listOf("உ" to "ஊ", "எ" to "ஏ", "அ" to "ஆ", "இ" to "ஈ", "க" to "ங", "ர" to "ற", "த" to "ந"),
        listOf(SpecialKey.SHIFT, "ஐ" to "ஔ", "ஂ" to "ஃ", "வ" to "ல", "ன" to "ந", "ப" to "ம", SpecialKey.BACKSPACE),
        listOf(SpecialKey.SYMBOL, COMMA_KEY, SpecialKey.GLOBE, SpecialKey.SPACE, PERIOD_KEY, SpecialKey.ENTER),
    )

// -------------------------------------------------------------------
// SCRIPT DATA
// -------------------------------------------------------------------

data class ScriptData(
    val script: BrahmiScript,
    val consonants: Set<String>,
    val modifiers: Set<String>,
    val vowelToMatraMap: Map<String, String>,
    val prefixableChars: Set<String>,
    val layers: Map<Int, List<List<Any>>>,
    val virama: String = "",
    val nukta: String = "",
) {
    /** True if [s] ends in a consonant, optionally followed by a nukta. */
    fun endsWithConsonant(s: String): Boolean {
        if (s.isEmpty()) return false
        val last = s.codePointBefore(s.length)
        val lastStr = String(Character.toChars(last))
        if (lastStr in consonants) return true
        if (lastStr != nukta || s.length <= lastStr.length) return false
        val rest = s.dropLast(lastStr.length)
        return String(Character.toChars(rest.codePointBefore(rest.length))) in consonants
    }

    /**
     * What the key faces build on, from the text before the cursor: the trailing consonant
     * cluster (up to [MAX_CLUSTER] consonants joined by virama; a nukta belongs to its consonant,
     * and any consonant may carry one, as in izzat),
     * so vowel keys show the whole conjunct with its vowel sign, or a trailing vowel (for marks
     * like anusvara), or "" otherwise. A ZWNJ is not part of a cluster, so it breaks it.
     */
    fun keyBase(textBefore: String): String {
        val cps = textBefore.codePoints().toArray()
        if (cps.isEmpty()) return ""
        val str = { cp: Int -> String(Character.toChars(cp)) }

        // Start index of a consonant (with an optional nukta) ending just before [end], or -1
        fun consonantStart(end: Int): Int {
            var j = end - 1
            if (j < 0) return -1
            if (str(cps[j]) == nukta) j--
            return if (j >= 0 && str(cps[j]) in consonants) j else -1
        }

        val last = str(cps.last())
        var start = consonantStart(cps.size)
        if (start < 0) return if (last in prefixableChars && last !in consonants) last else ""
        var count = 1
        while (count < MAX_CLUSTER && start >= 2 && str(cps[start - 1]) == virama) {
            val prev = consonantStart(start - 1)
            if (prev < 0) break
            start = prev
            count++
        }
        return cps.copyOfRange(start, cps.size).joinToString("") { str(it) }
    }

    private companion object {
        const val MAX_CLUSTER = 4
    }

    fun layoutFor(mode: com.akssri.krkey.state.InputMode): List<List<Any>> {
        return layers[mode.layer] ?: layers[KeyboardLayer.INDIC]!!
    }
}

private val DEVA_VOWELS = listOf("अ", "आ", "इ", "ई", "उ", "ऊ", "ऋ", "ॠ", "ऌ", "ॡ", "ए", "ऐ", "ऎ", "ओ", "औ", "ऒ")
private val DEVA_MATRAS = listOf("्", "ा", "ि", "ी", "ु", "ू", "ृ", "ॄ", "ॢ", "ॣ", "े", "ै", "ॆ", "ो", "ौ", "ॊ")
private val DEVA_CONSONANTS =
    setOf("क", "ख", "ग", "घ", "ङ", "च", "छ", "ज", "झ", "ञ", "ट", "ठ", "ड", "ढ", "ण", "त", "थ", "द", "ध", "न", "प", "फ", "ब", "भ", "म", "य", "र", "ल", "व", "श", "ष", "स", "ह", "ळ", "ऱ", "ऴ", "ऩ", "क़", "ख़", "ग़", "ज़", "ड़", "\u095D", "फ़", "य़")
private val DEVA_MODIFIERS = setOf("ं", "ँ", "ः", "ऽ", "़", "्")

object ScriptManager {
    private val cache = mutableMapOf<BrahmiScript, ScriptData>()

    fun getScriptData(script: BrahmiScript): ScriptData {
        return cache.getOrPut(script) { buildScriptData(script) }
    }

    private fun buildScriptData(script: BrahmiScript): ScriptData {
        val localVowelToMatra =
            DEVA_VOWELS.zip(DEVA_MATRAS).associate {
                it.first.toBrahmiScript(script) to it.second.toBrahmiScript(script)
            }
        val localVowels = DEVA_VOWELS.map { it.toBrahmiScript(script) }.toSet()
        val localConsonants = DEVA_CONSONANTS.map { it.toBrahmiScript(script) }.toSet()
        val localModifiers = DEVA_MODIFIERS.map { it.toBrahmiScript(script) }.toSet()

        val prefixableChars = localConsonants + localVowels

        val layers = mutableMapOf<Int, List<List<Any>>>()

        var indicLayer = if (script == BrahmiScript.TAMIL) TAMIL_INDIC_GRID else transliterateGrid(INDIC_GRID, script)
        if (script == BrahmiScript.KANNADA) {
            val longO = "ओ".toBrahmiScript(script)
            val shortO = "ऒ".toBrahmiScript(script)
            val longE = "ए".toBrahmiScript(script)
            val shortE = "ऎ".toBrahmiScript(script)

            indicLayer =
                indicLayer.map { row ->
                    row.map { item ->
                        if (item is Pair<*, *> && item.first == longO && item.second == shortO) {
                            shortO to longO
                        } else if (item is Pair<*, *> && item.first == longE && item.second == shortE) {
                            shortE to longE
                        } else {
                            item
                        }
                    }
                }
        }

        if (script == BrahmiScript.DEVANAGARI) {
            // ए/ओ flick to ऐ/औ; the short e/o (rarely used in Devanagari) move to the old ऐ/औ key
            indicLayer =
                indicLayer.map { row ->
                    row.map { item ->
                        when (item) {
                            "ओ" to "ऒ" -> "ओ" to "औ"
                            "ए" to "ऎ" -> "ए" to "ऐ"
                            "ऐ" to "औ" -> "ऎ" to "ऒ"
                            else -> item
                        }
                    }
                }
        }

        layers[KeyboardLayer.INDIC] = indicLayer
        layers[KeyboardLayer.LATIN] = LATIN_GRID
        layers[KeyboardLayer.SYM] = transliterateGrid(SYM_GRID, script)
        layers[KeyboardLayer.SYM_SHIFT] = transliterateGrid(SYM2_GRID, script)
        layers[KeyboardLayer.NUMBER] = transliterateGrid(NUMBER_GRID, script)
        layers[KeyboardLayer.PHONE] = transliterateGrid(PHONE_GRID, script)

        val filteredLayers =
            layers.mapValues {
                it.value.map {
                        row ->
                    row.filter {
                            item ->
                        if (item is Pair<*, *>) (item.first as String).isNotEmpty() || (item.second as String).isNotEmpty() else true
                    }
                }
            }
        return ScriptData(
            script,
            localConsonants,
            localModifiers,
            localVowelToMatra,
            prefixableChars,
            filteredLayers,
            virama = "\u094D".toBrahmiScript(script),
            nukta = "\u093C".toBrahmiScript(script),
        )
    }

    private fun transliterateGrid(
        grid: List<List<Any>>,
        script: BrahmiScript,
    ): List<List<Any>> {
        return grid.map {
                row ->
            row.map {
                    item ->
                when (item) {
                    is Pair<*, *> -> (item.first as String).toBrahmiScript(script) to (item.second as String).toBrahmiScript(script)
                    is FlickKey -> {
                        // A direction that transliterates to the base (e.g. a double danda in
                        // scripts that use ".") is dropped
                        val base = item.base.toBrahmiScript(script)
                        val dir = { s: String -> s.toBrahmiScript(script).let { if (it == base) "" else it } }
                        FlickKey(base, dir(item.up), dir(item.left), dir(item.right), dir(item.down))
                    }
                    else -> item
                }
            }
        }
    }
}

fun formatKeyText(
    raw: String,
    currentBaseChar: String,
    scriptData: ScriptData,
): String {
    if (raw.isEmpty()) return ""
    // The base may be a whole conjunct (see ScriptData.keyBase); it takes vowel signs if it ends in a consonant
    val isConsonantBase = scriptData.endsWithConsonant(currentBaseChar)
    val matra = scriptData.vowelToMatraMap[raw]
    if (matra != null && isConsonantBase) return currentBaseChar + matra
    if (raw in scriptData.modifiers) {
        val prefix = if (currentBaseChar.isNotEmpty()) currentBaseChar else "◌"
        return prefix + raw
    }
    return raw
}
