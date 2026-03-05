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
    MALAYALAM("Malayalam", "മലയാളം", "Malayāḷam", "മ"),
    TAMIL("Tamil", "தமிழ்", "Tamiḻ", "த"),
    TELUGU("Telugu", "తెలుగు", "Telugu", "తె"),
    GRANTHA("Grantha", "𑌗𑍍𑌰𑌨𑍍𑌥", "Grantha", "𑌗", true),
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

val INDIC_GRID =
    listOf(
        listOf("ओ" to "ऒ", "क" to "ख", "ब" to "भ", "ड" to "ढ", "ट" to "ठ", "ऋ" to "ॠ", "ह" to "ङ", "ग" to "घ", "द" to "ध", "ज" to "झ"),
        listOf("उ" to "ऊ", "ए" to "ऎ", "अ" to "आ", "इ" to "ई", "य" to "ळ", "प" to "फ", "र" to "ष", "व" to "ल", "त" to "थ"),
        listOf(SpecialKey.SHIFT, "ऐ" to "औ", "ं" to "ँ", "म" to "ण", "न" to "ञ", "ः" to "ऽ", "च" to "छ", "स" to "श", SpecialKey.BACKSPACE),
        listOf(SpecialKey.SYMBOL, "," to "'", SpecialKey.GLOBE, SpecialKey.SPACE, "।" to "?", SpecialKey.ENTER),
    )

val LATIN_GRID =
    listOf(
        listOf("q" to "Q", "w" to "W", "e" to "E", "r" to "R", "t" to "T", "y" to "Y", "u" to "U", "i" to "I", "o" to "O", "p" to "P"),
        listOf("a" to "A", "s" to "S", "d" to "D", "f" to "F", "g" to "G", "h" to "H", "j" to "J", "k" to "K", "l" to "L"),
        listOf(SpecialKey.SHIFT, "z" to "Z", "x" to "X", "c" to "C", "v" to "V", "b" to "B", "n" to "N", "m" to "M", SpecialKey.BACKSPACE),
        listOf(SpecialKey.SYMBOL, "," to "'", SpecialKey.GLOBE, SpecialKey.SPACE, "." to "?", SpecialKey.ENTER),
    )

val SYM_GRID =
    listOf(
        listOf("१" to "1", "२" to "2", "३" to "3", "४" to "4", "५" to "5", "६" to "6", "७" to "7", "८" to "8", "९" to "9", "०" to "0"),
        listOf("*" to "`", "#" to "^", "+" to "|", "-" to "_", "=" to "§", "(" to "{", ")" to "}", "@" to "%", ";" to ":"),
        listOf(SpecialKey.SHIFT, "ऌ" to "ॡ", "़" to "ॐ", "\\" to "/", "'" to "\"", "[" to "&", "]" to "~", "₹" to "$", SpecialKey.BACKSPACE),
        listOf(SpecialKey.SYMBOL, "," to "\"", SpecialKey.GLOBE, SpecialKey.SPACE, "." to "!", SpecialKey.ENTER),
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
        listOf("ஒ" to "ஓ", "க" to "", "ப" to "", "ட" to "", "த" to "", "" to "", "ஹ" to "ங", "" to "", "" to "", "ஜ" to ""),
        listOf("உ" to "ஊ", "எ" to "ஏ", "அ" to "ஆ", "இ" to "ஈ", "ய" to "ள", "ப" to "", "ர" to "ஷ", "வ" to "ல", "த" to ""),
        listOf(SpecialKey.SHIFT, "ஐ" to "ஔ", "ஂ" to "", "ம" to "ண", "ந" to "ஞ", "" to "", "ச" to "", "ஸ" to "ஶ", SpecialKey.BACKSPACE),
        listOf(SpecialKey.SYMBOL, "," to "'", SpecialKey.GLOBE, SpecialKey.SPACE, "." to "?", SpecialKey.ENTER),
    )

// -------------------------------------------------------------------
// SCRIPT DATA
// -------------------------------------------------------------------

data class ScriptData(
    val script: BrahmiScript,
    val consonants: Set<String>,
    val modifiers: Set<String>,
    val vowelToMatraMap: Map<String, String>,
    val layers: Map<Int, List<List<Any>>>,
) {
    fun layoutFor(mode: com.akssri.krkey.state.InputMode): List<List<Any>> {
        return layers[mode.layer] ?: layers[KeyboardLayer.INDIC]!!
    }
}

private val DEVA_VOWELS = listOf("अ", "आ", "इ", "ई", "उ", "ऊ", "ऋ", "ॠ", "ऌ", "ॡ", "ए", "ऐ", "ऎ", "ओ", "औ", "ऒ")
private val DEVA_MATRAS = listOf("्", "ा", "ि", "ी", "ु", "ू", "ृ", "ॄ", "ॢ", "ॣ", "े", "ै", "ॆ", "ो", "ौ", "ॊ")
private val DEVA_CONSONANTS =
    setOf("क", "ख", "ग", "घ", "ङ", "च", "छ", "ज", "झ", "ञ", "ट", "ठ", "ड", "ढ", "ण", "त", "थ", "द", "ध", "न", "प", "फ", "ब", "भ", "म", "य", "र", "ल", "व", "श", "ष", "स", "ह", "ळ", "ऱ", "ऴ", "ऩ", "क़", "ख़", "ग़", "ज़", "ड़", "ढ", "फ़", "य़")
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
        val localConsonants = DEVA_CONSONANTS.map { it.toBrahmiScript(script) }.toSet()
        val localModifiers = DEVA_MODIFIERS.map { it.toBrahmiScript(script) }.toSet()

        val layers = mutableMapOf<Int, List<List<Any>>>()
        layers[KeyboardLayer.INDIC] = if (script == BrahmiScript.TAMIL) TAMIL_INDIC_GRID else transliterateGrid(INDIC_GRID, script)
        layers[KeyboardLayer.LATIN] = LATIN_GRID
        layers[KeyboardLayer.SYM] = transliterateGrid(SYM_GRID, script)
        layers[KeyboardLayer.SYM_SHIFT] = transliterateGrid(SYM2_GRID, script)

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
        return ScriptData(script, localConsonants, localModifiers, localVowelToMatra, filteredLayers)
    }

    private fun transliterateGrid(
        grid: List<List<Any>>,
        script: BrahmiScript,
    ): List<List<Any>> {
        return grid.map {
                row ->
            row.map {
                    item ->
                if (item is Pair<*, *>) (item.first as String).toBrahmiScript(script) to (item.second as String).toBrahmiScript(script) else item
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
    val isConsonantBase = currentBaseChar.isNotEmpty() && scriptData.consonants.contains(currentBaseChar)
    val matra = scriptData.vowelToMatraMap[raw]
    if (matra != null && isConsonantBase) return currentBaseChar + matra
    if (raw in scriptData.modifiers) {
        val prefix = if (currentBaseChar.isNotEmpty()) currentBaseChar else "◌"
        return prefix + raw
    }
    return raw
}
