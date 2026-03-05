package com.akssri.krkey.state

import com.akssri.krkey.BrahmiScript

/**
 * Immutable keyboard state.
 */
data class KeyboardState(
    val mode: InputMode = InputMode.IndicNormal,
    val script: BrahmiScript = BrahmiScript.DEVANAGARI,
    val currentBaseChar: String = "",
    val isShiftLocked: Boolean = false,
) {
    fun toggleLanguage(): KeyboardState {
        val newMode =
            when (mode) {
                is InputMode.IndicNormal -> InputMode.LatinNormal
                is InputMode.LatinNormal -> InputMode.IndicNormal
                is InputMode.Symbol -> if (mode.fromLatin) InputMode.IndicNormal else InputMode.LatinNormal
                is InputMode.SymbolShifted -> if (mode.fromLatin) InputMode.IndicNormal else InputMode.LatinNormal
            }
        return copy(mode = newMode, isShiftLocked = false)
    }

    fun toggleShift(): KeyboardState {
        val newMode =
            when (mode) {
                is InputMode.Symbol -> InputMode.SymbolShifted(mode.fromLatin)
                is InputMode.SymbolShifted -> InputMode.Symbol(mode.fromLatin)
                else -> mode
            }
        return copy(mode = newMode, isShiftLocked = false)
    }

    fun toggleSymbol(wasLatinMode: Boolean = mode.isLatin()): KeyboardState {
        val newMode =
            when (mode) {
                is InputMode.IndicNormal, is InputMode.LatinNormal -> InputMode.Symbol(wasLatinMode)

                is InputMode.Symbol -> if (mode.fromLatin) InputMode.LatinNormal else InputMode.IndicNormal

                is InputMode.SymbolShifted -> if (mode.fromLatin) InputMode.LatinNormal else InputMode.IndicNormal
            }

        return copy(mode = newMode, isShiftLocked = false)
    }

    fun withBaseChar(baseChar: String) = copy(currentBaseChar = baseChar)

    fun withScript(script: BrahmiScript) = copy(script = script)

    fun clearBaseChar() = copy(currentBaseChar = "")
}
