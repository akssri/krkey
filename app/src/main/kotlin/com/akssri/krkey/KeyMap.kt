package com.akssri.krkey

enum class KeyType { SIMPLE, VOWEL, MODIFIER, CONSONANT }

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
    KeyConfig(R.id.r1c3, KeyType.CONSONANT, "भ", "ब", symBase = "३", symFlick = "3", sym2Base = "ε", sym2Flick = "³", latinBase = "e", latinFlick = "E"),
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