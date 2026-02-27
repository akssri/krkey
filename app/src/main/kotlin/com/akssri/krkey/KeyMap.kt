package com.akssri.krkey

import android.graphics.Rect
import com.akssri.krkey.keys.Key
import com.akssri.krkey.keys.KeyVariants

enum class KeyType { SIMPLE, VOWEL, MODIFIER, CONSONANT }

enum class BrahmiScript(val blockStart: Int, val scriptName: String, val nativeName: String, val iastName: String) {
    NAGARI(0x0900, "Nagari", "देवनागरी", "Devanāgarī"),
    KANNADA(0x0C80, "Kannada", "ಕನ್ನಡ", "Kannaḍa"),
    GRANTHA(0x11300, "Grantha", "𑌗𑍍𑌰𑌨𑍍𑌥", "Grantha"),
    MALAYALAM(0x0D00, "Malayalam", "മലയാളം", "Malayāḷam"),
    TAMIL(0x0B80, "Tamil", "தமிழ்", "Tamiḻ"),
    TELUGU(0x0C00, "Telugu", "తెలుగు", "Telugu"),
    BENGALI(0x0980, "Bengali", "বাংলা", "Bāṅglā"),
    GUJRATI(0x0A80, "Gujarati", "ગુજરાતી", "Gujarātī"),
    ORIYA(0x0B00, "Oriya", "ଓଡ଼ିଆ", "Oṛiā"),
    GURMUKHI(0x0A00, "Gurmukhi", "ਗੁਰਮੁਖੀ", "Gurmukhī"),
    SINHALA(0x0D80, "Sinhala", "සිංහල", "Siṃhala"),
    SHARADA(0x11180, "Sharada", "𑆯𑆳𑆫𑆢𑆳", "Śāradā"),
    BRAHMI(0x11000, "Brahmi", "𑀩𑁆𑀭𑀸𑀳𑁆𑀫𑀻", "Brāhmī"),
    SIDDHAM(0x11580, "Siddham", "𑖭𑖰𑖟𑖿𑖠𑖦𑖿", "Siddham")
}

private fun String.toCodePointList(): List<String> {
    val list = mutableListOf<String>()
    var i = 0
    while (i < this.length) {
        val cp = this.codePointAt(i)
        list.add(String(Character.toChars(cp)))
        i += Character.charCount(cp)
    }
    return list
}

data class KeyConfig(
    val id: Int,
    val type: KeyType,
    val base: String,
    val flick: String,
    val matraBase: String? = null,
    val matraFlick: String? = null,
    val symBase: String? = null,
    val symFlick: String? = null,
    val sym2Base: String? = null,
    val sym2Flick: String? = null,
    val symMatraBase: String? = null,
    val symMatraFlick: String? = null,
    val latinBase: String? = null,
    val latinFlick: String? = null,
    val latinSymBase: String? = null,
    val latinSymFlick: String? = null
) {
    fun getResolvedStrings(isLatin: Boolean, isSymbol: Boolean, isShifted: Boolean, isLatinSymbol: Boolean, currentBaseChar: String, script: BrahmiScript): Pair<String, String> {
        val vyanjanas = "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळ"
        val isConsonantBase = currentBaseChar.isNotEmpty() && vyanjanas.toBrahmiScript(script).contains(currentBaseChar)
        
        if (isSymbol) {
            var sB = if (isShifted) (sym2Base ?: symBase ?: base) else (symBase ?: base)
            var sF = if (isShifted) (sym2Flick ?: symFlick ?: flick) else (symFlick ?: flick)
            if (isLatinSymbol && !isShifted) {
                sB = latinSymBase ?: sB
                sF = latinSymFlick ?: sF
            }
            if (sB == "ॐ" || sF == "ॐ") return Pair(sB.toBrahmiScript(script), sF.toBrahmiScript(script))
            if (id == R.id.r3c2 && isConsonantBase) {
                val mB = if (isShifted) (symMatraBase ?: matraBase ?: base) else (symMatraBase ?: base)
                val mF = if (isShifted) (symMatraFlick ?: matraFlick ?: flick) else (symMatraFlick ?: flick)
                return Pair((currentBaseChar + mB).toBrahmiScript(script), (currentBaseChar + mF).toBrahmiScript(script))
            }
            // Prefixing only for explicit combining marks in symbol mode
            if ((sB.length == 1 && (sB[0] == '़' || sB[0] == '्')) || (sF.length == 1 && sF[0] == '़')) {
                val prefix = if (isConsonantBase) currentBaseChar else "◌"
                return Pair((prefix + sB).toBrahmiScript(script), (prefix + sF).toBrahmiScript(script))
            }
            return Pair(sB.toBrahmiScript(script), sF.toBrahmiScript(script))
        }
        
        if (isLatin) {
            val lB = latinBase ?: base
            val lF = latinFlick ?: flick
            return if (isShifted) {
                if (lB.equals(lF, ignoreCase = true)) Pair(lB.uppercase(), lF.lowercase()) else Pair(lB.uppercase(), lF)
            } else {
                if (lB.equals(lF, ignoreCase = true)) Pair(lB.lowercase(), lF.uppercase()) else Pair(lB.lowercase(), lF)
            }
        }
        
        return when (type) {
            KeyType.VOWEL -> {
                if (isConsonantBase) Pair((currentBaseChar + (matraBase ?: "")), (currentBaseChar + (matraFlick ?: "")))
                else Pair(base, flick)
            }
            KeyType.MODIFIER -> {
                val prefix = if (currentBaseChar.isNotEmpty()) currentBaseChar else "◌"
                Pair(prefix + base, prefix + flick)
            }
            else -> Pair(base, flick)
        }.let { (b, f) -> Pair(b.toBrahmiScript(script), f.toBrahmiScript(script)) }
    }
}


private fun buildMap(src: String, dst: String): Map<Char, String> {
    val srcList: List<Char> = src.toCharArray().toList()
    val dstList: List<String> = dst.toCodePointList()
    if (srcList.size != dstList.size) {
        throw IllegalArgumentException("Map strings must have same length! src="+srcList.size+" vs dst="+dstList.size)
    }
    return srcList.zip(dstList).toMap()
}

private val SHARADA_MAP = buildMap(
    "अआइईउऊऋॠऌॡएऐओऔ" +
    "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळ" +
    "ािीुूृॄॢॣेैोौ" +
    "ंँः़्ॐ।॥" +
    "०१२३४५६७८९",
    "𑆃𑆄𑆅𑆆𑆇𑆈𑆉𑆊𑆋𑆌𑆍𑆎𑆏𑆐" +
    "𑆑𑆒𑆓𑆔𑆕𑆖𑆗𑆘𑆙𑆚𑆛𑆜𑆝𑆞𑆟𑆠𑆡𑆢𑆣𑆤𑆥𑆦𑆧𑆨𑆩𑆪𑆫𑆬𑆮𑆯𑆰𑆱𑆲𑆭" +
    "𑆳𑆴𑆵𑆶𑆷𑆸𑆹𑆺𑆻𑆼𑆽𑆾𑆿" +
    "𑆁𑆀𑆂𑇀𑆀𑇄𑇂𑇃" +
    "𑇐𑇑𑇒𑇓𑇔𑇕𑇖𑇗𑇘𑇙"
)

private val SIDDHAM_MAP = buildMap(
    "अआइईउऊऋॠऌॡएऐओऔ" +
    "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसह" +
    "ािीुूृॄॢॣेैोौ" +
    "ंः़्ॐ।॥",
    "𑖀𑖁𑖂𑖃𑖄𑖅𑖆𑖇𑖈𑖉𑖊𑖋𑖌𑖍" +
    "𑖎𑖏𑖐𑖑𑖒𑖓𑖔𑖕𑖖𑖗𑖘𑖙𑖚𑖛𑖜𑖝𑖞𑖟𑖠𑖡𑖢𑖣𑖤𑖥𑖦𑖧𑖨𑖩𑖪𑖫𑖬𑖭𑖮" +
    "𑖯𑖰𑖱𑖲𑖳𑖴𑖵𑖶𑖷𑖸𑖹𑖺𑖻" +
    "𑖽𑖾𑖿𑖼𑗁𑗂𑗃"
)

private val BRAHMI_MAP = buildMap(
    "अआइईउऊऋॠऌॡएऐओऔ" +
    "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळ" +
    "ािीुूृॄॢॣेैोौ" +
    "ंः़्ॐ।॥" +
    "०१२३४५६७८९",
    "𑀅𑀆𑀇𑀈𑀉𑀊𑀋𑀌𑀍𑀎𑀏𑀐𑀑𑀒" +
    "𑀓𑀔𑀕𑀖𑀗𑀘𑀙𑀚𑀛𑀜𑀝𑀞𑀟𑀠𑀡𑀢𑀣𑀤𑀥𑀦𑀧𑀨𑀩𑀪𑀫𑀬𑀭𑀮𑀯𑀰𑀱𑀲𑀳𑀴" +
    "𑀸𑀹𑀺𑀻𑀼𑀽𑀾𑀿𑁀𑁁𑁂𑁃𑁄" +
    "𑀁𑀂𑀵्ॐ𑁇𑁈" +
    "𑁦𑁧𑁨𑁩𑁪𑁫𑁬𑁭𑁮𑁯"
)

/**
 * Get available dictionaries for a script.
 * Returns list of (file, displayName) pairs.
 */
fun BrahmiScript.getAvailableDictionaries(): List<Pair<String, String>> {
    // Sanskrit transliterated to each script - always available
    val sanskrit = "sa_dict.txt" to "संस्कृत".toBrahmiScript(this) + " (Sanskrit)"

    // Get language-specific dictionaries for each script
    val localDicts = when (this) {
        BrahmiScript.NAGARI -> listOf(
            "hi_dict.txt" to "हिन्दी (Hindi)",
            "mr_dict.txt" to "मराठी (Marathi)"
        )
        BrahmiScript.KANNADA -> listOf(
            "kn_dict.txt" to "ಕನ್ನಡ (Kannada)"
        )
        BrahmiScript.MALAYALAM -> listOf(
            "ml_dict.txt" to "മലയാളം (Malayalam)"
        )
        BrahmiScript.TAMIL -> listOf(
            "ta_dict.txt" to "தமிழ் (Tamil)"
        )
        BrahmiScript.TELUGU -> listOf(
            "te_dict.txt" to "తెలుగు (Telugu)"
        )
        BrahmiScript.BENGALI -> listOf(
            "bn_dict.txt" to "বাংলা (Bengali)",
            "as_dict.txt" to "অসমীয়া (Assamese)"
        )
        BrahmiScript.GUJRATI -> listOf(
            "gu_dict.txt" to "ગુજરાતી (Gujarati)"
        )
        BrahmiScript.ORIYA -> listOf(
            "or_dict.txt" to "ଓଡ଼ିଆ (Odia)"
        )
        BrahmiScript.GURMUKHI -> listOf(
            "pa_dict.txt" to "ਪੰਜਾਬੀ (Punjabi)"
        )
        BrahmiScript.SINHALA -> listOf(
            "si_dict.txt" to "සිංහල (Sinhala)"
        )
        BrahmiScript.SHARADA -> listOf(
            "ks_dict_devanagari.txt" to "𑆑𑆳𑆯𑆴𑆫𑆵 (Kashmiri)"
        )
        else -> emptyList()
    }

    // Return Sanskrit first, followed by all local language dictionaries
    return listOf(sanskrit) + localDicts
}

/**
 * Get default dictionaries for a script.
 * Returns set of dictionary files enabled by default.
 */
fun BrahmiScript.getDefaultDictionaries(): Set<String> {
    // Sanskrit is always default for all scripts
    val defaults = mutableSetOf("sa_dict.txt")

    // Also enable primary local language dictionary by default
    when (this) {
        BrahmiScript.NAGARI -> {
            defaults.add("hi_dict.txt")  // Hindi is primary
            // Marathi available but not default
        }
        BrahmiScript.KANNADA -> defaults.add("kn_dict.txt")
        BrahmiScript.MALAYALAM -> defaults.add("ml_dict.txt")
        BrahmiScript.TAMIL -> defaults.add("ta_dict.txt")
        BrahmiScript.TELUGU -> defaults.add("te_dict.txt")
        BrahmiScript.BENGALI -> {
            defaults.add("bn_dict.txt")  // Bengali is primary
            // Assamese available but not default
        }
        BrahmiScript.GUJRATI -> defaults.add("gu_dict.txt")
        BrahmiScript.ORIYA -> defaults.add("or_dict.txt")
        BrahmiScript.GURMUKHI -> defaults.add("pa_dict.txt")
        BrahmiScript.SINHALA -> defaults.add("si_dict.txt")
        BrahmiScript.SHARADA -> defaults.add("ks_dict_devanagari.txt")
        else -> {} // Sanskrit only for scripts without local dictionaries
    }

    return defaults
}

fun String.toBrahmiScript(targetScript: BrahmiScript): String {
    if (targetScript == BrahmiScript.NAGARI) return this
    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        val cp = this.codePointAt(i)
        val char = cp.toChar()
        if (targetScript == BrahmiScript.SIDDHAM && SIDDHAM_MAP.containsKey(char)) {
            sb.append(SIDDHAM_MAP[char])
        } else if (targetScript == BrahmiScript.SHARADA && SHARADA_MAP.containsKey(char)) {
            sb.append(SHARADA_MAP[char])
        } else {
            val offset = cp - 0x0900
            if (offset in 0..0x7F) {
                if (offset == 0x50) sb.append(when (targetScript) { BrahmiScript.TAMIL -> "ௐ"; BrahmiScript.GUJRATI -> "ૐ"; else -> String(Character.toChars(cp)) })
                else if (offset == 0x64 || offset == 0x65) sb.append(when (targetScript) { BrahmiScript.KANNADA, BrahmiScript.TELUGU, BrahmiScript.MALAYALAM, BrahmiScript.TAMIL, BrahmiScript.GUJRATI, BrahmiScript.SINHALA -> "."; else -> String(Character.toChars(cp)) })
                else sb.appendCodePoint(targetScript.blockStart + offset)
            } else sb.appendCodePoint(cp)
        }
        i += Character.charCount(cp)
    }
    return sb.toString()
}

val keyConfigs = listOf(
    KeyConfig(R.id.r1c1, KeyType.VOWEL, "ओ", "ऒ", "ो", "ॊ", symBase = "१", symFlick = "1", latinBase = "q", latinFlick = "Q", latinSymBase = "1", latinSymFlick = "१"),
    KeyConfig(R.id.r1c2, KeyType.CONSONANT, "क", "ख", symBase = "२", symFlick = "2", sym2Base = "∫", sym2Flick = "²", latinBase = "w", latinFlick = "W", latinSymBase = "2", latinSymFlick = "२"),
    KeyConfig(R.id.r1c3, KeyType.CONSONANT, "ब", "भ", symBase = "३", symFlick = "3", sym2Base = "ε", sym2Flick = "³", latinBase = "e", latinFlick = "E", latinSymBase = "3", latinSymFlick = "३"),
    KeyConfig(R.id.r1c4, KeyType.CONSONANT, "ड", "ढ", symBase = "४", symFlick = "4", sym2Base = "ℝ", sym2Flick = "⁴", latinBase = "r", latinFlick = "R", latinSymBase = "4", latinSymFlick = "४"),
    KeyConfig(R.id.r1c5, KeyType.CONSONANT, "ट", "ठ", symBase = "५", symFlick = "5", sym2Base = "⊕", sym2Flick = "⁵", latinBase = "t", latinFlick = "T", latinSymBase = "5", latinSymFlick = "५"),
    KeyConfig(R.id.r1c6, KeyType.VOWEL, "ऋ", "ॠ", "ृ", "ॄ", symBase = "६", symFlick = "6", sym2Base = "⊗", sym2Flick = "⁶", latinBase = "y", latinFlick = "Y", latinSymBase = "6", latinSymFlick = "६"),
    KeyConfig(R.id.r1c7, KeyType.CONSONANT, "ह", "ङ", symBase = "७", symFlick = "7", sym2Base = "∇", sym2Flick = "⁷", latinBase = "u", latinFlick = "U", latinSymBase = "7", latinSymFlick = "७"),
    KeyConfig(R.id.r1c8, KeyType.CONSONANT, "ग", "घ", symBase = "८", symFlick = "8", sym2Base = "ⅈ", sym2Flick = "⁸", latinBase = "i", latinFlick = "I", latinSymBase = "8", latinSymFlick = "८"),
    KeyConfig(R.id.r1c9, KeyType.CONSONANT, "द", "ध", symBase = "९", symFlick = "9", sym2Base = "ω", sym2Flick = "⁹", latinBase = "o", latinFlick = "O", latinSymBase = "9", latinSymFlick = "९"),
    KeyConfig(R.id.r1c10, KeyType.CONSONANT, "ज", "झ", symBase = "०", symFlick = "0", sym2Base = "π", sym2Flick = "⁰", latinBase = "p", latinFlick = "P", latinSymBase = "0", latinSymFlick = "०"),
    KeyConfig(R.id.r2c1, KeyType.VOWEL, " उ", "ऊ", "ु", "ू", symBase = "*", symFlick = "`", sym2Base = "α", sym2Flick = "∏", latinBase = "a", latinFlick = "A"),
    KeyConfig(R.id.r2c2, KeyType.VOWEL, "ए", "ऎ", "े", "ॆ", symBase = "#", symFlick = "^", sym2Base = "σ", sym2Flick = "∑", latinBase = "s", latinFlick = "S"),
    KeyConfig(R.id.r2c3, KeyType.VOWEL, "अ", "आ", "्", "ा", symBase = "+", symFlick = "|", sym2Base = "δ", sym2Flick = "⁺", latinBase = "d", latinFlick = "D"),
    KeyConfig(R.id.r2c4, KeyType.VOWEL, "इ", "ई", "ि", "ी", symBase = "-", symFlick = "_", sym2Base = "φ", sym2Flick = "⁻", latinBase = "f", latinFlick = "F"),
    KeyConfig(R.id.r2c5, KeyType.CONSONANT, "य", "ळ", symBase = "=", symFlick = "§", sym2Base = "γ", sym2Flick = "⁼", latinBase = "g", latinFlick = "G"),
    KeyConfig(R.id.r2c6, KeyType.CONSONANT, "प", "फ", symBase = "(", symFlick = "{", sym2Base = "η", sym2Flick = "⁽", latinBase = "h", latinFlick = "H"),
    KeyConfig(R.id.r2c7, KeyType.CONSONANT, "र", "ष", symBase = ")", symFlick = "}", sym2Base = "±", sym2Flick = "⁾", latinBase = "j", latinFlick = "J"),
    KeyConfig(R.id.r2c8, KeyType.CONSONANT, "व", "ल", symBase = "@", symFlick = "%", sym2Base = "κ", sym2Flick = "∅", latinBase = "k", latinFlick = "K"),
    KeyConfig(R.id.r2c9, KeyType.CONSONANT, "त", "थ", symBase = ";", symFlick = ":", sym2Base = "λ", sym2Flick = "√", latinBase = "l", latinFlick = "L"),
    KeyConfig(R.id.r3c2, KeyType.VOWEL, "ऐ", "औ", "ै", "ौ", symBase = "ऌ", symFlick = "ॡ", symMatraBase = "ॢ", symMatraFlick = "ॣ", latinBase = "z", latinFlick = "Z"),
    KeyConfig(R.id.r3c3, KeyType.MODIFIER, "ं", "ँ", symBase = "़", symFlick = "ॐ", sym2Base = "χ", sym2Flick = "×", latinBase = "x", latinFlick = "X"),
    KeyConfig(R.id.r3c4, KeyType.CONSONANT, "म", "ण", symBase = "\\", symFlick = "/", sym2Base = "∈", sym2Flick = "∉", latinBase = "c", latinFlick = "C"),
    KeyConfig(R.id.r3c5, KeyType.CONSONANT, "न", "ञ", symBase = "'", symFlick = "\"", sym2Base = "⊆", sym2Flick = "⊈", latinBase = "v", latinFlick = "V"),
    KeyConfig(R.id.r3c6, KeyType.MODIFIER, "ः", "ऽ", symBase = "[", symFlick = "&", sym2Base = "β", sym2Flick = "⊂", latinBase = "b", latinFlick = "B"),
    KeyConfig(R.id.r3c7, KeyType.CONSONANT, "च", "छ", symBase = "]", symFlick = "~", sym2Base = "∂", sym2Flick = "⋃", latinBase = "n", latinFlick = "N"),
    KeyConfig(R.id.r3c8, KeyType.CONSONANT, "स", "श", symBase = "₹", symFlick = "$", sym2Base = "μ", sym2Flick = "⋂", latinBase = "m", latinFlick = "M"),
    KeyConfig(R.id.r4c2, KeyType.SIMPLE, ",", "'", symBase = ",", symFlick = "\"", sym2Base = ",", sym2Flick = "\"", latinBase = ",", latinFlick = "'"),
    KeyConfig(R.id.r4c4, KeyType.SIMPLE, "।", "?", symBase = ".", symFlick = "!", sym2Base = ".", sym2Flick = "!", latinBase = ".", latinFlick = "?")
)

val configMap: Map<Int, KeyConfig> = keyConfigs.associateBy { it.id }

// Phase 2: Migration to new Key architecture
/**
 * Convert old KeyConfig to new Key.
 * Backward compatibility layer.
 */
fun KeyConfig.toKey(): Key {
    val variants = KeyVariants(
        matraBase = this.matraBase,
        matraFlick = this.matraFlick,
        symBase = this.symBase,
        symFlick = this.symFlick,
        sym2Base = this.sym2Base,
        sym2Flick = this.sym2Flick,
        symMatraBase = this.symMatraBase,
        symMatraFlick = this.symMatraFlick,
        latinBase = this.latinBase,
        latinFlick = this.latinFlick,
        latinSymBase = this.latinSymBase,
        latinSymFlick = this.latinSymFlick
    )

    return when (this.type) {
        KeyType.SIMPLE -> Key.simple(id, base, flick, variants)
        KeyType.VOWEL -> Key.vowel(id, base, flick, variants)
        KeyType.MODIFIER -> Key.modifier(id, base, flick, variants)
        KeyType.CONSONANT -> Key.consonant(id, base, flick, variants)
    }
}

/**
 * New key map using Key architecture.
 * Use this for new code, old configMap remains for compatibility.
 */
val newKeyMap: Map<Int, Key> = keyConfigs.associate { it.id to it.toKey() }
