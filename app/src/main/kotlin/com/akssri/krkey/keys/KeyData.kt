package com.akssri.krkey.keys

/**
 * Focused key data - replaces 16-parameter KeyConfig.
 * Separates data from resolution logic.
 */
data class KeyData(
    val id: Int,
    val baseText: String,
    val flickText: String,
    val variants: KeyVariants = KeyVariants()
)

/**
 * All variant texts for different modes.
 * Much cleaner than 14 optional parameters scattered in KeyConfig.
 */
data class KeyVariants(
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
)
