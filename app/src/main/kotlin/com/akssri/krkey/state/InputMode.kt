package com.akssri.krkey.state

/**
 * Layer indices.
 */
object KeyboardLayer {
    const val INDIC = 0
    const val LATIN = 1
    const val SYM = 2
    const val SYM_SHIFT = 3
}

/**
 * Represents the active keyboard layer and return-path metadata.
 */
sealed class InputMode(val layer: Int) {
    data object IndicNormal : InputMode(KeyboardLayer.INDIC)
    data object LatinNormal : InputMode(KeyboardLayer.LATIN)
    data class Symbol(val fromLatin: Boolean) : InputMode(KeyboardLayer.SYM)
    data class SymbolShifted(val fromLatin: Boolean) : InputMode(KeyboardLayer.SYM_SHIFT)

    fun isLatin() = this is LatinNormal || (this is Symbol && fromLatin) || (this is SymbolShifted && fromLatin)
    fun isSymbol() = this is Symbol || this is SymbolShifted
    fun isShifted() = this is SymbolShifted
}