package com.akssri.krkey.state

/**
 * Represents the input mode of the keyboard.
 * Replaces multiple boolean flags (isLatinMode, isSymbolMode, isShifted, etc.)
 * with a clear state machine.
 */
sealed class InputMode {
    data object IndicNormal : InputMode()
    data object LatinNormal : InputMode()
    data object LatinShifted : InputMode()
    data class Symbol(val fromLatin: Boolean) : InputMode()
    data class SymbolShifted(val fromLatin: Boolean) : InputMode()

    fun isLatin() = this is LatinNormal || this is LatinShifted || (this is Symbol && fromLatin) || (this is SymbolShifted && fromLatin)
    fun isSymbol() = this is Symbol || this is SymbolShifted
    fun isShifted() = this is LatinShifted || this is SymbolShifted
}
