package com.akssri.krkey.state

/**
 * Represents the input mode of the keyboard.
 * Replaces multiple boolean flags (isLatinMode, isSymbolMode, isShifted, etc.)
 * with a clear state machine.
 */
sealed class InputMode {
    data object IndicNormal : InputMode()
    data object IndicSymbol : InputMode()
    data object IndicSymbolShifted : InputMode()
    data object LatinNormal : InputMode()
    data object LatinShifted : InputMode()
    data object LatinSymbol : InputMode()

    fun isLatin() = this is LatinNormal || this is LatinShifted || this is LatinSymbol
    fun isSymbol() = this is IndicSymbol || this is IndicSymbolShifted || this is LatinSymbol
    fun isShifted() = this is IndicSymbolShifted || this is LatinShifted
}
