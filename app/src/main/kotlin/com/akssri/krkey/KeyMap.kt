package com.akssri.krkey

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

private fun buildMap(src: String, dst: String): Map<Char, String> {
    val srcList = src.toCharArray().toList()
    val dstList = dst.toCodePointList()
    if (srcList.size != dstList.size) {
        throw IllegalArgumentException("Map strings must have same length! src="+srcList.size+" vs dst="+dstList.size)
    }
    return srcList.zip(dstList).toMap()
}

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

private val SHARADA_MAP = buildMap(
    "अआइईउऊऋॠऌॡएऐओऔ" +
    "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळ" +
    "ािीुूृॄॢॣेैोौ" +
    "ंः़्ॐ।॥" +
    "०१२३४५६७८९",
    "𑆃𑆄𑆅𑆆𑆇𑆈𑆉𑆊𑆋𑆌𑆍𑆎𑆏𑆐" +
    "𑆑𑆒𑆓𑆔𑆕𑆖𑆗𑆘𑆙𑆚𑆛𑆜𑆝𑆞𑆟𑆠𑆡𑆢𑆣𑆤𑆥𑆦𑆧𑆨𑆩𑆪𑆫𑆬𑆮𑆯𑆰𑆱𑆲𑆭" +
    "𑆳𑆴𑆵𑆶𑆷𑆸𑆹𑆺𑆻𑆼𑆽𑆾𑆿" +
    "𑆁𑆂𑇀𑆀𑇄𑇂𑇃" +
    "𑇐𑇑𑇒𑇓𑇔𑇕𑇖𑇗𑇘𑇙"
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

fun translateCodePoint(cp: Int, target: BrahmiScript): String? {
    if (target == BrahmiScript.NAGARI) return null
    
    val char = cp.toChar()
    when (target) {
        BrahmiScript.SIDDHAM -> return SIDDHAM_MAP[char]
        BrahmiScript.SHARADA -> return SHARADA_MAP[char]
        BrahmiScript.BRAHMI -> return BRAHMI_MAP[char]
        else -> {}
    }

    if (cp < 0x0900 || cp > 0x097F) return null
    val offset = cp - 0x0900

    if (offset == 0x50) { // Devanagari Om
        return when (target) {
            BrahmiScript.TAMIL -> "ௐ"
            BrahmiScript.GUJRATI -> "ૐ"
            else -> null 
        }
    }

    if (offset == 0x64) { // Danda ।
        return when (target) {
            BrahmiScript.KANNADA, BrahmiScript.TELUGU, BrahmiScript.MALAYALAM,
            BrahmiScript.TAMIL, BrahmiScript.GUJRATI, BrahmiScript.SINHALA -> "."
            else -> null
        }
    }

    if (offset == 0x65) { // Double Danda ॥
        return when (target) {
            BrahmiScript.KANNADA, BrahmiScript.TELUGU, BrahmiScript.MALAYALAM,
            BrahmiScript.TAMIL, BrahmiScript.GUJRATI, BrahmiScript.SINHALA -> "."
            else -> null
        }
    }
    
    val outCp = target.blockStart + offset
    val sb = java.lang.StringBuilder()
    sb.appendCodePoint(outCp)
    return sb.toString()
}

fun String.toBrahmiScript(targetScript: BrahmiScript): String {
    if (targetScript == BrahmiScript.NAGARI) return this
    val sb = java.lang.StringBuilder()
    var i = 0
    while (i < this.length) {
        val cp = this.codePointAt(i)
        val translated = translateCodePoint(cp, targetScript)
        if (translated != null) {
            sb.append(translated)
        } else {
            sb.appendCodePoint(cp)
        }
        i += Character.charCount(cp)
    }
    return sb.toString()
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
    val latinBase: String? = null,
    val latinFlick: String? = null
)

val keyConfigs = listOf(
    // Consonants (Row 1)
    KeyConfig(R.id.r1c2, KeyType.CONSONANT, "क", "ख", symBase = "२", symFlick = "2", sym2Base = "∫", sym2Flick = "²", latinBase = "w", latinFlick = "W"),
    KeyConfig(R.id.r1c3, KeyType.CONSONANT, "ब", "भ", symBase = "३", symFlick = "3", sym2Base = "ε", sym2Flick = "³", latinBase = "e", latinFlick = "E"),
    KeyConfig(R.id.r1c4, KeyType.CONSONANT, "ड", "ढ", symBase = "४", symFlick = "4", sym2Base = "ℝ", sym2Flick = "⁴", latinBase = "r", latinFlick = "R"),
    KeyConfig(R.id.r1c5, KeyType.CONSONANT, "ट", "ठ", symBase = "५", symFlick = "5", sym2Base = "⊕", sym2Flick = "⁵", latinBase = "t", latinFlick = "T"),
    KeyConfig(R.id.r1c7, KeyType.CONSONANT, "ह", "ङ", symBase = "७", symFlick = "7", sym2Base = "∇", sym2Flick = "⁷", latinBase = "u", latinFlick = "U"),
    KeyConfig(R.id.r1c8, KeyType.CONSONANT, "ग", "घ", symBase = "८", symFlick = "8", sym2Base = "ⅈ", sym2Flick = "⁸", latinBase = "i", latinFlick = "I"),
    KeyConfig(R.id.r1c9, KeyType.CONSONANT, "द", "ध", symBase = "९", symFlick = "9", sym2Base = "ω", sym2Flick = "⁹", latinBase = "o", latinFlick = "O"),
    KeyConfig(R.id.r1c10, KeyType.CONSONANT, "ज", "झ", symBase = "०", symFlick = "0", sym2Base = "π", sym2Flick = "⁰", latinBase = "p", latinFlick = "P"),

    // Consonants (Row 2)
    KeyConfig(R.id.r2c5, KeyType.CONSONANT, "य", "ळ", symBase = "=", symFlick = "§", sym2Base = "γ", sym2Flick = "⁼", latinBase = "g", latinFlick = "G"),
    KeyConfig(R.id.r2c6, KeyType.CONSONANT, "प", "फ", symBase = "(", symFlick = "{", sym2Base = "η", sym2Flick = "⁽", latinBase = "h", latinFlick = "H"),
    KeyConfig(R.id.r2c7, KeyType.CONSONANT, "र", "ष", symBase = ")", symFlick = "}", sym2Base = "±", sym2Flick = "⁾", latinBase = "j", latinFlick = "J"),
    KeyConfig(R.id.r2c8, KeyType.CONSONANT, "व", "ल", symBase = "@", symFlick = "%", sym2Base = "κ", sym2Flick = "∅", latinBase = "k", latinFlick = "K"),
    KeyConfig(R.id.r2c9, KeyType.CONSONANT, "त", "थ", symBase = ";", symFlick = ":", sym2Base = "λ", sym2Flick = "√", latinBase = "l", latinFlick = "L"),

    // Consonants (Row 3)
    KeyConfig(R.id.r3c4, KeyType.CONSONANT, "म", "ण", symBase = "\\", symFlick = "/", sym2Base = "∈", sym2Flick = "∉", latinBase = "c", latinFlick = "C"),
    KeyConfig(R.id.r3c5, KeyType.CONSONANT, "न", "ञ", symBase = "'", symFlick = "\"", sym2Base = "⊆", sym2Flick = "⊈", latinBase = "v", latinFlick = "V"),
    KeyConfig(R.id.r3c7, KeyType.CONSONANT, "च", "छ", symBase = "]", symFlick = "~", sym2Base = "∂", sym2Flick = "⋃", latinBase = "n", latinFlick = "N"),
    KeyConfig(R.id.r3c8, KeyType.CONSONANT, "स", "श", symBase = "₹", symFlick = "$", sym2Base = "μ", sym2Flick = "⋂", latinBase = "m", latinFlick = "M"),

    // Vowels (Row 1)
    KeyConfig(R.id.r1c1, KeyType.VOWEL, "ओ", "ऒ", "ो", "ॊ", symBase = "१", symFlick = "1", sym2Base = "ψ", sym2Flick = "¹", latinBase = "q", latinFlick = "Q"),
    KeyConfig(R.id.r1c6, KeyType.VOWEL, "ऋ", "ॠ", "ृ", "ॄ", symBase = "६", symFlick = "6", sym2Base = "⊗", sym2Flick = "⁶", latinBase = "y", latinFlick = "Y"),
    
    // Vowels (Row 2)
    KeyConfig(R.id.r2c1, KeyType.VOWEL, "उ", "ऊ", "ु", "ू", symBase = "*", symFlick = "`", sym2Base = "α", sym2Flick = "∏", latinBase = "a", latinFlick = "A"),
    KeyConfig(R.id.r2c2, KeyType.VOWEL, "ए", "ऎ", "े", "ॆ", symBase = "#", symFlick = "^", sym2Base = "σ", sym2Flick = "∑", latinBase = "s", latinFlick = "S"),
    KeyConfig(R.id.r2c3, KeyType.VOWEL, "अ", "आ", "्", "ा", symBase = "+", symFlick = "|", sym2Base = "δ", sym2Flick = "⁺", latinBase = "d", latinFlick = "D"),
    KeyConfig(R.id.r2c4, KeyType.VOWEL, "इ", "ई", "ि", "ी", symBase = "-", symFlick = "_", sym2Base = "φ", sym2Flick = "⁻", latinBase = "f", latinFlick = "F"),
    
    // Vowels (Row 3)
    KeyConfig(R.id.r3c2, KeyType.VOWEL, "ऐ", "औ", "ै", "ौ", symBase = "ऌ", symFlick = "ॡ", sym2Base = "ζ", sym2Flick = "ℤ", latinBase = "z", latinFlick = "Z"),
    
    // Modifiers (Row 3)
    KeyConfig(R.id.r3c3, KeyType.MODIFIER, "ं", "ँ", symBase = "़", symFlick = "ॐ", sym2Base = "χ", sym2Flick = "×", latinBase = "x", latinFlick = "X"),
    KeyConfig(R.id.r3c6, KeyType.MODIFIER, "ः", "ऽ", symBase = "[", symFlick = "&", sym2Base = "β", sym2Flick = "⊂", latinBase = "b", latinFlick = "B"),
    
    // Simple (Row 4)
    KeyConfig(R.id.r4c2, KeyType.SIMPLE, "/", "'", symBase = ",", symFlick = "?", sym2Base = ",", sym2Flick = "?", latinBase = "/", latinFlick = "'"),
    KeyConfig(R.id.r4c4, KeyType.SIMPLE, "।", "!", symBase = ".", symFlick = "!", sym2Base = ".", sym2Flick = "!", latinBase = ".", latinFlick = "!")
)

val configMap = keyConfigs.associateBy { it.id }