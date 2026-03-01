package com.akssri.krkey.state

import com.akssri.krkey.BrahmiScript

/**
 * Immutable keyboard state.
 * Replaces scattered boolean flags with a cohesive state object.
 * All transformations return new instances.
 */
data class KeyboardState(
    val mode: InputMode = InputMode.IndicNormal,
    val script: BrahmiScript = BrahmiScript.DEVANAGARI,
    val currentBaseChar: String = "",
    val isShiftLocked: Boolean = false
) {
    /**
     * Toggles between Indic and Latin normal modes.
     */
    fun toggleLanguage(): KeyboardState {
        val newMode = when (mode) {
            is InputMode.IndicNormal -> InputMode.LatinNormal
            is InputMode.LatinNormal, is InputMode.LatinShifted -> InputMode.IndicNormal
            is InputMode.Symbol -> if (mode.fromLatin) InputMode.IndicNormal else InputMode.LatinNormal
            is InputMode.SymbolShifted -> if (mode.fromLatin) InputMode.IndicNormal else InputMode.LatinNormal
        }
        return copy(mode = newMode, isShiftLocked = false)
    }

    /**
     * Toggles shift behavior (capitalization in Latin, secondary symbols in Symbol mode).
     */
    fun toggleShift(): KeyboardState {
        val newMode = when (mode) {
            is InputMode.Symbol -> InputMode.SymbolShifted(mode.fromLatin)
            is InputMode.SymbolShifted -> InputMode.Symbol(mode.fromLatin)
            is InputMode.LatinNormal -> InputMode.LatinShifted
            is InputMode.LatinShifted -> InputMode.LatinNormal
            is InputMode.IndicNormal -> InputMode.IndicNormal // No shift in Indic base layout
        }
        return copy(mode = newMode, isShiftLocked = false)
    }

    /**
     * Toggle symbol mode.
     * Remembers whether we came from Latin or Indic mode.
     */
    fun toggleSymbol(wasLatinMode: Boolean = mode.isLatin()): KeyboardState {
        val newMode = when (mode) {
            is InputMode.IndicNormal, is InputMode.LatinNormal, is InputMode.LatinShifted ->
                InputMode.Symbol(wasLatinMode)
            is InputMode.Symbol ->
                if (mode.fromLatin) InputMode.LatinNormal else InputMode.IndicNormal
            is InputMode.SymbolShifted ->
                if (mode.fromLatin) InputMode.LatinNormal else InputMode.IndicNormal
        }
        return copy(mode = newMode, isShiftLocked = false)
    }

    /**
     * Update the base character for composition (e.g., consonant for matra attachment).
     */
    fun withBaseChar(baseChar: String): KeyboardState {
        return copy(currentBaseChar = baseChar)
    }

    /**
     * Switch to a different Brahmi script.
     */
    fun withScript(script: BrahmiScript): KeyboardState {
        return copy(script = script)
    }

    /**
     * Clear the base character (typically after committing text).
     */
    fun clearBaseChar(): KeyboardState {
        return copy(currentBaseChar = "")
    }

}
