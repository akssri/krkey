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

private val SIDDHAM_MAP = mapOf('अ' to "𑖀", 'आ' to "𑖁", 'इ' to "𑖂", 'ई' to "𑖃", 'उ' to "𑖄", 'ऊ' to "𑖅", 'ऋ' to "𑖆", 'ॠ' to "𑖇", 'ऌ' to "𑖈", 'ॡ' to "𑖉", 'ए' to "𑖊", 'ऐ' to "𑖋", 'ओ' to "𑖌", 'औ' to "𑖍", 'क' to "𑖎", 'ख' to "𑖏", 'ग' to "𑖐", 'घ' to "𑖑", 'ङ' to "𑖒", 'च' to "𑖓", 'छ' to "𑖔", 'ज' to "𑖕", 'झ' to "𑖖", 'ञ' to "𑖗", 'ट' to "𑖘", 'ठ' to "𑖙", 'ड' to "𑖚", 'ढ' to "𑖛", 'ण' to "𑖜", 'त' to "𑖝", 'थ' to "𑖞", 'द' to "𑖟", 'ध' to "𑖠", 'न' to "𑖡", 'प' to "𑖢", 'फ' to "𑖣", 'ब' to "𑖤", 'भ' to "𑖥", 'म' to "𑖦", 'य' to "𑖧", 'र' to "𑖨", 'ल' to "𑖩", 'व' to "𑖪", 'श' to "𑖫", 'ष' to "𑖬", 'स' to "𑖭", 'ह' to "𑖮", 'ा' to "𑖯", 'ि' to "𑖰", 'ी' to "𑖱", 'ु' to "𑖲", 'ू' to "𑖳", 'ृ' to "𑖴", 'ॄ' to "𑖵", 'ॢ' to "𑖶", 'ॣ' to "𑖷", 'े' to "𑖸", 'ै' to "𑖹", 'ो' to "𑖺", 'ौ' to "𑖻", 'ं' to "𑖽", 'ः' to "𑖾", '्' to "𑖿", '़' to "𑖼", 'ॐ' to "𑗁", '।' to "𑗂", '॥' to "𑗃")

fun String.toBrahmiScript(targetScript: BrahmiScript): String {
    if (targetScript == BrahmiScript.NAGARI) return this
    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        val cp = this.codePointAt(i)
        val char = cp.toChar()
        if (targetScript == BrahmiScript.SIDDHAM && SIDDHAM_MAP.containsKey(char)) {
            sb.append(SIDDHAM_MAP[char])
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
