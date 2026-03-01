package com.akssri.krkey

import android.graphics.Rect

enum class BrahmiScript(val scriptName: String, val nativeName: String, val iastName: String, val isExperimental: Boolean = false) {
    DEVANAGARI("Devanagari", "देवनागरी", "Devanāgarī"),
    KANNADA("Kannada", "ಕನ್ನಡ", "Kannaḍa"),    
    MALAYALAM("Malayalam", "മലയാളം", "Malayāḷam"),
    TAMIL("Tamil", "தமிழ்", "Tamiḻ"),
    TELUGU("Telugu", "తెలుగు", "Telugu"),
    GRANTHA("Grantha", "𑌗𑍍𑌰𑌨𑍍𑌥", "Grantha", true),
    BENGALI("Bengali", "বাংলা", "Bāṅglā", true),
    GUJARATI("Gujarati", "ગુજરાતી", "Gujarātī", true),
    ORIYA("Oriya", "ଓଡ଼ିଆ", "Oṛiā", true),
    GURMUKHI("Gurmukhi", "ਗੁਰਮੁਖੀ", "Gurmukhī", true),
    SINHALA("Sinhala", "සිංහල", "Siṃhala", true),
    SHARADA("Sharada", "𑆯𑆳𑆫𑆢𑆳", "Śāradā"),
    BRAHMI("Brahmi", "𑀩𑁆𑀭𑀸𑀳𑁆𑀫𑀻", "Brāhmī", true),
    SIDDHAM("Siddham", "𑖭𑖰𑖟𑖿𑖠𑖦𑖿", "Siddham", true),
    BALINESE("Balinese", "ᬩᬮᬶ", "Bali", true),
    SAURASHTRA("Saurashtra", "ꢱꣃꢬꢵꢰ꣄ꢜ꣄ꢬꢵ", "Saurashtra", true),
    KAITHI("Kaithi", "𑂍𑂶𑂟𑂲", "Kaithi", true),
    KHUDAWADI("Khudawadi", "𑊻𑋣𑋏𑋢𑋔𑋠𑋑𑋢", "Khudawadi", true),
    TULU_TIGALARI("Tulu-Tigalari", "𑒞𑒳𑒪𑒳", "Tulu-Tigalari", true),
    NEWA("Newa", "𑐣𑐾𑐰𑐵", "Newa", true),
    TIRHUTA("Tirhuta", "𑒞𑒱𑒩𑒯𑒳𑒞𑒰", "Tirhuta", true),
    MODI("Modi", "𑘦𑘻𑘚𑘲", "Modi", true),
    TAKRI("Takri", "𑚔𑚭𑚊𑚤𑚯", "Takri", true),
    DOGRA("Dogra", "𑠖𑠵𑠌𑠤𑠭", "Dogra", true),
    NANDINAGARI("Nandinagari", "𑦾𑧞𑧑𑧁𑧕𑧈𑧞𑧒", "Nandinagari", true),
    BHAIKSUKI("Bhaiksuki", "𑰥𑰺𑰎𑰿𑰬𑰲𑰎𑰱", "Bhaiksuki", true),
    MASARAM_GONDI("Masaram Gondi", "Masaram Gondi", "Masaram Gondi", true),
    GUNJALA_GONDI("Gunjala Gondi", "Gunjala Gondi", "Gunjala Gondi", true),
    KAWI("Kawi", "Kawi", "Kawi", true),
    GURUNG_KHEMA("Gurung Khema", "Gurung Khema", "Gurung Khema", true),
    KIRAT_RAI("Kirat Rai", "Kirat Rai", "Kirat Rai", true),
    AHOM("Ahom", "Ahom", "Ahom", true)
}

internal fun String.toCodePointList(): List<String> {
    val list = mutableListOf<String>()
    var i = 0
    while (i < this.length) {
        val cp = this.codePointAt(i)
        list.add(String(Character.toChars(cp)))
        i += Character.charCount(cp)
    }
    return list
}

internal fun buildMap(src: String, dst: String): Map<Char, String> {
    val srcList: List<Char> = src.toCharArray().toList()
    val dstList: List<String> = dst.toCodePointList()
    if (srcList.size != dstList.size) {
        throw IllegalArgumentException("Map strings must have same length! src="+srcList.size+" vs dst="+dstList.size)
    }
    return srcList.zip(dstList).toMap()
}

/**
 * Get available dictionaries for a script.
 */
fun BrahmiScript.getAvailableDictionaries(): List<Pair<String, String>> {
    val sanskrit = "sa_dict.txt" to "संस्कृत".toBrahmiScript(this) + " (Sanskrit)"
    val localDicts = when (this) {
        BrahmiScript.DEVANAGARI -> listOf("hi_dict.txt" to "हिन्दी (Hindi)", "mr_dict.txt" to "मराठी (Marathi)")
        BrahmiScript.KANNADA -> listOf("kn_dict.txt" to "ಕನ್ನಡ (Kannada)")
        BrahmiScript.MALAYALAM -> listOf("ml_dict.txt" to "മലയാളം (Malayalam)")
        BrahmiScript.TAMIL -> listOf("ta_dict.txt" to "தமிழ் (Tamil)")
        BrahmiScript.TELUGU -> listOf("te_dict.txt" to "తెలుగు (Telugu)")
        BrahmiScript.BENGALI -> listOf("bn_dict.txt" to "বাংলা (Bengali)", "as_dict.txt" to "অসমীয়া (Assamese)")
        BrahmiScript.GUJARATI -> listOf("gu_dict.txt" to "ગુજરાતી (Gujarati)")
        BrahmiScript.ORIYA -> listOf("or_dict.txt" to "ଓଡ଼ିଆ (Odia)")
        BrahmiScript.GURMUKHI -> listOf("pa_dict.txt" to "ਪੰਜਾਬੀ (Punjabi)")
        BrahmiScript.SINHALA -> listOf("si_dict.txt" to "සිංහල (Sinhala)")
        BrahmiScript.SHARADA -> listOf("ks_dict_devanagari.txt" to "𑆑𑆳𑆯𑆴𑆫𑆵 (Kashmiri)")
        else -> emptyList()
    }
    return listOf(sanskrit) + localDicts
}

/**
 * Get default dictionaries for a script.
 */
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
            // Apply custom fallbacks for scripts without full coverage or with quirks
            if (char == 'ॐ') {
                sb.append(when (targetScript) { BrahmiScript.TAMIL -> "ௐ"; BrahmiScript.GUJARATI -> "ૐ"; else -> "ॐ" })
            } else if (char == '।' || char == '॥') {
                sb.append(when (targetScript) { 
                    BrahmiScript.KANNADA, BrahmiScript.TELUGU, BrahmiScript.MALAYALAM, 
                    BrahmiScript.TAMIL, BrahmiScript.GUJARATI, BrahmiScript.SINHALA -> "." 
                    else -> char.toString() 
                })
            } else {
                sb.appendCodePoint(cp)
            }
        }
        i += Character.charCount(cp)
    }
    return sb.toString()
}

// -------------------------------------------------------------------
// LAYOUT & RESOLUTION ENGINE
// -------------------------------------------------------------------

data class KeyConfig(
    val id: Int,
    val base: String,
    val flick: String,
    val symBase: String? = null,
    val symFlick: String? = null,
    val sym2Base: String? = null,
    val sym2Flick: String? = null,
    val latinBase: String? = null,
    val latinFlick: String? = null,
    val latinSymBase: String? = null,
    val latinSymFlick: String? = null
) {
    fun getResolvedStrings(mode: com.akssri.krkey.state.InputMode, currentBaseChar: String, scriptData: ScriptData): Pair<String, String> {
        val isShifted = mode.isShifted()
        return when (mode) {
            is com.akssri.krkey.state.InputMode.IndicSymbol, is com.akssri.krkey.state.InputMode.IndicSymbolShifted -> {
                val bRaw = if (isShifted) (sym2Base ?: symBase ?: base) else (symBase ?: base)
                val fRaw = if (isShifted) (sym2Flick ?: symFlick ?: flick) else (symFlick ?: flick)
                Pair(formatIndic(bRaw, currentBaseChar, scriptData), formatIndic(fRaw, currentBaseChar, scriptData))
            }
            is com.akssri.krkey.state.InputMode.LatinSymbol -> {
                val bRaw = latinSymBase ?: symBase ?: base
                val fRaw = latinSymFlick ?: symFlick ?: flick
                Pair(formatIndic(bRaw, currentBaseChar, scriptData), formatIndic(fRaw, currentBaseChar, scriptData))
            }
            is com.akssri.krkey.state.InputMode.LatinNormal, is com.akssri.krkey.state.InputMode.LatinShifted -> {
                val b = latinBase ?: base
                val f = latinFlick ?: flick
                if (b.equals(f, ignoreCase = true)) {
                    if (isShifted) Pair(b.uppercase(), f.lowercase()) else Pair(b.lowercase(), f.uppercase())
                } else {
                    if (isShifted) Pair(b.uppercase(), f) else Pair(b.lowercase(), f)
                }
            }
            is com.akssri.krkey.state.InputMode.IndicNormal -> {
                Pair(formatIndic(base, currentBaseChar, scriptData), formatIndic(flick, currentBaseChar, scriptData))
            }
        }
    }

    private fun formatIndic(raw: String, currentBaseChar: String, scriptData: ScriptData): String {
        // Fallback for Om mapping
        if (raw == "ॐ") {
            return when (scriptData.script) {
                BrahmiScript.TAMIL -> "ௐ"
                BrahmiScript.GUJARATI -> "ૐ"
                else -> SCRIPT_MAPS[scriptData.script]?.get('ॐ') ?: "ॐ"
            }
        }
        
        val isConsonantBase = currentBaseChar.isNotEmpty() && 
            scriptData.consonants.contains(currentBaseChar)

        val matra = scriptData.vowelToMatraMap[raw]
        if (matra != null && isConsonantBase) {
            return currentBaseChar + matra
        }

        if (raw in scriptData.modifiers) {
            val prefix = if (currentBaseChar.isNotEmpty()) currentBaseChar else "◌"
            return prefix + raw
        }

        return raw
    }
}

data class ScriptData(
    val script: BrahmiScript,
    val vowels: List<String>,
    val matras: List<String>,      
    val consonants: Set<String>,
    val modifiers: Set<String>,
    val keyConfigs: Map<Int, KeyConfig>,
    val vowelToMatraMap: Map<String, String>,
    val layout: List<List<Int>>
)

private val DEVA_VOWELS = listOf("अ", "आ", "इ", "ई", "उ", "ऊ", " उ", "ऋ", "ॠ", "ऌ", "ॡ", "ए", "ऐ", "ऎ", "ओ", "औ", "ऒ")
private val DEVA_MATRAS = listOf("्", "ा", "ि", "ी", "ु", "ू", "ु", "ृ", "ॄ", "ॢ", "ॣ", "े", "ै", "ॆ", "ो", "ौ", "ॊ")
private val DEVA_CONSONANTS = setOf("क", "ख", "ग", "घ", "ङ", "च", "छ", "ज", "झ", "ञ", "ट", "ठ", "ड", "ढ", "ण", "त", "थ", "द", "ध", "न", "प", "फ", "ब", "भ", "म", "य", "र", "ल", "व", "श", "ष", "स", "ह", "ळ", "ऱ", "ऴ", "ऩ", "क़", "ख़", "ग़", "ज़", "ड़", "ढ़", "फ़", "य़")
private val DEVA_MODIFIERS = setOf("ं", "ँ", "ः", "ऽ", "़", "्")

private val baseKeyConfigs = listOf(
    KeyConfig(R.id.r1c1, "ओ", "ऒ", symBase = "१", symFlick = "1", sym2Base = "∂", sym2Flick = "¹", latinBase = "q", latinFlick = "Q", latinSymBase = "1", latinSymFlick = "१"),
    KeyConfig(R.id.r1c2, "क", "ख", symBase = "२", symFlick = "2", sym2Base = "∫", sym2Flick = "²", latinBase = "w", latinFlick = "W", latinSymBase = "2", latinSymFlick = "२"),
    KeyConfig(R.id.r1c3, "ब", "भ", symBase = "३", symFlick = "3", sym2Base = "ε", sym2Flick = "³", latinBase = "e", latinFlick = "E", latinSymBase = "3", latinSymFlick = "३"),
    KeyConfig(R.id.r1c4, "ड", "ढ", symBase = "४", symFlick = "4", sym2Base = "ℝ", sym2Flick = "⁴", latinBase = "r", latinFlick = "R", latinSymBase = "4", latinSymFlick = "४"),
    KeyConfig(R.id.r1c5, "ट", "ठ", symBase = "५", symFlick = "5", sym2Base = "⊕", sym2Flick = "⁵", latinBase = "t", latinFlick = "T", latinSymBase = "5", latinSymFlick = "५"),
    KeyConfig(R.id.r1c6, "ऋ", "ॠ", symBase = "६", symFlick = "6", sym2Base = "⊗", sym2Flick = "⁶", latinBase = "y", latinFlick = "Y", latinSymBase = "6", latinSymFlick = "६"),
    KeyConfig(R.id.r1c7, "ह", "ङ", symBase = "७", symFlick = "7", sym2Base = "∇", sym2Flick = "⁷", latinBase = "u", latinFlick = "U", latinSymBase = "7", latinSymFlick = "७"),
    KeyConfig(R.id.r1c8, "ग", "घ", symBase = "८", symFlick = "8", sym2Base = "ⅈ", sym2Flick = "⁸", latinBase = "i", latinFlick = "I", latinSymBase = "8", latinSymFlick = "८"),
    KeyConfig(R.id.r1c9, "द", "ध", symBase = "९", symFlick = "9", sym2Base = "ω", sym2Flick = "⁹", latinBase = "o", latinFlick = "O", latinSymBase = "9", latinSymFlick = "९"),
    KeyConfig(R.id.r1c10, "ज", "झ", symBase = "०", symFlick = "0", sym2Base = "π", sym2Flick = "⁰", latinBase = "p", latinFlick = "P", latinSymBase = "0", latinSymFlick = "०"),
    KeyConfig(R.id.r2c1, " उ", "ऊ", symBase = "*", symFlick = "`", sym2Base = "α", sym2Flick = "∏", latinBase = "a", latinFlick = "A"),
    KeyConfig(R.id.r2c2, "ए", "ऎ", symBase = "#", symFlick = "^", sym2Base = "σ", sym2Flick = "∑", latinBase = "s", latinFlick = "S"),
    KeyConfig(R.id.r2c3, "अ", "आ", symBase = "+", symFlick = "|", sym2Base = "δ", sym2Flick = "⁺", latinBase = "d", latinFlick = "D"),
    KeyConfig(R.id.r2c4, "इ", "ई", symBase = "-", symFlick = "_", sym2Base = "φ", sym2Flick = "⁻", latinBase = "f", latinFlick = "F"),
    KeyConfig(R.id.r2c5, "य", "ळ", symBase = "=", symFlick = "§", sym2Base = "γ", sym2Flick = "⁼", latinBase = "g", latinFlick = "G"),
    KeyConfig(R.id.r2c6, "प", "फ", symBase = "(", symFlick = "{", sym2Base = "η", sym2Flick = "⁽", latinBase = "h", latinFlick = "H"),
    KeyConfig(R.id.r2c7, "र", "ष", symBase = ")", symFlick = "}", sym2Base = "±", sym2Flick = "⁾", latinBase = "j", latinFlick = "J"),
    KeyConfig(R.id.r2c8, "व", "ल", symBase = "@", symFlick = "%", sym2Base = "κ", sym2Flick = "∅", latinBase = "k", latinFlick = "K"),
    KeyConfig(R.id.r2c9, "त", "थ", symBase = ";", symFlick = ":", sym2Base = "λ", sym2Flick = "√", latinBase = "l", latinFlick = "L"),
    KeyConfig(R.id.r3c2, "ऐ", "औ", symBase = "ऌ", symFlick = "ॡ", sym2Base = "ψ", sym2Flick = "∛", latinBase = "z", latinFlick = "Z"),
    KeyConfig(R.id.r3c3, "ं", "ँ", symBase = "़", symFlick = "ॐ", sym2Base = "χ", sym2Flick = "×", latinBase = "x", latinFlick = "X"),
    KeyConfig(R.id.r3c4, "म", "ण", symBase = "\\", symFlick = "/", sym2Base = "∈", sym2Flick = "∉", latinBase = "c", latinFlick = "C"),
    KeyConfig(R.id.r3c5, "न", "ञ", symBase = "'", symFlick = "\"", sym2Base = "⊆", sym2Flick = "⊈", latinBase = "v", latinFlick = "V"),
    KeyConfig(R.id.r3c6, "ः", "ऽ", symBase = "[", symFlick = "&", sym2Base = "β", sym2Flick = "⊂", latinBase = "b", latinFlick = "B"),
    KeyConfig(R.id.r3c7, "च", "छ", symBase = "]", symFlick = "~", sym2Base = "ν", sym2Flick = "⋃", latinBase = "n", latinFlick = "N"),
    KeyConfig(R.id.r3c8, "स", "श", symBase = "₹", symFlick = "$", sym2Base = "μ", sym2Flick = "⋂", latinBase = "m", latinFlick = "M"),
    KeyConfig(R.id.r4c2, ",", "'", symBase = ",", symFlick = "\"", sym2Base = ",", sym2Flick = "\"", latinBase = ",", latinFlick = "'"),
    KeyConfig(R.id.r4c4, "।", "?", symBase = ".", symFlick = "!", sym2Base = ".", sym2Flick = "!", latinBase = ".", latinFlick = "?")
)

val baseLayout = listOf(
    listOf(R.id.r1c1, R.id.r1c2, R.id.r1c3, R.id.r1c4, R.id.r1c5, R.id.r1c6, R.id.r1c7, R.id.r1c8, R.id.r1c9, R.id.r1c10),
    listOf(R.id.r2c1, R.id.r2c2, R.id.r2c3, R.id.r2c4, R.id.r2c5, R.id.r2c6, R.id.r2c7, R.id.r2c8, R.id.r2c9),
    listOf(R.id.key_shift, R.id.r3c2, R.id.r3c3, R.id.r3c4, R.id.r3c5, R.id.r3c6, R.id.r3c7, R.id.r3c8, R.id.key_backspace),
    listOf(R.id.key_sym, R.id.r4c2, R.id.key_globe, R.id.key_space, R.id.r4c4, R.id.key_enter)
)

object ScriptManager {
    private val cache = mutableMapOf<BrahmiScript, ScriptData>()

    init {
        val devaVowelToMatra = DEVA_VOWELS.zip(DEVA_MATRAS).toMap()
        cache[BrahmiScript.DEVANAGARI] = ScriptData(
            script = BrahmiScript.DEVANAGARI,
            vowels = DEVA_VOWELS,
            matras = DEVA_MATRAS,
            consonants = DEVA_CONSONANTS,
            modifiers = DEVA_MODIFIERS,
            keyConfigs = baseKeyConfigs.associateBy { it.id },
            vowelToMatraMap = devaVowelToMatra,
            layout = baseLayout
        )
    }

    fun getScriptData(script: BrahmiScript): ScriptData {
        return cache.getOrPut(script) { buildScriptData(script) }
    }

    private fun buildScriptData(script: BrahmiScript): ScriptData {
        val localVowels = DEVA_VOWELS.map { it.toBrahmiScript(script) }
        val localMatras = DEVA_MATRAS.map { it.toBrahmiScript(script) }
        val localConsonants = DEVA_CONSONANTS.map { it.toBrahmiScript(script) }.toSet()
        val localModifiers = DEVA_MODIFIERS.map { it.toBrahmiScript(script) }.toSet()
        val localVowelToMatra = localVowels.zip(localMatras).toMap()

        val translatedKeys = baseKeyConfigs.map { baseCfg ->
            baseCfg.copy(
                base = baseCfg.base.toBrahmiScript(script),
                flick = baseCfg.flick.toBrahmiScript(script),
                symBase = baseCfg.symBase?.toBrahmiScript(script),
                symFlick = baseCfg.symFlick?.toBrahmiScript(script),
                sym2Base = baseCfg.sym2Base?.toBrahmiScript(script),
                sym2Flick = baseCfg.sym2Flick?.toBrahmiScript(script)
            )
        }

        val finalKeys = applyLayoutOverrides(script, translatedKeys)
        
        // Dynamically strip out empty keys from the layout
        val validKeyIds = finalKeys.filter { it.base.isNotEmpty() || it.flick.isNotEmpty() }.map { it.id }.toSet()
        val specialKeys = setOf(R.id.key_shift, R.id.key_backspace, R.id.key_sym, R.id.key_globe, R.id.key_space, R.id.key_enter)
        val dynamicLayout = baseLayout.map { row ->
            row.filter { id -> specialKeys.contains(id) || validKeyIds.contains(id) }
        }

        return ScriptData(
            script = script,
            vowels = localVowels,
            matras = localMatras,
            consonants = localConsonants,
            modifiers = localModifiers,
            keyConfigs = finalKeys.associateBy { it.id },
            vowelToMatraMap = localVowelToMatra,
            layout = dynamicLayout
        )
    }

    private fun applyLayoutOverrides(script: BrahmiScript, keys: List<KeyConfig>): List<KeyConfig> {
        return when (script) {
            BrahmiScript.KANNADA, BrahmiScript.TELUGU, BrahmiScript.MALAYALAM, BrahmiScript.SINHALA -> {
                keys.map { k ->
                    when (k.id) {
                        R.id.r1c1 -> k.copy(base = k.flick, flick = k.base) // O / Short O
                        R.id.r2c2 -> k.copy(base = k.flick, flick = k.base) // E / Short E
                        else -> k
                    }
                }
            }
            BrahmiScript.TAMIL -> {
                keys.map { k ->
                    when (k.id) {
                        R.id.r1c1 -> k.copy(base = k.flick, flick = k.base) // O / Short O
                        R.id.r2c2 -> k.copy(base = k.flick, flick = k.base) // E / Short E
                        // Blank out missing consonants for Tamil
                        R.id.r1c2 -> k.copy(flick = "") // Ka is valid, Kha is missing
                        R.id.r1c3 -> k.copy(base = "", flick = "") // Ba, Bha missing (Tamil uses Pa)
                        R.id.r1c4 -> k.copy(base = "", flick = "") // Dda, Ddha missing
                        R.id.r1c8 -> k.copy(base = "", flick = "") // Ga, Gha missing
                        R.id.r1c9 -> k.copy(base = "", flick = "") // Da, Dha missing
                        R.id.r1c10 -> k.copy(flick = "") // Ja is there, Jha is missing
                        R.id.r2c6 -> k.copy(flick = "") // Pa is there, Pha is missing
                        R.id.r2c9 -> k.copy(flick = "") // Ta is there, Tha is missing
                        R.id.r3c7 -> k.copy(flick = "") // Ca is there, Cha is missing
                        R.id.r1c5 -> k.copy(flick = "") // Tta is there, Ttha is missing
                        R.id.r1c6 -> k.copy(base = "", flick = "") // Vocalic R missing
                        R.id.r3c2 -> k.copy(base = "ஐ", flick = "ஔ") // Ai, Au (Vocalic L missing)
                        else -> k
                    }
                }
            }
            else -> keys
        }
    }
}
